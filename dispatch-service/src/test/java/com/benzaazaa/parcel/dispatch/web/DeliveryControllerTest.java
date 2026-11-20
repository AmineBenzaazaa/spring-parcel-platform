package com.benzaazaa.parcel.dispatch.web;

import com.benzaazaa.parcel.dispatch.application.CourierView;
import com.benzaazaa.parcel.dispatch.application.DispatchService;
import com.benzaazaa.parcel.dispatch.domain.Courier;
import com.benzaazaa.parcel.dispatch.domain.Delivery;
import com.benzaazaa.parcel.dispatch.domain.DeliveryStatus;
import com.benzaazaa.parcel.support.security.ResourceServerSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeliveryController.class)
@Import(ResourceServerSecurityConfig.class)
class DeliveryControllerTest {

    @Autowired
    MockMvc mvc;
    @MockBean
    DispatchService dispatch;
    @MockBean
    JwtDecoder jwtDecoder;

    @Test
    void merchantsCannotTouchDeliveries() throws Exception {
        mvc.perform(get("/api/deliveries").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyOpsCanAssignManually() throws Exception {
        mvc.perform(post("/api/deliveries/{id}/assign", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_COURIER")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"courierId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void failedAttemptRequiresAReason() throws Exception {
        mvc.perform(post("/api/deliveries/{id}/failed", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_COURIER")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").exists());
    }

    @Test
    void ownershipViolationFromServiceIs403ProblemDetail() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(dispatch.outForDelivery(eq(orderId), any())).thenThrow(new AccessDeniedException("not yours"));

        mvc.perform(post("/api/deliveries/{id}/out-for-delivery", orderId)
                        .with(jwt().jwt(j -> j.claim("courierId", UUID.randomUUID().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_COURIER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    void opsSeesCouriersWithLoadAndAvailability() throws Exception {
        var casa1 = new Courier(UUID.randomUUID(), "Youssef El Amrani", "+212661000001", "CASA", true);
        var rabat = new Courier(UUID.randomUUID(), "Imane Tazi", "+212661000003", "RABAT", true);
        when(dispatch.couriers()).thenReturn(List.of(CourierView.of(casa1, 3, 1), CourierView.of(rabat, 0, 0)));

        mvc.perform(get("/api/couriers").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPS"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Youssef El Amrani"))
                .andExpect(jsonPath("$[0].activeLoad").value(3))
                .andExpect(jsonPath("$[0].availability").value("ON_ROUTE"))
                .andExpect(jsonPath("$[1].availability").value("IDLE"));
    }

    @Test
    void couriersCannotListTheFleet() throws Exception {
        mvc.perform(get("/api/couriers").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_COURIER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void opsCanFilterDeliveriesByStatusWithALimit() throws Exception {
        var d = Delivery.open(UUID.randomUUID(), "PX-TEST0001", "+212612345678", "Rabat", "RABAT",
                new BigDecimal("120.00"), 3, Instant.now());
        when(dispatch.list(any(), eq(List.of(DeliveryStatus.ASSIGNED)), eq(150))).thenReturn(List.of(d));

        mvc.perform(get("/api/deliveries").param("status", "ASSIGNED").param("limit", "150")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPS"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].trackingCode").value("PX-TEST0001"))
                .andExpect(jsonPath("$[0].zone").value("RABAT"));
    }
}
