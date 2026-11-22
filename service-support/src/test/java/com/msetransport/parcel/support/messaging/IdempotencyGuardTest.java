package com.msetransport.parcel.support.messaging;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdempotencyGuardTest {

    private final ProcessedEventRepository repository = mock(ProcessedEventRepository.class);
    private final IdempotencyGuard guard = new IdempotencyGuard(repository, Clock.systemUTC());

    @Test
    void firstDeliveryWhenTheInsertCreatesARow() {
        UUID id = UUID.randomUUID();
        when(repository.insertIfAbsent(eq(id), eq("dispatch"), any())).thenReturn(1);

        assertThat(guard.firstDelivery(id, "dispatch")).isTrue();
    }

    @Test
    void duplicateWhenTheRowAlreadyExists() {
        UUID id = UUID.randomUUID();
        when(repository.insertIfAbsent(eq(id), eq("dispatch"), any())).thenReturn(0);

        assertThat(guard.firstDelivery(id, "dispatch")).isFalse();
    }
}
