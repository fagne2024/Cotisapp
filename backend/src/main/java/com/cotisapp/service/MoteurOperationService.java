package com.cotisapp.service;

import com.cotisapp.domain.entity.*;
import com.cotisapp.domain.enums.*;
import com.cotisapp.dto.request.CotisationHebdoRequest;
import com.cotisapp.dto.request.CotisationMoisRequest;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.repository.OperationRepository;
import com.cotisapp.repository.RegleOperationRepository;
import com.cotisapp.domain.enums.ModePaiement;
import com.cotisapp.util.ModePaiementHelper;
import com.cotisapp.util.PartsCotisationUtil;
import com.cotisapp.security.OrganisationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MoteurOperationService {

    private final OperationRepository operationRepository;
    private final RegleOperationRepository regleOperationRepository;
    private final MembreRepository membreRepository;
    private final SuiviMensuelService suiviMensuelService;
    private final JournalService journalService;
    private final CotisationRegleExecutor cotisationRegleExecutor;
    private final CotisationAmendeHelper cotisationAmendeHelper;
    private final ExerciceService exerciceService;
    private final OperationPlanadGuardService operationPlanadGuardService;
    private final OperationMemeJourControleService operationMemeJourControleService;

    @Transactional
    public Operation cotisationHebdo(Long orgId, CotisationHebdoRequest request) {
        Membre membre = membreRepository.findByIdAndOrganisationId(request.getMembreId(), orgId)
                .orElseThrow(() -> new BusinessException("Membre introuvable"));
        operationMemeJourControleService.verifierCotisationHebdo(orgId, membre.getId(), request);
        RegleOperation regle = regleOperationRepository
                .findByOrganisationIdAndTypeOperationAndActifTrue(orgId, TypeOperation.COTISATION)
                .orElseThrow(() -> new BusinessException("Règle COTISATION (hebdomadaire) introuvable"));

        validerMontant(request.getMontant(), regle);
        cotisationAmendeHelper.valider(orgId, request.getMontantAmende(), regle);

        ModePaiement modePaiement = ModePaiementHelper.parser(request.getModePaiement());
        String obs = request.getObservation();
        String obsFinale = (obs != null && !obs.isBlank())
                ? "[" + request.getSemaineKey() + "] " + obs.trim()
                : "[" + request.getSemaineKey() + "]";
        obsFinale = ModePaiementHelper.enrichirObservation(obsFinale, modePaiement, request.getReferencePaiement());

        Long exerciceId = exerciceService.requireExerciceCourantId(orgId);
        operationPlanadGuardService.verifierDateOperationAutorisee(orgId, exerciceId, request.getDateOperation());
        Operation operation = Operation.builder()
                .organisationId(orgId)
                .exerciceId(exerciceId)
                .membreId(membre.getId())
                .typeOperation(TypeOperation.COTISATION)
                .montant(request.getMontant())
                .dateOperation(request.getDateOperation())
                .observation(obsFinale)
                .modePaiement(modePaiement)
                .referencePaiement(blankToNull(request.getReferencePaiement()))
                .utilisateurId(OrganisationContext.getUserId())
                .build();

        List<MouvementCompte> mouvements = cotisationRegleExecutor.executer(
                orgId, membre.getId(), operation, regle, request.getMontant());
        mouvements.addAll(cotisationAmendeHelper.appliquer(
                orgId, membre.getId(), operation, request.getMontantAmende()));
        operation.setMouvements(mouvements);
        Operation saved = operationRepository.save(operation);
        journalService.enregistrer(orgId, "COTISATION", "Opération " + saved.getId());
        return saved;
    }

    @Transactional
    public Operation cotisationMois(Long orgId, CotisationMoisRequest request) {
        Membre membre = membreRepository.findByIdAndOrganisationId(request.getMembreId(), orgId)
                .orElseThrow(() -> new BusinessException("Membre introuvable"));
        operationMemeJourControleService.verifierCotisationMois(orgId, membre.getId(), request);
        RegleOperation regle = regleOperationRepository
                .findByOrganisationIdAndTypeOperationAndActifTrue(orgId, TypeOperation.COTISATION_MOIS)
                .orElseThrow(() -> new BusinessException("Règle COTISATION_MOIS introuvable"));

        validerMontant(request.getMontant(), regle);
        cotisationAmendeHelper.valider(orgId, request.getMontantAmende(), regle);

        ModePaiement modePaiement = ModePaiementHelper.parser(request.getModePaiement());
        String obsMois = ModePaiementHelper.enrichirObservation(
                request.getObservation(), modePaiement, request.getReferencePaiement());

        Long exerciceId = exerciceService.requireExerciceCourantId(orgId);
        operationPlanadGuardService.verifierDateOperationAutorisee(orgId, exerciceId, request.getDateOperation());
        Operation operation = Operation.builder()
                .organisationId(orgId)
                .exerciceId(exerciceId)
                .membreId(membre.getId())
                .typeOperation(TypeOperation.COTISATION_MOIS)
                .montant(request.getMontant())
                .dateOperation(request.getDateOperation())
                .observation(obsMois)
                .modePaiement(modePaiement)
                .referencePaiement(blankToNull(request.getReferencePaiement()))
                .moisAnnee(request.getMoisAnnee())
                .utilisateurId(OrganisationContext.getUserId())
                .build();

        List<MouvementCompte> mouvements = cotisationRegleExecutor.executer(
                orgId, membre.getId(), operation, regle, request.getMontant());
        mouvements.addAll(cotisationAmendeHelper.appliquer(
                orgId, membre.getId(), operation, request.getMontantAmende()));
        operation.setMouvements(mouvements);
        Operation saved = operationRepository.save(operation);
        suiviMensuelService.mettreAJourApresPaiement(orgId, membre.getId(), request.getMoisAnnee(), request.getMontant());
        journalService.enregistrer(orgId, "COTISATION_MOIS", "Opération " + saved.getId());
        return saved;
    }

    private void validerMontant(BigDecimal montant, RegleOperation regle) {
        PartsCotisationUtil.validerMontantCotisation(montant, regle);
    }

    private static String blankToNull(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }
}
