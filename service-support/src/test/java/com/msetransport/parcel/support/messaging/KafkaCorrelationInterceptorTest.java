package com.msetransport.parcel.support.messaging;

import com.msetransport.parcel.events.EventHeaders;
import com.msetransport.parcel.support.correlation.CorrelationId;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaCorrelationInterceptorTest {

    private final KafkaCorrelationInterceptor interceptor = new KafkaCorrelationInterceptor();

    @Test
    void restoresCorrelationIdFromHeaderAndClearsAfterRecord() {
        var record = new ConsumerRecord<Object, Object>("t", 0, 0L, "k", "v");
        record.headers().add(EventHeaders.CORRELATION_ID, "corr-99".getBytes(StandardCharsets.UTF_8));

        interceptor.intercept(record, null);
        assertThat(CorrelationId.current()).isEqualTo("corr-99");

        interceptor.afterRecord(record, null);
        assertThat(CorrelationId.current()).isNull();
    }

    @Test
    void createsAnIdWhenHeaderIsMissing() {
        interceptor.intercept(new ConsumerRecord<>("t", 0, 0L, "k", "v"), null);

        assertThat(CorrelationId.current()).isNotBlank();
        CorrelationId.clear();
    }
}
