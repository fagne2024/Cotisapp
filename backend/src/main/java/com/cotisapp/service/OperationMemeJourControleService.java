package com.cotisapp.service;

import com.cotisapp.domain.entity.DemandeOperationMembre;
import com.cotisapp.domain.entity.Emprunt;
import com.cotisapp.domain.enums.DemandeOperationStatut;
import com.cotisapp.domain.enums.DemandeOperationType;
import com.cotisapp.domain.enums.TypeEmprunt;
import com.cotisapp.dto.request.CotisationHebdoRequest;
import com.cotisapp.dto.request.CotisationMoisRequest;
import com.cotisapp.dto.request.RembourserRequest;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.DemandeOperationMembreRepository;
import com.cotisapp.repository.EmpruntRepository;
import com.cotisapp.repository.OperationRepository;
import com.cotisapp.util.SemaineIsoUtil;
import com.cotisapp.util.SemaineIsoUtil.BornesSemaine;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Contrôles d'unicité des opérations membre :
 * <ul>
 *   <li>cotisation hebdomadaire : une par {@code semaineKey} (ISO, ex. 2026-W21)</li>
 *   <li>cotisation mensuelle : une par {@code moisAnnee} (ex. 2026-05)</li>
 *   <li>octroi d'emprunt : un par type et par jour calendaire</li>
 * </ul>
 * Les familles restent indépendantes (hebdo + mensuelle + emprunt le même jour : autorisé).
 * Les demandes mobile money en attente bloquent le même type pour la même période (semaine ou mois).
 */
@Service
@RequiredArgsConstructor
public class OperationMemeJourControleService {

