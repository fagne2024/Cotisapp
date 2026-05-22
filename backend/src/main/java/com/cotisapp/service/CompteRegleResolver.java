package com.cotisapp.service;

import com.cotisapp.domain.entity.Compte;
import com.cotisapp.domain.enums.TypeCompte;
import com.cotisapp.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Résout un code compte de règle (ex. MEMBRE.EPARGNE_HEBDO) vers un compte réel.
 */
@Component
@RequiredArgsConstructor
public class CompteRegleResolver {

    private final CompteService compteService;

    public Compte resoudre(Long organisationId, Long membreId, String codeCompte) {
        if (codeCompte == null || codeCompte.isBlank()) {
            throw new BusinessException("Code compte de règle invalide");
        }
        String[] parts = codeCompte.split("\\.", 2);
        if (parts.length != 2) {
            throw new BusinessException("Code compte de règle invalide: " + codeCompte);
        }
        String famille = parts[0].trim().toUpperCase();
        String typeStr = parts[1].trim().toUpperCase();
        TypeCompte typeCompte = mapTypeCompte(typeStr);

        if ("MEMBRE".equals(famille)) {
            if (membreId == null) {
                throw new BusinessException("Membre requis pour le compte " + codeCompte);
            }
            return compteService.getCompteMembre(membreId, typeCompte);
        }
        if ("ORGANISATION".equals(famille)) {
            return compteService.getCompteOrg(organisationId, typeCompte);
        }
        throw new BusinessException("Famille de compte inconnue: " + famille);
    }

    private static TypeCompte mapTypeCompte(String typeStr) {
        return switch (typeStr) {
            case "EPARGNE_HEBDO" -> TypeCompte.EPARGNE_HEBDO;
            case "EPARGNE_MOIS" -> TypeCompte.EPARGNE_MOIS;
            case "EPARGNE" -> TypeCompte.EPARGNE_HEBDO;
            case "SOLIDARITE" -> TypeCompte.SOLIDARITE;
            case "DEPENSE" -> TypeCompte.DEPENSE;
            case "PENALITE" -> TypeCompte.PENALITE;
            case "AMENDE" -> TypeCompte.AMENDE;
            case "CAISSE" -> TypeCompte.CAISSE;
            case "BANQUE" -> TypeCompte.BANQUE;
            default -> throw new BusinessException("Type de compte inconnu: " + typeStr);
        };
    }
}
