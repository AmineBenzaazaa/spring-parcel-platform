package com.msetransport.parcel.notification.live;

import com.msetransport.parcel.notification.live.LivePayloads.CircuitLive;
import com.msetransport.parcel.notification.live.LivePayloads.TimelineEntry;
import com.msetransport.parcel.support.security.ResourceServerSecurityConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ActivityController.class)
@Import({ResourceServerSecurityConfig.class, LiveEventHub.class, ActivityControllerTest.Infra.class})
@TestPropertySource(properties = "parcel.security.query-token-paths=/api/activity/stream")
class ActivityControllerTest {

    @TestConfiguration
    static class Infra {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    LiveEventHub hub;
    @MockBean
    ActivityService activity;
    @MockBean
    LiveBridges bridges;
    @MockBean
    JwtDecoder jwtDecoder;

    private static Jwt token(String tokenUse, String role) {
        var builder = Jwt.withTokenValue("t").header("alg", "RS256").subject("ops").claim("roles", List.of(role))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (tokenUse != null) {
            builder.claim("token_use", tokenUse);
        }
        return builder.build();
    }

    private static RequestPostProcessor ops(String tokenUse) {
        return jwt().jwt(token(tokenUse, "OPS")).authorities(new SimpleGrantedAuthority("ROLE_OPS"));
    }

    @Test
    void sessionTokenCannotOpenTheStream() throws Exception {
        mvc.perform(get("/api/activity/stream").with(ops(null))).andExpect(status().isForbidden());
    }

    @Test
    void merchantCannotOpenTheStream() throws Exception {
        mvc.perform(get("/api/activity/stream").with(jwt().jwt(token("stream", "MERCHANT"))
                        .authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void streamTokenOpensAnEventStreamThatStartsWithHello() throws Exception {
        when(bridges.circuit()).thenReturn(new CircuitLive("smsGateway", "CLOSED", -1, 0));

        var result = mvc.perform(get("/api/activity/stream").with(ops("stream")))
                .andExpect(request().asyncStarted())
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andReturn();
        hub.broadcast(new LiveEvent(LiveEvent.DELIVERY, java.util.Map.of("newStatus", "DELIVERED")));

        String body = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getContentType()).startsWith("text/event-stream");
        assertThat(body).contains("event:hello").contains("\"state\":\"CLOSED\"")
                .contains("event:delivery").contains("\"newStatus\":\"DELIVERED\"");
        assertThat(hub.subscribers()).isPositive();
    }

    @Test
    void queryParameterTokenIsOnlyHonouredOnTheStreamPath() throws Exception {
        when(jwtDecoder.decode("abc")).thenReturn(token("stream", "OPS"));
        when(bridges.circuit()).thenReturn(new CircuitLive("smsGateway", "CLOSED", -1, 0));

        mvc.perform(get("/api/activity/orders/{id}", UUID.randomUUID()).param("access_token", "abc"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/activity/stream").param("access_token", "abc"))
                .andExpect(request().asyncStarted());
    }

    @Test
    void timelineListsEventsWithCorrelationIds() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(activity.timeline(orderId)).thenReturn(List.of(
                new TimelineEntry(UUID.randomUUID(), "ORDER_CREATED", "CREATED", null, 0, null, null, "corr-1",
                        Instant.parse("2026-11-20T10:00:00Z")),
                new TimelineEntry(UUID.randomUUID(), "DELIVERY_STATUS_CHANGED", "ASSIGNED", "PENDING_ASSIGNMENT", 0,
                        null, UUID.randomUUID(), "corr-1", Instant.parse("2026-11-20T10:00:01Z"))));

        mvc.perform(get("/api/activity/orders/{id}", orderId).with(ops(null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("CREATED"))
                .andExpect(jsonPath("$[1].correlationId").value("corr-1"));
    }

    @Test
    void circuitBreakerStateIsExposed() throws Exception {
        when(bridges.circuit()).thenReturn(new CircuitLive("smsGateway", "OPEN", 80f, 10));

        mvc.perform(get("/api/notifications/circuit-breaker").with(ops(null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("OPEN"))
                .andExpect(jsonPath("$.bufferedCalls").value(10));
    }
}
