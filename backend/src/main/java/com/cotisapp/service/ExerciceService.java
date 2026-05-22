package com.cotisapp.service;

import com.cotisapp.domain.entity.Compte;
import com.cotisapp.domain.entity.Exercice;
import com.cotisapp.domain.entity.Organisation;
import com.cotisapp.domain.enums.StatutEmprunt;
import com.cotisapp.domain.enums.StatutExercice;
import com.cotisapp.domain.enums.StatutPlanad;
import com.cotisapp.domain.entity.JourneeReunion;
import com.cotisapp.dto.request.OuvrirExerciceRequest;
import com.cotisapp.dto.response.ExerciceResponse;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.CompteRepository;
import com.cotisapp.repository.EmpruntRepository;
import com.cotisapp.repository.ExerciceRepository;
import com.cotisapp.repository.JourneeReunionRepository;
import com.cotisapp.repository.OperationRepository;
import com.cotisapp.repository.OrganisationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ExerciceService {

    private final ExerciceRepository exerciceRepository;
    private final OrganisationRepository organisationRepository;
    private final JourneeReunionRepository journeeReunionRepository;
    private final CompteRepository compteRepository;
    private final EmpruntRepository empruntRepository;
    private final OperationRepository operationRepository;
    private final JournalService journalService;
    private final ClotureExerciceRepartitionService clotureExerciceRepartitionService;

    @Transactional
    public Exercice creerPremierExercice(Long orgId) {
        if (exerciceRepository.findByOrganisationIdAndStatut(orgId, StatutExercice.EN_COURS).isPresent()) {
            return exerciceRepository.findByOrganisationIdAndStatut(orgId, StatutExercice.EN_COURS).orElseThrow();
        }
        Exercice exercice = exerciceRepository.save(Exercice.builder()
                .organisationId(orgId)
                .numero(1)
                .statut(StatutExercice.EN_COURS)
                .dateDebut(LocalDate.now())
                .build());
        Organisation org = organisationRepository.findById(orgId)
                .orElseThrow(() -> new BusinessException("Organisation introuvable"));
        org.setExerciceCourantId(exercice.getId());
        organisationRepository.save(org);
        return exercice;
    }

    @Transactional(readOnly = true)
    public Long requireExerciceCourantId(Long orgId) {
        Organisation org = organisationRepository.findById(orgId)
                .orElseThrow(() -> new BusinessException("Organisation introuvable"));
        if (org.getExerciceCourantId() == null) {
            throw new BusinessException("Aucun exercice en cours pour cette organisation");
        }
        Exercice exercice = exerciceRepository.findByIdAndOrganisationId(org.getExerciceCourantId(), orgId)
                .orElseThrow(() -> new BusinessException("Exercice courant introuvable"));
        if (exercice.getStatut() != StatutExercice.EN_COURS) {
            throw new BusinessException("L'exercice courant n'est plus actif — ouvrez un nouvel exercice");
        }
        return exercice.getId();
    }

    @Transactional(readOnly = true)
    public Exercice requireExerciceCourant(Long orgId) {
        Long id = requireExerciceCourantId(orgId);
        return exerciceRepository.findById(id).orElseThrow();
    }

    public void verifierExerciceCourant(Long orgId, Long exerciceId) {
        if (exerciceId == null) {
            throw new BusinessException("Donnée sans exercice associé");
        }
        if (!requireExerciceCourantId(orgId).equals(exerciceId)) {
            throw new BusinessException("Cette action concerne un exercice clos — données historiques uniquement");
        }
    }

    @Transactional(readOnly = true)
    public void verifierExerciceAppartientOrg(Long orgId, Long exerciceId) {
        exerciceRepository.findByIdAndOrganisationId(exerciceId, orgId)
                .orElseThrow(() -> new BusinessException("Exercice introuvable"));
    }

    @Transactional(readOnly = true)
    public List<ExerciceResponse> lister(Long orgId) {
        organisationRepository.findById(orgId).orElseThrow(() -> new BusinessException("Organisation introuvable"));
        Long courantId = organisationRepository.findById(orgId).map(Organisation::getExerciceCourantId).orElse(null);
        return exerciceRepository.findByOrganisationIdOrderByNumeroDesc(orgId).stream()
                .map(e -> toResponse(e, courantId))
                .toList();
    }

    @Transactional(readOnly = true)
    public ExerciceResponse getCourant(Long orgId) {
        Exercice exercice = requireExerciceCourant(orgId);
        return toResponse(exercice, exercice.getId());
    }

    /**
     * Clôture l'exercice en cours (du PLANAD n°1 au dernier PLANAD enregistré) et ouvre le suivant.
     * Les données de l'exercice clôturé restent consultables ; les comptes peuvent être remis à zéro.
     */
    @Transactional
    public ExerciceResponse cloturerEtOuvrirSuivant(Long orgId, OuvrirExerciceRequest request) {
        Organisation org = organisationRepository.findById(orgId)
                .orElseThrow(() -> new BusinessException("Organisation introuvable"));
        Exercice courant = requireExerciceCourant(orgId);

        long empruntsEnCours = empruntRepository.findByOrganisationId(orgId).stream()
                .filter(e -> e.getExerciceId().equals(courant.getId()))
                .filter(e -> e.getStatut() == StatutEmprunt.EN_COURS)
                .count();
        if (empruntsEnCours > 0) {
            throw new BusinessException(
                    "Impossible de clôturer : " + empruntsEnCours + " emprunt(s) encore en cours sur cet exercice");
        }

        if (journeeReunionRepository.existsByExerciceIdAndStatut(courant.getId(), StatutPlanad.OUVERT)) {
            String libelle = journeeReunionRepository.findPlanadOuvert(courant.getId())
                    .map(JourneeReunion::getLibelle)
                    .orElse("PLANAD ouvert");
            throw new BusinessException("Impossible de clôturer l'exercice : clôturez d'abord le " + libelle);
        }

        if (request != null && Boolean.TRUE.equals(request.getEffectuerRepartition())) {
            clotureExerciceRepartitionService.executerRepartition(orgId, courant.getId());
        }

        int planadFin = journeeReunionRepository.findMaxNumero(courant.getId());
        courant.setStatut(StatutExercice.CLOTURE);
        courant.setDateCloture(LocalDate.now());
        courant.setPlanadFin(planadFin > 0 ? planadFin : null);
        if (request != null && request.getObservationCloture() != null && !request.getObservationCloture().isBlank()) {
            courant.setObservationCloture(request.getObservationCloture().trim());
        }
        exerciceRepository.save(courant);

        boolean reinit = request != null && Boolean.TRUE.equals(request.getReinitialiserComptes());
        int prochainNumero = exerciceRepository.findMaxNumero(orgId) + 1;
        Exercice nouveau = exerciceRepository.save(Exercice.builder()
                .organisationId(orgId)
                .numero(prochainNumero)
                .statut(StatutExercice.EN_COURS)
                .dateDebut(LocalDate.now())
                .reinitialisationComptes(reinit)
                .build());

        if (reinit) {
            reinitialiserSoldesComptes(orgId);
        }

        org.setExerciceCourantId(nouveau.getId());
        organisationRepository.save(org);

        journalService.enregistrer(
                orgId,
                "EXERCICE_TRANSITION",
                "Clôture exercice n°" + courant.getNumero()
                        + (planadFin > 0 ? " (PLANAD n°" + planadFin + ")" : "")
                        + " → ouverture exercice n°" + nouveau.getNumero()
                        + (reinit ? ", comptes réinitialisés" : ""));

        return toResponse(nouveau, nouveau.getId());
    }

    /**
     * Réouverture d'un exercice clôturé — réservée au superadmin (contrôle au niveau controller).
     * Seul l'exercice immédiatement précédent l'exercice en cours peut être réouvert, et seulement si
     * l'exercice suivant (en cours) ne contient aucune donnée.
     */
    @Transactional
    public ExerciceResponse reouvrir(Long orgId, Long exerciceId) {
        Organisation org = organisationRepository.findById(orgId)
                .orElseThrow(() -> new BusinessException("Organisation introuvable"));
        Exercice cible = exerciceRepository.findByIdAndOrganisationId(exerciceId, orgId)
                .orElseThrow(() -> new BusinessException("Exercice introuvable"));
        if (cible.getStatut() != StatutExercice.CLOTURE) {
            throw new BusinessException("Cet exercice est déjà en cours");
        }

        Exercice enCours = exerciceRepository.findByOrganisationIdAndStatut(orgId, StatutExercice.EN_COURS)
                .orElseThrow(() -> new BusinessException(
                        "Aucun exercice en cours — réouverture impossible dans cet état"));
        if (cible.getNumero() + 1 != enCours.getNumero()) {
            throw new BusinessException(
                    "Seul l'exercice n°" + (enCours.getNumero() - 1)
                            + " (immédiatement précédent) peut être réouvert");
        }
        verifierExerciceSuivantVide(orgId, enCours);

        enCours.setStatut(StatutExercice.CLOTURE);
        enCours.setDateCloture(LocalDate.now());
        enCours.setObservationCloture("Clôture automatique — réouverture de l'exercice n°" + cible.getNumero());
        exerciceRepository.save(enCours);

        cible.setStatut(StatutExercice.EN_COURS);
        cible.setDateCloture(null);
        cible.setPlanadFin(null);
        exerciceRepository.save(cible);

        org.setExerciceCourantId(cible.getId());
        organisationRepository.save(org);

        journalService.enregistrer(
                orgId,
                "EXERCICE_REOUVERTURE",
                "Réouverture exercice n°" + cible.getNumero()
                        + " (exercice n°" + enCours.getNumero() + " clôturé automatiquement)");

        return toResponse(cible, cible.getId());
    }

    private void verifierExerciceSuivantVide(Long orgId, Exercice exercice) {
        Long id = exercice.getId();
        long nbOps = operationRepository.countByOrganisationIdAndExerciceId(orgId, id);
        int nbPlanads = journeeReunionRepository.findMaxNumero(id);
        long nbEmprunts = empruntRepository.findByOrganisationIdAndExerciceId(orgId, id).size();
        if (nbOps > 0 || nbPlanads > 0 || nbEmprunts > 0) {
            throw new BusinessException(
                    "Impossible de réouvrir : l'exercice n°" + exercice.getNumero()
                            + " contient déjà des données (opérations, PLANAD ou emprunts)");
        }
    }

    private void reinitialiserSoldesComptes(Long orgId) {
        List<Compte> comptes = compteRepository.findByOrganisationId(orgId);
        for (Compte compte : comptes) {
            if (compte.getSolde().compareTo(BigDecimal.ZERO) != 0) {
                compte.setSolde(BigDecimal.ZERO);
            }
        }
        compteRepository.saveAll(comptes);
    }

    private ExerciceResponse toResponse(Exercice e, Long exerciceCourantId) {
        int nbPlanads = journeeReunionRepository.findMaxNumero(e.getId());
        long nbOuverts = journeeReunionRepository.countByExerciceIdAndStatut(e.getId(), StatutPlanad.OUVERT);
        Optional<JourneeReunion> ouvert = journeeReunionRepository.findPlanadOuvert(e.getId());
        return ExerciceResponse.builder()
                .id(e.getId())
                .organisationId(e.getOrganisationId())
                .numero(e.getNumero())
                .statut(e.getStatut())
                .dateDebut(e.getDateDebut())
                .dateCloture(e.getDateCloture())
                .planadFin(e.getPlanadFin())
                .reinitialisationComptes(e.getReinitialisationComptes())
                .observationCloture(e.getObservationCloture())
                .courant(exerciceCourantId != null && exerciceCourantId.equals(e.getId()))
                .nbPlanads(nbPlanads)
                .nbPlanadsOuverts((int) nbOuverts)
                .tousPlanadsClotures(nbOuverts == 0)
                .planadOuvertLibelle(ouvert.map(JourneeReunion::getLibelle).orElse(null))
                .build();
    }
}
