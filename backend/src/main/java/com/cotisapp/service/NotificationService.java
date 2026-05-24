package com.cotisapp.service;

import com.cotisapp.domain.entity.Compte;
import com.cotisapp.domain.entity.DemandeOperationMembre;
import com.cotisapp.domain.entity.Echeance;
import com.cotisapp.domain.entity.Emprunt;
import com.cotisapp.domain.entity.Membre;
import com.cotisapp.domain.entity.NotificationEtat;
import com.cotisapp.domain.entity.Operation;
import com.cotisapp.domain.enums.DemandeOperationStatut;
import com.cotisapp.domain.enums.DemandeOperationType;
import com.cotisapp.domain.enums.FamilleCompte;
import com.cotisapp.domain.enums.ModePaiement;
import com.cotisapp.domain.enums.ProprietaireCompte;
import com.cotisapp.domain.enums.Role;
import com.cotisapp.domain.enums.StatutEcheance;
import com.cotisapp.domain.enums.StatutEmprunt;
import com.cotisapp.domain.enums.TypeCompte;
import com.cotisapp.domain.enums.TypeEmprunt;
import com.cotisapp.domain.enums.TypeOperation;
import com.cotisapp.dto.response.NotificationCompteurResponse;
import com.cotisapp.dto.response.NotificationResponse;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.CompteRepository;
import com.cotisapp.repository.EmpruntRepository;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.repository.NotificationEtatRepository;
import com.cotisapp.repository.OperationRepository;
import com.cotisapp.repository.OrganisationRepository;
import com.cotisapp.repository.RegleOperationRepository;
import com.cotisapp.security.OrganisationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int JOURS_MEMBRE_RECENT = 14;
    private static final int JOURS_OPERATION_RECENTE = 7;
    private static final int MAX_REMBOURSEMENTS = 5;
    private static final int MAX_OPERATIONS_BANQUE = 3;
    private static final BigDecimal SEUIL_SOLIDARITE_RATIO = new BigDecimal("0.20");

    private final OrganisationRepository organisationRepository;
    private final MembreRepository membreRepository;
    private final EmpruntRepository empruntRepository;
    private final OperationRepository operationRepository;
    private final CompteRepository compteRepository;
    private final RegleOperationRepository regleOperationRepository;
    private final NotificationEtatRepository notificationEtatRepository;
    private final DemandeOperationMembreService demandeOperationMembreService;
    private final ParametrageCompteService parametrageCompteService;

    @Transactional(readOnly = true)
    public List<NotificationResponse> lister(Long orgId) {
        organisationRepository.findById(orgId).orElseThrow(() -> new BusinessException("Organisation introuvable"));
        Long userId = requireUserId();
        Map<String, NotificationEtat> etats = etatsParCle(orgId, userId);

        List<NotificationResponse> brutes;
        if (OrganisationContext.getRole() == Role.MEMBRE) {
            brutes = listerPourMembre(orgId);
        } else {
            brutes = listerPourAdmin(orgId);
        }

        return brutes.stream()
                .filter(n -> !estMasquee(n.id(), etats))
                .map(n -> appliquerLu(n, etats))
                .sorted(Comparator
                        .comparingInt((NotificationResponse n) -> severiteOrdre(n.severite()))
                        .thenComparing(NotificationResponse::dateTri, Comparator.reverseOrder()))
                .toList();
    }

    private List<NotificationResponse> listerPourAdmin(Long orgId) {
        List<NotificationResponse> brutes = new ArrayList<>();
        LocalDate today = LocalDate.now();
        Map<Long, Membre> membresParId = membreRepository.findByOrganisationIdAndActifTrue(orgId).stream()
                .collect(Collectors.toMap(Membre::getId, m -> m));

        if (demandeOperationMembreService.peutValiderDemandes()) {
            brutes.addAll(demandesOperationsEnAttente(orgId, membresParId));
        }
        brutes.addAll(empruntsEnRetard(orgId, today, membresParId, null));
        brutes.addAll(empruntsEcheancesProches(orgId, today, membresParId, null));
        brutes.addAll(cotisationsSemaineManquantes(orgId, today, membresParId));
        brutes.addAll(soldeSolidariteBas(orgId));
        brutes.addAll(membresRecents(orgId, membresParId));
        brutes.addAll(remboursementsRecents(orgId, membresParId));
        brutes.addAll(operationsBanqueRecentes(orgId));
        return brutes;
    }

    private List<NotificationResponse> listerPourMembre(Long orgId) {
        Long membreId = OrganisationContext.getMembreId();
        if (membreId == null) {
            throw new BusinessException("Contexte membre invalide pour les notifications");
        }
        Membre membre = membreRepository
                .findByIdAndOrganisationId(membreId, orgId)
                .orElseThrow(() -> new BusinessException("Membre introuvable"));
        LocalDate today = LocalDate.now();
        List<NotificationResponse> brutes = new ArrayList<>();
        brutes.addAll(demandesMembre(orgId, membre));
        brutes.addAll(empruntsEnRetard(orgId, today, Map.of(membreId, membre), membreId));
        brutes.addAll(empruntsEcheancesProches(orgId, today, Map.of(membreId, membre), membreId));
        brutes.addAll(cotisationSemaineManquanteMembre(orgId, today, membreId, membre));
        return brutes;
    }

    @Transactional(readOnly = true)
    public NotificationCompteurResponse compteur(Long orgId) {
        List<NotificationResponse> list = lister(orgId);
        int nonLues = (int) list.stream().filter(n -> !n.lu()).count();
        int urgences = (int) list.stream().filter(n -> !n.lu() && "urgence".equals(n.severite())).count();
        return new NotificationCompteurResponse(nonLues, urgences);
    }

    @Transactional
    public void marquerLu(Long orgId, String cle) {
        upsertEtat(orgId, requireUserId(), cle, true, null);
    }

    @Transactional
    public void marquerNonLu(Long orgId, String cle) {
        upsertEtat(orgId, requireUserId(), cle, false, null);
    }

    @Transactional
    public void marquerToutLu(Long orgId) {
        Long userId = requireUserId();
        for (NotificationResponse n : lister(orgId)) {
            if (!n.lu()) {
                upsertEtat(orgId, userId, n.id(), true, null);
            }
        }
    }

    @Transactional
    public void masquer(Long orgId, String cle) {
        upsertEtat(orgId, requireUserId(), cle, null, true);
    }

    private List<NotificationResponse> demandesOperationsEnAttente(
            Long orgId, Map<Long, Membre> membresParId) {
        List<NotificationResponse> list = new ArrayList<>();
        for (DemandeOperationMembre d : demandeOperationMembreService.listerEnAttente(orgId)) {
            Membre m = membresParId.get(d.getMembreId());
            String nom = m != null ? m.getNomComplet() : "Membre";
            String code = m != null ? m.getCodeMembre() : "";
            String typeLib = libelleTypeDemande(d.getTypeDemande());
            String modeLib = libelleModeNotif(d.getModePaiement());
            String cle = "demande-op:" + d.getId();
            LocalDateTime dateTri = d.getDateDemande();
            NotificationResponse.NotificationResponseBuilder nb = NotificationResponse.builder()
                    .id(cle)
                    .groupe(groupe(dateTri, false))
                    .severite("urgence")
                    .lu(false)
                    .icone("📲")
                    .iconeClass("ico-or")
                    .titre(typeLib + " à valider — " + nom)
                    .description(String.format(
                            Locale.FRENCH,
                            "%s · %s FCFA · %s · Réf. %s%s",
                            d.getLibelleResume() != null ? d.getLibelleResume() : typeLib,
                            formatMontant(d.getMontant()),
                            modeLib,
                            d.getReferencePaiement() != null ? d.getReferencePaiement() : "—",
                            code.isBlank() ? "" : " (" + code + ")"))
                    .temps(tempsRelatif(dateTri))
                    .tag("À valider")
                    .tagClass("tag-or")
                    .actionLabel(null)
                    .actionSegments(List.of("notifications"))
                    .actionQueryParams(Map.of())
                    .typeFiltre("COTISATION")
                    .dateTri(dateTri)
                    .demandeId(d.getId())
                    .workflowDemande(true)
                    .demandeWorkflowActif(true)
                    .demandeTypeDemande(d.getTypeDemande().name());
            enrichirAmendeValidation(orgId, d.getTypeDemande(), nb);
            list.add(nb.build());
        }
        return list;
    }

    private void enrichirAmendeValidation(
            Long orgId,
            DemandeOperationType typeDemande,
            NotificationResponse.NotificationResponseBuilder nb) {
        boolean cotisation =
                typeDemande == DemandeOperationType.COTISATION_HEBDO
                        || typeDemande == DemandeOperationType.COTISATION_MOIS;
        if (!cotisation) {
            nb.amendeApplicable(false);
            return;
        }
        boolean amendeOk = parametrageCompteService.familleActive(orgId, FamilleCompte.AMENDE);
        nb.amendeApplicable(amendeOk);
        if (!amendeOk) {
            return;
        }
        TypeOperation typeRegle =
                typeDemande == DemandeOperationType.COTISATION_MOIS
                        ? TypeOperation.COTISATION_MOIS
                        : TypeOperation.COTISATION;
        regleOperationRepository
                .findByOrganisationIdAndTypeOperationAndActifTrue(orgId, typeRegle)
                .ifPresent(regle -> {
                    nb.montantAmendeMin(regle.getMontantAmendeMin());
                    nb.montantAmendeMax(regle.getMontantAmendeMax());
                });
    }

    private static String libelleTypeDemande(com.cotisapp.domain.enums.DemandeOperationType type) {
        return switch (type) {
            case COTISATION_HEBDO -> "Cotisation hebdo";
            case COTISATION_MOIS -> "Cotisation mensuelle";
            case REMBOURSEMENT -> "Remboursement";
        };
    }

    private static String libelleModeNotif(ModePaiement mode) {
        if (mode == null) return "—";
        return switch (mode) {
            case WAVE -> "Wave";
            case ORANGE_MONEY -> "Orange Money";
            default -> mode.name();
        };
    }

    private List<NotificationResponse> demandesMembre(Long orgId, Membre membre) {
        List<NotificationResponse> list = new ArrayList<>();
        for (DemandeOperationMembre d :
                demandeOperationMembreService.listerPourNotificationsMembre(orgId, membre.getId())) {
            String cle = "demande-membre:" + d.getId() + ":" + d.getStatut().name();
            LocalDateTime dateTri =
                    d.getDateTraitement() != null ? d.getDateTraitement() : d.getDateDemande();
            String typeLib = libelleTypeDemande(d.getTypeDemande());
            boolean enAttente = d.getStatut() == DemandeOperationStatut.EN_ATTENTE;
            boolean validee = d.getStatut() == DemandeOperationStatut.VALIDEE;
            String severite = enAttente ? "warning" : (validee ? "success" : "urgence");
            String tag = enAttente ? "En attente" : (validee ? "Validée" : "Refusée");
            String tagClass = enAttente ? "tag-or" : (validee ? "tag-g" : "tag-re");
            String titre = enAttente
                    ? typeLib + " — en attente de validation"
                    : (validee ? typeLib + " — validée" : typeLib + " — refusée");
            String description;
            if (enAttente) {
                description = String.format(
                        Locale.FRENCH,
                        "%s · %s FCFA · %s · Réf. %s. Votre demande sera traitée par l'administrateur.",
                        d.getLibelleResume() != null ? d.getLibelleResume() : typeLib,
                        formatMontant(d.getMontant()),
                        libelleModeNotif(d.getModePaiement()),
                        d.getReferencePaiement() != null ? d.getReferencePaiement() : "—");
            } else if (validee) {
                description = String.format(
                        Locale.FRENCH,
                        "Votre demande de %s (%s FCFA) a été approuvée et comptabilisée.",
                        typeLib.toLowerCase(Locale.FRENCH),
                        formatMontant(d.getMontant()));
            } else {
                String motif = d.getMotifRefus() != null && !d.getMotifRefus().isBlank()
                        ? d.getMotifRefus()
                        : "Aucun motif indiqué";
                description = String.format(
                        Locale.FRENCH,
                        "Votre demande de %s (%s FCFA) a été rejetée. Motif : %s",
                        typeLib.toLowerCase(Locale.FRENCH),
                        formatMontant(d.getMontant()),
                        motif);
            }
            list.add(NotificationResponse.builder()
                    .id(cle)
                    .groupe(groupe(dateTri, false))
                    .severite(severite)
                    .lu(false)
                    .icone(enAttente ? "📲" : (validee ? "✓" : "✕"))
                    .iconeClass(enAttente ? "ico-or" : (validee ? "ico-g" : "ico-re"))
                    .titre(titre)
                    .description(description)
                    .temps(tempsRelatif(dateTri))
                    .tag(tag)
                    .tagClass(tagClass)
                    .actionLabel("Mon compte →")
                    .actionSegments(List.of("mon-compte"))
                    .actionQueryParams(Map.of())
                    .typeFiltre("COTISATION")
                    .dateTri(dateTri)
                    .workflowDemande(false)
                    .build());
        }
        return list;
    }

    private List<NotificationResponse> cotisationSemaineManquanteMembre(
            Long orgId, LocalDate today, Long membreId, Membre membre) {
        if (!regleOperationRepository.existsByOrganisationIdAndTypeOperation(orgId, TypeOperation.COTISATION)) {
            return List.of();
        }
        LocalDate debutSemaine = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate finSemaine = debutSemaine.plusDays(6);
        if (today.isBefore(debutSemaine.plusDays(2))) {
            return List.of();
        }
        boolean aCotise = operationRepository
                .findByOrganisationIdAndTypeOperationInAndDateOperationBetweenOrderByDateOperationDescDateCreationDesc(
                        orgId, List.of(TypeOperation.COTISATION), debutSemaine, finSemaine)
                .stream()
                .anyMatch(op -> membreId.equals(op.getMembreId()));
        if (aCotise) {
            return List.of();
        }
        int semaine = debutSemaine.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        String cle = "cotisation-semaine-membre:" + membreId + ":" + debutSemaine.getYear() + "-W" + semaine;
        LocalDateTime dateTri = LocalDateTime.now().withHour(8).withMinute(0);
        return List.of(NotificationResponse.builder()
                .id(cle)
                .groupe(groupe(dateTri, false))
                .severite("warning")
                .lu(false)
                .icone("⏰")
                .iconeClass("ico-or")
                .titre(String.format(Locale.FRENCH, "Semaine %d — cotisation hebdo à effectuer", semaine))
                .description(String.format(
                        Locale.FRENCH,
                        "Vous n'avez pas encore cotisé pour la semaine du %s au %s. Pensez à effectuer votre cotisation depuis Mon compte.",
                        debutSemaine.format(DateTimeFormatter.ofPattern("dd/MM")),
                        finSemaine.format(DateTimeFormatter.ofPattern("dd/MM"))))
                .temps(tempsRelatif(dateTri))
                .tag("Cotisation")
                .tagClass("tag-or")
                .actionLabel("Mon compte →")
                .actionSegments(List.of("mon-compte"))
                .actionQueryParams(Map.of())
                .typeFiltre("COTISATION")
                .dateTri(dateTri)
                .build());
    }

    private List<NotificationResponse> empruntsEnRetard(
            Long orgId, LocalDate today, Map<Long, Membre> membresParId, Long membreIdFiltre) {
        List<NotificationResponse> list = new ArrayList<>();
        for (Emprunt emprunt : empruntRepository.findByOrganisationId(orgId)) {
            if (membreIdFiltre != null && !membreIdFiltre.equals(emprunt.getMembreId())) {
                continue;
            }
            if (emprunt.getStatut() != StatutEmprunt.EN_COURS || emprunt.getEcheances() == null) {
                continue;
            }
            Membre membre = membresParId.get(emprunt.getMembreId());
            String nomMembre = membre != null ? membre.getNomComplet() : "Membre";
            for (Echeance ech : emprunt.getEcheances()) {
                if (ech.getStatut() == StatutEcheance.PAYE) {
                    continue;
                }
                if (!ech.getDateEcheance().isBefore(today)) {
                    continue;
                }
                long joursRetard = ChronoUnit.DAYS.between(ech.getDateEcheance(), today);
                BigDecimal restant = ech.getMontantEcheance().subtract(ech.getMontantPaye()).max(BigDecimal.ZERO);
                String cle = "emprunt-retard:" + ech.getId();
                LocalDateTime dateTri = ech.getDateEcheance().atStartOfDay();
                String typeEmprunt = libelleTypeEmprunt(emprunt.getTypeEmprunt());
                list.add(NotificationResponse.builder()
                        .id(cle)
                        .groupe(groupe(dateTri, false))
                        .severite("urgence")
                        .lu(false)
                        .icone("⚠")
                        .iconeClass("ico-re")
                        .titre(membreIdFiltre != null
                                ? "Votre emprunt en retard"
                                : "Emprunt en retard — " + nomMembre)
                        .description(String.format(
                                Locale.FRENCH,
                                "Échéance %d de %s était prévue le %s. Retard : %d jour(s). Reste à payer : %s FCFA (%s).",
                                ech.getNumero(),
                                formatMontant(ech.getMontantEcheance()),
                                ech.getDateEcheance().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                                joursRetard,
                                formatMontant(restant),
                                typeEmprunt))
                        .temps(tempsRelatif(dateTri))
                        .tag("Emprunt en retard")
                        .tagClass("tag-re")
                        .actionLabel(membreIdFiltre != null ? "Mes emprunts →" : "Voir le remboursement →")
                        .actionSegments(membreIdFiltre != null
                                ? List.of("operations", "emprunts", "suivi")
                                : actionRemboursement(emprunt.getTypeEmprunt()))
                        .actionQueryParams(membreIdFiltre != null
                                ? Map.of()
                                : actionRemboursementParams(emprunt.getTypeEmprunt()))
                        .typeFiltre("EMPRUNT")
                        .dateTri(dateTri)
                        .build());
            }
        }
        return list;
    }

    private List<NotificationResponse> empruntsEcheancesProches(
            Long orgId, LocalDate today, Map<Long, Membre> membresParId, Long membreIdFiltre) {
        List<NotificationResponse> list = new ArrayList<>();
        for (Emprunt emprunt : empruntRepository.findByOrganisationId(orgId)) {
            if (membreIdFiltre != null && !membreIdFiltre.equals(emprunt.getMembreId())) {
                continue;
            }
            if (emprunt.getStatut() != StatutEmprunt.EN_COURS || emprunt.getEcheances() == null) {
                continue;
            }
            Membre membre = membresParId.get(emprunt.getMembreId());
            String nomMembre = membre != null ? membre.getNomComplet() : "Membre";
            int joursAlerte = EmpruntRegleHelper.joursAlerteEcheanceProchePourType(
                    regleOperationRepository, orgId, emprunt.getTypeEmprunt());
            LocalDate limite = today.plusDays(joursAlerte);
            for (Echeance ech : emprunt.getEcheances()) {
                if (ech.getStatut() == StatutEcheance.PAYE) {
                    continue;
                }
                if (ech.getDateEcheance().isBefore(today)) {
                    continue;
                }
                if (ech.getDateEcheance().isAfter(limite)) {
                    continue;
                }
                long joursRestants = ChronoUnit.DAYS.between(today, ech.getDateEcheance());
                BigDecimal restant = ech.getMontantEcheance().subtract(ech.getMontantPaye()).max(BigDecimal.ZERO);
                String cle = "emprunt-proche:" + ech.getId();
                LocalDateTime dateTri = ech.getDateEcheance().atStartOfDay();
                String typeEmprunt = libelleTypeEmprunt(emprunt.getTypeEmprunt());
                String libelleDelai = joursRestants == 0
                        ? "aujourd'hui"
                        : joursRestants == 1
                                ? "demain"
                                : String.format(Locale.FRENCH, "dans %d jours", joursRestants);
                list.add(NotificationResponse.builder()
                        .id(cle)
                        .groupe(groupe(dateTri, false))
                        .severite("warning")
                        .lu(false)
                        .icone("⏰")
                        .iconeClass("ico-or")
                        .titre(membreIdFiltre != null
                                ? "Échéance proche — votre emprunt"
                                : "Échéance proche — " + nomMembre)
                        .description(String.format(
                                Locale.FRENCH,
                                "Échéance %d de %s (%s) %s — prévue le %s. Montant dû : %s FCFA.",
                                ech.getNumero(),
                                formatMontant(ech.getMontantEcheance()),
                                typeEmprunt,
                                libelleDelai,
                                ech.getDateEcheance().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                                formatMontant(restant)))
                        .temps(tempsRelatif(dateTri))
                        .tag("Échéance proche")
                        .tagClass("tag-or")
                        .actionLabel(membreIdFiltre != null ? "Mes emprunts →" : "Préparer le remboursement →")
                        .actionSegments(membreIdFiltre != null
                                ? List.of("operations", "emprunts", "suivi")
                                : actionRemboursement(emprunt.getTypeEmprunt()))
                        .actionQueryParams(membreIdFiltre != null
                                ? Map.of()
                                : actionRemboursementParams(emprunt.getTypeEmprunt()))
                        .typeFiltre("EMPRUNT")
                        .dateTri(dateTri)
                        .build());
            }
        }
        return list;
    }

    private List<NotificationResponse> cotisationsSemaineManquantes(
            Long orgId, LocalDate today, Map<Long, Membre> membresParId) {
        if (!regleOperationRepository.existsByOrganisationIdAndTypeOperation(orgId, TypeOperation.COTISATION)) {
            return List.of();
        }
        LocalDate debutSemaine = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate finSemaine = debutSemaine.plusDays(6);
        if (today.isBefore(debutSemaine.plusDays(2))) {
            return List.of();
        }

        Set<Long> ontCotise = operationRepository
                .findByOrganisationIdAndTypeOperationInAndDateOperationBetweenOrderByDateOperationDescDateCreationDesc(
                        orgId, List.of(TypeOperation.COTISATION), debutSemaine, finSemaine)
                .stream()
                .map(Operation::getMembreId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        List<Membre> manquants = membresParId.values().stream()
                .filter(m -> !ontCotise.contains(m.getId()))
                .sorted(Comparator.comparing(Membre::getNomComplet, String.CASE_INSENSITIVE_ORDER))
                .toList();

        if (manquants.isEmpty()) {
            return List.of();
        }

        int semaine = debutSemaine.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        String cle = "cotisation-semaine:" + debutSemaine.getYear() + "-W" + semaine;
        String noms = manquants.stream()
                .limit(4)
                .map(Membre::getNomComplet)
                .collect(Collectors.joining(", "));
        if (manquants.size() > 4) {
            noms += "…";
        }
        LocalDateTime dateTri = LocalDateTime.now().withHour(8).withMinute(0);
        int total = membresParId.size();
        return List.of(NotificationResponse.builder()
                .id(cle)
                .groupe(groupe(dateTri, false))
                .severite("warning")
                .lu(false)
                .icone("⏰")
                .iconeClass("ico-or")
                .titre(String.format(
                        Locale.FRENCH,
                        "Semaine %d — %d membre(s) n'ont pas cotisé",
                        semaine,
                        manquants.size()))
                .description(String.format(
                        Locale.FRENCH,
                        "Du %s au %s, %d membre(s) sur %d n'ont pas encore cotisé. Ex. : %s.",
                        debutSemaine.format(DateTimeFormatter.ofPattern("dd/MM")),
                        finSemaine.format(DateTimeFormatter.ofPattern("dd/MM")),
                        manquants.size(),
                        total,
                        noms))
                .temps(tempsRelatif(dateTri))
                .tag("Cotisations manquantes")
                .tagClass("tag-or")
                .actionLabel("Saisir →")
                .actionSegments(List.of("operations", "cotisation-mois"))
                .actionQueryParams(Map.of("t", "hebdo"))
                .typeFiltre("COTISATION")
                .dateTri(dateTri)
                .build());
    }

    private List<NotificationResponse> soldeSolidariteBas(Long orgId) {
        if (!regleOperationRepository.existsByOrganisationIdAndTypeOperation(orgId, TypeOperation.EMPRUNT)) {
            return List.of();
        }
        BigDecimal solde = soldeOrg(orgId, TypeCompte.SOLIDARITE);
        BigDecimal encours = empruntRepository.findByOrganisationId(orgId).stream()
                .filter(e -> e.getTypeEmprunt() == TypeEmprunt.SOLIDARITE)
                .filter(e -> e.getStatut() == StatutEmprunt.EN_COURS)
                .map(e -> e.getMontantTotal().subtract(e.getMontantRembourse()).max(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (encours.signum() <= 0) {
            return List.of();
        }
        BigDecimal seuil = encours.multiply(SEUIL_SOLIDARITE_RATIO);
        if (solde.compareTo(seuil) >= 0) {
            return List.of();
        }

        long nbEmprunts = empruntRepository.findByOrganisationId(orgId).stream()
                .filter(e -> e.getTypeEmprunt() == TypeEmprunt.SOLIDARITE)
                .filter(e -> e.getStatut() == StatutEmprunt.EN_COURS)
                .count();

        LocalDateTime dateTri = LocalDateTime.now().minusHours(4);
        return List.of(NotificationResponse.builder()
                .id("solde-solidarite-bas")
                .groupe(groupe(dateTri, false))
                .severite("warning")
                .lu(false)
                .icone("💰")
                .iconeClass("ico-or")
                .titre("Solde Solidarité bas — Seuil atteint")
                .description(String.format(
                        Locale.FRENCH,
                        "Le fonds Solidarité est à %s FCFA (moins de 20 %% des encours actifs : %s FCFA). %d emprunt(s) solidarité en cours.",
                        formatMontant(solde),
                        formatMontant(encours),
                        nbEmprunts))
                .temps(tempsRelatif(dateTri))
                .tag("Alerte Solidarité")
                .tagClass("tag-or")
                .actionLabel("Tableau de bord →")
                .actionSegments(List.of("dashboard"))
                .actionQueryParams(Map.of())
                .typeFiltre("SYSTEME")
                .dateTri(dateTri)
                .build());
    }

    private List<NotificationResponse> membresRecents(Long orgId, Map<Long, Membre> membresParId) {
        LocalDateTime limite = LocalDateTime.now().minusDays(JOURS_MEMBRE_RECENT);
        List<NotificationResponse> list = new ArrayList<>();
        for (Membre m : membresParId.values()) {
            if (m.getDateCreation() == null || m.getDateCreation().isBefore(limite)) {
                continue;
            }
            list.add(NotificationResponse.builder()
                    .id("membre-nouveau:" + m.getId())
                    .groupe(groupe(m.getDateCreation(), false))
                    .severite("info")
                    .lu(false)
                    .icone("👤")
                    .iconeClass("ico-g")
                    .titre("Nouveau membre — " + m.getNomComplet())
                    .description(String.format(
                            Locale.FRENCH,
                            "Le membre %s (%s) a été ajouté le %s.",
                            m.getNomComplet(),
                            m.getCodeMembre(),
                            m.getDateCreation().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))))
                    .temps(tempsRelatif(m.getDateCreation()))
                    .tag("Nouveau membre")
                    .tagClass("tag-g")
                    .actionLabel("Voir les membres →")
                    .actionSegments(List.of("membres"))
                    .actionQueryParams(Map.of())
                    .typeFiltre("SYSTEME")
                    .dateTri(m.getDateCreation())
                    .build());
        }
        return list;
    }

    private List<NotificationResponse> remboursementsRecents(Long orgId, Map<Long, Membre> membresParId) {
        LocalDate debut = LocalDate.now().minusDays(JOURS_OPERATION_RECENTE);
        return operationRepository
                .findByOrganisationIdAndTypeOperationInAndDateOperationBetweenOrderByDateOperationDescDateCreationDesc(
                        orgId, List.of(TypeOperation.REMBOURSEMENT), debut, LocalDate.now())
                .stream()
                .limit(MAX_REMBOURSEMENTS)
                .map(op -> {
                    Membre m = op.getMembreId() != null ? membresParId.get(op.getMembreId()) : null;
                    String nom = m != null ? m.getNomComplet() : "Membre";
                    LocalDateTime dateTri = op.getDateCreation() != null ? op.getDateCreation() : op.getDateOperation().atStartOfDay();
                    return NotificationResponse.builder()
                            .id("remboursement:" + op.getId())
                            .groupe(groupe(dateTri, false))
                            .severite("info")
                            .lu(false)
                            .icone("🔄")
                            .iconeClass("ico-bl")
                            .titre("Remboursement — " + nom)
                            .description(String.format(
                                    Locale.FRENCH,
                                    "%s a remboursé %s FCFA le %s.",
                                    nom,
                                    formatMontant(op.getMontant()),
                                    op.getDateOperation().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))))
                            .temps(tempsRelatif(dateTri))
                            .tag("Remboursement")
                            .tagClass("tag-g")
                            .actionLabel("Remboursements →")
                            .actionSegments(List.of("operations", "remboursements"))
                            .actionQueryParams(Map.of("t", "etale"))
                            .typeFiltre("EMPRUNT")
                            .dateTri(dateTri)
                            .build();
                })
                .toList();
    }

    private List<NotificationResponse> operationsBanqueRecentes(Long orgId) {
        LocalDate debut = LocalDate.now().minusDays(JOURS_OPERATION_RECENTE);
        return operationRepository
                .findByOrganisationIdAndTypeOperationInAndDateOperationBetweenOrderByDateOperationDescDateCreationDesc(
                        orgId,
                        List.of(TypeOperation.BANQUE_VERSEMENT, TypeOperation.BANQUE_RETRAIT),
                        debut,
                        LocalDate.now())
                .stream()
                .limit(MAX_OPERATIONS_BANQUE)
                .map(op -> {
                    boolean versement = op.getTypeOperation() == TypeOperation.BANQUE_VERSEMENT;
                    LocalDateTime dateTri = op.getDateCreation() != null ? op.getDateCreation() : op.getDateOperation().atStartOfDay();
                    return NotificationResponse.builder()
                            .id("banque:" + op.getId())
                            .groupe(groupe(dateTri, false))
                            .severite("info")
                            .lu(false)
                            .icone("🏦")
                            .iconeClass("ico-bl")
                            .titre(versement ? "Versement en banque" : "Retrait banque")
                            .description(String.format(
                                    Locale.FRENCH,
                                    "%s de %s FCFA le %s.",
                                    versement ? "Versement" : "Retrait",
                                    formatMontant(op.getMontant()),
                                    op.getDateOperation().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))))
                            .temps(tempsRelatif(dateTri))
                            .tag("Banque")
                            .tagClass("tag-muted")
                            .actionLabel("Trésorerie →")
                            .actionSegments(List.of("gestion", "tresorerie"))
                            .actionQueryParams(Map.of())
                            .typeFiltre("SYSTEME")
                            .dateTri(dateTri)
                            .build();
                })
                .toList();
    }

    private Map<String, NotificationEtat> etatsParCle(Long orgId, Long userId) {
        return notificationEtatRepository.findByOrganisationIdAndUtilisateurId(orgId, userId).stream()
                .collect(Collectors.toMap(NotificationEtat::getCleNotification, e -> e, (a, b) -> a));
    }

    private boolean estMasquee(String cle, Map<String, NotificationEtat> etats) {
        NotificationEtat e = etats.get(cle);
        return e != null && Boolean.TRUE.equals(e.getMasque());
    }

    private NotificationResponse appliquerLu(NotificationResponse n, Map<String, NotificationEtat> etats) {
        NotificationEtat e = etats.get(n.id());
        boolean lu = e != null && Boolean.TRUE.equals(e.getLu());
        if (lu == n.lu()) {
            return n;
        }
        return n.toBuilder()
                .groupe(groupe(n.dateTri(), lu))
                .lu(lu)
                .tagClass(lu ? "tag-muted" : n.tagClass())
                .build();
    }

    private void upsertEtat(Long orgId, Long userId, String cle, Boolean lu, Boolean masque) {
        NotificationEtat etat = notificationEtatRepository
                .findByOrganisationIdAndUtilisateurIdAndCleNotification(orgId, userId, cle)
                .orElseGet(() -> NotificationEtat.builder()
                        .organisationId(orgId)
                        .utilisateurId(userId)
                        .cleNotification(cle)
                        .build());
        if (lu != null) {
            etat.setLu(lu);
        }
        if (masque != null) {
            etat.setMasque(masque);
        }
        etat.setDateModification(LocalDateTime.now());
        notificationEtatRepository.save(etat);
    }

    private Long requireUserId() {
        Long userId = OrganisationContext.getUserId();
        if (userId == null) {
            throw new BusinessException("Utilisateur non authentifié");
        }
        return userId;
    }

    private BigDecimal soldeOrg(Long orgId, TypeCompte type) {
        return compteRepository
                .findByOrganisationIdAndTypeCompteAndProprietaire(orgId, type, ProprietaireCompte.ORGANISATION)
                .map(Compte::getSolde)
                .orElse(BigDecimal.ZERO);
    }

    private static List<String> actionRemboursement(TypeEmprunt type) {
        return List.of("operations", "remboursements");
    }

    private static Map<String, String> actionRemboursementParams(TypeEmprunt type) {
        String t = switch (type) {
            case SOLIDARITE -> "solidarite";
            case CAISSE -> "caisse";
            default -> "etale";
        };
        return Map.of("t", t);
    }

    private static String libelleTypeEmprunt(TypeEmprunt type) {
        return switch (type) {
            case SOLIDARITE -> "Solidarité";
            case CAISSE -> "Caisse";
            default -> "Étalé";
        };
    }

    private static String formatMontant(BigDecimal m) {
        if (m == null) {
            return "0";
        }
        return m.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private static int severiteOrdre(String severite) {
        return switch (severite) {
            case "urgence" -> 0;
            case "warning" -> 1;
            case "info" -> 2;
            default -> 3;
        };
    }

    private static String groupe(LocalDateTime date, boolean lu) {
        if (date == null) {
            return "Récent";
        }
        LocalDate d = date.toLocalDate();
        LocalDate today = LocalDate.now();
        if (d.equals(today)) {
            return "Aujourd'hui";
        }
        if (d.equals(today.minusDays(1))) {
            return "Hier";
        }
        if (lu) {
            return "Cette semaine — Lues";
        }
        if (d.isAfter(today.minusWeeks(1))) {
            return "Cette semaine";
        }
        return d.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH));
    }

    private static String tempsRelatif(LocalDateTime date) {
        if (date == null) {
            return "";
        }
        LocalDateTime now = LocalDateTime.now();
        long minutes = ChronoUnit.MINUTES.between(date, now);
        if (minutes < 1) {
            return "À l'instant";
        }
        if (minutes < 60) {
            return "Il y a " + minutes + " min";
        }
        long hours = ChronoUnit.HOURS.between(date, now);
        if (hours < 24) {
            return hours == 1 ? "Il y a 1 heure" : "Il y a " + hours + " heures";
        }
        LocalDate d = date.toLocalDate();
        LocalDate today = LocalDate.now();
        if (d.equals(today)) {
            return "Auj. " + date.format(DateTimeFormatter.ofPattern("HH:mm"));
        }
        if (d.equals(today.minusDays(1))) {
            return "Hier " + date.format(DateTimeFormatter.ofPattern("HH:mm"));
        }
        return d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH));
    }
}
