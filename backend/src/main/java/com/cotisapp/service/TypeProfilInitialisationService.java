package com.cotisapp.service;

import com.cotisapp.domain.entity.TypeProfil;
import com.cotisapp.domain.enums.CanalConnexion;
import com.cotisapp.domain.enums.PosteMembre;
import com.cotisapp.domain.enums.Role;
import com.cotisapp.repository.TypeProfilRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TypeProfilInitialisationService {

    private final TypeProfilRepository typeProfilRepository;
    private final TypeProfilDroitService typeProfilDroitService;

    @Transactional
    public void assurerTypesGlobaux() {
        assurerGlobal("SUPERADMIN", "Superadmin", Role.SUPERADMIN, null, CanalConnexion.EMAIL, 0);
        assurerGlobal("ADMIN_GIE", "Admin GIE", Role.ADMIN_GIE, null, CanalConnexion.EMAIL, 1);
    }

    @Transactional
    public void initialiserTypesOrganisation(Long orgId) {
        assurerOrg(orgId, "MEMBRE", "Membre", Role.MEMBRE, PosteMembre.SIMPLE, CanalConnexion.TELEPHONE, 10);
        assurerOrg(orgId, "SG", "Secrétaire général", Role.MEMBRE, PosteMembre.SECRETAIRE_GENERAL, CanalConnexion.TELEPHONE, 20);
        assurerOrg(orgId, "SGA", "Secrétaire général adjoint", Role.MEMBRE, PosteMembre.SECRETAIRE_GENERAL_ADJOINT, CanalConnexion.TELEPHONE, 21);
        assurerOrg(orgId, "PRESIDENT", "Président(e)", Role.MEMBRE, PosteMembre.PRESIDENT, CanalConnexion.TELEPHONE, 22);
        assurerOrg(orgId, "TRESORIER", "Trésorier(ère)", Role.MEMBRE, PosteMembre.TRESORIER, CanalConnexion.TELEPHONE, 23);
        assurerOrg(orgId, "SUPERVISEUR", "Superviseur", Role.MEMBRE, PosteMembre.SUPERVISEUR, CanalConnexion.LES_DEUX, 24);
    }

    private void assurerGlobal(
            String code, String libelle, Role role, PosteMembre poste, CanalConnexion canal, int ordre) {
        if (typeProfilRepository.findFirstByOrganisationIdIsNullAndCodeOrderByIdAsc(code).isPresent()) {
            return;
        }
        typeProfilRepository.save(TypeProfil.builder()
                .code(code)
                .libelle(libelle)
                .role(role)
                .posteMembre(poste)
                .canalConnexion(canal)
                .ordre(ordre)
                .build());
    }

    private void assurerOrg(
            Long orgId,
            String code,
            String libelle,
            Role role,
            PosteMembre poste,
            CanalConnexion canal,
            int ordre) {
        if (typeProfilRepository.findFirstByOrganisationIdAndCodeOrderByIdAsc(orgId, code).isPresent()) {
            return;
        }
        TypeProfil saved = typeProfilRepository.save(TypeProfil.builder()
                .organisationId(orgId)
                .code(code)
                .libelle(libelle)
                .role(role)
                .posteMembre(poste)
                .canalConnexion(canal)
                .ordre(ordre)
                .build());
        typeProfilDroitService.appliquerDroitsParDefaut(saved);
    }
}
