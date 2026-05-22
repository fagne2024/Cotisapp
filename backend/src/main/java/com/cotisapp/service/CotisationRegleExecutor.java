package com.cotisapp.service;

import com.cotisapp.domain.entity.Compte;
import com.cotisapp.domain.entity.MouvementCompte;
import com.cotisapp.domain.entity.MouvementRegle;
import com.cotisapp.domain.entity.Operation;
import com.cotisapp.domain.entity.RegleOperation;
import com.cotisapp.domain.enums.SensMouvement;
import com.cotisapp.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Applique les mouvements définis dans le paramétrage (regle_operation + mouvement_regle).
 */
@Component
@RequiredArgsConstructor
public class CotisationRegleExecutor {

    private final CompteRegleResolver compteRegleResolver;
    private final CompteService compteService;

    public List<MouvementCompte> executer(
            Long organisationId,
            Long membreId,
            Operation operation,
            RegleOperation regle,
            BigDecimal montantSaisi) {

        List<MouvementCompte> result = new ArrayList<>();
        List<MouvementRegle> defs = regle.getMouvements().stream()
                .sorted(Comparator.comparing(MouvementRegle::getOrdre))
                .toList();

        for (MouvementRegle def : defs) {
            BigDecimal montant = montantPourMouvement(def, regle, montantSaisi);
            if (montant == null || montant.signum() <= 0) {
                continue;
            }
            Compte source = compteRegleResolver.resoudre(organisationId, membreId, def.getSourceType());
            appliquer(operation, result, source.getId(), def.getSens(), montant);
            if (!def.getCibleType().equals(def.getSourceType())) {
                Compte cible = compteRegleResolver.resoudre(organisationId, membreId, def.getCibleType());
                if (!cible.getId().equals(source.getId())) {
                    appliquer(operation, result, cible.getId(), def.getSens(), montant);
                }
            }
        }

        if (result.isEmpty()) {
            throw new BusinessException("Aucun mouvement généré — vérifiez le paramétrage de la règle");
        }
        return result;
    }

    private void appliquer(
            Operation operation,
            List<MouvementCompte> result,
            Long compteId,
            SensMouvement sens,
            BigDecimal montant) {
        MouvementCompte mc = MouvementCompte.builder()
                .operation(operation)
                .compteId(compteId)
                .sens(sens)
                .montant(montant)
                .build();
        operation.getMouvements().add(mc);
        result.add(mc);
        compteService.appliquerMouvement(compteId, sens, montant);
    }

    private BigDecimal montantPourMouvement(MouvementRegle def, RegleOperation regle, BigDecimal montantSaisi) {
        String type = def.getTypeMontant() != null ? def.getTypeMontant() : "MONTANT_SAISI";
        return switch (type) {
            case "MONTANT_FIXE" -> Boolean.TRUE.equals(regle.getSolidariteAuto())
                    && regle.getMontantSolidariteAuto() != null
                    ? regle.getMontantSolidariteAuto()
                    : BigDecimal.ZERO;
            case "POURCENTAGE" -> montantSaisi.multiply(new BigDecimal("0.01"));
            default -> montantSaisi;
        };
    }
}
