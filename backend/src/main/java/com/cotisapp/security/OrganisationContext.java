package com.cotisapp.security;

import com.cotisapp.domain.enums.Role;

public final class OrganisationContext {

    private static final ThreadLocal<Long> organisationId = new ThreadLocal<>();
    private static final ThreadLocal<Role> role = new ThreadLocal<>();
    private static final ThreadLocal<Long> userId = new ThreadLocal<>();
    private static final ThreadLocal<Long> membreId = new ThreadLocal<>();

    private OrganisationContext() {}

    public static void set(Long orgId, Role r, Long uid, Long mid) {
        organisationId.set(orgId);
        role.set(r);
        userId.set(uid);
        membreId.set(mid);
    }

    public static Long getOrganisationId() {
        return organisationId.get();
    }

    public static Role getRole() {
        return role.get();
    }

    public static Long getUserId() {
        return userId.get();
    }

    public static Long getMembreId() {
        return membreId.get();
    }

    public static void clear() {
        organisationId.remove();
        role.remove();
        userId.remove();
        membreId.remove();
    }
}
