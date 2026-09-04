package com.fares.demo1.service;

import com.fares.demo1.model.MonitorEventEntity;
import com.fares.demo1.repo.MonitorEventRepo;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * The write path for events an admin acts on directly, as opposed to the automatic
 * open/resolve state machine in {@code HealthCheckService}. Two actions:
 *
 * <ul>
 *   <li>{@link #acknowledge} - "seen it, dealing with it" - does not touch
 *       {@code resolvedAt}, so the checker keeps evaluating the underlying condition
 *       exactly as before and can still escalate or auto-resolve it.
 *   <li>{@link #forceResolve} - closes the event by hand, for a condition that cleared
 *       in a way the checker can't observe, or a flag that was simply wrong.
 * </ul>
 *
 * <p>The scheduled collectors that <em>raise</em> new events ({@code HealthCheckService},
 * {@code ErrorLogService}, {@code ConfigSnapshotService}) still write
 * {@code MonitorEventEntity} rows directly through the repo, same as before - this
 * service only ever operates on an event that already exists, which is a different
 * enough job (lookup by id, 404 if missing, notify on resolve) to earn its own class
 * instead of living in the controller.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MonitorEventService {

    private final MonitorEventRepo monitorEventRepo;
    private final Notifier notifier;

    @Transactional
    public MonitorEventEntity acknowledge(Long id, String note) {
        MonitorEventEntity event = find(id);
        event.setAcknowledged(true);
        event.setAckNote(note);
        return monitorEventRepo.save(event);
    }

    @Transactional
    public MonitorEventEntity forceResolve(Long id) {
        MonitorEventEntity event = find(id);
        if (event.getResolvedAt() == null) {
            event.setResolvedAt(Instant.now());
            event = monitorEventRepo.save(event);
            notifier.onResolved(event);
            log.info("event {} force-resolved by admin", id);
        }
        return event;
    }

    private MonitorEventEntity find(Long id) {
        return monitorEventRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("no event with id " + id));
    }
}
