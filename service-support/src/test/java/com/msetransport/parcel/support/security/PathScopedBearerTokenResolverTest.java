package com.msetransport.parcel.support.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PathScopedBearerTokenResolverTest {

    private final PathScopedBearerTokenResolver resolver =
            new PathScopedBearerTokenResolver(List.of("/api/activity/stream"));

    private static MockHttpServletRequest get(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setQueryString("access_token=abc");
        request.addParameter("access_token", "abc");
        return request;
    }

    @Test
    void queryTokenIsAcceptedOnlyOnAllowListedPaths() {
        assertThat(resolver.resolve(get("/api/activity/stream"))).isEqualTo("abc");
        assertThat(resolver.resolve(get("/api/notifications"))).isNull();
    }

    @Test
    void headerTokenWorksEverywhere() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notifications");
        request.addHeader("Authorization", "Bearer from-header");

        assertThat(resolver.resolve(request)).isEqualTo("from-header");
    }
}
