package com.fares.demo1.model;

import jakarta.persistence.Column;
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
 * One row per query shape (digest) per collection cycle - the top-N statements on the
 * target by total time, from {@code performance_schema.events_statements_summary_by_digest}.
 * The counters are cumulative since the digest table was last reset; the useful signal
 * is the change between cycles, plus the rows-examined / rows-sent ratio and full-scan
 * count for spotting inefficient queries.
 *
 * <p>Not linked to a {@code DatabaseSnapshotEntity} by FK - grouped by {@code capturedAt}
 * instead, so it survives the write buffer and is pruned on its own by retention.
 */
@Entity
@Table(indexes = @Index(name = "idx_query_digest_captured_at", columnList = "capturedAt"))
@Getter
@Setter
public class QueryDigestSample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    private Instant capturedAt;

    private String schemaName;

    /** The digest hash - stable id for the query shape across cycles. */
    private String digest;

    /** Normalised statement text (literals stripped). MySQL caps this near 1024 chars. */
    @Column(length = 1024)
    private String digestText;

    private long execCount;
    private double totalLatencyMs;
    private double avgLatencyMs;
    private long rowsExamined;
    private long rowsSent;

    /** Executions that used no index (full scans). */
    private long fullScans;

    /** Executions that spilled a temp table to disk. */
    private long tmpDiskTables;
}
