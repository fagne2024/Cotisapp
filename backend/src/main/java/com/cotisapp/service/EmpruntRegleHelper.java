package com.cotisapp.service;

import com.cotisapp.domain.entity.RegleOperation;
import com.cotisapp.domain.enums.TypeEmprunt;
import com.cotisapp.domain.enums.TypeOperation;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.RegleOperationRepository;

import java.util.List;

/** Résolution des règles d'emprunt (étalé, caisse, solidarité) par organisation. */
public final class EmpruntRegleHelper {

    private EmpruntRegleHelper() {}

    public static RegleOperation trouverRegleEmprunt(RegleOperationRepository repo, Long orgId, TypeEmprunt type) {
        List<RegleOperation> emprunts = repo.findByOrganisationId(orgId).stream()
                .filter(r -> r.getTypeOperation() == TypeOperation.EMPRUNT)
                .toList();
        return switch (type) {
            case ETALE -> trouverParLibelle(emprunts, "étalé", "etale", "financement")
                    .orElseThrow(() -> new BusinessException("Règle emprunt étalé introuvable"));
            case CAISSE -> trouverParLibelle(emprunts, "caisse")
                    .orElseThrow(() -> new BusinessException("Règle emprunt caisse introuvable"));
            case SOLIDARITE -> trouverParLibelle(emprunts, "solidar")
                    .orElseThrow(() -> new BusinessException("Règle emprunt solidarité introuvable"));
        };
    }

    private static java.util.Optional<RegleOperation> trouverParLibelle(List<RegleOperation> regles, String... mots) {
        return regles.stream()
                .filter(r -> Boolean.TRUE.equals(r.getActif()))
                .filter(r -> {
                    String lib = r.getLibelle() != null ? r.getLibelle().toLowerCase() : "";
                    for (String m : mots) {
                        if (lib.contains(m.toLowerCase())) {
                            return true;
                        }
                    }
                    return false;
                })
                .findFirst();
    }
}
