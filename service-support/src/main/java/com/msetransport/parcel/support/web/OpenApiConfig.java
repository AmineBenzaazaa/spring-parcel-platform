package com.msetransport.parcel.support.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI at /swagger-ui.html with a "Bearer JWT" authorize button. */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI serviceOpenApi(@Value("${spring.application.name}") String name) {
        return new OpenAPI()
                .info(new Info().title(name).version("v1")
                        .description("Part of spring-parcel-platform (cash-on-delivery parcel delivery)"))
                .components(new Components().addSecuritySchemes("bearer-jwt",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));
    }
}
