package com.fares.demo1.model;

/**
 * The kinds of problem the {@code HealthCheckService} can flag. One open
 * {@link MonitorEventEntity} per type at a time - a type stays open until its
 * condition clears.
 */
public enum EventType {

    /** A {@code SELECT 1} against the monitored database failed. */
    DB_UNREACHABLE,

    /** Host filesystem usage is above the configured limit. */
    DISK_SPACE_LOW,

    /** The oldest open transaction on the target has been running too long. */
    LONG_RUNNING_TRANSACTION,

    /** Slow queries are accumulating faster than the configured per-minute rate. */
    SLOW_QUERY_RATE_HIGH,

    /** Internal on-disk temp tables are being created faster than the configured rate. */
    TMP_DISK_TABLE_RATE_HIGH,

    /** Buffer-pool hit ratio over the last interval dropped below the configured floor. */
    BUFFER_POOL_HIT_RATIO_LOW,

    /** One or more new InnoDB deadlocks since the previous snapshot. */
    DEADLOCK_INCREASE,

    /** Connected threads are close to {@code max_connections}. */
    CONNECTION_SATURATION,

    /** Connect latency is far outside its own recent baseline (a spike). */
    CONNECT_LATENCY_ANOMALY,

    /**
     * A stretch where no snapshots were collected - the service was down or wedged.
     * Recorded after the fact with both {@code occurredAt} (gap start) and
     * {@code resolvedAt} (gap end) set.
     */
    COLLECTION_GAP,

    /**
     * An Error/Warning line seen in the target's {@code performance_schema.error_log}.
     * Point-in-time: {@code occurredAt} = {@code resolvedAt} = the log timestamp.
     */
    TARGET_ERROR_LOG
}
