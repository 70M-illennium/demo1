package com.fares.demo1.service;

import com.fares.demo1.model.QueryDigestSample;
import com.fares.demo1.model.SessionSample;
import com.fares.demo1.model.TableSizeSample;
import com.fares.demo1.model.WaitSample;
import com.fares.demo1.repo.QueryDigestSampleRepo;
import com.fares.demo1.repo.SessionSampleRepo;
import com.fares.demo1.repo.TableSizeSampleRepo;
import com.fares.demo1.repo.WaitSampleRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Per-cycle "workload" detail from the target's {@code performance_schema} /
 * {@code information_schema}: the top query digests, the live sessions, and the top
 * wait events. Each produces many rows per cycle (unlike the one-row snapshots), so
 * these are their own tables, grouped by {@code capturedAt} and pruned by retention.
 *
 * <p>Reads only - the target user needs {@code SELECT} on {@code performance_schema}
 * and {@code PROCESS} (both granted in {@code mysql-init.sql}).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WorkloadSnapshotService {

    private static final int TOP_DIGESTS = 25;
    private static final int TOP_WAITS = 15;

    // queries/sessions/waits come from capture() (60s cadence); tables from
    // captureTableSizes() (10 min cadence) - each cache TTL matches its own collector.
    private static final Duration CAPTURE_CACHE_TTL = Duration.ofSeconds(60);
    private static final Duration TABLE_SIZE_CACHE_TTL = Duration.ofMinutes(10);

    private final QueryDigestSampleRepo queryDigestSampleRepo;
    private final SessionSampleRepo sessionSampleRepo;
    private final WaitSampleRepo waitSampleRepo;
    private final TableSizeSampleRepo tableSizeSampleRepo;
    private final SnapshotWriteBuffer writeBuffer;
    private final MetricCache metricCache;

    @Qualifier("targetJdbcTemplate")
    private final JdbcTemplate jdbcTemplate;

    // ---------- reads for the API ----------

    /**
     * The top query digests from the most recent cycle. Goes through {@link
     * MetricCache} under {@code "workload.queries"}.
     */
    @Transactional(readOnly = true)
    public List<QueryDigestSample> latestDigests() {
        return metricCache.getOrLoad("workload.queries", CAPTURE_CACHE_TTL, queryDigestSampleRepo::findLatestCycle);
    }

    /**
     * Live (non-idle) sessions from the most recent cycle. Goes through {@link
     * MetricCache} under {@code "workload.sessions"} - but that key defaults to
     * {@code cached=false} in {@code EndpointPolicyRegistry} (live activity, a stale
     * answer defeats the point), so in practice this bypasses the cache unless an
     * admin explicitly turns it on.
     */
    @Transactional(readOnly = true)
    public List<SessionSample> latestSessions() {
        return metricCache.getOrLoad("workload.sessions", CAPTURE_CACHE_TTL, sessionSampleRepo::findLatestCycle);
    }

    /**
     * Live (non-idle) sessions read directly from the target right now, not from
     * history. Unlike {@link #latestSessions()}, which returns whatever the last
     * 60-second scheduled {@link #capture()} caught, this runs the same {@code
     * PROCESSLIST} query at call time - not cached, not persisted, since it's a
     * point-in-time answer to "what's running right now", not a snapshot worth keeping.
     */
    public List<SessionSample> currentActiveQueries() {
        return readSessions(Instant.now());
    }

    /**
     * The top wait events from the most recent cycle. Goes through {@link MetricCache}
     * under {@code "workload.waits"}.
     */
    @Transactional(readOnly = true)
    public List<WaitSample> latestWaits() {
        return metricCache.getOrLoad("workload.waits", CAPTURE_CACHE_TTL, waitSampleRepo::findLatestCycle);
    }

    /**
     * Per-table sizes from the most recent (10-minute) cycle. Goes through {@link
     * MetricCache} under {@code "workload.tables"}.
     */
    @Transactional(readOnly = true)
    public List<TableSizeSample> latestTableSizes() {
        return metricCache.getOrLoad("workload.tables", TABLE_SIZE_CACHE_TTL, tableSizeSampleRepo::findLatestCycle);
    }

    // ---------- collection ----------

    @Scheduled(fixedRate = 60_000, initialDelay = 12_000)   // 60s, offset from the other collectors
    public void capture() {
        Instant now = Instant.now();
        List<QueryDigestSample> digests;
        List<SessionSample> sessions;
        List<WaitSample> waits;
        try {
            digests = readDigests(now);
            sessions = readSessions(now);
            waits = readWaits(now);
        } catch (DataAccessException ex) {
            log.warn("Workload collection skipped - target unreachable: {}", ex.getMessage());
            return;
        }

        log.info("Workload snapshot: {} digests, {} sessions, {} waits",
                digests.size(), sessions.size(), waits.size());
        writeBuffer.save(() -> queryDigestSampleRepo.saveAll(digests));
        writeBuffer.save(() -> sessionSampleRepo.saveAll(sessions));
        writeBuffer.save(() -> waitSampleRepo.saveAll(waits));
    }

    /**
     * Per-table sizes on a slow cadence - they move slowly and
     * {@code information_schema.tables} can be expensive on servers with many tables.
     */
    @Scheduled(fixedRate = 600_000, initialDelay = 30_000)   // every 10 min
    public void captureTableSizes() {
        Instant now = Instant.now();
        List<TableSizeSample> tables;
        try {
            tables = readTableSizes(now);
        } catch (DataAccessException ex) {
            log.warn("Table-size collection skipped - target unreachable: {}", ex.getMessage());
            return;
        }
        log.info("Table-size snapshot: {} tables", tables.size());
        writeBuffer.save(() -> tableSizeSampleRepo.saveAll(tables));
    }

    private List<TableSizeSample> readTableSizes(Instant capturedAt) {
        RowMapper<TableSizeSample> mapper = (rs, i) -> {
            TableSizeSample s = new TableSizeSample();
            s.setCapturedAt(capturedAt);
            s.setSchemaName(rs.getString("TABLE_SCHEMA"));
            s.setTableName(rs.getString("TABLE_NAME"));
            s.setEngine(rs.getString("ENGINE"));
            s.setRowsEstimate(rs.getLong("TABLE_ROWS"));
            s.setDataBytes(rs.getLong("DATA_LENGTH"));
            s.setIndexBytes(rs.getLong("INDEX_LENGTH"));
            s.setDataFreeBytes(rs.getLong("DATA_FREE"));
            return s;
        };
        return jdbcTemplate.query(
                "SELECT TABLE_SCHEMA, TABLE_NAME, ENGINE, "
                        + "COALESCE(TABLE_ROWS, 0) AS TABLE_ROWS, "
                        + "COALESCE(DATA_LENGTH, 0) AS DATA_LENGTH, "
                        + "COALESCE(INDEX_LENGTH, 0) AS INDEX_LENGTH, "
                        + "COALESCE(DATA_FREE, 0) AS DATA_FREE "
                        + "FROM information_schema.tables "
                        + "WHERE TABLE_TYPE = 'BASE TABLE' AND TABLE_SCHEMA NOT IN "
                        + "('information_schema', 'performance_schema', 'mysql', 'sys') "
                        + "ORDER BY DATA_LENGTH + INDEX_LENGTH DESC",
                mapper);
    }

    // ---------- query digests ----------

    private List<QueryDigestSample> readDigests(Instant capturedAt) {
        RowMapper<QueryDigestSample> mapper = (rs, i) -> {
            QueryDigestSample s = new QueryDigestSample();
            s.setCapturedAt(capturedAt);
            s.setSchemaName(rs.getString("SCHEMA_NAME"));
            s.setDigest(rs.getString("DIGEST"));
            s.setDigestText(rs.getString("DIGEST_TEXT"));
            s.setExecCount(rs.getLong("COUNT_STAR"));
            s.setTotalLatencyMs(rs.getDouble("total_ms"));
            s.setAvgLatencyMs(rs.getDouble("avg_ms"));
            s.setRowsExamined(rs.getLong("SUM_ROWS_EXAMINED"));
            s.setRowsSent(rs.getLong("SUM_ROWS_SENT"));
            s.setFullScans(rs.getLong("SUM_NO_INDEX_USED"));
            s.setTmpDiskTables(rs.getLong("SUM_CREATED_TMP_DISK_TABLES"));
            return s;
        };
        return jdbcTemplate.query(
                "SELECT SCHEMA_NAME, DIGEST, DIGEST_TEXT, COUNT_STAR, "
                        + "SUM_TIMER_WAIT / 1e9 AS total_ms, AVG_TIMER_WAIT / 1e9 AS avg_ms, "
                        + "SUM_ROWS_EXAMINED, SUM_ROWS_SENT, SUM_NO_INDEX_USED, SUM_CREATED_TMP_DISK_TABLES "
                        + "FROM performance_schema.events_statements_summary_by_digest "
                        + "WHERE DIGEST_TEXT IS NOT NULL "
                        + "ORDER BY SUM_TIMER_WAIT DESC LIMIT " + TOP_DIGESTS,
                mapper);
    }

    // ---------- live sessions ----------

    private List<SessionSample> readSessions(Instant capturedAt) {
        RowMapper<SessionSample> mapper = (rs, i) -> {
            SessionSample s = new SessionSample();
            s.setCapturedAt(capturedAt);
            s.setConnectionId(rs.getLong("ID"));
            s.setUser(rs.getString("USER"));
            s.setHost(rs.getString("HOST"));
            s.setDb(rs.getString("DB"));
            s.setCommand(rs.getString("COMMAND"));
            s.setTimeSeconds(rs.getInt("TIME"));
            s.setState(rs.getString("STATE"));
            String info = rs.getString("INFO");
            s.setCurrentSql(info == null ? null : info.substring(0, Math.min(info.length(), 512)));
            return s;
        };
        return jdbcTemplate.query(
                "SELECT ID, USER, HOST, DB, COMMAND, TIME, STATE, INFO "
                        + "FROM information_schema.PROCESSLIST "
                        + "WHERE COMMAND NOT IN ('Sleep', 'Daemon', 'Connect') AND ID <> CONNECTION_ID() "
                        + "ORDER BY TIME DESC",
                mapper);
    }

    // ---------- wait events ----------

    private List<WaitSample> readWaits(Instant capturedAt) {
        RowMapper<WaitSample> mapper = (rs, i) -> {
            WaitSample s = new WaitSample();
            s.setCapturedAt(capturedAt);
            s.setEventName(rs.getString("EVENT_NAME"));
            s.setCount(rs.getLong("COUNT_STAR"));
            s.setTotalWaitMs(rs.getDouble("total_ms"));
            s.setAvgWaitMs(rs.getDouble("avg_ms"));
            return s;
        };
        return jdbcTemplate.query(
                "SELECT EVENT_NAME, COUNT_STAR, "
                        + "SUM_TIMER_WAIT / 1e9 AS total_ms, AVG_TIMER_WAIT / 1e9 AS avg_ms "
                        + "FROM performance_schema.events_waits_summary_global_by_event_name "
                        + "WHERE COUNT_STAR > 0 AND EVENT_NAME <> 'idle' "
                        + "ORDER BY SUM_TIMER_WAIT DESC LIMIT " + TOP_WAITS,
                mapper);
    }
}
