package com.cotisapp.service;

import com.cotisapp.domain.entity.Membre;
import com.cotisapp.domain.entity.Operation;
import com.cotisapp.domain.entity.SuiviMensuel;
import com.cotisapp.domain.enums.PosteMembre;
import com.cotisapp.domain.enums.StatutSuiviMensuel;
import com.cotisapp.domain.enums.TypeOperation;
import com.cotisapp.dto.response.CotisationPanneauResponse;
import com.cotisapp.dto.response.CotisationRecenteResponse;
import com.cotisapp.dto.response.CotisationSuiviMembreResponse;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.repository.OperationRepository;
import com.cotisapp.repository.OrganisationRepository;
import com.cotisapp.util.SemaineIsoUtil;
import com.cotisapp.util.SemaineIsoUtil.BornesSemaine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CotisationSuiviService {

    private static final int RECENTES_MAX = 8;
    private static final DateTimeFormatter HEURE =
            DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH);
    private static final DateTimeFormatter JOUR_MOIS =
            DateTimeFormatter.ofPattern("dd/MM", Locale.FRENCH);

    private final OrganisationRepository organisationRepository;
    private final MembreRepository membreRepository;
    private final OperationRepository operationRepository;
    private final SuiviMensuelService suiviMensuelService;

    @Transactional(readOnly = true)
    public CotisationPanneauResponse panneau(Long orgId, String type, String semaineKey, String moisAnnee) {
        organisationRepository.findById(orgId).orElseThrow(() -> new BusinessException("Organisation introuvable"));

        boolean hebdo = !"mois".equalsIgnoreCase(type);
        List<Membre> membresActifs = membreRepository.findByOrganisationIdAndActifTrue(orgId).stream()
                .sorted(Comparator.comparing(Membre::getPoste)
                        .thenComparing(m -> m.getNomComplet(), String.CASE_INSENSITIVE_ORDER))
                .toList();

        String periodeLabel;
        List<CotisationSuiviMembreResponse> suivi;
        if (hebdo) {
            if (semaineKey == null || semaineKey.isBlank()) {
                throw new BusinessException("Semaine requise pour le suivi hebdomadaire");
            }
            periodeLabel = SemaineIsoUtil.libelleSemaine(semaineKey);
            suivi = suiviHebdo(membresActifs, orgId, semaineKey);
        } else {
            if (moisAnnee == null || moisAnnee.isBlank()) {
                throw new BusinessException("Mois requis pour le suivi mensuel");
            }
            periodeLabel = libelleMois(moisAnnee);
            suivi = suiviMois(membresActifs, orgId, moisAnnee);
        }

        TypeOperation typeRecent = hebdo ? TypeOperation.COTISATION : TypeOperation.COTISATION_MOIS;
        List<CotisationRecenteResponse> recentes = cotisationsRecentes(orgId, typeRecent, hebdo);
        ResumeJour resume = resumeAujourdhui(orgId, hebdo);

        return CotisationPanneauResponse.builder()
                .periodeLabel(periodeLabel)
                .suivi(suivi)
                .recentes(recentes)
                .cotisationsAujourdhui(resume.nombre())
                .montantAujourdhui(resume.montant())
                .build();
    }

    private List<CotisationSuiviMembreResponse> suiviHebdo(
            List<Membre> membres, Long orgId, String semaineKey) {
        BornesSemaine bornes = SemaineIsoUtil.parserSemaineKey(semaineKey);
        String marqueur = SemaineIsoUtil.marqueurObservation(semaineKey);

        Set<Long> payesParMarqueur = operationRepository
                .findByOrganisationIdAndTypeOperationAndObservationContaining(
                        orgId, TypeOperation.COTISATION, marqueur)
                .stream()
                .map(Operation::getMembreId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        final Set<Long> payes;
        if (payesParMarqueur.isEmpty()) {
            payes = operationRepository
                    .findByOrganisationIdAndTypeOperationInAndDateOperationBetweenOrderByDateOperationDescDateCreationDesc(
                            orgId,
                            List.of(TypeOperation.COTISATION),
                            bornes.lundi(),
                            bornes.dimanche())
                    .stream()
                    .map(Operation::getMembreId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } else {
            payes = payesParMarqueur;
        }

        return membres.stream()
                .map(m -> CotisationSuiviMembreResponse.builder()
                        .membreId(m.getId())
                        .nomComplet(m.getNomComplet())
                        .codeMembre(m.getCodeMembre())
                        .poste(m.getPoste() != null ? m.getPoste().name() : null)
                        .sousTitre(sousTitreMembre(m))
                        .statut(payes.contains(m.getId()) ? "PAYE" : "ATTENTE")
                        .build())
                .toList();
    }

    private List<CotisationSuiviMembreResponse> suiviMois(List<Membre> membres, Long orgId, String moisAnnee) {
        Map<Long, SuiviMensuel> parMembre = suiviMensuelService.listerParMois(orgId, moisAnnee).stream()
                .collect(Collectors.toMap(SuiviMensuel::getMembreId, s -> s, (a, b) -> a));

        return membres.stream()
                .map(m -> {
                    SuiviMensuel s = parMembre.get(m.getId());
                    boolean paye = s != null && s.getStatut() == StatutSuiviMensuel.PAYE;
                    return CotisationSuiviMembreResponse.builder()
                            .membreId(m.getId())
                            .nomComplet(m.getNomComplet())
                            .codeMembre(m.getCodeMembre())
                            .poste(m.getPoste() != null ? m.getPoste().name() : null)
                            .sousTitre(sousTitreMembre(m))
                            .statut(paye ? "PAYE" : "ATTENTE")
                            .build();
                })
                .toList();
    }

    private List<CotisationRecenteResponse> cotisationsRecentes(
            Long orgId, TypeOperation type, boolean hebdo) {
        return operationRepository
                .findByOrganisationIdAndTypeOperationOrderByDateOperationDescDateCreationDesc(orgId, type)
                .stream()
                .limit(RECENTES_MAX)
                .map(op -> {
                    String membreNom = membreRepository.findById(op.getMembreId())
                            .map(Membre::getNomComplet)
                            .orElse("Membre");
                    String libelle = libelleRecente(op, hebdo, membreNom);
                    return CotisationRecenteResponse.builder()
                            .membreNom(membreNom)
                            .libelle(libelle)
                            .meta(metaRecente(op, hebdo))
                            .montant(op.getMontant())
                            .iconeClass(hebdo ? "g3" : "pi2")
                            .build();
                })
                .toList();
    }

    private ResumeJour resumeAujourdhui(Long orgId, boolean hebdo) {
        LocalDate today = LocalDate.now();
        List<TypeOperation> types = hebdo
                ? List.of(TypeOperation.COTISATION)
                : List.of(TypeOperation.COTISATION, TypeOperation.COTISATION_MOIS);

        List<Operation> ops = operationRepository
                .findByOrganisationIdAndTypeOperationInAndDateOperationBetweenOrderByDateOperationDescDateCreationDesc(
                        orgId, types, today, today);

        BigDecimal total = ops.stream().map(Operation::getMontant).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ResumeJour(ops.size(), total);
    }

    private String libelleRecente(Operation op, boolean hebdo, String membreNom) {
        if (hebdo) {
            String sem = extraireSemaineKey(op.getObservation());
            return sem != null ? membreNom + " — " + sem : membreNom + " — Cotisation hebdo";
        }
        String mois = op.getMoisAnnee() != null ? formatMoisCourt(op.getMoisAnnee()) : "Mois";
        return membreNom + " — " + mois;
    }

    private String metaRecente(Operation op, boolean hebdo) {
        LocalDateTime created = op.getDateCreation();
        LocalDate today = LocalDate.now();
        String heure = created != null ? created.format(HEURE) : "";
        if (op.getDateOperation().equals(today)) {
            return "Auj. " + heure;
        }
        if (op.getDateOperation().equals(today.minusDays(1))) {
            return "Hier " + heure;
        }
        if (hebdo) {
            return op.getDateOperation().format(JOUR_MOIS) + " · " + heure;
        }
        return op.getDateOperation().format(JOUR_MOIS) + " · Mois";
    }

    private String extraireSemaineKey(String observation) {
        if (observation == null) {
            return null;
        }
        int start = observation.indexOf('[');
        int end = observation.indexOf(']', start + 1);
        if (start < 0 || end <= start) {
            return null;
        }
        String key = observation.substring(start + 1, end);
        return key.matches("\\d{4}-W\\d{1,2}") ? "Sem. " + key.substring(key.indexOf('W') + 1) : null;
    }

    private String sousTitreMembre(Membre m) {
        if (m.getPoste() != null && m.getPoste() != PosteMembre.SIMPLE) {
            return m.getCodeMembre() + " · " + libellePoste(m.getPoste());
        }
        return m.getCodeMembre();
    }

    private String libellePoste(PosteMembre poste) {
        return switch (poste) {
            case PRESIDENT -> "Président(e)";
            case VICE_PRESIDENT -> "Vice-président(e)";
            case SECRETAIRE_GENERAL -> "S.G.";
            case SECRETAIRE_GENERAL_ADJOINT -> "S.G. Adjoint(e)";
            case TRESORIER -> "Trésorier(ère)";
            case TRESORIER_ADJOINT -> "Trésorier(ère) adj.";
            case COMMISSAIRE_AUX_COMPTES -> "Commissaire au compte";
            case SUPERVISEUR -> "Superviseur";
            default -> "Membre simple";
        };
    }

    private String libelleMois(String moisAnnee) {
        try {
            LocalDate d = LocalDate.parse(moisAnnee + "-01");
            return d.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH));
        } catch (Exception e) {
            return moisAnnee;
        }
    }

    private String formatMoisCourt(String moisAnnee) {
        try {
            LocalDate d = LocalDate.parse(moisAnnee + "-01");
            String m = d.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH));
            if (m.length() > 0) {
                return Character.toUpperCase(m.charAt(0)) + m.substring(1);
            }
            return m;
        } catch (Exception e) {
            return moisAnnee;
        }
    }

    private record ResumeJour(int nombre, BigDecimal montant) {}
}
