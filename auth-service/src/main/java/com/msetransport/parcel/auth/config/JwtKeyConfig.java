package com.msetransport.parcel.auth.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.util.UUID;

/**
 * RS256 signing key. Private key signs (only auth-service has it); the public key is published as a
 * JWKS so every other service verifies tokens locally, without calling auth-service per request.
 *
 * <p>Demo trade-off: the key pair is generated at startup, so a restart invalidates issued tokens.
 * In production it would come from a KMS/Vault, and rotation would publish old + new keys (kid).
 */
@Configuration
public class JwtKeyConfig {

    @Bean
    public RSAKey signingKey() throws JOSEException {
        return new RSAKeyGenerator(2048)
                .keyUse(KeyUse.SIGNATURE)
                .keyID(UUID.randomUUID().toString())
                .generate();
    }

    @Bean
    public JWKSet jwkSet(RSAKey signingKey) {
        return new JWKSet(signingKey);
    }

    @Bean
    public JwtEncoder jwtEncoder(JWKSet jwkSet) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(jwkSet));
    }

    /** auth-service verifies its own tokens (for /api/auth/me) with the public half. */
    @Bean
    public JwtDecoder jwtDecoder(RSAKey signingKey) throws JOSEException {
        return NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey()).build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
