package com.benzaazaa.parcel.auth.token;

import com.benzaazaa.parcel.auth.user.UserAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final Clock clock;
    private final String issuer;
    private final Duration ttl;

    public TokenService(JwtEncoder encoder, Clock clock,
                        @Value("${auth.issuer}") String issuer,
                        @Value("${auth.token-ttl:1h}") Duration ttl) {
        this.encoder = encoder;
        this.clock = clock;
        this.issuer = issuer;
        this.ttl = ttl;
    }

    public TokenResponse issue(UserAccount user) {
        Instant now = clock.instant();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.getUsername())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("roles", List.of(user.getRole().name()));
        if (user.getMerchantId() != null) {
            claims.claim("merchantId", user.getMerchantId());
        }
        if (user.getCourierId() != null) {
            claims.claim("courierId", user.getCourierId().toString());
        }
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new TokenResponse(token, "Bearer", ttl.toSeconds(), user.getRole().name());
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn, String role) {
    }
}
