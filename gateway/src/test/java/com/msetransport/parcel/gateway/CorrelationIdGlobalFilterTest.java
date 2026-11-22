package com.msetransport.parcel.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdGlobalFilterTest {

    private final CorrelationIdGlobalFilter filter = new CorrelationIdGlobalFilter();

    private String forwardedId(MockServerHttpRequest request, AtomicReference<ServerWebExchange> captured) {
        var exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = ex -> {
            captured.set(ex);
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        return captured.get().getRequest().getHeaders().getFirst(CorrelationIdGlobalFilter.HEADER);
    }

    @Test
    void generatesIdForDownstreamAndResponse() {
        var captured = new AtomicReference<ServerWebExchange>();

        String id = forwardedId(MockServerHttpRequest.get("/api/orders").build(), captured);

        assertThat(id).isNotBlank();
        assertThat(captured.get().getResponse().getHeaders().getFirst(CorrelationIdGlobalFilter.HEADER)).isEqualTo(id);
    }

    @Test
    void keepsValidClientIdButReplacesUnsafeOne() {
        assertThat(forwardedId(MockServerHttpRequest.get("/x").header("X-Correlation-Id", "abc-123").build(),
                new AtomicReference<>())).isEqualTo("abc-123");
        assertThat(forwardedId(MockServerHttpRequest.get("/x").header("X-Correlation-Id", "bad id\r\ninjected").build(),
                new AtomicReference<>())).doesNotContain("injected");
    }
}
