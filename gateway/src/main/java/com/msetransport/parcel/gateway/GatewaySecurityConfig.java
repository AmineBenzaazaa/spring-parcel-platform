package com.benzaazaa.parcel.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;

/**
 * Edge authentication: reject missing/expired/forged tokens before they reach any service.
 * Authorisation by role stays in each service (@PreAuthorize), which knows its own rules.
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    /** The only path where a token may travel as ?access_token= (EventSource cannot set headers). */
    public static final String STREAM_PATH = "/api/activity/stream";

    @Bean
    SecurityWebFilterChain gatewaySecurity(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(ex -> ex
                        .pathMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .pathMatchers("/api/auth/jwks", "/actuator/health/**", "/actuator/prometheus",
                                "/actuator/info").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(o -> o.bearerTokenConverter(bearerTokenConverter()).jwt(Customizer.withDefaults()))
                .build();
    }

    /**
     * Header everywhere; header or {@code access_token} query parameter on the SSE stream only. The
     * service behind it accepts nothing but 60-second stream tokens there, which limits what a token
     * leaked through an access log is worth.
     */
    static ServerAuthenticationConverter bearerTokenConverter() {
        var headerOnly = new ServerBearerTokenAuthenticationConverter();
        var headerOrQuery = new ServerBearerTokenAuthenticationConverter();
        headerOrQuery.setAllowUriQueryParameter(true);
        return exchange -> STREAM_PATH.equals(exchange.getRequest().getPath().pathWithinApplication().value())
                ? headerOrQuery.convert(exchange)
                : headerOnly.convert(exchange);
    }
}
