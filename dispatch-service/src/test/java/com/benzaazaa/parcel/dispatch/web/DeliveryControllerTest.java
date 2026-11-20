package com.benzaazaa.parcel.dispatch.web;

import com.benzaazaa.parcel.dispatch.application.DispatchService;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
}
