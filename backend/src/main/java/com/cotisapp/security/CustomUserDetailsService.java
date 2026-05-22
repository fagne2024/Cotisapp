package com.cotisapp.security;

import com.cotisapp.domain.entity.Utilisateur;
import com.cotisapp.domain.entity.UtilisateurRole;
import com.cotisapp.domain.enums.Role;
import com.cotisapp.repository.UtilisateurRepository;
import com.cotisapp.repository.UtilisateurRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UtilisateurRepository utilisateurRepository;
    private final UtilisateurRoleRepository utilisateurRoleRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Utilisateur user = utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur introuvable: " + email));
        List<UtilisateurRole> roles = utilisateurRoleRepository.findByUtilisateurId(user.getId());
        UtilisateurRole ur = roles.stream()
                .min(Comparator.comparingInt(r -> prioriteRole(r.getRole())))
                .orElseThrow(() -> new UsernameNotFoundException("Rôle introuvable pour: " + email));
        return new CustomUserDetails(
                user.getId(),
                user.getEmail(),
                user.getMotDePasse(),
                ur.getRole(),
                ur.getOrganisationId(),
                ur.getMembreId(),
                Boolean.TRUE.equals(user.getActif())
        );
    }

    /** Rôle le plus privilégié si plusieurs enregistrements (ex. SUPERADMIN + ADMIN_GIE). */
    private static int prioriteRole(Role role) {
        return switch (role) {
            case SUPERADMIN -> 0;
            case ADMIN_GIE -> 1;
            case MEMBRE -> 2;
        };
    }
}
