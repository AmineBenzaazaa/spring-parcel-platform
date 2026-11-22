package com.msetransport.parcel.auth;

import com.msetransport.parcel.auth.user.DemoUserSeeder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full Spring context against a real Postgres (Testcontainers) with Flyway migrations applied. */
@SpringBootTest
@AutoConfigureMockMvc
class AuthApiIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    private String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginBody(username, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    @Test
    void seededMerchantLogsInAndTokenWorksOnProtectedEndpoint() throws Exception {
        String token = login("merchant.atlas", DemoUserSeeder.DEMO_PASSWORD);

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("merchant.atlas"))
                .andExpect(jsonPath("$.merchantId").value("M-1001"))
                .andExpect(jsonPath("$.roles[0]").value("MERCHANT"));
    }

    @Test
    void wrongPasswordReturns401ProblemDetail() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginBody("ops", "nope"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Authentication failed"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void unknownUserGetsTheSameAnswerAsWrongPassword() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginBody("ghost", "whatever"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }

    @Test
    void blankCredentialsFailValidationWithFieldErrors() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void jwksExposesOnlyThePublicRsaKey() throws Exception {
        String body = mvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].kid").exists())
                .andExpect(jsonPath("$.keys[0]", not(hasKey("d"))))
                .andReturn().getResponse().getContentAsString();
        JsonNode key = json.readTree(body).get("keys").get(0);
        org.assertj.core.api.Assertions.assertThat(key.has("n")).isTrue();
    }

    @Test
    void protectedEndpointWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedTokenIs401() throws Exception {
        String token = login("ops", DemoUserSeeder.DEMO_PASSWORD);
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void streamTokenRequiresAValidSessionToken() throws Exception {
        mvc.perform(post("/api/auth/stream-token")).andExpect(status().isUnauthorized());

        String token = login("ops", DemoUserSeeder.DEMO_PASSWORD);
        mvc.perform(post("/api/auth/stream-token").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(60))
                .andExpect(jsonPath("$.role").value("OPS"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    record LoginBody(String username, String password) {
    }
}
