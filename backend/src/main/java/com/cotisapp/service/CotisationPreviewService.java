package com.cotisapp.service;

import com.cotisapp.domain.entity.MouvementRegle;
import com.cotisapp.domain.entity.RegleOperation;
import com.cotisapp.dto.response.MouvementPreviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CotisationPreviewService {

    private final CotisationAmendeHelper cotisationAmendeHelper;

    public List<MouvementPreviewResponse> previsualiser(
            String nomMembre, BigDecimal montantSaisi, RegleOperation regle, BigDecimal montantAmende) {

        List<MouvementPreviewResponse> lignes = new ArrayList<>();
        List<MouvementRegle> defs = regle.getMouvements().stream()
                .sorted(Comparator.comparing(MouvementRegle::getOrdre))
                .toList();

        MouvementRegle cotisationMouv = defs.stream()
                .filter(m -> "MONTANT_SAISI".equals(m.getTypeMontant()))
                .findFirst()
                .orElse(null);

        if (cotisationMouv != null) {
            String libelle = libelleCotisationFusionnee(nomMembre, cotisationMouv);
            lignes.add(MouvementPreviewResponse.builder()
                    .libelle(libelle)
                    .sens("CREDIT")
                    .montant(montantSaisi)
                    .build());
        }

        for (MouvementRegle def : defs) {
            if (def == cotisationMouv) {
                continue;
            }
            BigDecimal mv = montantLigne(def, regle, montantSaisi);
            if (mv == null || mv.signum() <= 0) {
                continue;
            }
            lignes.add(MouvementPreviewResponse.builder()
                    .libelle(libelleMouvement(def, regle))
                    .sens(def.getSens() != null ? def.getSens().name() : "CREDIT")
                    .montant(mv)
                    .build());
        }

        if (lignes.isEmpty()) {
            lignes.add(MouvementPreviewResponse.builder()
                    .libelle("Cotisation — Épargne (" + nomMembre + ") et Caisse organisation")
                    .sens("CREDIT")
                    .montant(montantSaisi)
                    .build());
        }
        cotisationAmendeHelper.lignePreview(montantAmende).ifPresent(lignes::add);
        return lignes;
    }

    private String libelleCotisationFusionnee(String nomMembre, MouvementRegle m) {
        String src = libelleCourt(m.getSourceType());
        String cible = libelleCourt(m.getCibleType());
        if (m.getSourceType() != null && m.getSourceType().contains("EPARGNE")
                && "ORGANISATION.CAISSE".equals(m.getCibleType())) {
            return "Cotisation — Épargne (" + nomMembre + ") et Caisse organisation";
        }
        return src + " → " + cible + " (" + nomMembre + ")";
    }

    private String libelleMouvement(MouvementRegle m, RegleOperation regle) {
        if ("MONTANT_FIXE".equals(m.getTypeMontant()) && m.getSourceType() != null
                && m.getSourceType().contains("SOLIDARITE")) {
            BigDecimal sol = regle.getMontantSolidariteAuto();
            return "Solidarité auto (règle : " + (sol != null ? sol : "0") + " F fixe)";
        }
        return libelleCourt(m.getSourceType()) + " → " + libelleCourt(m.getCibleType());
    }

    private String libelleCourt(String code) {
        if (code == null) {
            return "—";
        }
        return code.replace("MEMBRE.", "Membre · ").replace("ORGANISATION.", "Organisation · ");
    }

    private BigDecimal montantLigne(MouvementRegle def, RegleOperation regle, BigDecimal montantSaisi) {
        String type = def.getTypeMontant() != null ? def.getTypeMontant() : "MONTANT_SAISI";
        return switch (type) {
            case "MONTANT_FIXE" -> Boolean.TRUE.equals(regle.getSolidariteAuto())
                    ? regle.getMontantSolidariteAuto()
                    : BigDecimal.ZERO;
            default -> null;
        };
    }
}
