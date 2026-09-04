package com.fares.demo1.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One row per wait event per collection cycle - the top-N by total wait time from
 * {@code performance_schema.events_waits_summary_global_by_event_name} (excluding the
 * {@code idle} pseudo-event). Cumulative since server start, so the between-cycle delta
 * is what shows where time is actually going (I/O, locks, mutexes).
 */
@Entity
@Table(indexes = @Index(name = "idx_wait_sample_captured_at", columnList = "capturedAt"))
@Getter
@Setter
public class WaitSample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    private Instant capturedAt;

    /** e.g. wait/io/file/innodb/innodb_data_file, wait/lock/table/sql/handler */
    private String eventName;

    private long count;
    private double totalWaitMs;
    private double avgWaitMs;
}
