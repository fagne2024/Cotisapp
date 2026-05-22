package com.cotisapp.service;

import com.cotisapp.domain.entity.ParametrageClotureExercice;
import com.cotisapp.domain.entity.RegleOperation;
import com.cotisapp.domain.entity.Membre;
import com.cotisapp.domain.enums.ModeAgregationPostesCloture;
import com.cotisapp.domain.enums.ModeCalculProrataCloture;
import com.cotisapp.domain.enums.ModeRepartitionCloture;
import com.cotisapp.domain.enums.TypeOperation;
import com.cotisapp.dto.request.ParametrageClotureExerciceRequest;
import com.cotisapp.dto.request.PostePartageClotureRequest;
import com.cotisapp.dto.request.RetenueClotureRequest;
import com.cotisapp.dto.request.cloture.MembrePourcentageRepartitionRequest;
import com.cotisapp.dto.response.MembrePourcentageRepartitionResponse;
import com.cotisapp.dto.response.ParametrageClotureExerciceResponse;
import com.cotisapp.dto.response.PostePartageClotureResponse;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.repository.ParametrageClotureExerciceRepository;
import com.cotisapp.repository.RegleOperationRepository;
import com.cotisapp.service.cloture.MembrePourcentageRepartitionItem;
import com.cotisapp.service.cloture.PostePartageClotureItem;
import com.cotisapp.service.cloture.RetenueClotureItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ParametrageClotureExerciceService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ParametrageClotureExerciceRepository repository;
    private final RegleOperationRepository regleOperationRepository;
    private final MembreRepository membreRepository;

    @Transactional(readOnly = true)
    public ParametrageClotureExerciceResponse get(Long orgId) {
        return toResponse(assurerParametrage(orgId));
    }

    @Transactional
    public ParametrageClotureExerciceResponse enregistrer(Long orgId, ParametrageClotureExerciceRequest request) {
        validerRequest(request);
        ParametrageClotureExercice p = repository.findByOrganisationId(orgId).orElseGet(() -> ParametrageClotureExercice.builder()
                .organisationId(orgId)
                .build());
        appliquerRequest(p, request);
        return toResponse(repository.save(p));
    }

    @Transactional(readOnly = true)
    public ParametrageClotureExercice assurerParametrage(Long orgId) {
        return repository.findByOrganisationId(orgId).orElseGet(() -> creerDepuisRegles(orgId));
    }

    public List<PostePartageClotureItem> lirePostes(ParametrageClotureExercice p) {
        if (p.getPostesPartageJson() != null && !p.getPostesPartageJson().isBlank()) {
            try {
                return JSON.readValue(p.getPostesPartageJson(), new TypeReference<List<PostePartageClotureItem>>() {});
            } catch (Exception e) {
                throw new BusinessException("Configuration des postes de partage invalide");
            }
        }
        return postesDepuisFlagsLegacy(p);
    }

    public List<PostePartageClotureItem> postesParDefaut() {
        return List.of(
                PostePartageClotureItem.interetsDefaut(),
                PostePartageClotureItem.penalitesDefaut(),
                PostePartageClotureItem.amendesDefaut());
    }

    public List<MembrePourcentageRepartitionItem> lirePourcentages(ParametrageClotureExercice p) {
        if (p.getPourcentagesRepartitionJson() == null || p.getPourcentagesRepartitionJson().isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(p.getPourcentagesRepartitionJson(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new BusinessException("Configuration des pourcentages de répartition invalide");
        }
    }

    public List<RetenueClotureItem> lireRetenues(ParametrageClotureExercice p) {
        if (p.getRetenuesJson() == null || p.getRetenuesJson().isBlank()) {
            return List.of();
        }
        try {
            List<RetenueClotureItem> items = JSON.readValue(p.getRetenuesJson(), new TypeReference<>() {});
            return items.stream()
                    .sorted(Comparator.comparingInt(RetenueClotureItem::ordre))
                    .toList();
        } catch (Exception e) {
            throw new BusinessException("Configuration des retenues de clôture invalide");
        }
    }

    private ParametrageClotureExercice creerDepuisRegles(Long orgId) {
        RegleOperation cotisation = regleOperationRepository
                .findByOrganisationIdAndTypeOperationAndActifTrue(orgId, TypeOperation.COTISATION)
                .orElse(null);
        BigDecimal min = cotisation != null && cotisation.getMontantMin() != null
                ? cotisation.getMontantMin()
                : new BigDecimal("1000");
        BigDecimal max = cotisation != null && cotisation.getMontantMax() != null
                ? cotisation.getMontantMax()
                : new BigDecimal("10000");
        return ParametrageClotureExercice.builder()
                .organisationId(orgId)
                .cotisationMontantMin(min)
                .cotisationMontantMax(max)
                .partsMin(1)
                .partsMax(10)
                .build();
    }

    private void appliquerRequest(ParametrageClotureExercice p, ParametrageClotureExerciceRequest request) {
        p.setCotisationMontantMin(request.getCotisationMontantMin());
        p.setCotisationMontantMax(request.getCotisationMontantMax());
        p.setPartsMin(request.getPartsMin());
        p.setPartsMax(request.getPartsMax());
        List<PostePartageClotureItem> postes = resoudrePostesRequest(request);
        p.setPartagerInterets(postes.stream().anyMatch(x -> "INTERETS".equals(x.code()) && x.actif()));
        p.setPartagerPenalites(postes.stream().anyMatch(x -> "PENALITES".equals(x.code()) && x.actif()));
        p.setPartagerAmendes(postes.stream().anyMatch(x -> "AMENDES".equals(x.code()) && x.actif()));
        p.setModeRepartition(
                request.getModeRepartition() != null
                        ? request.getModeRepartition()
                        : ModeRepartitionCloture.PRORATA);
        p.setModeAgregationPostes(
                request.getModeAgregationPostes() != null
                        ? request.getModeAgregationPostes()
                        : ModeAgregationPostesCloture.SEPARER);
        p.setModeCalculProrata(
                request.getModeCalculProrata() != null
                        ? request.getModeCalculProrata()
                        : ModeCalculProrataCloture.PARTS);
        p.setPourcentagesRepartitionJson(serialiserPourcentages(request.getPourcentagesRepartition()));
        p.setExclureMembresPretEnCours(Boolean.TRUE.equals(request.getExclureMembresPretEnCours()));
        p.setPostesPartageJson(serialiserPostes(postes));
        p.setFraisClotureType(request.getFraisClotureType());
        p.setFraisClotureValeur(request.getFraisClotureValeur());
        p.setCompteVersementMembre(request.getCompteVersementMembre());
        p.setCompteSourceOrg(request.getCompteSourceOrg());
        p.setRetenuesJson(serialiserRetenues(request.getRetenues()));
    }

    private List<PostePartageClotureItem> resoudrePostesRequest(ParametrageClotureExerciceRequest request) {
        if (request.getPostesPartage() != null && !request.getPostesPartage().isEmpty()) {
            List<PostePartageClotureItem> items = new ArrayList<>();
            for (PostePartageClotureRequest r : request.getPostesPartage()) {
                items.add(new PostePartageClotureItem(
                        r.getCode().trim().toUpperCase(),
                        r.getLibelle().trim(),
                        r.isActif(),
                        r.isBuiltIn(),
                        r.getCompteMembre(),
                        r.getCompteSourceOrg(),
                        r.getTypeOperation(),
                        r.getGroupePartage(),
                        r.isInclureDansPoolAdditionne()));
            }
            return items;
        }
        List<PostePartageClotureItem> items = new ArrayList<>();
        for (PostePartageClotureItem item : postesParDefaut()) {
            boolean actif = switch (item.code()) {
                case "INTERETS" -> Boolean.TRUE.equals(request.getPartagerInterets());
                case "PENALITES" -> Boolean.TRUE.equals(request.getPartagerPenalites());
                case "AMENDES" -> Boolean.TRUE.equals(request.getPartagerAmendes());
                default -> item.actif();
            };
            items.add(new PostePartageClotureItem(
                    item.code(),
                    item.libelle(),
                    actif,
                    item.builtIn(),
                    item.compteMembre(),
                    item.compteSourceOrg(),
                    item.typeOperation(),
                    item.groupePartage(),
                    item.inclureDansPoolAdditionne()));
        }
        return items;
    }

    private List<PostePartageClotureItem> postesDepuisFlagsLegacy(ParametrageClotureExercice p) {
        List<PostePartageClotureItem> defaut = new ArrayList<>(postesParDefaut());
        List<PostePartageClotureItem> items = new ArrayList<>();
        for (PostePartageClotureItem item : defaut) {
            boolean actif = switch (item.code()) {
                case "INTERETS" -> Boolean.TRUE.equals(p.getPartagerInterets());
                case "PENALITES" -> Boolean.TRUE.equals(p.getPartagerPenalites());
                case "AMENDES" -> Boolean.TRUE.equals(p.getPartagerAmendes());
                default -> item.actif();
            };
            items.add(new PostePartageClotureItem(
                    item.code(),
                    item.libelle(),
                    actif,
                    item.builtIn(),
                    item.compteMembre(),
                    item.compteSourceOrg(),
                    item.typeOperation(),
                    item.groupePartage(),
                    item.inclureDansPoolAdditionne()));
        }
        return items;
    }

    private String serialiserPourcentages(List<MembrePourcentageRepartitionRequest> pourcentages) {
        if (pourcentages == null || pourcentages.isEmpty()) {
            return null;
        }
        try {
            List<MembrePourcentageRepartitionItem> items = pourcentages.stream()
                    .map(r -> new MembrePourcentageRepartitionItem(r.getMembreId(), r.getPourcentage()))
                    .toList();
            return JSON.writeValueAsString(items);
        } catch (Exception e) {
            throw new BusinessException("Impossible d'enregistrer les pourcentages de répartition");
        }
    }

    private String serialiserPostes(List<PostePartageClotureItem> postes) {
        try {
            return JSON.writeValueAsString(postes);
        } catch (Exception e) {
            throw new BusinessException("Impossible d'enregistrer les postes de partage");
        }
    }

    private String serialiserRetenues(List<RetenueClotureRequest> retenues) {
        if (retenues == null || retenues.isEmpty()) {
            return null;
        }
        try {
            List<RetenueClotureItem> items = new ArrayList<>();
            int i = 0;
            for (RetenueClotureRequest r : retenues) {
                items.add(new RetenueClotureItem(
                        r.getLibelle().trim(),
                        r.getTypeMode(),
                        r.getValeur(),
                        r.getOrdre() > 0 ? r.getOrdre() : ++i));
            }
            return JSON.writeValueAsString(items);
        } catch (Exception e) {
            throw new BusinessException("Impossible d'enregistrer les retenues");
        }
    }

    private void validerRequest(ParametrageClotureExerciceRequest request) {
        if (request.getCotisationMontantMax().compareTo(request.getCotisationMontantMin()) < 0) {
            throw new BusinessException("Le montant max. de cotisation doit être ≥ au minimum");
        }
        if (request.getPartsMax() <= request.getPartsMin()) {
            throw new BusinessException("Le nombre max. de parts doit être supérieur au minimum");
        }
        if (request.getFraisClotureValeur() != null && request.getFraisClotureValeur().signum() < 0) {
            throw new BusinessException("Les frais de clôture ne peuvent pas être négatifs");
        }
        List<PostePartageClotureItem> postes = resoudrePostesRequest(request);
        if (postes.stream().noneMatch(PostePartageClotureItem::actif)) {
            throw new BusinessException("Activez au moins un montant à partager");
        }
        for (PostePartageClotureItem poste : postes) {
            if (!poste.builtIn() && poste.typeOperation() == null) {
                throw new BusinessException("Chaque poste personnalisé doit avoir un type d'opération");
            }
        }
        ModeAgregationPostesCloture agreg = request.getModeAgregationPostes() != null
                ? request.getModeAgregationPostes()
                : ModeAgregationPostesCloture.SEPARER;
        if (agreg == ModeAgregationPostesCloture.GROUPES) {
            List<PostePartageClotureItem> actifs = postes.stream().filter(PostePartageClotureItem::actif).toList();
            for (PostePartageClotureItem poste : actifs) {
                Integer g = poste.groupePartage();
                if (g == null || (g != 1 && g != 2)) {
                    throw new BusinessException(
                            "Chaque poste actif doit être assigné au groupe 1 ou 2 en mode « deux groupes »");
                }
            }
        }
        if (agreg == ModeAgregationPostesCloture.ADDITIONNER) {
            boolean auMoinsUnDansPool = postes.stream()
                    .anyMatch(p -> p.actif() && p.inclureDansPoolAdditionne());
            if (!auMoinsUnDansPool) {
                throw new BusinessException(
                        "Cochez au moins un montant à additionner (intérêts, pénalités ou amendes)");
            }
        }
        ModeRepartitionCloture mode = request.getModeRepartition() != null
                ? request.getModeRepartition()
                : ModeRepartitionCloture.PRORATA;
        ModeCalculProrataCloture calc = request.getModeCalculProrata() != null
                ? request.getModeCalculProrata()
                : ModeCalculProrataCloture.PARTS;
        if (mode == ModeRepartitionCloture.PRORATA && calc == ModeCalculProrataCloture.POURCENTAGE) {
            validerPourcentages(request.getPourcentagesRepartition());
        }
    }

    private void validerPourcentages(List<MembrePourcentageRepartitionRequest> pourcentages) {
        if (pourcentages == null || pourcentages.isEmpty()) {
            throw new BusinessException("Définissez les pourcentages par membre (total 100 %)");
        }
        BigDecimal total = pourcentages.stream()
                .map(MembrePourcentageRepartitionRequest::getPourcentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(new BigDecimal("100")) != 0) {
            throw new BusinessException("La somme des pourcentages doit être égale à 100 % (actuellement " + total + " %)");
        }
        for (MembrePourcentageRepartitionRequest r : pourcentages) {
            if (r.getPourcentage() == null || r.getPourcentage().signum() <= 0) {
                throw new BusinessException("Chaque pourcentage membre doit être strictement positif");
            }
        }
    }

    private ParametrageClotureExerciceResponse toResponse(ParametrageClotureExercice p) {
        List<RetenueClotureItem> retenues = lireRetenues(p);
        List<PostePartageClotureItem> postes = lirePostes(p);
        return ParametrageClotureExerciceResponse.builder()
                .organisationId(p.getOrganisationId())
                .cotisationMontantMin(p.getCotisationMontantMin())
                .cotisationMontantMax(p.getCotisationMontantMax())
                .partsMin(p.getPartsMin())
                .partsMax(p.getPartsMax())
                .partagerInterets(Boolean.TRUE.equals(p.getPartagerInterets()))
                .partagerPenalites(Boolean.TRUE.equals(p.getPartagerPenalites()))
                .partagerAmendes(Boolean.TRUE.equals(p.getPartagerAmendes()))
                .modeRepartition(
                        p.getModeRepartition() != null ? p.getModeRepartition() : ModeRepartitionCloture.PRORATA)
                .modeAgregationPostes(
                        p.getModeAgregationPostes() != null
                                ? p.getModeAgregationPostes()
                                : ModeAgregationPostesCloture.SEPARER)
                .modeCalculProrata(
                        p.getModeCalculProrata() != null ? p.getModeCalculProrata() : ModeCalculProrataCloture.PARTS)
                .pourcentagesRepartition(enrichirPourcentages(p.getOrganisationId(), lirePourcentages(p)))
                .exclureMembresPretEnCours(Boolean.TRUE.equals(p.getExclureMembresPretEnCours()))
                .postesPartage(postes.stream().map(this::toPosteDto).toList())
                .fraisClotureType(p.getFraisClotureType())
                .fraisClotureValeur(p.getFraisClotureValeur())
                .retenues(retenues.stream()
                        .map(r -> ParametrageClotureExerciceResponse.RetenueClotureRequestDto.builder()
                                .libelle(r.libelle())
                                .typeMode(r.typeMode())
                                .valeur(r.valeur())
                                .ordre(r.ordre())
                                .build())
                        .toList())
                .compteVersementMembre(p.getCompteVersementMembre())
                .compteSourceOrg(p.getCompteSourceOrg())
                .build();
    }

    private List<MembrePourcentageRepartitionResponse> enrichirPourcentages(
            Long orgId, List<MembrePourcentageRepartitionItem> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        Map<Long, Membre> membres = membreRepository.findByOrganisationIdAndActifTrue(orgId).stream()
                .collect(Collectors.toMap(Membre::getId, m -> m));
        return items.stream()
                .map(i -> {
                    Membre m = membres.get(i.membreId());
                    return MembrePourcentageRepartitionResponse.builder()
                            .membreId(i.membreId())
                            .codeMembre(m != null ? m.getCodeMembre() : "")
                            .nomComplet(m != null ? m.getNomComplet() : "Membre #" + i.membreId())
                            .pourcentage(i.pourcentage())
                            .build();
                })
                .toList();
    }

    private PostePartageClotureResponse toPosteDto(PostePartageClotureItem item) {
        return PostePartageClotureResponse.builder()
                .code(item.code())
                .libelle(item.libelle())
                .actif(item.actif())
                .builtIn(item.builtIn())
                .compteMembre(item.compteMembre())
                .compteSourceOrg(item.compteSourceOrg())
                .typeOperation(item.typeOperation())
                .groupePartage(item.groupePartage())
                .inclureDansPoolAdditionne(item.inclureDansPoolAdditionne())
                .build();
    }
}
