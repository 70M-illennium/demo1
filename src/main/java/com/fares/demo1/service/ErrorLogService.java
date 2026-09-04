package com.fares.demo1.service;

import com.fares.demo1.model.EventType;
import com.fares.demo1.model.MonitorEventEntity;
import com.fares.demo1.model.Severity;
import com.fares.demo1.repo.MonitorEventRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Turns Error / Warning lines from the target's {@code performance_schema.error_log}
 * into {@link EventType#TARGET_ERROR_LOG} events - each is point-in-time (already
 * resolved) and deduped by its log timestamp. Only the last couple of hours are
 * scanned each poll, so the log is never re-read whole.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ErrorLogService {

    private static final Duration LOOKBACK = Duration.ofHours(2);

    private final MonitorEventRepo monitorEventRepo;
    private final Notifier notifier;

    @Qualifier("targetJdbcTemplate")
    private final JdbcTemplate jdbcTemplate;

    @Scheduled(fixedRate = 180_000, initialDelay = 25_000)   // every 3 min
    public void poll() {
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                    "SELECT LOGGED, PRIO, ERROR_CODE, SUBSYSTEM, DATA "
                            + "FROM performance_schema.error_log "
                            + "WHERE PRIO IN ('Error', 'Warning') AND LOGGED > ? "
                            + "ORDER BY LOGGED",
                    Timestamp.from(Instant.now().minus(LOOKBACK)));
        } catch (DataAccessException ex) {
            log.warn("error_log poll skipped - target unreachable: {}", ex.getMessage());
            return;
        }

        int recorded = 0;
        for (Map<String, Object> row : rows) {
            Instant logged = ((Timestamp) row.get("LOGGED")).toInstant();
            if (monitorEventRepo.existsByTypeAndOccurredAt(EventType.TARGET_ERROR_LOG, logged)) {
                continue;
            }
            boolean isError = "Error".equalsIgnoreCase(String.valueOf(row.get("PRIO")));

            MonitorEventEntity event = new MonitorEventEntity();
            event.setType(EventType.TARGET_ERROR_LOG);
            event.setSeverity(isError ? Severity.CRITICAL : Severity.WARNING);
            event.setMessage(String.format("[%s/%s] %s",
                    row.get("ERROR_CODE"), row.get("SUBSYSTEM"), row.get("DATA")));
            event.setOccurredAt(logged);
            event.setLastSeenAt(logged);
            event.setResolvedAt(logged);   // a log line is a moment, not an ongoing state
            monitorEventRepo.save(event);
            notifier.onOpened(event);
            recorded++;
        }
        if (recorded > 0) {
            log.info("error_log: {} new Error/Warning line(s) recorded", recorded);
        }
    }
}
