package com.benzaazaa.parcel.support.security;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * Every business service is an OAuth2 resource server: it validates the RS256 JWT itself using the
 * auth-service JWKS (defence in depth, the gateway is not the only check). Stateless, no sessions.
 */
@Configuration
@EnableMethodSecurity
public class ResourceServerSecurityConfig {

    @Bean
    SecurityFilterChain resourceServerFilterChain(HttpSecurity http,
            @Value("${parcel.security.public-paths:/actuator/**,/v3/api-docs/**,/swagger-ui/**,/swagger-ui.html}")
            List<String> publicPaths) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // the async re-dispatch of a long-lived response (SSE) was authorised when it started
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers(publicPaths.toArray(String[]::new)).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(new JwtRolesConverter())))
                .build();
    }

    /** Picked up by oauth2ResourceServer(); see {@link PathScopedBearerTokenResolver}. */
    @Bean
    BearerTokenResolver bearerTokenResolver(
            @Value("${parcel.security.query-token-paths:}") List<String> queryTokenPaths) {
        return new PathScopedBearerTokenResolver(queryTokenPaths.stream().filter(p -> !p.isBlank()).toList());
    }
}
