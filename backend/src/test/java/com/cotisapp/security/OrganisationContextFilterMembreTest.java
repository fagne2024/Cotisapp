package com.cotisapp.security;

import com.cotisapp.domain.enums.Role;
import com.cotisapp.repository.UtilisateurRoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class OrganisationContextFilterMembreTest {

    @Mock
    private UtilisateurRoleRepository utilisateurRoleRepository;

    @InjectMocks
    private OrganisationContextFilter filter;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        OrganisationContext.clear();
    }

    @Test
    void membre_conserve_membre_id_du_jwt_malgre_plusieurs_roles_meme_org() throws Exception {
        CustomUserDetails principal = new CustomUserDetails(
                10L,
                "membre@exemple.sn",
                "hash",
                Role.MEMBRE,
                1L,
                100L,
                true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(OrganisationContextFilter.HEADER_ORGANISATION_ID, "1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<Long> membreId = new AtomicReference<>();
        AtomicReference<Long> orgId = new AtomicReference<>();
        AtomicReference<Role> role = new AtomicReference<>();
        filter.doFilterInternal(
                request,
                response,
                (req, res) -> {
                    membreId.set(OrganisationContext.getMembreId());
                    orgId.set(OrganisationContext.getOrganisationId());
                    role.set(OrganisationContext.getRole());
                });

        assertThat(membreId.get()).isEqualTo(100L);
        assertThat(orgId.get()).isEqualTo(1L);
        assertThat(role.get()).isEqualTo(Role.MEMBRE);
    }
}
