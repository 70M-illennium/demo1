package com.fares.demo1.dto;

import com.fares.demo1.model.DatabaseSnapshotEntity;

import java.time.Instant;

/**
 * The shape the API returns for one database snapshot. A read-only view: the JPA entity
 * is never serialised directly, so the response can evolve independently of the table
 * and no persistence details leak out.
 *
 * <p>Metrics are grouped by concern (availability, connections, workload, buffer pool,
 * locking, storage). Field names shorten inside a group because the group already
 * carries the context. Computed values (hit ratio, MB) are derived here, not stored.
 */
public record DatabaseSnapshotResponseDTO(
        long id,
        Instant timestamp,
        Availability availability,
        Connections connections,
        Workload workload,
        BufferPool bufferPool,
        Locking locking,
        Storage storage
) {

    public record Availability(
            boolean reachable,
            long connectLatencyMs,
            long uptimeSeconds
    ) {}

    public record Connections(
            int connected,
            int running,
            int maxUsed
    ) {}

    public record Workload(
            long questions,
            long slowQueries,
            long createdTmpDiskTables
    ) {}

    public record BufferPool(
            long readRequests,
            long reads,
            double hitRatioPct
    ) {}

    public record Locking(
            int rowLockCurrentWaits,
            long deadlocks,
            long oldestTransactionAgeSeconds
    ) {}

    public record Storage(
            double sizeMb
    ) {}

    public static DatabaseSnapshotResponseDTO from(DatabaseSnapshotEntity e) {
        return new DatabaseSnapshotResponseDTO(
                e.getId(),
                e.getTimestamp(),
                new Availability(
                        e.isReachable(),
                        e.getConnectLatencyMs(),
                        e.getUptimeSeconds()
                ),
                new Connections(
                        e.getThreadsConnected(),
                        e.getThreadsRunning(),
                        e.getMaxUsedConnections()
                ),
                new Workload(
                        e.getQuestions(),
                        e.getSlowQueries(),
                        e.getCreatedTmpDiskTables()
                ),
                new BufferPool(
                        e.getInnodbBufferPoolReadRequests(),
                        e.getInnodbBufferPoolReads(),
                        hitRatioPct(e.getInnodbBufferPoolReadRequests(), e.getInnodbBufferPoolReads())
                ),
                new Locking(
                        e.getInnodbRowLockCurrentWaits(),
                        e.getInnodbDeadlocks(),
                        e.getOldestTransactionAgeSeconds()
                ),
                new Storage(
                        Units.toMb(e.getDatabaseSizeBytes())
                )
        );
    }

    /** Buffer pool hit ratio as a percentage, rounded to 2 decimals. 0 when there are no reads yet. */
    private static double hitRatioPct(long readRequests, long reads) {
        if (readRequests <= 0) {
            return 0.0;
        }
        double ratio = (1.0 - (double) reads / readRequests) * 100.0;
        return Math.round(ratio * 100.0) / 100.0;
    }
}
