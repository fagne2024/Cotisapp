package com.cotisapp.service;

import com.cotisapp.domain.entity.MouvementRegle;
import com.cotisapp.domain.entity.RegleOperation;
import com.cotisapp.domain.enums.Periodicite;
import com.cotisapp.domain.enums.SensMouvement;
import com.cotisapp.domain.enums.TypeModeCalcul;
import com.cotisapp.domain.enums.TypeOperation;
import com.cotisapp.repository.RegleOperationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RegleInitialisationService {

    private final RegleOperationRepository regleOperationRepository;

    @Transactional
    public void assurerReglesEmprunt(Long organisationId) {
        List<RegleOperation> existantes = regleOperationRepository.findByOrganisationId(organisationId).stream()
                .filter(r -> r.getTypeOperation() == TypeOperation.EMPRUNT)
                .toList();
        if (existantes.stream().noneMatch(r -> libelleContient(r, "étalé", "etale", "financement"))) {
            regleOperationRepository.save(creerEmpruntEtale(organisationId));
        }
        if (existantes.stream().noneMatch(r -> libelleContient(r, "caisse"))) {
            regleOperationRepository.save(creerEmpruntCaisse(organisationId));
        }
        if (existantes.stream().noneMatch(r -> libelleContient(r, "solidar"))) {
            regleOperationRepository.save(creerEmpruntSolidarite(organisationId));
        }
    }

    private static boolean libelleContient(RegleOperation r, String... mots) {
        String lib = r.getLibelle() != null ? r.getLibelle().toLowerCase() : "";
        for (String m : mots) {
            if (lib.contains(m.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    @Transactional
    public void assurerReglesCotisation(Long organisationId) {
        if (!regleOperationRepository.existsByOrganisationIdAndTypeOperation(organisationId, TypeOperation.COTISATION)) {
            regleOperationRepository.save(creerCotisationHebdo(organisationId));
        }
        if (!regleOperationRepository.existsByOrganisationIdAndTypeOperation(organisationId, TypeOperation.COTISATION_MOIS)) {
            regleOperationRepository.save(creerCotisationMois(organisationId));
        }
    }

    @Transactional
    public void initialiserReglesParDefaut(Long organisationId) {
        List<RegleOperation> regles = new ArrayList<>();
        regles.add(creerCotisationHebdo(organisationId));
        regles.add(creerCotisationMois(organisationId));
        regles.add(creerRegleSimple(organisationId, TypeOperation.VERSEMENT, "Versement", Periodicite.LIBRE));
        regles.add(creerEmpruntEtale(organisationId));
        regles.add(creerEmpruntCaisse(organisationId));
        regles.add(creerEmpruntSolidarite(organisationId));
        regles.add(creerRegleSimple(organisationId, TypeOperation.PENALITE, "Pénalité", null));
        regles.add(creerRegleSimple(organisationId, TypeOperation.AMENDE, "Amende", null));
        regles.add(creerRegleSimple(organisationId, TypeOperation.DEPENSE, "Dépense", null));
        regles.add(creerRegleSimple(organisationId, TypeOperation.BANQUE_VERSEMENT, "Banque (Versement / Retrait)", null));
        regles.add(creerRegleSimple(organisationId, TypeOperation.REMBOURSEMENT, "Remboursement", null));
        regleOperationRepository.saveAll(regles);
    }

    private RegleOperation creerCotisationHebdo(Long orgId) {
        RegleOperation regle = RegleOperation.builder()
                .organisationId(orgId)
                .typeOperation(TypeOperation.COTISATION)
                .libelle("Cotisation Hebdomadaire")
                .periodicite(Periodicite.HEBDOMADAIRE)
                .montantParPart(new BigDecimal("1000"))
                .partsMin(1)
                .partsMax(10)
                .montantMin(new BigDecimal("1000"))
                .montantMax(new BigDecimal("10000"))
                .solidariteAuto(true)
                .montantSolidariteAuto(new BigDecimal("200"))
                .montantAmendeMin(new BigDecimal("500"))
                .montantAmendeMax(new BigDecimal("3000"))
                .actif(true)
                .build();
        ajouterMouvementsCotisation(regle, "MEMBRE.EPARGNE_HEBDO");
        return regle;
    }

    private RegleOperation creerCotisationMois(Long orgId) {
        RegleOperation regle = RegleOperation.builder()
                .organisationId(orgId)
                .typeOperation(TypeOperation.COTISATION_MOIS)
                .libelle("Cotisation Mensuelle (Mois)")
                .periodicite(Periodicite.MENSUEL)
                .montantParPart(new BigDecimal("1000"))
                .partsMin(5)
                .partsMax(20)
                .montantMin(new BigDecimal("5000"))
                .montantMax(new BigDecimal("20000"))
                .solidariteAuto(true)
                .montantSolidariteAuto(new BigDecimal("200"))
                .montantAmendeMin(new BigDecimal("1000"))
                .montantAmendeMax(new BigDecimal("5000"))
                .actif(true)
                .build();
        ajouterMouvementsCotisation(regle, "MEMBRE.EPARGNE_MOIS");
        return regle;
    }

    private void ajouterMouvementsCotisation(RegleOperation regle, String sourceEpargne) {
        regle.getMouvements().add(MouvementRegle.builder()
                .regleOperation(regle).ordre(1)
                .sourceType(sourceEpargne).cibleType("ORGANISATION.CAISSE")
                .sens(SensMouvement.CREDIT).typeMontant("MONTANT_SAISI").build());
        regle.getMouvements().add(MouvementRegle.builder()
                .regleOperation(regle).ordre(2)
                .sourceType("MEMBRE.SOLIDARITE").cibleType("ORGANISATION.SOLIDARITE")
                .sens(SensMouvement.CREDIT).typeMontant("MONTANT_FIXE").build());
    }

    private RegleOperation creerEmpruntEtale(Long orgId) {
        return RegleOperation.builder()
                .organisationId(orgId)
                .typeOperation(TypeOperation.EMPRUNT)
                .libelle("Emprunt Étalé / Financement")
                .montantMin(new BigDecimal("25000"))
                .montantMax(new BigDecimal("500000"))
                .typeFrais(TypeModeCalcul.POURCENTAGE)
                .pourcentageFrais(new BigDecimal("5"))
                .nbEcheancesMin(3)
                .nbEcheancesMax(12)
                .nbEcheancesDefaut(4)
                .jourEcheanceMois(15)
                .montantEcheanceMin(new BigDecimal("5000"))
                .montantEcheanceMax(new BigDecimal("150000"))
                .typePenalite(TypeModeCalcul.FIXE)
                .montantPenalite(new BigDecimal("500"))
                .solidariteAuto(false)
                .actif(true)
                .build();
    }

    private RegleOperation creerEmpruntCaisse(Long orgId) {
        return RegleOperation.builder()
                .organisationId(orgId)
                .typeOperation(TypeOperation.EMPRUNT)
                .libelle("Emprunt Caisse")
                .montantMin(new BigDecimal("50000"))
                .montantMax(new BigDecimal("1000000"))
                .typeFrais(TypeModeCalcul.FIXE)
                .montantFrais(new BigDecimal("5000"))
                .nbEcheancesMin(1)
                .nbEcheancesMax(6)
                .nbEcheancesDefaut(3)
                .jourEcheanceMois(15)
                .montantEcheanceMin(new BigDecimal("10000"))
                .montantEcheanceMax(new BigDecimal("400000"))
                .typePenalite(TypeModeCalcul.POURCENTAGE)
                .pourcentagePenalite(new BigDecimal("2"))
                .solidariteAuto(false)
                .actif(true)
                .build();
    }

    private RegleOperation creerEmpruntSolidarite(Long orgId) {
        return RegleOperation.builder()
                .organisationId(orgId)
                .typeOperation(TypeOperation.EMPRUNT)
                .libelle("Emprunt Solidarité")
                .montantMin(new BigDecimal("5000"))
                .montantMax(new BigDecimal("150000"))
                .typeFrais(null)
                .montantFrais(null)
                .nbEcheancesMin(1)
                .nbEcheancesMax(1)
                .nbEcheancesDefaut(1)
                .montantEcheanceMin(new BigDecimal("5000"))
                .montantEcheanceMax(new BigDecimal("150000"))
                .typePenalite(TypeModeCalcul.FIXE)
                .montantPenalite(new BigDecimal("200"))
                .solidariteAuto(false)
                .actif(true)
                .build();
    }

    private RegleOperation creerRegleSimple(Long orgId, TypeOperation type, String libelle, Periodicite periodicite) {
        return RegleOperation.builder()
                .organisationId(orgId)
                .typeOperation(type)
                .libelle(libelle)
                .periodicite(periodicite)
                .actif(true)
                .build();
    }
}
