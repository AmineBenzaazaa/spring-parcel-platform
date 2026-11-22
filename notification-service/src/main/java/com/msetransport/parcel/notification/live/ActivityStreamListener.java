package com.msetransport.parcel.notification.live;

import com.msetransport.parcel.events.EventCodec;
import com.msetransport.parcel.events.Topics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Feeds the live dashboard from the existing topics, with its <b>own consumer group</b>, so it never
 * steals partitions or offsets from the notification consumer.
 *
 * <p>The group id is unique per instance ({@code notification-live-<uuid>}) and starts at the
 * latest offset: every replica must see every event, because a browser is connected to only one
 * replica. That is broadcast, not work sharing. History comes from the database, not from replaying
 * Kafka.
 *
 * <p>Best effort on purpose: this is a read model for humans. A broken event is logged and skipped
 * instead of being retried or dead-lettered (the business consumers already do that).
 */
@Component
public class ActivityStreamListener {

    private static final Logger log = LoggerFactory.getLogger(ActivityStreamListener.class);

    private final EventCodec codec;
    private final ActivityService activity;
    private final LiveEventHub hub;

    public ActivityStreamListener(EventCodec codec, ActivityService activity, LiveEventHub hub) {
        this.codec = codec;
        this.activity = activity;
        this.hub = hub;
    }

    @KafkaListener(id = "notification-live-activity", topics = {Topics.ORDER_EVENTS, Topics.DELIVERY_STATUS},
            groupId = "${parcel.live.group-id}",
            properties = {"auto.offset.reset=${parcel.live.auto-offset-reset:latest}"})
    public void onEvent(ConsumerRecord<String, String> record) {
        try {
            activity.record(codec.decode(record.value())).ifPresent(hub::broadcast);
        } catch (RuntimeException e) {
            log.warn("Live activity skipped record topic={} offset={}: {}", record.topic(), record.offset(),
                    e.toString());
        }
    }
}
