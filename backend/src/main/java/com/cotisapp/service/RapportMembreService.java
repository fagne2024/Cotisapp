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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RapportMembreService {

    private static final List<String> AV_COLORS = List.of(
            "#7c3aed", "#1e6fa8", "#1a5c3a", "#c9922a", "#c0392b", "#2d7a52");

    private final MembreRepository membreRepository;
    private final CompteRepository compteRepository;
    private final EmpruntRepository empruntRepository;
    private final OperationRepository operationRepository;
    private final SuiviMensuelRepository suiviMensuelRepository;
    private final RegleOperationRepository regleOperationRepository;
    private final MembreFicheService membreFicheService;
    private final ExerciceService exerciceService;
    private final JourneeReunionRepository journeeReunionRepository;

    @Transactional(readOnly = true)
    public RapportMembreResponse generer(Long orgId, Long membreId, String periodeParam) {
        Membre membre = membreRepository
                .findByIdAndOrganisationId(membreId, orgId)
                .orElseThrow(() -> new BusinessException("Membre introuvable"));

        PeriodeScope periode = resoudrePeriode(periodeParam);
        LocalDate today = LocalDate.now();
        List<Operation> ops = operationRepository
                .findByMembreIdAndDateOperationBetweenOrderByDateOperationDescDateCreationDesc(
                        membreId, periode.debut(), periode.fin())
                .stream()
                .filter(RapportDonneesHelper::operationComptable)
                .toList();

        Long exerciceId = exerciceService.requireExerciceCourantId(orgId);
        LocalDate finEffective = periode.fin().isBefore(today) ? periode.fin() : today;
        int nbPlanads = finEffective.isBefore(periode.debut())
                ? 0
                : (int) journeeReunionRepository.countByExerciceIdAndDateReunionBetween(
                        exerciceId, periode.debut(), finEffective);
        int semainesAttendues = RapportDonneesHelper.compterSemainesAttendues(
                periode.debut(), periode.fin(), today, nbPlanads);
        int semainesRef = Math.max(semainesAttendues, 1);

        BigDecimal montantHebdo = montantRegle(orgId, TypeOperation.COTISATION);
        BigDecimal montantMoisRegle = montantRegle(orgId, TypeOperation.COTISATION_MOIS);
        long nbSemainesPayees = RapportDonneesHelper.compterSemainesHebdoPayees(ops);
        BigDecimal totalHebdo = sommeTypes(ops, TypeOperation.COTISATION);

        SuiviMensuel suivi = periode.moisAnnee() != null
                ? suiviMensuelRepository.findByMembreIdAndMoisAnnee(membreId, periode.moisAnnee()).orElse(null)
                : null;
        BigDecimal payeMois = RapportDonneesHelper.montantMoisPaye(suivi, ops, periode.moisAnnee());

        BigDecimal totalCotisations = totalHebdo.add(payeMois);
        String statut;
        String statutLabel;
        if (RapportDonneesHelper.membreAJour(
                nbSemainesPayees, semainesRef, suivi, ops, periode.moisAnnee(), montantMoisRegle)) {
            statut = "complet";
            statutLabel = "✓ Complet";
        } else if (nbSemainesPayees > 0
                || totalCotisations.compareTo(BigDecimal.ZERO) > 0
                || (suivi != null && suivi.getStatut() == StatutSuiviMensuel.PARTIEL)) {
            statut = "partiel";
            statutLabel = "◐ Partiel";
        } else {
            statut = "manque";
            statutLabel = "⚠ Manque";
        }

        List<Emprunt> empruntsMembre = empruntRepository.findByMembreIdAndOrganisationId(membreId, orgId);
        MembreSoldeMembreResponse solde = membreFicheService.calculerSoldeMembre(orgId, membreId);
        PosteStyle ps = posteStyle(membre.getPoste());

        return RapportMembreResponse.builder()
                .membreId(membreId)
                .nom(membre.getNomComplet())
                .code(membre.getCodeMembre())
                .initials(initiales(membre))
                .avColor(couleurAvatar(membreId))
                .posteLabel(ps.label())
                .posteBadgeClass(ps.badgeClass())
                .periode(periode.valeur())
                .periodeLabel(periode.label())
                .periodesDisponibles(construirePeriodesDisponibles(membreId))
                .heroStats(construireHero(solde, ops, empruntsMembre, today, totalCotisations, nbSemainesPayees))
                .hebdo(nbSemainesPayees > 0
                        ? nbSemainesPayees + "/" + semainesRef + " PLANAD · " + formatFcfa(totalHebdo)
                        : "—")
                .mois(libelleMois(suivi, payeMois, montantMoisRegle))
                .solidarite(formatFcfa(soldeMembre(orgId, membreId, TypeCompte.SOLIDARITE)))
                .totalCotisationsLabel(formatFcfa(totalCotisations))
                .totalCotisations(totalCotisations)
                .statutCotisation(statut)
                .statutCotisationLabel(statutLabel)
                .cotisationsParSemaine(construireGraphiqueHebdo(ops, montantHebdo))
                .emprunts(construireEmpruntsCards(empruntsMembre, today))
                .operations(construireOperations(ops))
                .soldeMembre(solde)
                .comptes(construireComptes(orgId, membreId))
                .build();
    }

    private List<RapportPeriodeOption> construirePeriodesDisponibles(Long membreId) {
        Set<String> mois = new TreeSet<>(Comparator.reverseOrder());
        mois.add(YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM")));
        operationRepository.findByMembreIdOrderByDateCreationDesc(membreId).stream()
                .map(Operation::getMoisAnnee)
                .filter(m -> m != null && !m.isBlank())
                .forEach(mois::add);
        operationRepository.findByMembreIdOrderByDateCreationDesc(membreId).stream()
                .map(o -> o.getDateOperation().format(DateTimeFormatter.ofPattern("yyyy-MM")))
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
            MembreSoldeMembreResponse solde,
            List<Operation> ops,
            List<Emprunt> emprunts,
            LocalDate today,
            BigDecimal totalCotisations,
            long nbSemainesPayees) {
        BigDecimal epargne = solde.getEpargne() != null ? solde.getEpargne() : BigDecimal.ZERO;
        BigDecimal empruntEncours = emprunts.stream()
                .filter(e -> e.getStatut() == StatutEmprunt.EN_COURS)
                .map(e -> e.getMontantTotal().subtract(e.getMontantRembourse()).max(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long enRetard = emprunts.stream()
                .filter(e -> e.getStatut() == StatutEmprunt.EN_COURS)
                .filter(e -> empruntEnRetard(e, today))
                .count();
        BigDecimal penalites = sommeTypes(ops, TypeOperation.PENALITE, TypeOperation.AMENDE);

        return List.of(
                RapportHeroStatResponse.builder()
                        .valeur(formatFcfa(epargne))
                        .label("Épargne totale")
                        .trend("Comptes épargne")
                        .build(),
                RapportHeroStatResponse.builder()
                        .valeur(formatFcfa(totalCotisations))
                        .label("Cotisations période")
                        .trend(nbSemainesPayees + " semaine(s) hebdo payée(s)")
                        .build(),
                RapportHeroStatResponse.builder()
                        .valeur(formatFcfa(empruntEncours))
                        .label("Emprunt en cours")
                        .trend(enRetard > 0 ? "⚠ " + enRetard + " en retard" : "À jour")
                        .build(),
                RapportHeroStatResponse.builder()
                        .valeur(formatFcfa(solde.getSolde()))
                        .label("Solde membre")
                        .trend("Synthèse globale")
                        .build(),
                RapportHeroStatResponse.builder()
                        .valeur(formatFcfa(penalites))
                        .label("Pénalités / amendes")
                        .trend("Sur la période")
                        .build());
    }

    private List<RapportBarChartItemResponse> construireGraphiqueHebdo(
            List<Operation> ops, BigDecimal montantHebdo) {
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
        BigDecimal cible = montantHebdo.max(BigDecimal.ONE);
        return parSemaine.entrySet().stream()
                .map(e -> {
                    int hauteur = max.compareTo(BigDecimal.ZERO) > 0
                            ? e.getValue()
                                    .multiply(BigDecimal.valueOf(100))
                                    .divide(max, 0, RoundingMode.HALF_UP)
                                    .intValue()
                            : 0;
                    return RapportBarChartItemResponse.builder()
                            .label(RapportDonneesHelper.libelleSemaineGraphique(e.getKey()))
                            .valeurLabel(formatCourt(e.getValue()))
                            .heightPct(Math.max(8, hauteur))
                            .belowTarget(e.getValue().compareTo(cible) < 0)
                            .build();
                })
                .toList();
    }

    private List<RapportEmpruntCardResponse> construireEmpruntsCards(List<Emprunt> emprunts, LocalDate today) {
        return emprunts.stream()
                .filter(e -> e.getStatut() == StatutEmprunt.EN_COURS)
                .map(e -> {
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
                    long echRestantes = e.getEcheances() != null
                            ? e.getEcheances().stream()
                                    .filter(ech -> ech.getStatut() != StatutEcheance.PAYE)
                                    .count()
                            : 0;
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
                            .nom(typeLabel)
                            .badge(badge)
                            .badgeClass(badgeClass)
                            .detail(echRestantes + " échéance(s) restante(s) · " + formatFcfa(e.getMontantTotal()))
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

    private List<RapportMembreOperationLigneResponse> construireOperations(List<Operation> ops) {
        return ops.stream()
                .map(o -> {
                    String libelle = libelleOperation(o.getTypeOperation());
                    boolean credit = o.getTypeOperation() == TypeOperation.COTISATION
                            || o.getTypeOperation() == TypeOperation.COTISATION_MOIS
                            || o.getTypeOperation() == TypeOperation.REMBOURSEMENT;
                    return RapportMembreOperationLigneResponse.builder()
                            .id(o.getId())
                            .dateOperation(o.getDateOperation())
                            .dateLabel(formatDateCourt(o.getDateOperation()))
                            .typeOperation(o.getTypeOperation().name())
                            .libelle(libelle)
                            .montant(o.getMontant())
                            .montantLabel((credit ? "+ " : "− ") + formatFcfa(o.getMontant()))
                            .sens(credit ? "credit" : "debit")
                            .build();
                })
                .toList();
    }

    private List<RapportMembreCompteResponse> construireComptes(Long orgId, Long membreId) {
        return membreFicheService.listerComptes(orgId, membreId).stream()
                .map(c -> RapportMembreCompteResponse.builder()
                        .typeCompte(c.getTypeCompte() != null ? c.getTypeCompte().name() : "")
                        .libelle(c.getLibelle())
                        .solde(c.getSolde())
                        .soldeLabel(formatFcfa(c.getSolde()))
                        .build())
                .toList();
    }

    private String libelleMois(SuiviMensuel suivi, BigDecimal payeMois, BigDecimal montantMoisRegle) {
        if (RapportDonneesHelper.cotisationMensuelleDue(suivi, montantMoisRegle)) {
            return formatFcfa(payeMois) + " / " + formatFcfa(RapportDonneesHelper.montantMoisDu(suivi, montantMoisRegle));
        }
        if (payeMois.compareTo(BigDecimal.ZERO) > 0) {
            return formatFcfa(payeMois);
        }
        return "—";
    }

    private BigDecimal soldeMembre(Long orgId, Long membreId, TypeCompte type) {
        return compteRepository
                .findByMembreIdAndTypeCompte(membreId, type)
                .map(Compte::getSolde)
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal montantRegle(Long orgId, TypeOperation type) {
        return regleOperationRepository
                .findByOrganisationIdAndTypeOperationAndActifTrue(orgId, type)
                .map(r -> r.getMontantMin() != null ? r.getMontantMin() : BigDecimal.ZERO)
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

    private PeriodeScope resoudrePeriode(String param) {
        if (param != null && param.matches("\\d{4}")) {
            int y = Integer.parseInt(param);
            return new PeriodeScope(
                    param, "Année " + y, LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31), null);
        }
        YearMonth ym;
        try {
            ym = param != null && !param.isBlank() ? YearMonth.parse(param) : YearMonth.now();
        } catch (Exception e) {
            ym = YearMonth.now();
        }
        return new PeriodeScope(
                ym.toString(), labelMois(ym.toString()), ym.atDay(1), ym.atEndOfMonth(), ym.toString());
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

    private PosteStyle posteStyle(PosteMembre poste) {
        return switch (poste) {
            case PRESIDENT -> new PosteStyle("👑 Président(e)", "b-pu");
            case TRESORIER -> new PosteStyle("💼 Trésorière", "b-green");
            case SECRETAIRE_GENERAL -> new PosteStyle("📝 S.G.", "b-blue");
            case SECRETAIRE_GENERAL_ADJOINT -> new PosteStyle("📝 S.G. adj.", "b-blue");
            case VICE_PRESIDENT -> new PosteStyle("Vice-président(e)", "b-pu");
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
        return AV_COLORS.get((int) (membreId % AV_COLORS.size()));
    }

    private String libelleOperation(TypeOperation type) {
        return switch (type) {
            case COTISATION -> "Cotisation hebdomadaire";
            case COTISATION_MOIS -> "Cotisation mensuelle";
            case REMBOURSEMENT -> "Remboursement emprunt";
            case EMPRUNT -> "Octroi emprunt";
            case PENALITE -> "Pénalité";
            case AMENDE -> "Amende";
            case VERSEMENT -> "Versement";
            default -> type.name();
        };
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
        if (v >= 1_000) {
            return (v / 1_000) + "k";
        }
        return String.valueOf(v);
    }

    private String formatDateCourt(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    private record PeriodeScope(String valeur, String label, LocalDate debut, LocalDate fin, String moisAnnee) {}

    private record PosteStyle(String label, String badgeClass) {}
}
