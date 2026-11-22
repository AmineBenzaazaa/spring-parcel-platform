package com.msetransport.parcel.support.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtRolesConverterTest {

    private static Jwt.Builder jwt() {
        return Jwt.withTokenValue("t").header("alg", "RS256").subject("courier.casa1")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
    }

    @Test
    void mapsRolesClaimToSpringAuthorities() {
        var auth = new JwtRolesConverter().convert(jwt().claim("roles", List.of("COURIER")).build());

        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_COURIER");
        assertThat(auth.getName()).isEqualTo("courier.casa1");
    }

    @Test
    void noRolesClaimMeansNoAuthorities() {
        assertThat(new JwtRolesConverter().convert(jwt().build()).getAuthorities()).isEmpty();
    }

    @Test
    void authenticatedUserExposesTypedClaims() {
        UUID courierId = UUID.randomUUID();
        var user = AuthenticatedUser.from(jwt().claim("roles", List.of("COURIER"))
                .claim("courierId", courierId.toString()).build());

        assertThat(user.courierId()).isEqualTo(courierId);
        assertThat(user.hasRole(Roles.COURIER)).isTrue();
        assertThat(user.isOps()).isFalse();
        assertThat(user.merchantId()).isNull();
    }
}
