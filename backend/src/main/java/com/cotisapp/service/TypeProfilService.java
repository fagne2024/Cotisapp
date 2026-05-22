package com.cotisapp.service;

import com.cotisapp.domain.entity.TypeProfil;
import com.cotisapp.domain.enums.CanalConnexion;
import com.cotisapp.domain.enums.PosteMembre;
import com.cotisapp.domain.enums.Role;
import com.cotisapp.dto.request.CreateTypeProfilRequest;
import com.cotisapp.dto.request.UpdateTypeProfilRequest;
import com.cotisapp.dto.response.TypeProfilResponse;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.TypeProfilRepository;
import com.cotisapp.repository.UtilisateurRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TypeProfilService {

    static final Set<String> CODES_SYSTEME =
            Set.of("MEMBRE", "SG", "SGA", "PRESIDENT", "TRESORIER", "SUPERVISEUR");

    private final TypeProfilRepository typeProfilRepository;
    private final TypeProfilInitialisationService typeProfilInitialisationService;
    private final TypeProfilDroitService typeProfilDroitService;
    private final UtilisateurRoleRepository utilisateurRoleRepository;
    private final ActionDroitInitialisationService actionDroitInitialisationService;

    @Transactional
    public List<TypeProfilResponse> listerPourOrganisation(Long orgId) {
        actionDroitInitialisationService.assurerCatalogueActions();
        typeProfilInitialisationService.assurerTypesGlobaux();
        typeProfilInitialisationService.initialiserTypesOrganisation(orgId);
        List<TypeProfil> types = typeProfilRepository.findDisponiblesPourOrganisation(orgId);
        for (TypeProfil tp : types) {
            if (tp.getOrganisationId() != null) {
                typeProfilDroitService.assurerDroitsProfil(tp);
            }
        }
        return types.stream()
                .filter(t -> Boolean.TRUE.equals(t.getActif()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<TypeProfilResponse> listerGestionOrganisation(Long orgId) {
        actionDroitInitialisationService.assurerCatalogueActions();
        typeProfilInitialisationService.initialiserTypesOrganisation(orgId);
        List<TypeProfil> types =
                typeProfilRepository.findByOrganisationIdOrderByOrdreAscLibelleAsc(orgId);
        for (TypeProfil tp : types) {
            typeProfilDroitService.assurerDroitsProfil(tp);
        }
        return types.stream().map(this::toResponse).toList();
    }

    @Transactional
    public TypeProfilResponse creer(Long orgId, CreateTypeProfilRequest request) {
        String code = request.getCode().trim().toUpperCase().replaceAll("\\s+", "_");
        if (typeProfilRepository.findFirstByOrganisationIdAndCodeOrderByIdAsc(orgId, code).isPresent()) {
            throw new BusinessException("Un type de profil avec ce code existe déjà");
        }
        if (request.getRole() == Role.SUPERADMIN) {
            throw new BusinessException("Le rôle superadmin ne peut pas être créé au niveau organisation");
        }
        CanalConnexion canal = canalPourRole(request.getRole(), request.getCanalConnexion());
        PosteMembre poste = request.getRole() == Role.MEMBRE ? request.getPosteMembre() : null;
        if (request.getRole() == Role.MEMBRE && poste == null) {
            poste = PosteMembre.SIMPLE;
        }
        TypeProfil saved = typeProfilRepository.save(TypeProfil.builder()
                .organisationId(orgId)
                .code(code)
                .libelle(request.getLibelle().trim())
                .role(request.getRole())
                .posteMembre(poste)
                .canalConnexion(canal)
                .actif(request.getActif() == null || request.getActif())
                .ordre(request.getOrdre() != null ? request.getOrdre() : 100)
                .build());
        typeProfilDroitService.appliquerDroitsParDefaut(saved);
        return toResponse(saved);
    }

    @Transactional
    public TypeProfilResponse mettreAJour(Long orgId, Long id, UpdateTypeProfilRequest request) {
        TypeProfil tp = typeProfilRepository
                .findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new BusinessException("Type de profil introuvable"));
        if (request.getLibelle() != null) {
            tp.setLibelle(request.getLibelle().trim());
        }
        if (request.getPosteMembre() != null && tp.getRole() == Role.MEMBRE) {
            tp.setPosteMembre(request.getPosteMembre());
        }
        if (request.getCanalConnexion() != null) {
            tp.setCanalConnexion(canalPourRole(tp.getRole(), request.getCanalConnexion()));
        }
        if (request.getActif() != null) {
            tp.setActif(request.getActif());
        }
        if (request.getOrdre() != null) {
            tp.setOrdre(request.getOrdre());
        }
        return toResponse(typeProfilRepository.save(tp));
    }

    @Transactional
    public void supprimer(Long orgId, Long id) {
        TypeProfil tp = typeProfilRepository
                .findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new BusinessException("Type de profil introuvable"));
        if (estCodeSysteme(tp.getCode())) {
            throw new BusinessException("Les types de profil par défaut ne peuvent pas être supprimés");
        }
        long usages = utilisateurRoleRepository.countByTypeProfilId(tp.getId());
        if (usages > 0) {
            throw new BusinessException(
                    "Ce type est assigné à " + usages + " utilisateur(s). Réassignez-les avant suppression.");
        }
        typeProfilRepository.delete(tp);
    }

    static boolean estCodeSysteme(String code) {
        return code != null && CODES_SYSTEME.contains(code.toUpperCase());
    }

    private static CanalConnexion canalPourRole(Role role, CanalConnexion demande) {
        if (role == Role.ADMIN_GIE || role == Role.SUPERADMIN) {
            return CanalConnexion.EMAIL;
        }
        if (demande != null) {
            return demande;
        }
        return CanalConnexion.TELEPHONE;
    }

    private TypeProfilResponse toResponse(TypeProfil t) {
        return TypeProfilResponse.builder()
                .id(t.getId())
                .organisationId(t.getOrganisationId())
                .code(t.getCode())
                .libelle(t.getLibelle())
                .role(t.getRole())
                .posteMembre(t.getPosteMembre())
                .canalConnexion(t.getCanalConnexion())
                .actif(Boolean.TRUE.equals(t.getActif()))
                .ordre(t.getOrdre() != null ? t.getOrdre() : 0)
                .systeme(estCodeSysteme(t.getCode()))
                .build();
    }
}
