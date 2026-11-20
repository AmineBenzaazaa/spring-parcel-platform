package com.benzaazaa.parcel.support.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
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
                        .requestMatchers(publicPaths.toArray(String[]::new)).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(new JwtRolesConverter())))
                .build();
    }
}