    private static final DateTimeFormatter DATE_FR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH);

    private final OperationRepository operationRepository;
    private final DemandeOperationMembreRepository demandeRepository;
    private final EmpruntRepository empruntRepository;
    private final ObjectMapper objectMapper;

    public void verifierCotisationHebdo(Long orgId, Long membreId, CotisationHebdoRequest request) {
        String semaineKey = normaliserSemaineKey(request.getSemaineKey());
        if (existeCotisationHebdoComptabilisee(orgId, membreId, semaineKey)) {
            throw dejaCotisationHebdo(semaineKey);
        }
        verifierDemandeCotisationHebdoEnAttente(orgId, membreId, semaineKey);
    }

    public void verifierCotisationMois(Long orgId, Long membreId, CotisationMoisRequest request) {
        String moisAnnee = normaliserMoisAnnee(request.getMoisAnnee());
        if (operationRepository.existsCotisationMoisMembrePourMois(orgId, membreId, moisAnnee)) {
            throw dejaCotisationMois(moisAnnee);
        }
        verifierDemandeCotisationMoisEnAttente(orgId, membreId, moisAnnee);
    }

    public void verifierOctroiEmprunt(Long orgId, Long membreId, TypeEmprunt typeEmprunt, LocalDate date) {
        verifierPasDejaMemeJour(
                orgId,
                membreId,
                date,
                () -> operationRepository.existsOctroiEmpruntMemeTypeMemeJour(
                        orgId, membreId, typeEmprunt, date),
                null,
                typeEmprunt,
                "octroi d'emprunt « " + libelleTypeEmprunt(typeEmprunt) + " »");
    }

    /** Plus de limite « un remboursement par type et par jour » : plusieurs remboursements le même jour sont autorisés. */
    public void verifierRemboursement(Long orgId, Long membreId, TypeEmprunt typeEmprunt, LocalDate date) {
        // contrôle désactivé
    }

    private boolean existeCotisationHebdoComptabilisee(Long orgId, Long membreId, String semaineKey) {
        String marqueur = SemaineIsoUtil.marqueurObservation(semaineKey);
        if (operationRepository.existsCotisationHebdoMembreAvecMarqueurSemaine(
                orgId, membreId, marqueur)) {
            return true;
        }
        BornesSemaine bornes = SemaineIsoUtil.parserSemaineKey(semaineKey);
        return operationRepository.existsCotisationHebdoMembreEntreDates(
                orgId, membreId, bornes.lundi(), bornes.dimanche());
    }

    private void verifierDemandeCotisationHebdoEnAttente(Long orgId, Long membreId, String semaineKey) {
        for (DemandeOperationMembre d : demandesEnAttente(orgId, membreId)) {
            if (d.getTypeDemande() != DemandeOperationType.COTISATION_HEBDO) {
                continue;
            }
            String semaineDemande = semaineKeyDepuisPayload(d);
            if (semaineKey.equals(semaineDemande)) {
                throw demandeCotisationHebdoEnAttente(semaineKey);
            }
        }
    }

    private void verifierDemandeCotisationMoisEnAttente(Long orgId, Long membreId, String moisAnnee) {
        for (DemandeOperationMembre d : demandesEnAttente(orgId, membreId)) {
            if (d.getTypeDemande() != DemandeOperationType.COTISATION_MOIS) {
                continue;
            }
            String moisDemande = moisAnneeDepuisPayload(d);
            if (moisAnnee.equals(moisDemande)) {
                throw demandeCotisationMoisEnAttente(moisAnnee);
            }
        }
    }

    private void verifierPasDejaMemeJour(
            Long orgId,
            Long membreId,
            LocalDate date,
            java.util.function.BooleanSupplier dejaComptabilise,
            DemandeOperationType typeDemande,
            TypeEmprunt typeEmpruntRemb,
            String libelle) {
        if (dejaComptabilise.getAsBoolean()) {
            throw dejaMemeJour(libelle, date);
        }
        if (typeDemande != null) {
            verifierDemandeMemeJourEnAttente(orgId, membreId, typeDemande, date, typeEmpruntRemb, libelle);
        }
    }

    private void verifierDemandeMemeJourEnAttente(
            Long orgId,
            Long membreId,
            DemandeOperationType typeDemande,
            LocalDate date,
            TypeEmprunt typeEmpruntRemb,
            String libelle) {
        for (DemandeOperationMembre d : demandesEnAttente(orgId, membreId)) {
            if (d.getTypeDemande() != typeDemande) {
                continue;
            }
            if (typeDemande == DemandeOperationType.REMBOURSEMENT && typeEmpruntRemb != null) {
                TypeEmprunt typeDemandeEmprunt = empruntRepository
                        .findByIdAndOrganisationId(d.getEmpruntId(), orgId)
                        .map(Emprunt::getTypeEmprunt)
                        .orElse(null);
                if (typeDemandeEmprunt != typeEmpruntRemb) {
                    continue;
                }
            }
            LocalDate dateOp = dateOperationDepuisPayloadRemboursement(d);
            if (date.equals(dateOp)) {
                throw demandeEnAttenteMemeJour(libelle, date);
            }
        }
    }

    private List<DemandeOperationMembre> demandesEnAttente(Long orgId, Long membreId) {
        return demandeRepository.findByOrganisationIdAndMembreIdAndStatutOrderByDateDemandeDesc(
                orgId, membreId, DemandeOperationStatut.EN_ATTENTE);
    }

    private String semaineKeyDepuisPayload(DemandeOperationMembre d) {
        try {
            CotisationHebdoRequest req =
                    objectMapper.readValue(d.getPayloadJson(), CotisationHebdoRequest.class);
            return normaliserSemaineKey(req.getSemaineKey());
        } catch (Exception e) {
            throw new BusinessException("Impossible de lire la semaine de la demande en attente");
        }
    }

    private String moisAnneeDepuisPayload(DemandeOperationMembre d) {
        try {
            CotisationMoisRequest req =
                    objectMapper.readValue(d.getPayloadJson(), CotisationMoisRequest.class);
            return normaliserMoisAnnee(req.getMoisAnnee());
        } catch (Exception e) {
            throw new BusinessException("Impossible de lire le mois de la demande en attente");
        }
    }

    private LocalDate dateOperationDepuisPayloadRemboursement(DemandeOperationMembre d) {
        try {
            RembourserRequest req = objectMapper.readValue(d.getPayloadJson(), RembourserRequest.class);
            return req.getDatePaiement() != null ? req.getDatePaiement() : LocalDate.now();
        } catch (Exception e) {
            throw new BusinessException("Impossible de lire la date de la demande en attente");
        }
    }

    private static String normaliserSemaineKey(String semaineKey) {
        if (semaineKey == null || semaineKey.isBlank()) {
            throw new BusinessException("Semaine de cotisation obligatoire");
        }
        return semaineKey.trim();
    }

    private static String normaliserMoisAnnee(String moisAnnee) {
        if (moisAnnee == null || moisAnnee.isBlank()) {
            throw new BusinessException("Mois de cotisation obligatoire");
        }
        return moisAnnee.trim();
    }

    private static BusinessException dejaCotisationHebdo(String semaineKey) {
        return new BusinessException(String.format(
                Locale.FRENCH,
                "Une cotisation hebdomadaire a déjà été enregistrée pour ce membre pour la semaine %s. "
                        + "Une seule cotisation hebdomadaire est autorisée par semaine.",
                semaineKey));
    }

    private static BusinessException dejaCotisationMois(String moisAnnee) {
        return new BusinessException(String.format(
                Locale.FRENCH,
                "Une cotisation mensuelle a déjà été enregistrée pour ce membre pour le mois %s. "
                        + "Une seule cotisation mensuelle est autorisée par mois.",
                moisAnnee));
    }

    private static BusinessException demandeCotisationHebdoEnAttente(String semaineKey) {
        return new BusinessException(String.format(
                Locale.FRENCH,
                "Une demande de cotisation hebdomadaire est déjà en attente de validation pour ce membre "
                        + "pour la semaine %s. Attendez le traitement de cette demande ou choisissez une autre semaine.",
                semaineKey));
    }

    private static BusinessException demandeCotisationMoisEnAttente(String moisAnnee) {
        return new BusinessException(String.format(
                Locale.FRENCH,
                "Une demande de cotisation mensuelle est déjà en attente de validation pour ce membre "
                        + "pour le mois %s. Attendez le traitement de cette demande ou choisissez un autre mois.",
                moisAnnee));
    }

    private static BusinessException dejaMemeJour(String libelle, LocalDate date) {
        return new BusinessException(String.format(
                Locale.FRENCH,
                "Une %s a déjà été enregistrée pour ce membre le %s. "
                        + "Une seule opération de ce type est autorisée par jour "
                        + "(cotisation hebdo, mensuelle, emprunt ou remboursement par type restent possibles le même jour).",
                libelle,
                date.format(DATE_FR)));
    }

    private static BusinessException demandeEnAttenteMemeJour(String libelle, LocalDate date) {
        return new BusinessException(String.format(
                Locale.FRENCH,
                "Une demande de %s est déjà en attente de validation pour ce membre à la date du %s. "
                        + "Attendez le traitement de cette demande ou choisissez une autre date.",
                libelle,
                date.format(DATE_FR)));
    }

    private static String libelleTypeEmprunt(TypeEmprunt type) {
        return switch (type) {
            case ETALE -> "étalé";
            case SOLIDARITE -> "solidarité";
            case CAISSE -> "caisse";
        };
    }
}
