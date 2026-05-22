package com.cotisapp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = authHeader.substring(7);
        if (jwtService.isPending2faToken(token)) {
            filterChain.doFilter(request, response);
            return;
        }
        String email = jwtService.extractUsername(token);
        Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        boolean peutAuthentifier = existing == null
                || !existing.isAuthenticated()
                || existing instanceof AnonymousAuthenticationToken;
        if (email != null && peutAuthentifier) {
            UserDetails loaded = userDetailsService.loadUserByUsername(email);
            if (!jwtService.isTokenValid(token, loaded)) {
                filterChain.doFilter(request, response);
                return;
            }
            CustomUserDetails principal = buildPrincipalFromToken(token, loaded);
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        filterChain.doFilter(request, response);
    }

    /** Aligne le rôle / l'organisation sur le JWT (session de connexion), pas sur le rôle le plus élevé en BDD. */
    private CustomUserDetails buildPrincipalFromToken(String token, UserDetails loaded) {
        if (!(loaded instanceof CustomUserDetails base)) {
            return (CustomUserDetails) loaded;
        }
        try {
            JwtClaims claims = jwtService.extractClaims(token);
            if (claims.role() != null && claims.userId() != null) {
                return new CustomUserDetails(
                        claims.userId(),
                        base.getEmail(),
                        base.getPassword(),
                        claims.role(),
                        claims.organisationId(),
                        claims.membreId(),
                        base.isEnabled());
            }
        } catch (Exception ignored) {
            // Token ancien ou invalide : repli sur les données BDD
        }
        return base;
    }
}
