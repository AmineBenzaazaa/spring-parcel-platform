package com.msetransport.parcel.support.correlation;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    private String run(MockHttpServletRequest request, MockHttpServletResponse response, AtomicReference<String> seen)
            throws Exception {
        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seen.set(CorrelationId.current());
            }
        });
        return response.getHeader(CorrelationId.HTTP_HEADER);
    }

    @Test
    void keepsAValidIncomingIdAndExposesItInMdcAndResponse() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(CorrelationId.HTTP_HEADER, "req-42");
        var seen = new AtomicReference<String>();

        String echoed = run(request, new MockHttpServletResponse(), seen);

        assertThat(seen.get()).isEqualTo("req-42");
        assertThat(echoed).isEqualTo("req-42");
    }

    @Test
    void generatesAnIdWhenMissing() throws Exception {
        var seen = new AtomicReference<String>();

        String echoed = run(new MockHttpServletRequest(), new MockHttpServletResponse(), seen);

        assertThat(echoed).isNotBlank().isEqualTo(seen.get());
    }

    @Test
    void replacesUnsafeIdsToPreventLogInjection() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(CorrelationId.HTTP_HEADER, "abc\nFAKE LOG LINE");
        var seen = new AtomicReference<String>();

        String echoed = run(request, new MockHttpServletResponse(), seen);

        assertThat(echoed).doesNotContain("\n").doesNotContain("FAKE");
    }

    @Test
    void clearsMdcAfterTheRequest() throws Exception {
        run(new MockHttpServletRequest(), new MockHttpServletResponse(), new AtomicReference<>());

        assertThat(CorrelationId.current()).isNull();
    }
}
