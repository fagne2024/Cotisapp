package com.cotisapp.security;

import com.cotisapp.domain.enums.Role;

public record JwtClaims(
        String sub,
        Long userId,
        Role role,
        Long organisationId,
        Long membreId
) {}
