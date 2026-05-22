package com.cotisapp.service;

import com.cotisapp.domain.entity.Compte;
import com.cotisapp.domain.entity.MouvementCompte;
import com.cotisapp.domain.entity.Operation;
import com.cotisapp.domain.entity.SuiviMensuel;
import com.cotisapp.domain.enums.ProprietaireCompte;
import com.cotisapp.domain.enums.SensMouvement;
import com.cotisapp.domain.enums.StatutSuiviMensuel;
import com.cotisapp.domain.enums.TypeOperation;
import com.cotisapp.dto.response.CotisationAnnulationResponse;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.CompteRepository;
import com.cotisapp.repository.OperationRepository;
import com.cotisapp.repository.SuiviMensuelRepository;
import com.cotisapp.security.OrganisationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CotisationAnnulationService {

    private final OperationRepository operationRepository;
    private final CompteRepository compteRepository;
    private final CompteService compteService;
    private final SuiviMensuelRepository suiviMensuelRepository;
    private final JournalService journalService;
    private final OperationPlanadGuardService operationPlanadGuardService;

    @Transactional
    public CotisationAnnulationResponse annuler(Long orgId, Long operationId) {
        Operation origine = operationRepository.findByIdAndOrganisationId(operationId, orgId)
                .orElseThrow(() -> new BusinessException("Opération introuvable"));

        if (origine.getOperationOrigineId() != null) {
            throw new BusinessException("Impossible d'annuler une opération de contre-passation");
        }
        if (Boolean.TRUE.equals(origine.getAnnulee())) {
            throw new BusinessException("Cette cotisation est déjà annulée");
        }
        if (operationRepository.existsByOperationOrigineId(operationId)) {
            throw new BusinessException("Cette cotisation est déjà annulée");
        }
        if (origine.getTypeOperation() != TypeOperation.COTISATION
                && origine.getTypeOperation() != TypeOperation.COTISATION_MOIS) {
            throw new BusinessException("Seules les cotisations hebdo ou mensuelles peuvent être annulées");
        }

        origine.getMouvements().size();
        if (origine.getMouvements().isEmpty()) {
            throw new BusinessException("Aucun mouvement comptable à inverser pour cette opération");
        }

        operationPlanadGuardService.verifierDateOperationAutorisee(
                orgId, origine.getExerciceId(), origine.getDateOperation());
        LocalDate dateAnnulation = origine.getDateOperation();
        String obsAnnulation = "[ANNULATION] Contre-passation opération #" + operationId
                + (origine.getObservation() != null ? " — " + origine.getObservation() : "");

        Operation annulation = Operation.builder()
                .organisationId(orgId)
                .exerciceId(origine.getExerciceId())
                .membreId(origine.getMembreId())
                .typeOperation(origine.getTypeOperation())
                .montant(origine.getMontant())
                .montantFrais(origine.getMontantFrais())
                .dateOperation(dateAnnulation)
                .observation(obsAnnulation)
                .moisAnnee(origine.getMoisAnnee())
                .operationOrigineId(operationId)
                .utilisateurId(OrganisationContext.getUserId())
                .build();

        List<MouvementCompte> mouvementsInverses = new ArrayList<>();
        for (MouvementCompte mc : origine.getMouvements()) {
            SensMouvement sensInverse = mc.getSens() == SensMouvement.CREDIT
                    ? SensMouvement.DEBIT
                    : SensMouvement.CREDIT;
            Compte compte = compteRepository.findById(mc.getCompteId())
                    .orElseThrow(() -> new BusinessException("Compte introuvable pour le mouvement"));
            boolean autoriserNegatif = compte.getProprietaire() == ProprietaireCompte.MEMBRE;

            MouvementCompte inverse = MouvementCompte.builder()
                    .operation(annulation)
                    .compteId(mc.getCompteId())
                    .sens(sensInverse)
                    .montant(mc.getMontant())
                    .build();
            annulation.getMouvements().add(inverse);
            mouvementsInverses.add(inverse);
            compteService.appliquerMouvement(mc.getCompteId(), sensInverse, mc.getMontant(), autoriserNegatif);
        }

        Operation saved = operationRepository.save(annulation);
        origine.setAnnulee(true);
        operationRepository.save(origine);

        if (origine.getTypeOperation() == TypeOperation.COTISATION_MOIS && origine.getMoisAnnee() != null) {
            retirerPaiementSuiviMensuel(orgId, origine.getMembreId(), origine.getMoisAnnee(), origine.getMontant());
        }

        journalService.enregistrer(orgId, "ANNULATION_COTISATION",
                "Opération " + operationId + " annulée → contre-passation " + saved.getId());

        return CotisationAnnulationResponse.builder()
                .operationOrigineId(operationId)
                .operationAnnulationId(saved.getId())
                .dateAnnulation(dateAnnulation)
                .mouvementsInverses(mouvementsInverses.size())
                .message("Cotisation annulée — " + mouvementsInverses.size() + " mouvement(s) inversé(s) sur les comptes.")
                .build();
    }

    private void retirerPaiementSuiviMensuel(Long orgId, Long membreId, String moisAnnee, BigDecimal montant) {
        if (membreId == null) {
            return;
        }
        suiviMensuelRepository.findByMembreIdAndMoisAnnee(membreId, moisAnnee).ifPresent(suivi -> {
            BigDecimal nouveauPaye = suivi.getMontantPaye().subtract(montant).max(BigDecimal.ZERO);
            suivi.setMontantPaye(nouveauPaye);
            if (nouveauPaye.compareTo(BigDecimal.ZERO) <= 0) {
                suivi.setStatut(StatutSuiviMensuel.NON_PAYE);
                suivi.setDatePaiement(null);
            } else if (nouveauPaye.compareTo(suivi.getMontantDu()) >= 0) {
                suivi.setStatut(StatutSuiviMensuel.PAYE);
            } else {
                suivi.setStatut(StatutSuiviMensuel.PARTIEL);
            }
            suiviMensuelRepository.save(suivi);
        });
    }
}
