package com.cotisapp.service;

import com.cotisapp.domain.entity.TypeProfil;
import com.cotisapp.domain.entity.Utilisateur;
import com.cotisapp.domain.entity.UtilisateurRole;
import com.cotisapp.domain.enums.PosteMembre;
import com.cotisapp.domain.enums.Role;
import com.cotisapp.dto.request.CreateUtilisateurOrgRequest;
import com.cotisapp.dto.response.UtilisateurOrgResponse;
import com.cotisapp.repository.JournalAuditRepository;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.repository.TypeProfilRepository;
import com.cotisapp.repository.UtilisateurRepository;
import com.cotisapp.repository.UtilisateurRoleRepository;
import com.cotisapp.service.MembreCompteAccesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class UtilisateurAccesServiceBureauTest {

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
    @Mock
    MembreCompteAccesService membreCompteAccesService;
    @Mock
    PresenceService presenceService;
    @Mock
    JournalAuditRepository journalAuditRepository;

    @InjectMocks
    UtilisateurAccesService service;

    @Test
    void creer_bureau_sans_membre() {
        when(utilisateurRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        Utilisateur savedUser = Utilisateur.builder()
                .id(42L)
                .email("sg@gie.sn")
                .prenom("Fatou")
                .nom("Bâ")
                .actif(true)
                .build();
        when(utilisateurRepository.save(any())).thenReturn(savedUser);
        when(utilisateurRepository.findById(42L)).thenReturn(Optional.of(savedUser));
        TypeProfil sg = TypeProfil.builder()
                .id(7L)
                .organisationId(1L)
                .code("SG")
                .libelle("Secrétaire général")
                .role(Role.MEMBRE)
                .posteMembre(PosteMembre.SECRETAIRE_GENERAL)
                .build();
        when(typeProfilRepository.findById(7L)).thenReturn(Optional.of(sg));
        when(membreRepository.findFirstByUtilisateurIdAndOrganisationIdOrderByIdAsc(42L, 1L))
                .thenReturn(Optional.empty());
        when(utilisateurRoleRepository.save(any())).thenAnswer(inv -> {
            UtilisateurRole ur = inv.getArgument(0);
            ur.setId(99L);
            return ur;
        });
        when(journalAuditRepository.findDerniereConnexionParUtilisateur(1L)).thenReturn(java.util.List.of());
        when(journalAuditRepository.countConnexions30jParUtilisateur(any(), any())).thenReturn(java.util.List.of());

        CreateUtilisateurOrgRequest req = new CreateUtilisateurOrgRequest();
        req.setPrenom("Fatou");
        req.setNom("Bâ");
        req.setEmail("sg@gie.sn");
        req.setRole(Role.MEMBRE);
        req.setPoste(PosteMembre.SECRETAIRE_GENERAL);
        req.setTypeProfilId(7L);
        req.setCompteActif(true);
        req.setEnvoyerEmailActivation(false);

        UtilisateurOrgResponse resp = service.creer(1L, req);

        assertThat(resp.getMembreId()).isNull();
        assertThat(resp.getPoste()).isEqualTo(PosteMembre.SECRETAIRE_GENERAL);
        assertThat(resp.getTypeProfilCode()).isEqualTo("SG");
        verify(membreRepository, org.mockito.Mockito.never()).findByIdAndOrganisationId(any(), any());
    }
}
