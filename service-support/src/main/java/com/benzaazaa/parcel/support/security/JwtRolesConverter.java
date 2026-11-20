package com.benzaazaa.parcel.support.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.List;

/** Maps the custom {@code roles} claim (e.g. ["OPS"]) to Spring authorities (ROLE_OPS). */
public class JwtRolesConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String ROLES_CLAIM = "roles";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, authorities(jwt), jwt.getSubject());
    }

    static Collection<GrantedAuthority> authorities(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(ROLES_CLAIM);
        if (roles == null) {
            return List.of();
        }
        return roles.stream().<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
    }
}
