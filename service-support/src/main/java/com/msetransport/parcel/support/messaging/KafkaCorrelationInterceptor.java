package com.msetransport.parcel.support.messaging;

import com.msetransport.parcel.events.EventHeaders;
import com.msetransport.parcel.support.correlation.CorrelationId;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.listener.RecordInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * Restores the correlation id from the Kafka header into the MDC before the listener runs, so log
 * lines and any outbox events written by the listener carry the original request's id.
 * Typed Object/Object so Spring Boot's listener factory picks the bean up automatically.
 */
public class KafkaCorrelationInterceptor implements RecordInterceptor<Object, Object> {

    @Override
    public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> record,
                                                    Consumer<Object, Object> consumer) {
        Header header = record.headers().lastHeader(EventHeaders.CORRELATION_ID);
        String id = header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
        CorrelationId.set(CorrelationId.sanitize(id));
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        CorrelationId.clear();
    }
}
