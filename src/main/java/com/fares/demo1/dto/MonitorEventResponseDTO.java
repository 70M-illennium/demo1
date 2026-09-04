package com.fares.demo1.dto;

import com.fares.demo1.model.EventType;
import com.fares.demo1.model.MonitorEventEntity;
import com.fares.demo1.model.Severity;

import java.time.Instant;

/**
 * API view of one flagged health problem. {@code active} is derived from
 * {@code resolvedAt} so callers don't have to reason about nulls.
 */
public record MonitorEventResponseDTO(
        long id,
        EventType type,
        Severity severity,
        String message,
        Double metricValue,
        Instant occurredAt,
        Instant lastSeenAt,
        Instant resolvedAt,
        boolean active,
        boolean acknowledged,
        String ackNote
) {

    public static MonitorEventResponseDTO from(MonitorEventEntity e) {
        return new MonitorEventResponseDTO(
                e.getId(),
                e.getType(),
                e.getSeverity(),
                e.getMessage(),
                e.getMetricValue(),
                e.getOccurredAt(),
                e.getLastSeenAt(),
                e.getResolvedAt(),
                e.getResolvedAt() == null,
                e.isAcknowledged(),
                e.getAckNote()
        );
    }
}
