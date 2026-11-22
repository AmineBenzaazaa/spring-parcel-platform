package com.msetransport.parcel.gateway;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real gateway + real Redis (Testcontainers). A JDK HttpServer plays both the auth-service JWKS
 * endpoint and a downstream service, so routing, JWT validation and rate limiting are tested end to end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "RATE_LIMIT_REPLENISH=1", "RATE_LIMIT_BURST=3"})
class GatewayIntegrationTest {

    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    static final RSAKey KEY;
    static final HttpServer BACKEND;
    static final AtomicReference<String> LAST_AUTH = new AtomicReference<>();
    static final AtomicReference<String> LAST_CORRELATION = new AtomicReference<>();

    static {
        REDIS.start();
        try {
            KEY = new RSAKeyGenerator(2048).keyID("test-kid").generate();
            BACKEND = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        BACKEND.createContext("/.well-known/jwks.json", ex -> respond(ex, new JWKSet(KEY).toPublicJWKSet().toString()));
        BACKEND.createContext("/api/", ex -> {
            LAST_AUTH.set(ex.getRequestHeaders().getFirst("Authorization"));
            LAST_CORRELATION.set(ex.getRequestHeaders().getFirst("X-Correlation-Id"));
            respond(ex, "{\"path\":\"" + ex.getRequestURI().getPath() + "\"}");
        });
        BACKEND.createContext("/actuator/", ex -> respond(ex, "{\"status\":\"UP\"}"));
        BACKEND.createContext("/fake-sms/", ex -> respond(ex, "{\"path\":\"" + ex.getRequestURI().getPath() + "\"}"));
        BACKEND.start();
    }

    private static void respond(com.sun.net.httpserver.HttpExchange ex, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        String backend = "http://localhost:" + BACKEND.getAddress().getPort();
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> backend + "/.well-known/jwks.json");
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> "http://auth-test");
        registry.add("AUTH_URI", () -> backend);
        registry.add("ORDER_URI", () -> backend);
        registry.add("DISPATCH_URI", () -> backend);
        registry.add("NOTIFICATION_URI", () -> backend);
    }

    @Autowired
    WebTestClient web;

    private static String token(String subject, String issuer, RSAKey key) {
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        var claims = JwtClaimsSet.builder().issuer(issuer).subject(subject).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300)).claim("roles", List.of("MERCHANT")).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                .getTokenValue();
    }

    @Test
    void protectedRouteWithoutTokenIs401AndNeverReachesTheService() {
        LAST_AUTH.set("untouched");

        web.get().uri("/api/orders").exchange().expectStatus().isUnauthorized();

        assertThat(LAST_AUTH.get()).isEqualTo("untouched");
    }

    @Test
    void validTokenIsRoutedWithAuthorizationAndCorrelationHeaders() {
        String jwt = token("merchant.routed", "http://auth-test", KEY);

        web.get().uri("/api/orders/123").header("Authorization", "Bearer " + jwt)
                .header("X-Correlation-Id", "gw-corr-1")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Correlation-Id", "gw-corr-1")
                .expectBody().jsonPath("$.path").isEqualTo("/api/orders/123");

        assertThat(LAST_AUTH.get()).isEqualTo("Bearer " + jwt);
        assertThat(LAST_CORRELATION.get()).isEqualTo("gw-corr-1");
    }

    @Test
    void tokenFromWrongIssuerOrForeignKeyIsRejected() throws Exception {
        RSAKey attacker = new RSAKeyGenerator(2048).keyID("test-kid").generate();

        web.get().uri("/api/orders").header("Authorization", "Bearer " + token("x", "http://evil", KEY))
                .exchange().expectStatus().isUnauthorized();
        web.get().uri("/api/orders").header("Authorization", "Bearer " + token("x", "http://auth-test", attacker))
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void loginIsPublic() {
        web.post().uri("/api/auth/login").bodyValue("{}").header("Content-Type", "application/json")
                .exchange().expectStatus().isOk();
    }

    @Test
    void burstAboveTheRedisTokenBucketIsRejectedWith429() {
        String jwt = token("merchant.greedy", "http://auth-test", KEY);
        int tooMany = 0;
        for (int i = 0; i < 8; i++) {
            int status = web.get().uri("/api/orders").header("Authorization", "Bearer " + jwt).exchange()
                    .returnResult(String.class).getStatus().value();
            if (status == 429) {
                tooMany++;
            }
        }
        assertThat(tooMany).as("burst capacity 3, replenish 1/s").isGreaterThanOrEqualTo(3);
    }

    @Test
    void queryParameterTokenIsAcceptedOnlyOnTheStreamPath() {
        String jwt = token("ops.stream", "http://auth-test", KEY);

        web.get().uri("/api/activity/stream?access_token=" + jwt).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.path").isEqualTo("/api/activity/stream");
        web.get().uri("/api/orders?access_token=" + jwt).exchange().expectStatus().isUnauthorized();
    }

    @Test
    void systemHealthAggregatesEveryServiceAndKafka() {
        String jwt = token("ops.health", "http://auth-test", KEY);

        web.get().uri("/api/system/health").header("Authorization", "Bearer " + jwt).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.components.length()").isEqualTo(6)
                .jsonPath("$.components[0].name").isEqualTo("gateway")
                .jsonPath("$.components[5].name").isEqualTo("kafka");
        web.get().uri("/api/system/health").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void demoSmsRouteIsRewrittenToTheFakeProviderAdminPath() {
        String jwt = token("ops.demo", "http://auth-test", KEY);

        web.post().uri("/api/demo/sms-provider/FAIL").header("Authorization", "Bearer " + jwt).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.path").isEqualTo("/fake-sms/admin/mode/FAIL");
    }
}
