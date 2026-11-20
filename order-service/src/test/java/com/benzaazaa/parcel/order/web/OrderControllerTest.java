package com.benzaazaa.parcel.order.web;

import com.benzaazaa.parcel.order.application.CreateOrderCommand;
import com.benzaazaa.parcel.order.application.OrderService;
import com.benzaazaa.parcel.order.application.OrderStats;
import com.benzaazaa.parcel.order.domain.Order;
import com.benzaazaa.parcel.order.domain.OrderStatus;
import com.benzaazaa.parcel.support.security.ResourceServerSecurityConfig;
import com.benzaazaa.parcel.support.web.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    private static RequestPostProcessor ops() {
        return jwt().jwt(j -> j.subject("ops").claim("roles", List.of("OPS")))
                .authorities(new SimpleGrantedAuthority("ROLE_OPS"));
    }

    @Test
    void listFiltersByRepeatedStatusParameters() throws Exception {
        Order order = Order.create("M-1001", "Salma", "+212612345678", "12 Rue Atlas", "Casablanca",
                new BigDecimal("249.90"), null, Instant.now());
        when(orderService.list(any(), eq(List.of(OrderStatus.ASSIGNED, OrderStatus.OUT_FOR_DELIVERY)), any()))
                .thenReturn(new PageImpl<>(List.of(order), Pageable.ofSize(50), 1));

        mvc.perform(get("/api/orders").with(ops()).param("status", "ASSIGNED", "OUT_FOR_DELIVERY")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].trackingCode").value(order.getTrackingCode()))
                .andExpect(jsonPath("$.totalElements").value(1));
        verify(orderService).list(any(), eq(List.of(OrderStatus.ASSIGNED, OrderStatus.OUT_FOR_DELIVERY)),
                eq(org.springframework.data.domain.PageRequest.of(0, 50,
                        org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,
                                "createdAt"))));
    }

    @Test
    void unknownStatusFilterIs400() throws Exception {
        mvc.perform(get("/api/orders").with(ops()).param("status", "LOST_IN_SPACE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statsReturnsTodaysKpis() throws Exception {
        when(orderService.stats(any())).thenReturn(new OrderStats(Instant.parse("2026-11-19T23:00:00Z"), 12, 5,
                new BigDecimal("1249.50"), 4, 2, new BigDecimal("83.3"), Map.of(OrderStatus.DELIVERED, 5L)));

        mvc.perform(get("/api/orders/stats").with(ops()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ordersToday").value(12))
                .andExpect(jsonPath("$.codCollectedToday").value(1249.50))
                .andExpect(jsonPath("$.deliveredRate").value(83.3))
                .andExpect(jsonPath("$.byStatus.DELIVERED").value(5));
    }

    @Test
    void couriersCannotReadStats() throws Exception {
        mvc.perform(get("/api/orders/stats").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_COURIER"))))
                .andExpect(status().isForbidden());
    }
}
