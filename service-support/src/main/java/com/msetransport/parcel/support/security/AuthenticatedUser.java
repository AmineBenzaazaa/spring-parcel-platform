package com.benzaazaa.parcel.support.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Typed view of the JWT claims our auth-service issues. */
public record AuthenticatedUser(String username, Set<String> roles, String merchantId, UUID courierId) {

    public static AuthenticatedUser from(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(JwtRolesConverter.ROLES_CLAIM);
        String courierId = jwt.getClaimAsString("courierId");
        return new AuthenticatedUser(
                jwt.getSubject(),
                roles == null ? Set.of() : Set.copyOf(roles),
                jwt.getClaimAsString("merchantId"),
                courierId == null ? null : UUID.fromString(courierId));
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean isOps() {
        return hasRole(Roles.OPS);
    }
}
