package com.cotisapp.security;

import com.cotisapp.domain.enums.Role;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class CustomUserDetails implements UserDetails {

    private final Long userId;
    private final String email;
    private final String password;
    private final Role role;
    private final Long organisationId;
    private final Long membreId;
    private final boolean actif;

    public CustomUserDetails(
            Long userId, String email, String password, Role role,
            Long organisationId, Long membreId, boolean actif) {
        this.userId = userId;
        this.email = email;
        this.password = password;
        this.role = role;
        this.organisationId = organisationId;
        this.membreId = membreId;
        this.actif = actif;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return actif;
    }
}
