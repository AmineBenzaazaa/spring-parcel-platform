package com.msetransport.parcel.auth.token;

import com.msetransport.parcel.auth.user.UserAccount;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
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

    /** Claim that marks a token as only good for opening the live event stream. */
    public static final String TOKEN_USE_CLAIM = "token_use";
    public static final String TOKEN_USE_STREAM = "stream";

    private final JwtEncoder encoder;
    private final Clock clock;
    private final String issuer;
    private final Duration ttl;
    private final Duration streamTtl;

    public TokenService(JwtEncoder encoder, Clock clock, String issuer, Duration ttl) {
        this(encoder, clock, issuer, ttl, Duration.ofSeconds(60));
    }

    @Autowired
    public TokenService(JwtEncoder encoder, Clock clock,
                        @Value("${auth.issuer}") String issuer,
                        @Value("${auth.token-ttl:1h}") Duration ttl,
                        @Value("${auth.stream-token-ttl:60s}") Duration streamTtl) {
        this.encoder = encoder;
        this.clock = clock;
        this.issuer = issuer;
        this.ttl = ttl;
        this.streamTtl = streamTtl;
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

    /**
     * Browsers' EventSource cannot send an Authorization header, so the token has to travel in the
     * URL, where it can end up in proxy and access logs. This mints a copy of the caller's token
     * (same subject and roles) that lives for seconds, not an hour, and is marked
     * {@code token_use=stream} so the stream endpoint accepts nothing else.
     */
    public TokenResponse issueStreamToken(Jwt current) {
        Instant now = clock.instant();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(current.getSubject())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(streamTtl))
                .claim(TOKEN_USE_CLAIM, TOKEN_USE_STREAM);
        List<String> roles = current.getClaimAsStringList("roles");
        claims.claim("roles", roles == null ? List.of() : roles);
        for (String claim : List.of("merchantId", "courierId")) {
            if (current.hasClaim(claim)) {
                claims.claim(claim, current.getClaimAsString(claim));
            }
        }
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        String role = roles == null || roles.isEmpty() ? null : roles.get(0);
        return new TokenResponse(token, "Bearer", streamTtl.toSeconds(), role);
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn, String role) {
    }
}
