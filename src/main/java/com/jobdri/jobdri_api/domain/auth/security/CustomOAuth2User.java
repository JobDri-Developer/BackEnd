package com.jobdri.jobdri_api.domain.auth.security;

import com.jobdri.jobdri_api.domain.user.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Map;

public record CustomOAuth2User(
        User user,
        Map<String, Object> attributes,
        Collection<? extends GrantedAuthority> authorities
) implements OAuth2User {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public String getName() {
        return String.valueOf(attributes.getOrDefault("sub", user.getEmail()));
    }
}
