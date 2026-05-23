package com.cotisapp.service;

import com.cotisapp.domain.catalogue.ProfilDroitDefaults;
import com.cotisapp.domain.entity.ActionDroit;
import com.cotisapp.domain.entity.TypeProfil;
import com.cotisapp.domain.entity.TypeProfilDroit;
import com.cotisapp.domain.enums.NiveauDroit;
import com.cotisapp.dto.request.TypeProfilDroitItemRequest;
import com.cotisapp.dto.response.ActionDroitResponse;
import com.cotisapp.dto.response.TypeProfilDroitResponse;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.ActionDroitRepository;
import com.cotisapp.repository.TypeProfilDroitRepository;
import com.cotisapp.repository.TypeProfilRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TypeProfilDroitService {

    private final ActionDroitRepository actionDroitRepository;
    private final TypeProfilDroitRepository typeProfilDroitRepository;
    private final TypeProfilRepository typeProfilRepository;
    private final ActionDroitInitialisationService actionDroitInitialisationService;
    private final JournalService journalService;

    @Transactional(readOnly = true)
    public List<ActionDroitResponse> listerCatalogue() {
        actionDroitInitialisationService.assurerCatalogueActions();
        return actionDroitRepository.findAllByOrderByOrdreAscLibelleAsc().stream()
                .map(this::toActionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TypeProfilDroitResponse> listerDroitsTypeProfil(Long orgId, Long typeProfilId) {
        TypeProfil tp = chargerTypeProfilOrg(orgId, typeProfilId);
        actionDroitInitialisationService.assurerCatalogueActions();
        Map<String, NiveauDroit> niveaux = typeProfilDroitRepository.findByTypeProfilIdOrderByActionCodeAsc(tp.getId()).stream()
                .collect(Collectors.toMap(TypeProfilDroit::getActionCode, TypeProfilDroit::getNiveau));
        return actionDroitRepository.findAllByOrderByOrdreAscLibelleAsc().stream()
                .map(a -> TypeProfilDroitResponse.builder()
                        .actionCode(a.getCode())
                        .section(a.getSection())
                        .libelle(a.getLibelle())
                        .niveau(niveaux.getOrDefault(a.getCode(), NiveauDroit.NO))
                        .build())
                .toList();
    }

    @Transactional
    public List<TypeProfilDroitResponse> sauvegarderDroits(
            Long orgId, Long typeProfilId, List<TypeProfilDroitItemRequest> items) {
        TypeProfil tp = chargerTypeProfilOrg(orgId, typeProfilId);
        actionDroitInitialisationService.assurerCatalogueActions();
        Map<String, NiveauDroit> niveauxAvant = typeProfilDroitRepository.findByTypeProfilIdOrderByActionCodeAsc(tp.getId())
                .stream()
                .collect(Collectors.toMap(TypeProfilDroit::getActionCode, TypeProfilDroit::getNiveau));
        Map<String, String> libellesActions = actionDroitRepository.findAll().stream()
                .collect(Collectors.toMap(ActionDroit::getCode, ActionDroit::getLibelle));
        Set<String> codesCatalogue = actionDroitRepository.findAll().stream()
                .map(ActionDroit::getCode)
                .collect(Collectors.toSet());
        if (items == null || items.isEmpty()) {
            throw new BusinessException(
                    "Aucun droit à enregistrer. Attendez le chargement complet du catalogue d'actions.");
        }
        if (items.size() != codesCatalogue.size()) {
            throw new BusinessException(
                    "Toutes les actions du catalogue doivent être envoyées ("
                            + items.size()
                            + " reçues, "
                            + codesCatalogue.size()
                            + " attendues). Rechargez la page puis réessayez.");
        }
        typeProfilDroitRepository.deleteByTypeProfilId(tp.getId());
        for (TypeProfilDroitItemRequest item : items) {
            if (item.getActionCode() == null || item.getNiveau() == null) {
                throw new BusinessException("Chaque action doit avoir un code et un niveau d'accès.");
            }
            if (!codesCatalogue.contains(item.getActionCode())) {
                throw new BusinessException("Action inconnue : " + item.getActionCode());
            }
            typeProfilDroitRepository.save(TypeProfilDroit.builder()
                    .typeProfilId(tp.getId())
                    .actionCode(item.getActionCode())
                    .niveau(item.getNiveau())
                    .build());
        }
        List<String> changements = new ArrayList<>();
        for (TypeProfilDroitItemRequest item : items) {
            NiveauDroit avant = niveauxAvant.get(item.getActionCode());
            if (avant == item.getNiveau()) {
                continue;
            }
            String libelle = libellesActions.getOrDefault(item.getActionCode(), item.getActionCode());
            changements.add(
                    libelle
                            + " : "
                            + JournalModificationFormatter.libelleNiveauDroit(avant)
                            + " → "
                            + JournalModificationFormatter.libelleNiveauDroit(item.getNiveau()));
        }
        int total = changements.size();
        int maxAffiche = 12;
        List<String> affiches = changements.size() <= maxAffiche
                ? changements
                : changements.subList(0, maxAffiche);
        journalService.enregistrer(
                orgId,
                "DROITS_PROFIL_MAJ",
                JournalModificationFormatter.resumeDroitsModifies(tp.getLibelle(), affiches, total));
        return listerDroitsTypeProfil(orgId, typeProfilId);
    }

    /**
     * Ajoute les nouvelles actions du catalogue (niveau NO) sans écraser les droits déjà configurés.
     */
    @Transactional
    public void synchroniserActionsManquantes(Long typeProfilId) {
        actionDroitInitialisationService.assurerCatalogueActions();
        Set<String> existants = typeProfilDroitRepository.findByTypeProfilIdOrderByActionCodeAsc(typeProfilId).stream()
                .map(TypeProfilDroit::getActionCode)
                .collect(Collectors.toSet());
        for (com.cotisapp.domain.catalogue.ActionDroitCatalogue.ActionDef def :
                com.cotisapp.domain.catalogue.ActionDroitCatalogue.toutes()) {
            if (existants.contains(def.code())) {
                continue;
            }
            typeProfilDroitRepository.save(TypeProfilDroit.builder()
                    .typeProfilId(typeProfilId)
                    .actionCode(def.code())
                    .niveau(NiveauDroit.NO)
                    .build());
        }
    }

    @Transactional
    public void assurerDroitsProfil(TypeProfil tp) {
        if (typeProfilDroitRepository.countByTypeProfilId(tp.getId()) == 0) {
            appliquerDroitsParDefaut(tp);
        } else {
            synchroniserActionsManquantes(tp.getId());
        }
    }

    /**
     * Réapplique les droits par défaut (SG, SGA, Trésorier, etc.) pour les profils système de l'organisation.
     * Utile après mise à jour des matrices métier.
     */
    @Transactional
    public void reinitialiserDroitsProfilsSysteme(Long orgId) {
        for (String code : TypeProfilService.CODES_SYSTEME) {
            typeProfilRepository
                    .findFirstByOrganisationIdAndCodeOrderByIdAsc(orgId, code)
                    .ifPresent(tp -> {
                        typeProfilDroitRepository.deleteByTypeProfilId(tp.getId());
                        appliquerDroitsParDefaut(tp);
                    });
        }
    }

    @Transactional
    public void appliquerDroitsParDefaut(TypeProfil tp) {
        if (typeProfilDroitRepository.countByTypeProfilId(tp.getId()) > 0) {
            return;
        }
        Map<String, NiveauDroit> defaults = ProfilDroitDefaults.pourProfil(tp.getRole(), tp.getPosteMembre());
        for (Map.Entry<String, NiveauDroit> e : defaults.entrySet()) {
            typeProfilDroitRepository.save(TypeProfilDroit.builder()
                    .typeProfilId(tp.getId())
                    .actionCode(e.getKey())
                    .niveau(e.getValue())
                    .build());
        }
    }

    private TypeProfil chargerTypeProfilOrg(Long orgId, Long typeProfilId) {
        return typeProfilRepository
                .findByIdAndOrganisationId(typeProfilId, orgId)
                .orElseThrow(() -> new BusinessException("Type de profil introuvable"));
    }

    private ActionDroitResponse toActionResponse(ActionDroit a) {
        return ActionDroitResponse.builder()
                .code(a.getCode())
                .section(a.getSection())
                .libelle(a.getLibelle())
                .ordre(a.getOrdre())
                .build();
    }

    /** Codes d'actions (évite dépendance circulaire sur le catalogue). */
    static final class ActionDroitCatalogueCodes {
        private ActionDroitCatalogueCodes() {}

        static List<String> tous() {
            return com.cotisapp.domain.catalogue.ActionDroitCatalogue.toutes().stream()
                    .map(com.cotisapp.domain.catalogue.ActionDroitCatalogue.ActionDef::code)
                    .toList();
        }
    }
}
