package com.cotisapp.service;

import com.cotisapp.domain.entity.MouvementRegle;
import com.cotisapp.domain.entity.RegleOperation;
import com.cotisapp.domain.enums.TypeOperation;
import com.cotisapp.dto.request.MouvementRegleRequest;
import com.cotisapp.dto.request.UpdateRegleOperationRequest;
import com.cotisapp.dto.response.CotisationsReglesResponse;
import com.cotisapp.dto.response.EmpruntsReglesResponse;
import com.cotisapp.dto.response.MouvementRegleResponse;
import com.cotisapp.dto.response.RegleOperationResponse;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.RegleOperationRepository;
import com.cotisapp.util.PartsCotisationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RegleOperationService {

    private static final Map<TypeOperation, Integer> ORDRE_AFFICHAGE = Map.ofEntries(
            Map.entry(TypeOperation.COTISATION, 1),
            Map.entry(TypeOperation.COTISATION_MOIS, 2),
            Map.entry(TypeOperation.VERSEMENT, 3),
            Map.entry(TypeOperation.EMPRUNT, 4),
            Map.entry(TypeOperation.REMBOURSEMENT, 5),
            Map.entry(TypeOperation.PENALITE, 6),
            Map.entry(TypeOperation.AMENDE, 7),
            Map.entry(TypeOperation.DEPENSE, 8),
            Map.entry(TypeOperation.BANQUE_VERSEMENT, 9),
            Map.entry(TypeOperation.BANQUE_RETRAIT, 10)
    );

    private final RegleOperationRepository regleOperationRepository;
    private final RegleInitialisationService regleInitialisationService;
    private final RegleBootstrapService regleBootstrapService;

    @Transactional(readOnly = true)
    public CotisationsReglesResponse obtenirReglesCotisations(Long organisationId) {
        regleBootstrapService.assurerReglesPourOrganisation(organisationId);
        RegleOperationResponse hebdo = regleOperationRepository
                .findByOrganisationIdAndTypeOperationAndActifTrue(organisationId, TypeOperation.COTISATION)
                .map(this::toResponse)
                .orElse(null);
        RegleOperationResponse mois = regleOperationRepository
                .findByOrganisationIdAndTypeOperationAndActifTrue(organisationId, TypeOperation.COTISATION_MOIS)
                .map(this::toResponse)
                .orElse(null);
        return CotisationsReglesResponse.builder()
                .hebdomadaire(hebdo)
                .mensuelle(mois)
                .build();
    }

    @Transactional(readOnly = true)
    public EmpruntsReglesResponse obtenirReglesEmprunts(Long organisationId) {
        regleBootstrapService.assurerReglesPourOrganisation(organisationId);
        regleInitialisationService.assurerReglesEmprunt(organisationId);
        List<RegleOperation> emprunts = regleOperationRepository.findByOrganisationId(organisationId).stream()
                .filter(r -> r.getTypeOperation() == TypeOperation.EMPRUNT)
                .toList();
        return EmpruntsReglesResponse.builder()
                .etale(trouverRegleEmprunt(emprunts, "étalé", "etale", "financement").map(this::toResponse).orElse(null))
                .caisse(trouverRegleEmprunt(emprunts, "caisse").map(this::toResponse).orElse(null))
                .solidarite(trouverRegleEmprunt(emprunts, "solidar").map(this::toResponse).orElse(null))
                .build();
    }

    private java.util.Optional<RegleOperation> trouverRegleEmprunt(List<RegleOperation> regles, String... mots) {
        return regles.stream()
                .filter(RegleOperation::getActif)
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

    @Transactional(readOnly = true)
    public List<RegleOperationResponse> lister(Long organisationId) {
        regleBootstrapService.assurerReglesPourOrganisation(organisationId);
        return regleOperationRepository.findByOrganisationId(organisationId).stream()
                .sorted(Comparator
                        .comparingInt((RegleOperation r) -> ORDRE_AFFICHAGE.getOrDefault(r.getTypeOperation(), 99))
                        .thenComparing(RegleOperation::getLibelle, String.CASE_INSENSITIVE_ORDER))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public RegleOperationResponse mettreAJour(Long organisationId, Long regleId, UpdateRegleOperationRequest request) {
        RegleOperation regle = chargerRegle(organisationId, regleId);
        regle.setLibelle(request.getLibelle());
        regle.setPeriodicite(request.getPeriodicite());
        regle.setMontantMin(request.getMontantMin());
        regle.setMontantMax(request.getMontantMax());
        regle.setMontantParPart(request.getMontantParPart());
        regle.setPartsMin(request.getPartsMin());
        regle.setPartsMax(request.getPartsMax());
        PartsCotisationUtil.synchroniserMontantsDepuisParts(regle);
        regle.setSolidariteAuto(Boolean.TRUE.equals(request.getSolidariteAuto()));
        regle.setMontantSolidariteAuto(request.getMontantSolidariteAuto());
        regle.setMontantAmendeMin(request.getMontantAmendeMin());
        regle.setMontantAmendeMax(request.getMontantAmendeMax());
        regle.setTypeFrais(request.getTypeFrais());
        if (request.getTypeFrais() == null) {
            regle.setMontantFrais(null);
            regle.setPourcentageFrais(null);
        } else {
            regle.setMontantFrais(request.getMontantFrais());
            regle.setPourcentageFrais(request.getPourcentageFrais());
        }
        regle.setNbEcheancesMin(request.getNbEcheancesMin());
        regle.setNbEcheancesMax(request.getNbEcheancesMax());
        regle.setNbEcheancesDefaut(request.getNbEcheancesDefaut());
        regle.setUniteEcheance(request.getUniteEcheance() != null ? request.getUniteEcheance()
                : com.cotisapp.domain.enums.UniteEcheance.MOIS);
        regle.setJourEcheanceMois(
                request.getUniteEcheance() == com.cotisapp.domain.enums.UniteEcheance.JOURS
                        ? null : request.getJourEcheanceMois());
        regle.setJoursAlerteEcheanceProche(request.getJoursAlerteEcheanceProche());
        regle.setMontantEcheanceMin(request.getMontantEcheanceMin());
        regle.setMontantEcheanceMax(request.getMontantEcheanceMax());
        regle.setTypePenalite(request.getTypePenalite());
        regle.setMontantPenalite(request.getMontantPenalite());
        regle.setPourcentagePenalite(request.getPourcentagePenalite());
        regle.setActif(Boolean.TRUE.equals(request.getActif()));

        regle.getMouvements().clear();
        for (MouvementRegleRequest m : request.getMouvements()) {
            regle.getMouvements().add(MouvementRegle.builder()
                    .regleOperation(regle)
                    .ordre(m.getOrdre())
                    .sourceType(m.getSourceType())
                    .cibleType(m.getCibleType())
                    .sens(m.getSens())
                    .typeMontant(m.getTypeMontant())
                    .build());
        }
        return toResponse(regleOperationRepository.save(regle));
    }

    @Transactional
    public RegleOperationResponse basculerActif(Long organisationId, Long regleId, boolean actif) {
        RegleOperation regle = chargerRegle(organisationId, regleId);
        regle.setActif(actif);
        return toResponse(regleOperationRepository.save(regle));
    }

    @Transactional
    public List<RegleOperationResponse> reinitialiser(Long organisationId) {
        List<RegleOperation> existantes = regleOperationRepository.findByOrganisationId(organisationId);
        regleOperationRepository.deleteAll(existantes);
        regleInitialisationService.initialiserReglesParDefaut(organisationId);
        return lister(organisationId);
    }

    private RegleOperation chargerRegle(Long organisationId, Long regleId) {
        RegleOperation regle = regleOperationRepository.findById(regleId)
                .orElseThrow(() -> new BusinessException("Règle introuvable"));
        if (!organisationId.equals(regle.getOrganisationId())) {
            throw new BusinessException("Règle hors organisation");
        }
        return regle;
    }

    private RegleOperationResponse toResponse(RegleOperation regle) {
        PartsCotisationUtil.normaliserPartsDepuisMontants(regle);
        List<MouvementRegleResponse> mouvements = regle.getMouvements().stream()
                .sorted(Comparator.comparing(MouvementRegle::getOrdre))
                .map(m -> MouvementRegleResponse.builder()
                        .id(m.getId())
                        .ordre(m.getOrdre())
                        .sourceType(m.getSourceType())
                        .cibleType(m.getCibleType())
                        .sens(m.getSens())
                        .typeMontant(m.getTypeMontant())
                        .build())
                .toList();
        return RegleOperationResponse.builder()
                .id(regle.getId())
                .typeOperation(regle.getTypeOperation())
                .libelle(regle.getLibelle())
                .periodicite(regle.getPeriodicite())
                .montantMin(regle.getMontantMin())
                .montantMax(regle.getMontantMax())
                .montantParPart(regle.getMontantParPart())
                .partsMin(regle.getPartsMin())
                .partsMax(regle.getPartsMax())
                .solidariteAuto(regle.getSolidariteAuto())
                .montantSolidariteAuto(regle.getMontantSolidariteAuto())
                .montantAmendeMin(regle.getMontantAmendeMin())
                .montantAmendeMax(regle.getMontantAmendeMax())
                .typeFrais(regle.getTypeFrais())
                .montantFrais(regle.getMontantFrais())
                .pourcentageFrais(regle.getPourcentageFrais())
                .nbEcheancesMin(regle.getNbEcheancesMin())
                .nbEcheancesMax(regle.getNbEcheancesMax())
                .nbEcheancesDefaut(regle.getNbEcheancesDefaut())
                .uniteEcheance(regle.getUniteEcheance())
                .jourEcheanceMois(regle.getJourEcheanceMois())
                .joursAlerteEcheanceProche(regle.getJoursAlerteEcheanceProche())
                .montantEcheanceMin(regle.getMontantEcheanceMin())
                .montantEcheanceMax(regle.getMontantEcheanceMax())
                .typePenalite(regle.getTypePenalite())
                .montantPenalite(regle.getMontantPenalite())
                .pourcentagePenalite(regle.getPourcentagePenalite())
                .actif(regle.getActif())
                .mouvements(mouvements)
                .build();
    }
}
