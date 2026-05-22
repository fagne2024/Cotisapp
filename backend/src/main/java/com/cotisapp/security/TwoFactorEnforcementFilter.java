package com.cotisapp.security;

import com.cotisapp.domain.entity.Utilisateur;
import com.cotisapp.domain.enums.Role;
import com.cotisapp.repository.UtilisateurRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TwoFactorEnforcementFilter extends OncePerRequestFilter {

    private final UtilisateurRepository utilisateurRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        Role role = OrganisationContext.getRole();
        Long userId = OrganisationContext.getUserId();
        if (role == null || userId == null || !TotpPolicy.isAdminRole(role)) {
            filterChain.doFilter(request, response);
            return;
        }

        Utilisateur u = utilisateurRepository.findById(userId).orElse(null);
        if (u == null || !TotpPolicy.mustSetupTwoFactor(role, u)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isAllowedDuringSetup(request.getRequestURI(), request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                Map.of(
                        "message",
                        "La double authentification est obligatoire pour les administrateurs. "
                                + "Configurez Google Authenticator pour continuer."));
    }

    static boolean isAllowedDuringSetup(String uri, String method) {
        if (uri == null) {
            return false;
        }
        if (uri.startsWith("/api/me/2fa")) {
            return true;
        }
        if ("/api/auth/changer-mot-de-passe-initial".equals(uri) && "POST".equalsIgnoreCase(method)) {
            return true;
        }
        if ("/api/me".equals(uri)) {
            return "GET".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method);
        }
        if ("/api/me/mot-de-passe".equals(uri) && "PATCH".equalsIgnoreCase(method)) {
            return true;
        }
        return false;
    }
}
