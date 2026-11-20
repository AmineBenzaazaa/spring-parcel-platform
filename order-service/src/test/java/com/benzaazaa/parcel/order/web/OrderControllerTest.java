package com.benzaazaa.parcel.order.web;

import com.benzaazaa.parcel.order.application.CreateOrderCommand;
import com.benzaazaa.parcel.order.application.OrderService;
import com.benzaazaa.parcel.order.domain.Order;
import com.benzaazaa.parcel.support.security.ResourceServerSecurityConfig;
import com.benzaazaa.parcel.support.web.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import(ResourceServerSecurityConfig.class)
class OrderControllerTest {

    private static final String VALID = """
            {"recipientName":"Salma","recipientPhone":"+212612345678","addressLine":"12 Rue Atlas",
             "city":"Casablanca","codAmount":249.90}
            """;

    @Autowired
    MockMvc mvc;
    @MockBean
    OrderService orderService;
    @MockBean
    JwtDecoder jwtDecoder;

    private static RequestPostProcessor merchant() {
        return jwt().jwt(j -> j.subject("merchant.atlas").claim("roles", java.util.List.of("MERCHANT"))
                .claim("merchantId", "M-1001")).authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"));
    }

    @Test
    void anonymousRequestIs401() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void courierCannotCreateOrders() throws Exception {
        mvc.perform(post("/api/orders").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_COURIER")))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidPayloadReturnsProblemDetailWithFieldErrors() throws Exception {
        mvc.perform(post("/api/orders").with(merchant()).contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-Id", "test-corr")
                        .content("""
                                {"recipientName":"","recipientPhone":"0612","addressLine":"x","city":"Casa",
                                 "codAmount":-5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.errors.recipientName").exists())
                .andExpect(jsonPath("$.errors.recipientPhone").value("must be a Moroccan number like +212612345678"))
                .andExpect(jsonPath("$.errors.codAmount").exists())
                .andExpect(jsonPath("$.correlationId").value("test-corr"));
    }

    @Test
    void merchantCreatesOrderForThemselvesAnd201HasLocation() throws Exception {
        Order order = Order.create("M-1001", "Salma", "+212612345678", "12 Rue Atlas", "Casablanca",
                new BigDecimal("249.90"), "k1", Instant.now());
        when(orderService.create(eq(new CreateOrderCommand("M-1001", "Salma", "+212612345678", "12 Rue Atlas",
                "Casablanca", new BigDecimal("249.90"), "k1")))).thenReturn(order);

        mvc.perform(post("/api/orders").with(merchant()).header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/orders/" + order.getId()))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.merchantId").value("M-1001"));
    }

    @Test
    void businessRuleViolationMapsTo409() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderService.cancel(eq(id), any(), any()))
                .thenThrow(new BusinessRuleException("order-not-cancellable", "too late"));

        mvc.perform(post("/api/orders/{id}/cancel", id).with(merchant()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"changed mind\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("order-not-cancellable"));
    }
}
