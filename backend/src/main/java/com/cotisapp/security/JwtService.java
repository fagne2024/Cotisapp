package com.cotisapp.security;



import com.cotisapp.domain.enums.Role;

import io.jsonwebtoken.Claims;

import io.jsonwebtoken.Jwts;

import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.security.core.Authentication;

import org.springframework.security.core.userdetails.UserDetails;

import org.springframework.stereotype.Service;



import javax.crypto.SecretKey;

import java.nio.charset.StandardCharsets;

import java.util.Date;

import java.util.HashMap;

import java.util.Map;



@Service

public class JwtService {



    private static final String CLAIM_TOKEN_TYPE = "tokenType";

    private static final String TOKEN_TYPE_PENDING_2FA = "PENDING_2FA";



    private final SecretKey key;

    private final long expirationMs;

    private final long pending2faExpirationMs;



    public JwtService(

            @Value("${cotisapp.jwt.secret}") String secret,

            @Value("${cotisapp.jwt.expiration-ms}") long expirationMs,

            @Value("${cotisapp.totp.pending-token-ms}") long pending2faExpirationMs) {

        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        this.expirationMs = expirationMs;

        this.pending2faExpirationMs = pending2faExpirationMs;

    }



    public String generateToken(JwtClaims claims) {

        return buildToken(claims, expirationMs, null);

    }



    public String generatePending2faToken(JwtClaims claims) {

        return buildToken(claims, pending2faExpirationMs, TOKEN_TYPE_PENDING_2FA);

    }



    public boolean isPending2faToken(String token) {

        try {

            return TOKEN_TYPE_PENDING_2FA.equals(parse(token).get(CLAIM_TOKEN_TYPE, String.class));

        } catch (Exception e) {

            return false;

        }

    }



    public JwtClaims extractClaims(Authentication auth) {

        if (auth == null || !(auth.getPrincipal() instanceof UserDetails user)) {

            return null;

        }

        return extractClaims(user.getUsername());

    }



    public JwtClaims extractClaims(String token) {

        Claims claims = parse(token);

        return new JwtClaims(

                claims.getSubject(),

                claims.get("userId", Long.class),

                Role.valueOf(claims.get("role", String.class)),

                claims.get("organisationId", Long.class),

                claims.get("membreId", Long.class)

        );

    }



    public String extractUsername(String token) {

        return parse(token).getSubject();

    }



    public boolean isTokenValid(String token, UserDetails user) {

        if (isPending2faToken(token)) {

            return false;

        }

        String username = extractUsername(token);

        return username.equals(user.getUsername()) && !isExpired(token);

    }



    private boolean isExpired(String token) {

        return parse(token).getExpiration().before(new Date());

    }



    private String buildToken(JwtClaims claims, long validityMs, String tokenType) {

        Map<String, Object> extra = new HashMap<>();

        extra.put("userId", claims.userId());

        extra.put("role", claims.role().name());

        if (claims.organisationId() != null) {

            extra.put("organisationId", claims.organisationId());

        }

        if (claims.membreId() != null) {

            extra.put("membreId", claims.membreId());

        }

        if (tokenType != null) {

            extra.put(CLAIM_TOKEN_TYPE, tokenType);

        }

        return Jwts.builder()

                .subject(claims.sub())

                .claims(extra)

                .issuedAt(new Date())

                .expiration(new Date(System.currentTimeMillis() + validityMs))

                .signWith(key)

                .compact();

    }



    private Claims parse(String token) {

        return Jwts.parser()

                .verifyWith(key)

                .build()

                .parseSignedClaims(token)

                .getPayload();

    }

}


