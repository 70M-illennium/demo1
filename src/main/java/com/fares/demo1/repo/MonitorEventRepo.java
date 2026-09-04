package com.fares.demo1.repo;

import com.fares.demo1.model.EventType;
import com.fares.demo1.model.MonitorEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MonitorEventRepo extends JpaRepository<MonitorEventEntity, Long> {

    /** The currently-open event of this type, if any. The service keeps at most one. */
    Optional<MonitorEventEntity> findFirstByTypeAndResolvedAtIsNull(EventType type);

    /** Used to avoid recording the same collection gap twice (keyed by its start instant). */
    boolean existsByTypeAndOccurredAt(EventType type, Instant occurredAt);

    /** Every open event, newest first (the API re-sorts by severity). */
    List<MonitorEventEntity> findByResolvedAtIsNullOrderByOccurredAtDesc();

    /** Recent events, open or resolved, newest first. */
    List<MonitorEventEntity> findByOrderByOccurredAtDesc(Pageable pageable);

    /** Bulk-delete events that resolved before the cutoff. Unresolved events are kept. */
    @Modifying
    @Query("delete from MonitorEventEntity e where e.resolvedAt is not null and e.resolvedAt < :cutoff")
    int deleteResolvedBefore(@Param("cutoff") Instant cutoff);
}
