package com.cotisapp.service;

import com.cotisapp.domain.entity.Membre;
import com.cotisapp.domain.entity.Utilisateur;
import com.cotisapp.domain.enums.PosteMembre;
import com.cotisapp.domain.enums.Role;
import com.cotisapp.dto.request.CreateUtilisateurOrgRequest;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.repository.TypeProfilRepository;
import com.cotisapp.repository.UtilisateurRepository;
import com.cotisapp.repository.UtilisateurRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UtilisateurAccesServiceMembrePasswordTest {

    @Mock
    UtilisateurRoleRepository utilisateurRoleRepository;
    @Mock
    UtilisateurRepository utilisateurRepository;
    @Mock
    MembreRepository membreRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    TypeProfilRepository typeProfilRepository;
    @Mock
    ActivationEmailService activationEmailService;

    @InjectMocks
    UtilisateurAccesService service;

    @Test
    void creerMembre_utiliseToujoursPasser123() {
        when(utilisateurRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(ActivationEmailService.MDP_MEMBRE_INITIAL)).thenReturn("hash-membre");
        when(utilisateurRepository.save(any())).thenAnswer(inv -> {
            Utilisateur u = inv.getArgument(0);
            u.setId(10L);
            return u;
        });

        Membre membre = Membre.builder()
                .id(5L)
                .organisationId(1L)
                .telephone("77 123 45 67")
                .build();
        MembreService.appliquerTelephoneNormalise(membre);

        when(membreRepository.findByIdAndOrganisationId(5L, 1L)).thenReturn(Optional.of(membre));
        when(membreRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(utilisateurRoleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(typeProfilRepository.findFirstByOrganisationIdAndRoleAndPosteMembre(1L, Role.MEMBRE, PosteMembre.SIMPLE))
                .thenReturn(Optional.empty());

        CreateUtilisateurOrgRequest req = new CreateUtilisateurOrgRequest();
        req.setPrenom("Awa");
        req.setNom("Diop");
        req.setEmail("awa@exemple.sn");
        req.setRole(Role.MEMBRE);
        req.setMembreId(5L);
        req.setCompteActif(true);
        req.setMotDePasse("Admin@2026");

        service.creer(1L, req);

        ArgumentCaptor<String> pwdCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).encode(pwdCaptor.capture());
        assertThat(pwdCaptor.getValue()).isEqualTo(ActivationEmailService.MDP_MEMBRE_INITIAL);
    }
}
