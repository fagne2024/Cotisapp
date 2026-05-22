package com.cotisapp.service;

import com.cotisapp.domain.entity.Compte;
import com.cotisapp.domain.entity.MouvementCompte;
import com.cotisapp.domain.entity.Operation;
import com.cotisapp.domain.entity.RegleOperation;
import com.cotisapp.domain.enums.FamilleCompte;
import com.cotisapp.domain.enums.SensMouvement;
import com.cotisapp.domain.enums.TypeCompte;
import com.cotisapp.dto.response.MouvementPreviewResponse;
import com.cotisapp.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CotisationAmendeHelper {

    private final CompteService compteService;
    private final ParametrageCompteService parametrageCompteService;

    public boolean estRenseignee(BigDecimal montantAmende) {
        return montantAmende != null && montantAmende.signum() > 0;
    }

    public void valider(Long organisationId, BigDecimal montantAmende, RegleOperation regle) {
        if (!estRenseignee(montantAmende)) {
            return;
        }
        if (!parametrageCompteService.familleActive(organisationId, FamilleCompte.AMENDE)) {
            throw new BusinessException(
                    "Le compte amende n'est pas activé pour cette organisation — activez-le dans le paramétrage des comptes");
        }
        if (regle.getMontantAmendeMin() != null && montantAmende.compareTo(regle.getMontantAmendeMin()) < 0) {
            throw new BusinessException("Montant amende inférieur au minimum: " + regle.getMontantAmendeMin());
        }
        if (regle.getMontantAmendeMax() != null && montantAmende.compareTo(regle.getMontantAmendeMax()) > 0) {
            throw new BusinessException("Montant amende supérieur au maximum: " + regle.getMontantAmendeMax());
        }
    }

    public List<MouvementCompte> appliquer(
            Long organisationId,
            Long membreId,
            Operation operation,
            BigDecimal montantAmende) {
        if (!estRenseignee(montantAmende)) {
            return List.of();
        }
        Compte compteAmende = compteService.creerCompteMembre(organisationId, membreId, TypeCompte.AMENDE, null);
        MouvementCompte mc = MouvementCompte.builder()
                .operation(operation)
                .compteId(compteAmende.getId())
                .sens(SensMouvement.CREDIT)
                .montant(montantAmende)
                .build();
        operation.getMouvements().add(mc);
        compteService.appliquerMouvement(compteAmende.getId(), SensMouvement.CREDIT, montantAmende);
        return new ArrayList<>(List.of(mc));
    }

    public Optional<MouvementPreviewResponse> lignePreview(BigDecimal montantAmende) {
        if (!estRenseignee(montantAmende)) {
            return Optional.empty();
        }
        return Optional.of(MouvementPreviewResponse.builder()
                .libelle("Amende (optionnelle) — Compte membre")
                .sens("CREDIT")
                .montant(montantAmende)
                .build());
    }
}
