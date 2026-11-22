package com.msetransport.parcel.support.messaging;

import com.msetransport.parcel.events.InvalidEventException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Blocking retry + dead-letter topic. The consumer retries in place (keeps partition ordering), and
 * after the last attempt the record is published to {@code <topic>.<group>-dlt} and the offset moves on.
 * Poison messages ({@link InvalidEventException}) skip the retries and go to the DLT immediately.
 */
public final class KafkaErrorHandling {

    private KafkaErrorHandling() {
    }

    public static String dltTopic(String topic, String group) {
        return topic + "." + group + "-dlt";
    }

    public static DefaultErrorHandler blockingRetryThenDlt(KafkaOperations<?, ?> template, String group,
                                                           int maxRetries, long initialBackoffMs) {
        // partition -1: let the producer choose, so the DLT may have fewer partitions than the source
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(dltTopic(record.topic(), group), -1));
        var backOff = new ExponentialBackOff(initialBackoffMs, 2.0);
        backOff.setMaxAttempts(maxRetries);
        var handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(InvalidEventException.class);
        return handler;
    }
}
