package com.benzaazaa.parcel.auth;

import com.benzaazaa.parcel.auth.config.JwtKeyConfig;
import com.benzaazaa.parcel.auth.token.TokenService;
import com.benzaazaa.parcel.auth.user.Role;
import com.benzaazaa.parcel.auth.user.UserAccount;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenServiceTest {

    private final JwtKeyConfig keys = new JwtKeyConfig();

    @Test
    void issuesAnRs256TokenWithRoleAndCourierClaims() throws Exception {
        RSAKey key = keys.signingKey();
        var service = new TokenService(keys.jwtEncoder(keys.jwkSet(key)), Clock.systemUTC(), "http://auth-test",
                Duration.ofMinutes(30));
        UUID courierId = UUID.randomUUID();
        var user = new UserAccount("courier.casa1", "hash", Role.COURIER, null, courierId, Instant.now());

        var response = service.issue(user);
        Jwt jwt = keys.jwtDecoder(key).decode(response.accessToken());

        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256").containsEntry("kid", key.getKeyID());
        assertThat(jwt.getSubject()).isEqualTo("courier.casa1");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("http://auth-test");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("COURIER");
        assertThat(jwt.getClaimAsString("courierId")).isEqualTo(courierId.toString());
        assertThat(jwt.getClaims()).doesNotContainKey("merchantId");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(30));
        assertThat(response.expiresIn()).isEqualTo(1800);
    }

    @Test
    void tokenSignedByAnotherKeyIsRejected() throws Exception {
        RSAKey ours = keys.signingKey();
        RSAKey attacker = keys.signingKey();
        var forger = new TokenService(keys.jwtEncoder(keys.jwkSet(attacker)), Clock.systemUTC(), "http://auth-test",
                Duration.ofMinutes(5));
        String forged = forger.issue(new UserAccount("ops", "h", Role.OPS, null, null, Instant.now())).accessToken();

        JwtDecoder decoder = keys.jwtDecoder(ours);

        assertThatThrownBy(() -> decoder.decode(forged)).hasMessageContaining("signature");
    }

    @Test
    void expiredTokensAreRejected() throws Exception {
        RSAKey key = keys.signingKey();
        Clock past = Clock.fixed(Instant.now().minus(2, ChronoUnit.HOURS), java.time.ZoneOffset.UTC);
        var service = new TokenService(keys.jwtEncoder(keys.jwkSet(key)), past, "http://auth-test",
                Duration.ofMinutes(5));
        String token = service.issue(new UserAccount("ops", "h", Role.OPS, null, null, Instant.now())).accessToken();

        NimbusJwtDecoder decoder = (NimbusJwtDecoder) keys.jwtDecoder(key);

        assertThatThrownBy(() -> decoder.decode(token)).hasMessageContaining("expired");
    }

    @Test
    void userInvariantsRequireMerchantOrCourierIds() {
        assertThatThrownBy(() -> new UserAccount("m", "h", Role.MERCHANT, null, null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UserAccount("c", "h", Role.COURIER, null, null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
