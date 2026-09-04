package com.fares.demo1.service;

import com.fares.demo1.model.DatabaseSnapshotEntity;
import com.fares.demo1.repo.DatabaseSnapshotRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class DatabaseSnapshotService {

    private final DatabaseSnapshotRepo databaseSnapshotRepo;
    private final SnapshotWriteBuffer writeBuffer;

    /** Bound to the monitored database, not the history store. */
    @Qualifier("targetJdbcTemplate")
    private final JdbcTemplate jdbcTemplate;

    // ---------- reads for the API ----------

    /** The most recent snapshot, or empty if none has been collected yet. */
    @Transactional(readOnly = true)
    public Optional<DatabaseSnapshotEntity> latestSnapshot() {
        return databaseSnapshotRepo.findFirstByOrderByTimestampDesc();
    }

    /** The most recent {@code limit} snapshots, newest first. */
    @Transactional(readOnly = true)
    public List<DatabaseSnapshotEntity> recentSnapshots(int limit) {
        return databaseSnapshotRepo.findByOrderByTimestampDesc(PageRequest.of(0, limit));
    }

    // ---------- collection ----------

    @Scheduled(fixedRate = 60_000, initialDelay = 10_000)   // every 60s, wait 10s at startup
    public void capture() {
        DatabaseSnapshotEntity snapshot = new DatabaseSnapshotEntity();
        snapshot.setTimestamp(Instant.now());

        try {
            // 1. reachability + round-trip time of a trivial query
            long startNanos = System.nanoTime();
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            snapshot.setReachable(true);
            snapshot.setConnectLatencyMs((System.nanoTime() - startNanos) / 1_000_000);

            // 2. counters and gauges from SHOW GLOBAL STATUS (no special privilege needed)
            Map<String, String> status = readGlobalStatus();
            snapshot.setUptimeSeconds(longValue(status, "Uptime"));
            snapshot.setThreadsConnected((int) longValue(status, "Threads_connected"));
            snapshot.setThreadsRunning((int) longValue(status, "Threads_running"));
            snapshot.setMaxUsedConnections((int) longValue(status, "Max_used_connections"));
            snapshot.setQuestions(longValue(status, "Questions"));
            snapshot.setSlowQueries(longValue(status, "Slow_queries"));
            snapshot.setCreatedTmpDiskTables(longValue(status, "Created_tmp_disk_tables"));
            snapshot.setInnodbRowLockCurrentWaits((int) longValue(status, "Innodb_row_lock_current_waits"));
            snapshot.setInnodbBufferPoolReadRequests(longValue(status, "Innodb_buffer_pool_read_requests"));
            snapshot.setInnodbBufferPoolReads(longValue(status, "Innodb_buffer_pool_reads"));

            // 3. on-disk size of the connected database
            snapshot.setDatabaseSizeBytes(readDatabaseSizeBytes());

            // 4. locking detail that SHOW GLOBAL STATUS does not carry
            //    (both need PROCESS on the connection - demo_user now has it)
            snapshot.setInnodbDeadlocks(readLong(
                    "SELECT COUNT FROM information_schema.INNODB_METRICS WHERE NAME = 'lock_deadlocks'"));
            snapshot.setOldestTransactionAgeSeconds(readLong(
                    "SELECT COALESCE(TIMESTAMPDIFF(SECOND, MIN(trx_started), NOW()), 0) " +
                            "FROM information_schema.innodb_trx"));

        } catch (DataAccessException ex) {
            // the DB is unreachable or refused the query - still record a row saying so
            snapshot.setReachable(false);
            log.warn("Database unreachable while collecting snapshot: {}", ex.getMessage());
        }

        log.info("DB snapshot: reachable={} latency={}ms connected={} running={} deadlocks={} oldestTxAge={}s size={}MB",
                snapshot.isReachable(),
                snapshot.getConnectLatencyMs(),
                snapshot.getThreadsConnected(),
                snapshot.getThreadsRunning(),
                snapshot.getInnodbDeadlocks(),
                snapshot.getOldestTransactionAgeSeconds(),
                Math.round(snapshot.getDatabaseSizeBytes() / 1024.0 / 1024.0));
        writeBuffer.save(() -> databaseSnapshotRepo.save(snapshot));
    }

    /**
     * Total on-disk size (data + indexes) of the connected database, in bytes.
     */
    public long readDatabaseSizeBytes() {
        Long bytes = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(data_length + index_length), 0) " +
                        "FROM information_schema.tables " +
                        "WHERE table_schema = DATABASE()",
                Long.class);
        return bytes == null ? 0L : bytes;
    }

    /** A single scalar {@code long} from a query; 0 when the value is NULL or no row comes back. */
    private long readLong(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    /** Every row of SHOW GLOBAL STATUS as name -> value. Values stay text; some are non-numeric. */
    private Map<String, String> readGlobalStatus() {
        Map<String, String> status = new HashMap<>();
        jdbcTemplate.query("SHOW GLOBAL STATUS", rs -> {
            status.put(rs.getString(1), rs.getString(2));
        });
        return status;
    }

    /** One status value parsed as a long; 0 when missing or not a number. */
    private static long longValue(Map<String, String> status, String name) {
        String raw = status.get(name);
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}
