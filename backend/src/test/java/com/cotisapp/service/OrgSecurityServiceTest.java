package com.cotisapp.service;

import com.cotisapp.domain.entity.TypeProfilDroit;
import com.cotisapp.domain.entity.UtilisateurRole;
import com.cotisapp.domain.enums.NiveauDroit;
import com.cotisapp.domain.enums.Role;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.repository.TypeProfilDroitRepository;
import com.cotisapp.repository.UtilisateurRoleRepository;
import com.cotisapp.security.OrgSecurityService;
import com.cotisapp.security.OrganisationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrgSecurityServiceTest {

    @Mock
    private MembreRepository membreRepository;

    @Mock
    private UtilisateurRoleRepository utilisateurRoleRepository;

    @Mock
    private TypeProfilDroitRepository typeProfilDroitRepository;

    @InjectMocks
    private OrgSecurityService orgSecurityService;

    @AfterEach
    void tearDown() {
        OrganisationContext.clear();
    }

    @Test
    void belongsTo_adminMemeOrg_retourneTrue() {
        OrganisationContext.set(1L, Role.ADMIN_GIE, 10L, null);
        assertThat(orgSecurityService.belongsTo(1L)).isTrue();
    }

    @Test
    void belongsTo_adminAutreOrg_retourneFalse() {
        OrganisationContext.set(1L, Role.ADMIN_GIE, 10L, null);
        assertThat(orgSecurityService.belongsTo(2L)).isFalse();
    }

    @Test
    void belongsTo_superadmin_retourneTrue() {
        OrganisationContext.set(null, Role.SUPERADMIN, 1L, null);
        assertThat(orgSecurityService.belongsTo(99L)).isTrue();
    }

    @Test
    void isMemberOf_membreExistant_retourneTrue() {
        OrganisationContext.set(1L, Role.MEMBRE, 5L, 3L);
        when(membreRepository.existsByUtilisateurIdAndOrganisationId(5L, 1L)).thenReturn(true);
        assertThat(orgSecurityService.isMemberOf(1L)).isTrue();
    }

    @Test
    void peutGestionOrg_adminGie_retourneTrue() {
        OrganisationContext.set(1L, Role.ADMIN_GIE, 10L, null);
        assertThat(orgSecurityService.peutGestionOrg(1L)).isTrue();
    }

    @Test
    void peutGestionOrg_membreAvecRapportComplet_retourneTrue() {
        OrganisationContext.set(1L, Role.MEMBRE, 5L, 3L);
        UtilisateurRole ur = UtilisateurRole.builder().typeProfilId(99L).build();
        when(utilisateurRoleRepository.findFirstByUtilisateurIdAndRoleAndOrganisationIdOrderByIdAsc(
                        5L, Role.MEMBRE, 1L))
                .thenReturn(Optional.of(ur));
        when(typeProfilDroitRepository.findByTypeProfilIdOrderByActionCodeAsc(99L))
                .thenReturn(List.of(TypeProfilDroit.builder()
                        .typeProfilId(99L)
                        .actionCode("RAPPORT_COMPLET")
                        .niveau(NiveauDroit.LIM)
                        .build()));
        assertThat(orgSecurityService.peutGestionOrg(1L)).isTrue();
    }

    @Test
    void peutActionOrg_membreSansDroit_retourneFalse() {
        OrganisationContext.set(1L, Role.MEMBRE, 5L, 3L);
        UtilisateurRole ur = UtilisateurRole.builder().typeProfilId(99L).build();
        when(utilisateurRoleRepository.findFirstByUtilisateurIdAndRoleAndOrganisationIdOrderByIdAsc(
                        5L, Role.MEMBRE, 1L))
                .thenReturn(Optional.of(ur));
        when(typeProfilDroitRepository.findByTypeProfilIdOrderByActionCodeAsc(99L))
                .thenReturn(List.of());
        assertThat(orgSecurityService.peutActionOrg(1L, "OP_COTISATION")).isFalse();
    }
}
