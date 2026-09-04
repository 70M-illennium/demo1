package com.fares.demo1.service;

import com.fares.demo1.model.MonitorEventEntity;
import com.fares.demo1.repo.MonitorEventRepo;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit test with mocked collaborators - no Spring context, no database. Covers
 * the two admin write paths that {@code EventController}'s ack/resolve endpoints call.
 */
@ExtendWith(MockitoExtension.class)
class MonitorEventServiceTest {

    @Mock
    private MonitorEventRepo monitorEventRepo;

    @Mock
    private Notifier notifier;

    @InjectMocks
    private MonitorEventService monitorEventService;

    @Test
    void acknowledgeSetsFlagAndNote_withoutTouchingResolvedAtOrNotifying() {
        MonitorEventEntity event = new MonitorEventEntity();
        when(monitorEventRepo.findById(1L)).thenReturn(Optional.of(event));
        when(monitorEventRepo.save(event)).thenReturn(event);

        MonitorEventEntity result = monitorEventService.acknowledge(1L, "investigating");

        assertThat(result.isAcknowledged()).isTrue();
        assertThat(result.getAckNote()).isEqualTo("investigating");
        assertThat(result.getResolvedAt()).isNull();   // ack is independent of resolve
        verifyNoInteractions(notifier);
    }

    @Test
    void acknowledgeUnknownId_throwsNotFound() {
        when(monitorEventRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> monitorEventService.acknowledge(99L, "x"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void forceResolveSetsResolvedAtAndNotifiesOnce() {
        MonitorEventEntity event = new MonitorEventEntity();
        when(monitorEventRepo.findById(1L)).thenReturn(Optional.of(event));
        when(monitorEventRepo.save(event)).thenReturn(event);

        MonitorEventEntity result = monitorEventService.forceResolve(1L);

        assertThat(result.getResolvedAt()).isNotNull();
        verify(notifier, times(1)).onResolved(event);
    }

    @Test
    void forceResolveIsIdempotent_alreadyResolvedEventIsLeftAloneAndNotRenotified() {
        MonitorEventEntity event = new MonitorEventEntity();
        Instant firstResolve = Instant.now().minusSeconds(60);
        event.setResolvedAt(firstResolve);
        when(monitorEventRepo.findById(1L)).thenReturn(Optional.of(event));

        MonitorEventEntity result = monitorEventService.forceResolve(1L);

        assertThat(result.getResolvedAt()).isEqualTo(firstResolve);
        verify(monitorEventRepo, never()).save(any());
        verifyNoInteractions(notifier);
    }

    @Test
    void forceResolveUnknownId_throwsNotFound() {
        when(monitorEventRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> monitorEventService.forceResolve(99L))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
