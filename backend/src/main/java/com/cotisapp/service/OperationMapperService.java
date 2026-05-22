package com.cotisapp.service;

import com.cotisapp.domain.entity.Membre;
import com.cotisapp.domain.entity.MouvementCompte;
import com.cotisapp.domain.entity.Operation;
import com.cotisapp.domain.entity.RegleOperation;
import com.cotisapp.domain.enums.SensMouvement;
import com.cotisapp.domain.enums.TypeOperation;
import com.cotisapp.dto.request.CotisationHebdoRequest;
import com.cotisapp.dto.request.CotisationMoisRequest;
import com.cotisapp.dto.response.MouvementPreviewResponse;
import com.cotisapp.dto.response.OperationResponse;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.repository.RegleOperationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OperationMapperService {

    private final MembreRepository membreRepository;
    private final RegleOperationRepository regleOperationRepository;
    private final CotisationPreviewService cotisationPreviewService;
    private final CotisationAmendeHelper cotisationAmendeHelper;

    public OperationResponse toResponse(Operation op) {
        return toResponse(op, null);
    }

    public OperationResponse toResponse(Operation op, Long compteSolidariteMembreId) {
        String membreNom = null;
        if (op.getMembreId() != null) {
            membreNom = membreRepository.findById(op.getMembreId())
                    .map(Membre::getNomComplet).orElse(null);
        }
        BigDecimal montantSolidarite = montantCreditCompte(op, compteSolidariteMembreId);
        return OperationResponse.builder()
                .id(op.getId())
                .typeOperation(op.getTypeOperation())
                .membreId(op.getMembreId())
                .membreNom(membreNom)
                .montant(op.getMontant())
                .montantFrais(op.getMontantFrais())
                .montantSolidarite(montantSolidarite.signum() > 0 ? montantSolidarite : null)
                .dateOperation(op.getDateOperation())
                .moisAnnee(op.getMoisAnnee())
                .empruntId(op.getEmpruntId())
                .observation(op.getObservation())
                .modePaiement(op.getModePaiement())
                .referencePaiement(op.getReferencePaiement())
                .build();
    }

    private static BigDecimal montantCreditCompte(Operation op, Long compteId) {
        if (compteId == null || op.getMouvements() == null) {
            return BigDecimal.ZERO;
        }
        TypeOperation type = op.getTypeOperation();
        if (type != TypeOperation.COTISATION && type != TypeOperation.COTISATION_MOIS) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (MouvementCompte mc : op.getMouvements()) {
            if (compteId.equals(mc.getCompteId()) && mc.getSens() == SensMouvement.CREDIT) {
                total = total.add(mc.getMontant() != null ? mc.getMontant() : BigDecimal.ZERO);
            }
        }
        return total;
    }

    public List<MouvementPreviewResponse> previewCotisationHebdo(Long orgId, CotisationHebdoRequest req) {
        Membre membre = membreRepository.findById(req.getMembreId()).orElse(null);
        String nom = membre != null ? membre.getNomComplet() : "";
        RegleOperation regle = regleOperationRepository
                .findByOrganisationIdAndTypeOperationAndActifTrue(orgId, TypeOperation.COTISATION)
                .orElse(null);
        return previsualiser(orgId, nom, req.getMontant(), req.getMontantAmende(), regle);
    }

    public List<MouvementPreviewResponse> previewCotisationMois(Long orgId, CotisationMoisRequest req) {
        Membre membre = membreRepository.findById(req.getMembreId()).orElse(null);
        String nom = membre != null ? membre.getNomComplet() : "";
        RegleOperation regle = regleOperationRepository
                .findByOrganisationIdAndTypeOperationAndActifTrue(orgId, TypeOperation.COTISATION_MOIS)
                .orElse(null);
        return previsualiser(orgId, nom, req.getMontant(), req.getMontantAmende(), regle);
    }

    private List<MouvementPreviewResponse> previsualiser(
            Long orgId,
            String nomMembre,
            BigDecimal montant,
            BigDecimal montantAmende,
            RegleOperation regle) {
        if (regle != null) {
            cotisationAmendeHelper.valider(orgId, montantAmende, regle);
        }
        RegleOperation regleEffective = regle != null
                ? regle
                : RegleOperation.builder().solidariteAuto(false).build();
        return cotisationPreviewService.previsualiser(nomMembre, montant, regleEffective, montantAmende);
    }
}
