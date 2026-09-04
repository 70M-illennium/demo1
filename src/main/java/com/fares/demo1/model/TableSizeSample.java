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
 * One row per user table per collection cycle, from {@code information_schema.tables} -
 * the per-table breakdown behind {@code DatabaseSnapshotEntity.databaseSizeBytes},
 * plus {@code dataFreeBytes} for spotting fragmentation (space an {@code OPTIMIZE TABLE}
 * would reclaim). Collected on a slow cadence; system schemas are excluded.
 *
 * <p>Grouped by {@code capturedAt}, no FK - same reasoning as the workload samples.
 */
@Entity
@Table(indexes = @Index(name = "idx_table_size_captured_at", columnList = "capturedAt"))
@Getter
@Setter
public class TableSizeSample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    private Instant capturedAt;

    private String schemaName;
    private String tableName;
    private String engine;

    /** Optimizer's row estimate - approximate for InnoDB. */
    private long rowsEstimate;

    private long dataBytes;
    private long indexBytes;

    /** Allocated-but-unused space; a large value relative to size means fragmentation. */
    private long dataFreeBytes;
}
