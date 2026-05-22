package com.cotisapp.security;

import com.cotisapp.domain.entity.UtilisateurRole;
import com.cotisapp.domain.enums.Role;
import com.cotisapp.repository.UtilisateurRoleRepository;
import com.cotisapp.service.PresenceService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OrganisationContextFilter extends OncePerRequestFilter {

    public static final String HEADER_ORGANISATION_ID = "X-Organisation-Id";

    private final UtilisateurRoleRepository utilisateurRoleRepository;
    private final PresenceService presenceService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof CustomUserDetails user) {
                Long headerOrgId = parseOrgHeader(request.getHeader(HEADER_ORGANISATION_ID));
                ContexteOrg ctx = resoudreContexte(user, headerOrgId);
                OrganisationContext.set(ctx.orgId(), ctx.role(), ctx.userId(), ctx.membreId());
                if (ctx.orgId() != null) {
                    presenceService.touch(ctx.userId(), ctx.orgId());
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            OrganisationContext.clear();
        }
    }

    private ContexteOrg resoudreContexte(CustomUserDetails user, Long headerOrgId) {
        // Connexion membre : fiche choisie au login (JWT) — ne jamais substituer un autre membre_id.
        if (user.getRole() == Role.MEMBRE) {
            return new ContexteOrg(
                    user.getOrganisationId(),
                    Role.MEMBRE,
                    user.getUserId(),
                    user.getMembreId());
        }

        List<UtilisateurRole> roles = utilisateurRoleRepository.findByUtilisateurId(user.getUserId());

        if (user.getRole() == Role.SUPERADMIN) {
            return new ContexteOrg(
                    headerOrgId != null ? headerOrgId : null,
                    Role.SUPERADMIN,
                    user.getUserId(),
                    null);
        }

        if (headerOrgId != null) {
            Optional<UtilisateurRole> pourOrg = roles.stream()
                    .filter(r -> headerOrgId.equals(r.getOrganisationId()))
                    .min(Comparator.comparingInt(r -> prioriteRole(r.getRole())));
            if (pourOrg.isPresent()) {
                UtilisateurRole ur = pourOrg.get();
                return new ContexteOrg(ur.getOrganisationId(), ur.getRole(), user.getUserId(), ur.getMembreId());
            }
            // En-tête X-Organisation-Id sans rôle sur cette org : garder le contexte JWT
            return new ContexteOrg(
                    user.getOrganisationId(), user.getRole(), user.getUserId(), user.getMembreId());
        }

        UtilisateurRole defaut = roles.stream()
                .min(Comparator.comparingInt(r -> prioriteRole(r.getRole())))
                .orElse(null);
        if (defaut != null) {
            return new ContexteOrg(defaut.getOrganisationId(), defaut.getRole(), user.getUserId(), defaut.getMembreId());
        }

        return new ContexteOrg(user.getOrganisationId(), user.getRole(), user.getUserId(), user.getMembreId());
    }

    private static int prioriteRole(Role role) {
        return switch (role) {
            case SUPERADMIN -> 0;
            case ADMIN_GIE -> 1;
            case MEMBRE -> 2;
        };
    }

    private Long parseOrgHeader(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record ContexteOrg(Long orgId, Role role, Long userId, Long membreId) {}
}
