package com.cotisapp.service;

import com.cotisapp.domain.entity.*;
import com.cotisapp.domain.enums.*;
import com.cotisapp.dto.response.*;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RapportService {

    private static final Pattern CAT_PATTERN =
            Pattern.compile("^\\[cat:([a-z_]+)]\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    private static final List<String> AV_COLORS = List.of(
            "var(--g2)", "var(--g1)", "var(--re)", "var(--bl)", "#7c3aed", "#0d9488");

    private static final Map<String, String> CAT_LABELS = Map.ofEntries(
            Map.entry("restauration", "🍽 Restauration"),
            Map.entry("transport", "🚗 Transport"),
            Map.entry("fournitures", "📦 Fournitures"),
            Map.entry("loyer", "🏠 Loyer local"),
            Map.entry("energie", "💡 Électricité / Eau"),
            Map.entry("communication", "📞 Communication"),
            Map.entry("sante", "🏥 Santé / Urgence"),
            Map.entry("autre", "📝 Autre"));

    private final OrganisationRepository organisationRepository;
    private final MembreRepository membreRepository;
    private final CompteRepository compteRepository;
    private final EmpruntRepository empruntRepository;
    private final OperationRepository operationRepository;
    private final SuiviMensuelRepository suiviMensuelRepository;
    private final RegleOperationRepository regleOperationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ExerciceService exerciceService;
    private final JourneeReunionRepository journeeReunionRepository;

    @Transactional(readOnly = true)
    public RapportResponse generer(Long orgId, String periodeParam) {
        organisationRepository.findById(orgId).orElseThrow(() -> new BusinessException("Organisation introuvable"));

        PeriodeScope periode = resoudrePeriode(periodeParam);
        LocalDate today = LocalDate.now();
        List<Membre> membresActifs = membreRepository.findByOrganisationIdAndActifTrue(orgId);
        long bureau = membresActifs.stream().filter(m -> m.getPoste() != PosteMembre.SIMPLE).count();

        List<Operation> opsPeriode = new ArrayList<>(
                operationRepository
                        .findByOrganisationIdAndTypeOperationInAndDateOperationBetweenOrderByDateOperationDescDateCreationDesc(
                                orgId,
                                List.of(
                                        TypeOperation.COTISATION,
                                        TypeOperation.COTISATION_MOIS,
                                        TypeOperation.DEPENSE,
                                        TypeOperation.PENALITE,
                                        TypeOperation.AMENDE,
                                        TypeOperation.REMBOURSEMENT,
                                        TypeOperation.EMPRUNT),
                                periode.debut(),
                                periode.fin())
                        .stream()
                        .filter(RapportDonneesHelper::operationComptable)
                        .toList());
        opsPeriode = enrichirOpsCotisationMois(orgId, periode.moisAnnee(), opsPeriode);
        opsPeriode.forEach(o -> o.getMouvements().size());

        Long exerciceId = exerciceService.requireExerciceCourantId(orgId);
        LocalDate finEffective = periode.fin().isBefore(today) ? periode.fin() : today;
        int nbPlanads = finEffective.isBefore(periode.debut())
                ? 0
                : (int) journeeReunionRepository.countByExerciceIdAndDateReunionBetween(
                        exerciceId, periode.debut(), finEffective);
        int semainesAttendues = RapportDonneesHelper.compterSemainesAttendues(
                periode.debut(), periode.fin(), today, nbPlanads);

        Map<Long, List<Operation>> opsParMembre = grouperOpsParMembre(opsPeriode);

        BigDecimal montantHebdo = montantRegle(orgId, TypeOperation.COTISATION);
        BigDecimal montantMoisRegle = montantRegle(orgId, TypeOperation.COTISATION_MOIS);
        BigDecimal totalCotisations = sommeTypes(
                opsPeriode, TypeOperation.COTISATION, TypeOperation.COTISATION_MOIS);

        List<SuiviMensuel> suivisMois = periode.moisAnnee() != null
                ? suiviMensuelRepository.findByOrganisationIdAndExerciceIdAndMoisAnnee(
                        orgId, exerciceId, periode.moisAnnee())
                : List.of();
        Map<Long, SuiviMensuel> suiviParMembre = suivisMois.stream()
                .collect(Collectors.toMap(SuiviMensuel::getMembreId, s -> s, (a, b) -> a));

        List<Emprunt> emprunts = empruntRepository.findByOrganisationId(orgId);

        Map<Long, String> nomsUtilisateurs = chargerNomsUtilisateurs(opsPeriode);

        return RapportResponse.builder()
                .periode(periode.valeur())
                .periodeLabel(periode.label())
                .nbMembresActifs(membresActifs.size())
                .nbMembresBureau((int) bureau)
                .periodesDisponibles(construirePeriodesDisponibles(orgId))
                .heroStats(construireHero(orgId, opsPeriode, emprunts, today, totalCotisations))
                .cotisationsParSemaine(construireGraphiqueHebdo(
                        opsPeriode, montantHebdo, membresActifs.size()))
                .participation(construireParticipation(
                        membresActifs,
                        opsParMembre,
                        suiviParMembre,
                        semainesAttendues,
                        periode.moisAnnee(),
                        montantMoisRegle))
                .totalCotisations(totalCotisations)
                .cotisationsMembres(construireCotisationsMembres(
                        membresActifs,
                        opsParMembre,
                        suiviParMembre,
                        montantHebdo,
                        semainesAttendues,
                        periode.moisAnnee(),
                        montantMoisRegle))
                .emprunts(construireEmpruntsCards(emprunts, membresActifs, today))
                .empruntsSynthese(construireSyntheseEmprunts(emprunts, opsPeriode, today))
                .membresFinancier(construireMembresFinancier(membresActifs, emprunts, today))
                .depenses(construireDepenses(opsPeriode, nomsUtilisateurs))
                .totalDepenses(sommeTypes(opsPeriode, TypeOperation.DEPENSE))
                .build();
    }

    private List<RapportPeriodeOption> construirePeriodesDisponibles(Long orgId) {
        Set<String> mois = new TreeSet<>(Comparator.reverseOrder());
        mois.add(YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM")));
        suiviMensuelRepository.findByOrganisationId(orgId).stream()
                .map(SuiviMensuel::getMoisAnnee)
                .filter(Objects::nonNull)
                .forEach(mois::add);
        operationRepository.findByOrganisationIdOrderByDateCreationDesc(orgId).stream()
                .map(Operation::getMoisAnnee)
                .filter(m -> m != null && !m.isBlank())
                .forEach(mois::add);

        List<RapportPeriodeOption> options = mois.stream()
                .limit(12)
                .map(m -> RapportPeriodeOption.builder().value(m).label(labelMois(m)).build())
                .collect(Collectors.toCollection(ArrayList::new));

        String annee = String.valueOf(YearMonth.now().getYear());
        options.add(RapportPeriodeOption.builder().value(annee).label("Année " + annee).build());
        return options;
    }

    private List<RapportHeroStatResponse> construireHero(
            Long orgId,
            List<Operation> ops,
            List<Emprunt> emprunts,
            LocalDate today,
            BigDecimal totalCotisations) {
        long enCours = emprunts.stream().filter(e -> e.getStatut() == StatutEmprunt.EN_COURS).count();
        long enRetard = emprunts.stream()
                .filter(e -> e.getStatut() == StatutEmprunt.EN_COURS)
                .filter(e -> empruntEnRetard(e, today))
                .count();
        BigDecimal encoursActifs = emprunts.stream()
                .filter(e -> e.getStatut() == StatutEmprunt.EN_COURS)
                .map(e -> e.getMontantTotal().subtract(e.getMontantRembourse()).max(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String trendEmprunts = enCours == 0
                ? "Aucun emprunt actif"
                : (enRetard > 0
                        ? enCours + " emprunt(s) · ⚠ " + enRetard + " en retard"
                        : enCours + " emprunt(s) · Aucun retard");
        BigDecimal penalites = sommeTypes(ops, TypeOperation.PENALITE, TypeOperation.AMENDE);
        long nbPenalites = ops.stream().filter(o -> o.getTypeOperation() == TypeOperation.PENALITE).count();
        long nbAmendes = ops.stream().filter(o -> o.getTypeOperation() == TypeOperation.AMENDE).count();

        return List.of(
                RapportHeroStatResponse.builder()
                        .valeur(formatFcfa(totalCotisations))
                        .label("Cotisations collectées")
                        .trend("Période sélectionnée")
                        .build(),
                RapportHeroStatResponse.builder()
                        .valeur(formatFcfa(encoursActifs))
                        .label("Emprunts actifs")
                        .trend(trendEmprunts)
                        .build(),
                RapportHeroStatResponse.builder()
                        .valeur(formatFcfa(soldeOrg(orgId, TypeCompte.CAISSE)))
                        .label("Solde Caisse")
                        .trend("Temps réel")
                        .build(),
                RapportHeroStatResponse.builder()
                        .valeur(formatFcfa(soldeOrg(orgId, TypeCompte.SOLIDARITE)))
                        .label("Fonds Solidarité")
                        .trend("Temps réel")
                        .build(),
                RapportHeroStatResponse.builder()
                        .valeur(formatFcfa(penalites))
                        .label("Pénalités / Amendes")
                        .trend(nbPenalites + " pénalité(s) · " + nbAmendes + " amende(s)")
                        .build());
    }

    private List<RapportBarChartItemResponse> construireGraphiqueHebdo(
            List<Operation> ops, BigDecimal montantHebdo, int nbMembresActifs) {
        Map<String, BigDecimal> parSemaine = new TreeMap<>();
        for (Operation op : ops) {
            if (op.getTypeOperation() != TypeOperation.COTISATION) {
                continue;
            }
            String cle = RapportDonneesHelper.cleSemaineCotisation(op);
            if (cle == null) {
                continue;
            }
            parSemaine.merge(cle, op.getMontant(), BigDecimal::add);
        }
        if (parSemaine.isEmpty()) {
            return List.of();
        }
        BigDecimal max = parSemaine.values().stream().max(BigDecimal::compareTo).orElse(BigDecimal.ONE);
        BigDecimal cible = montantHebdo.multiply(BigDecimal.valueOf(Math.max(nbMembresActifs, 1)));
        if (cible.compareTo(BigDecimal.ZERO) <= 0) {
            cible = max;
        }
        final BigDecimal cibleFinale = cible.max(BigDecimal.ONE);
        return parSemaine.entrySet().stream()
                .map(e -> {
                    int hauteur = max.compareTo(BigDecimal.ZERO) > 0
                            ? e.getValue()
                                    .multiply(BigDecimal.valueOf(100))
                                    .divide(max, 0, RoundingMode.HALF_UP)
                                    .intValue()
                            : 0;
                    boolean sousCible = e.getValue().compareTo(cibleFinale) < 0;
                    return RapportBarChartItemResponse.builder()
                            .label(RapportDonneesHelper.libelleSemaineGraphique(e.getKey()))
                            .valeurLabel(formatCourt(e.getValue()))
                            .heightPct(Math.max(8, hauteur))
                            .belowTarget(sousCible)
                            .build();
                })
                .toList();
    }

    private RapportParticipationResponse construireParticipation(
            List<Membre> membres,
            Map<Long, List<Operation>> opsParMembre,
            Map<Long, SuiviMensuel> suiviParMembre,
            int semainesAttendues,
            String moisAnnee,
            BigDecimal montantMoisRegle) {
        int total = membres.size();
        int aJour = 0;
        int hebdoOk = 0;
        int moisOk = 0;
        int moisTotal = 0;
        int bureauOk = 0;
        int bureauTotal = 0;
        int semainesRef = Math.max(semainesAttendues, 1);
        int sommePctMembres = 0;

        for (Membre m : membres) {
            List<Operation> ops = opsParMembre.getOrDefault(m.getId(), List.of());
            long nbSemainesPayees = RapportDonneesHelper.compterSemainesHebdoPayees(ops);
            SuiviMensuel suivi = suiviParMembre.get(m.getId());
            boolean contribueHebdo = nbSemainesPayees > 0;
            boolean mensuelDu = RapportDonneesHelper.cotisationMensuelleDue(suivi, montantMoisRegle);
            boolean moisAJour = RapportDonneesHelper.membreMoisAJour(suivi, ops, moisAnnee, montantMoisRegle);

            sommePctMembres += RapportDonneesHelper.pctParticipationMoyenne(
                    nbSemainesPayees, semainesRef, suivi, ops, moisAnnee, montantMoisRegle);

            if (m.getPoste() != PosteMembre.SIMPLE) {
                bureauTotal++;
                if (RapportDonneesHelper.membreAJour(
                        nbSemainesPayees, semainesRef, suivi, ops, moisAnnee, montantMoisRegle)) {
                    bureauOk++;
                }
            }
            if (contribueHebdo) {
                hebdoOk++;
            }
            if (mensuelDu) {
                moisTotal++;
                if (moisAJour) {
                    moisOk++;
                }
            }
            if (RapportDonneesHelper.membreAJour(
                    nbSemainesPayees, semainesRef, suivi, ops, moisAnnee, montantMoisRegle)) {
                aJour++;
            }
        }

        int pct = total > 0 ? Math.round((float) sommePctMembres / total) : 0;
        return RapportParticipationResponse.builder()
                .pctGlobal(pct)
                .membresAJour(aJour)
                .membresTotal(total)
                .hebdoPayes(hebdoOk)
                .hebdoTotal(total)
                .moisPayes(moisOk)
                .moisTotal(moisTotal > 0 ? moisTotal : total)
                .bureauPayes(bureauOk)
                .bureauTotal(bureauTotal)
                .build();
    }

    private List<Operation> enrichirOpsCotisationMois(
            Long orgId, String moisAnnee, List<Operation> opsPeriode) {
        if (moisAnnee == null || moisAnnee.isBlank()) {
            return opsPeriode;
        }
        Map<Long, Operation> parId = new LinkedHashMap<>();
        for (Operation o : opsPeriode) {
            parId.put(o.getId(), o);
        }
        for (Operation o : operationRepository.findCotisationsMoisPourMois(orgId, moisAnnee)) {
            if (RapportDonneesHelper.operationComptable(o)) {
                parId.putIfAbsent(o.getId(), o);
            }
        }
        return new ArrayList<>(parId.values());
    }

    private Map<Long, List<Operation>> grouperOpsParMembre(List<Operation> ops) {
        Set<Long> compteIds = ops.stream()
                .flatMap(o -> o.getMouvements().stream())
                .map(MouvementCompte::getCompteId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Compte> comptes = compteIds.isEmpty()
                ? Map.of()
                : compteRepository.findAllById(compteIds).stream()
                        .collect(Collectors.toMap(Compte::getId, c -> c));

        Map<Long, List<Operation>> parMembre = new HashMap<>();
        for (Operation op : ops) {
            Long membreId = RapportDonneesHelper.resoudreMembreId(op, comptes);
            if (membreId != null) {
                parMembre.computeIfAbsent(membreId, k -> new ArrayList<>()).add(op);
            }
        }
        return parMembre;
    }

    private List<RapportCotisationMembreResponse> construireCotisationsMembres(
            List<Membre> membres,
            Map<Long, List<Operation>> opsParMembre,
            Map<Long, SuiviMensuel> suiviParMembre,
            BigDecimal montantHebdo,
            int semainesAttendues,
            String moisAnnee,
            BigDecimal montantMoisRegle) {
        int semainesRef = Math.max(semainesAttendues, 1);
        return membres.stream()
                .sorted(Comparator.comparing(Membre::getPoste).thenComparing(Membre::getNomComplet,
                        String.CASE_INSENSITIVE_ORDER))
                .map(m -> {
                    List<Operation> ops = opsParMembre.getOrDefault(m.getId(), List.of());
                    long nbSemainesPayees = RapportDonneesHelper.compterSemainesHebdoPayees(ops);
                    BigDecimal totalHebdo = ops.stream()
                            .filter(o -> o.getTypeOperation() == TypeOperation.COTISATION)
                            .map(Operation::getMontant)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    SuiviMensuel suivi = suiviParMembre.get(m.getId());
                    BigDecimal payeMois = RapportDonneesHelper.montantMoisPaye(suivi, ops, moisAnnee);
                    BigDecimal duMois = RapportDonneesHelper.montantMoisDu(suivi, montantMoisRegle);

                    String hebdo = nbSemainesPayees > 0
                            ? nbSemainesPayees + "/" + semainesRef + " PLANAD · " + formatFcfa(totalHebdo)
                            : "—";
                    String mois;
                    if (RapportDonneesHelper.cotisationMensuelleDue(suivi, montantMoisRegle)) {
                        mois = formatFcfa(payeMois) + " / " + formatFcfa(duMois);
                    } else if (payeMois.compareTo(BigDecimal.ZERO) > 0) {
                        mois = formatFcfa(payeMois);
                    } else {
                        mois = "—";
                    }
                    BigDecimal solidariteOps = ops.stream()
                            .filter(o -> o.getTypeOperation() == TypeOperation.COTISATION
                                    || o.getTypeOperation() == TypeOperation.COTISATION_MOIS)
                            .map(Operation::getMontant)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    String solidarite = solidariteOps.compareTo(BigDecimal.ZERO) > 0
                            ? formatFcfa(soldeMembre(m.getId(), TypeCompte.SOLIDARITE))
                            : "—";

                    BigDecimal total = totalHebdo.add(payeMois);
                    String statut;
                    String statutLabel;
                    if (RapportDonneesHelper.membreAJour(
                            nbSemainesPayees, semainesRef, suivi, ops, moisAnnee, montantMoisRegle)) {
                        statut = "complet";
                        statutLabel = "✓ Complet";
                    } else if (nbSemainesPayees > 0
                            || total.compareTo(BigDecimal.ZERO) > 0
                            || (suivi != null && suivi.getStatut() == StatutSuiviMensuel.PARTIEL)) {
                        statut = "partiel";
                        statutLabel = "◐ Partiel";
                    } else {
                        statut = "manque";
                        statutLabel = "⚠ Manque";
                    }

                    PosteStyle ps = posteStyle(m.getPoste());
                    return RapportCotisationMembreResponse.builder()
                            .nom(m.getNomComplet())
                            .code(m.getCodeMembre())
                            .initials(initiales(m))
                            .avColor(couleurAvatar(m.getId()))
                            .posteLabel(ps.label())
                            .posteBadgeClass(ps.badgeClass())
                            .hebdo(hebdo)
                            .mois(mois)
                            .solidarite(solidarite)
                            .total(formatFcfa(total))
                            .statut(statut)
                            .statutLabel(statutLabel)
                            .totalMontant(total)
                            .build();
                })
                .toList();
    }

    private List<RapportEmpruntCardResponse> construireEmpruntsCards(
            List<Emprunt> emprunts, List<Membre> membres, LocalDate today) {
        Map<Long, Membre> parId = membres.stream().collect(Collectors.toMap(Membre::getId, m -> m));
        return emprunts.stream()
                .filter(e -> e.getStatut() == StatutEmprunt.EN_COURS)
                .map(e -> {
                    Membre m = parId.get(e.getMembreId());
                    String nom = m != null ? m.getNomComplet() : "Membre";
                    boolean retard = empruntEnRetard(e, today);
                    int pct = e.getMontantTotal().compareTo(BigDecimal.ZERO) > 0
                            ? e.getMontantRembourse()
                                    .multiply(BigDecimal.valueOf(100))
                                    .divide(e.getMontantTotal(), 0, RoundingMode.HALF_UP)
                                    .intValue()
                            : 0;
                    String typeLabel = switch (e.getTypeEmprunt()) {
                        case ETALE -> "Étalé";
                        case SOLIDARITE -> "Solidarité";
                        case CAISSE -> "Caisse";
                    };
                    long echRestantes = e.getEcheances().stream()
                            .filter(ech -> ech.getStatut() != StatutEcheance.PAYE)
                            .count();
                    String border;
                    String bar;
                    String badge;
                    String badgeClass;
                    if (retard) {
                        border = "retard";
                        bar = "red";
                        badge = "⚠ Retard";
                        badgeClass = "b-red";
                    } else if (e.getTypeEmprunt() == TypeEmprunt.SOLIDARITE) {
                        border = "sol";
                        bar = "blue";
                        badge = "Solidarité";
                        badgeClass = "b-blue";
                    } else {
                        border = "cours";
                        bar = "green";
                        badge = typeLabel;
                        badgeClass = "b-or";
                    }
                    return RapportEmpruntCardResponse.builder()
                            .nom(nom)
                            .badge(badge)
                            .badgeClass(badgeClass)
                            .detail(typeLabel + " · " + echRestantes + " échéance(s) restante(s)")
                            .rembourse(formatFcfa(e.getMontantRembourse()).replace(" F", ""))
                            .total(formatFcfa(e.getMontantTotal()))
                            .pct(Math.min(100, pct))
                            .barClass(bar)
                            .borderClass(border)
                            .bgClass(retard ? "re2" : null)
                            .build();
                })
                .toList();
    }

    private RapportEmpruntSyntheseResponse construireSyntheseEmprunts(
            List<Emprunt> emprunts, List<Operation> ops, LocalDate today) {
        long enCours = emprunts.stream().filter(e -> e.getStatut() == StatutEmprunt.EN_COURS).count();
        long enRetard = emprunts.stream()
                .filter(e -> e.getStatut() == StatutEmprunt.EN_COURS)
                .filter(e -> empruntEnRetard(e, today))
                .count();
        long soldes = emprunts.stream().filter(e -> e.getStatut() == StatutEmprunt.SOLDE).count();
        BigDecimal encours = emprunts.stream()
                .filter(e -> e.getStatut() == StatutEmprunt.EN_COURS)
                .map(e -> e.getMontantTotal().subtract(e.getMontantRembourse()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal rembMois = sommeTypes(ops, TypeOperation.REMBOURSEMENT);
        BigDecimal fraisRestants = emprunts.stream()
                .filter(e -> e.getStatut() == StatutEmprunt.EN_COURS)
                .map(e -> e.getMontantFrais() != null ? e.getMontantFrais() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return RapportEmpruntSyntheseResponse.builder()
                .enCours(enCours)
                .enRetard(enRetard)
                .soldesMois(soldes)
                .encoursTotal(encours)
                .remboursementsMois(rembMois)
                .fraisRestants(fraisRestants)
                .build();
    }

    private List<RapportMembreFinancierResponse> construireMembresFinancier(
            List<Membre> membres, List<Emprunt> emprunts, LocalDate today) {
        Map<Long, BigDecimal> empruntRestant = emprunts.stream()
                .filter(e -> e.getStatut() == StatutEmprunt.EN_COURS)
                .collect(Collectors.groupingBy(
                        Emprunt::getMembreId,
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                e -> e.getMontantTotal().subtract(e.getMontantRembourse()).max(BigDecimal.ZERO),
                                BigDecimal::add)));

        return membres.stream()
                .sorted(Comparator.comparing(Membre::getNomComplet, String.CASE_INSENSITIVE_ORDER))
                .map(m -> {
                    BigDecimal epargne = soldeMembre(m.getId(), TypeCompte.EPARGNE_HEBDO)
                            .add(soldeMembre(m.getId(), TypeCompte.EPARGNE_MOIS));
                    BigDecimal solidarite = soldeMembre(m.getId(), TypeCompte.SOLIDARITE);
                    BigDecimal penalite = soldeMembre(m.getId(), TypeCompte.PENALITE);
                    BigDecimal amende = soldeMembre(m.getId(), TypeCompte.AMENDE);
                    BigDecimal emprunt = empruntRestant.getOrDefault(m.getId(), BigDecimal.ZERO);
                    boolean retard = emprunts.stream()
                            .filter(e -> e.getMembreId().equals(m.getId()))
                            .filter(e -> e.getStatut() == StatutEmprunt.EN_COURS)
                            .anyMatch(e -> empruntEnRetard(e, today));
                    boolean penalitePos = penalite.compareTo(BigDecimal.ZERO) > 0;

                    String situation;
                    String situationClass;
                    if (retard || penalitePos) {
                        situation = "⚠ Retard";
                        situationClass = "b-red";
                    } else {
                        situation = "✓ Bon";
                        situationClass = "b-green";
                    }

                    PosteStyle ps = posteStyle(m.getPoste());
                    return RapportMembreFinancierResponse.builder()
                            .nom(m.getNomComplet())
                            .code(m.getCodeMembre())
                            .initials(initiales(m))
                            .avColor(couleurAvatar(m.getId()))
                            .posteHtml(ps.badgeClass())
                            .epargne(formatFcfa(epargne))
                            .solidarite(formatFcfa(solidarite))
                            .penalite(formatFcfa(penalite))
                            .amende(formatFcfa(amende))
                            .emprunt(emprunt.compareTo(BigDecimal.ZERO) > 0
                                    ? formatFcfa(emprunt) + (retard ? " ⚠" : "")
                                    : "—")
                            .situation(situation)
                            .situationClass(situationClass)
                            .empruntRestant(emprunt)
                            .build();
                })
                .toList();
    }

    private List<RapportDepenseResponse> construireDepenses(
            List<Operation> ops, Map<Long, String> nomsUtilisateurs) {
        return ops.stream()
                .filter(o -> o.getTypeOperation() == TypeOperation.DEPENSE)
                .map(o -> {
                    ParsedDep p = parseDepense(o.getObservation());
                    String catLabel = CAT_LABELS.getOrDefault(p.categorieId(), CAT_LABELS.get("autre"));
                    return RapportDepenseResponse.builder()
                            .categorie(catLabel)
                            .categorieId(p.categorieId())
                            .beneficiaire(p.beneficiaire())
                            .description(p.description())
                            .montant(o.getMontant())
                            .dateLabel(formatDateCourt(o.getDateOperation()))
                            .saisiPar(nomsUtilisateurs.getOrDefault(o.getUtilisateurId(), "—"))
                            .build();
                })
                .toList();
    }

    private Map<Long, String> chargerNomsUtilisateurs(List<Operation> ops) {
        Set<Long> ids = ops.stream().map(Operation::getUtilisateurId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return utilisateurRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(
                        Utilisateur::getId,
                        u -> (u.getPrenom().charAt(0) + ". " + u.getNom()).trim()));
    }

    private PeriodeScope resoudrePeriode(String param) {
        if (param != null && param.matches("\\d{4}")) {
            int y = Integer.parseInt(param);
            return new PeriodeScope(
                    param,
                    "Année " + y,
                    LocalDate.of(y, 1, 1),
                    LocalDate.of(y, 12, 31),
                    null);
        }
        YearMonth ym;
        try {
            ym = param != null && !param.isBlank()
                    ? YearMonth.parse(param)
                    : YearMonth.now();
        } catch (Exception e) {
            ym = YearMonth.now();
        }
        return new PeriodeScope(
                ym.toString(),
                labelMois(ym.toString()),
                ym.atDay(1),
                ym.atEndOfMonth(),
                ym.toString());
    }

    private String labelMois(String moisAnnee) {
        try {
            YearMonth ym = YearMonth.parse(moisAnnee);
            String m = ym.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH);
            return m.substring(0, 1).toUpperCase(Locale.FRENCH) + m.substring(1) + " " + ym.getYear();
        } catch (Exception e) {
            return moisAnnee;
        }
    }

    private BigDecimal montantRegle(Long orgId, TypeOperation type) {
        return regleOperationRepository
                .findByOrganisationIdAndTypeOperationAndActifTrue(orgId, type)
                .map(r -> r.getMontantMin() != null ? r.getMontantMin() : BigDecimal.ZERO)
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal soldeOrg(Long orgId, TypeCompte type) {
        return compteRepository
                .findByOrganisationIdAndTypeCompteAndProprietaire(
                        orgId, type, ProprietaireCompte.ORGANISATION)
                .map(Compte::getSolde)
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal soldeMembre(Long membreId, TypeCompte type) {
        return compteRepository
                .findByMembreIdAndTypeCompte(membreId, type)
                .map(Compte::getSolde)
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal sommeTypes(List<Operation> ops, TypeOperation... types) {
        Set<TypeOperation> set = Set.of(types);
        return ops.stream()
                .filter(o -> set.contains(o.getTypeOperation()))
                .map(Operation::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean empruntEnRetard(Emprunt emprunt, LocalDate today) {
        if (emprunt.getEcheances() == null) {
            return false;
        }
        for (Echeance ech : emprunt.getEcheances()) {
            if (ech.getStatut() == StatutEcheance.PAYE) {
                continue;
            }
            if (ech.getDateEcheance().isBefore(today)) {
                return true;
            }
        }
        return false;
    }

    private ParsedDep parseDepense(String observation) {
        if (observation == null || observation.isBlank()) {
            return new ParsedDep("autre", null, null);
        }
        Matcher m = CAT_PATTERN.matcher(observation.trim());
        if (!m.find()) {
            return new ParsedDep("autre", null, observation);
        }
        String catId = m.group(1).toLowerCase();
        String reste = m.group(2) != null ? m.group(2).trim() : "";
        String benef = null;
        String desc = reste;
        if (reste.startsWith("Bénéficiaire:")) {
            int sep = reste.indexOf(" — ");
            if (sep > 0) {
                benef = reste.substring("Bénéficiaire:".length(), sep).trim();
                desc = reste.substring(sep + 3).trim();
            } else {
                benef = reste.substring("Bénéficiaire:".length()).trim();
                desc = "";
            }
        }
        return new ParsedDep(catId, benef, desc.isEmpty() ? null : desc);
    }

    private PosteStyle posteStyle(PosteMembre poste) {
        return switch (poste) {
            case PRESIDENT -> new PosteStyle("👑 Président(e)", "b-pu");
            case VICE_PRESIDENT -> new PosteStyle("Vice-président(e)", "b-pu");
            case SECRETAIRE_GENERAL -> new PosteStyle("📝 S.G.", "b-blue");
            case SECRETAIRE_GENERAL_ADJOINT -> new PosteStyle("📝 S.G. adj.", "b-blue");
            case TRESORIER -> new PosteStyle("💼 Trésorière", "b-green");
            case SUPERVISEUR -> new PosteStyle("Superviseur", "b-green");
            default -> new PosteStyle("👤 Simple", "b-gray");
        };
    }

    private String initiales(Membre m) {
        String p = m.getPrenom() != null && !m.getPrenom().isBlank() ? m.getPrenom().substring(0, 1) : "";
        String n = m.getNom() != null && !m.getNom().isBlank() ? m.getNom().substring(0, 1) : "";
        return (p + n).toUpperCase(Locale.ROOT);
    }

    private String couleurAvatar(Long membreId) {
        int i = membreId != null ? (int) (membreId % AV_COLORS.size()) : 0;
        return AV_COLORS.get(Math.max(0, i));
    }

    private String formatFcfa(BigDecimal montant) {
        if (montant == null) {
            return "0 F";
        }
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.FRENCH);
        sym.setGroupingSeparator(' ');
        DecimalFormat df = new DecimalFormat("#,##0", sym);
        return df.format(montant.setScale(0, RoundingMode.HALF_UP)) + " F";
    }

    private String formatCourt(BigDecimal montant) {
        long v = montant.setScale(0, RoundingMode.HALF_UP).longValue();
        if (v >= 1_000_000) {
            return (v / 1_000_000) + "M";
        }
        if (v >= 1_000) {
            return (v / 1_000) + "k";
        }
        return String.valueOf(v);
    }

    private String formatDateCourt(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("dd/MM"));
    }

    private record PeriodeScope(String valeur, String label, LocalDate debut, LocalDate fin, String moisAnnee) {}

    private record PosteStyle(String label, String badgeClass) {}

    private record ParsedDep(String categorieId, String beneficiaire, String description) {}
}
