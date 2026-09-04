package com.fares.demo1.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A health problem the monitor has flagged, rather than a periodic measurement. It is
 * <b>stateful</b>: {@code occurredAt} is set when the condition first trips and
 * {@code resolvedAt} stays null until it clears, so a problem that lasts an hour is one
 * row, not one row per collection cycle. {@code HealthCheckService} keeps at most one
 * unresolved event per {@link EventType}.
 */
@Entity
@Getter
@Setter
public class MonitorEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    // Plain varchar via columnDefinition, NOT @Enumerated's default: that would make a
    // native MySQL ENUM (or a CHECK constraint), and ddl-auto=update never updates
    // either - so a new EventType / Severity constant would fail to insert.
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(64)")
    private EventType type;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(32)")
    private Severity severity;

    /** Human-readable, with the actual numbers ("disk usage 93.4% (warn 90%)"). */
    private String message;

    /** The value that tripped the check, for the API/graphs. Null when not numeric. */
    private Double metricValue;

    /** When the condition first met the "open" rule. */
    private Instant occurredAt;

    /** The most recent cycle in which the condition was still breaching. */
    private Instant lastSeenAt;

    /** When the condition met the "resolve" rule; null while the event is active. */
    private Instant resolvedAt;
}
