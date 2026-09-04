package com.fares.demo1.service;

import com.fares.demo1.model.ConfigSnapshotEntity;
import com.fares.demo1.model.ConfigValueSample;
import com.fares.demo1.repo.ConfigSnapshotRepo;
import com.fares.demo1.repo.ConfigValueSampleRepo;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Captures a fixed set of the target's GLOBAL VARIABLES as key/value rows, writing a
 * new snapshot only when a value changes - so the history is a list of config changes.
 * Adding a variable to {@link #TRACKED} is all it takes to track another one.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ConfigSnapshotService {

    /** The GLOBAL VARIABLES we keep an eye on: sizing, durability, replication, security. */
    private static final List<String> TRACKED = List.of(
            "max_connections", "wait_timeout", "interactive_timeout",
            "innodb_buffer_pool_size", "innodb_log_file_size", "innodb_io_capacity",
            "innodb_flush_log_at_trx_commit", "sync_binlog", "binlog_format", "log_bin",
            "read_only", "super_read_only",
            "slow_query_log", "long_query_time",
            "max_allowed_packet", "table_open_cache", "tmp_table_size",
            "local_infile", "require_secure_transport", "skip_name_resolve", "sql_mode");

    private final ConfigSnapshotRepo configSnapshotRepo;
    private final ConfigValueSampleRepo configValueSampleRepo;

    @Qualifier("targetJdbcTemplate")
    private final JdbcTemplate jdbcTemplate;

    // ---------- reads for the API ----------

    @Transactional(readOnly = true)
    public Optional<ConfigSnapshotEntity> latestSnapshot() {
        return configSnapshotRepo.findFirstByOrderByTimestampDesc();
    }

    @Transactional(readOnly = true)
    public List<ConfigSnapshotEntity> recentSnapshots(int limit) {
        return configSnapshotRepo.findByOrderByTimestampDesc(PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public Map<String, String> valuesOf(ConfigSnapshotEntity snapshot) {
        Map<String, String> map = new LinkedHashMap<>();
        for (ConfigValueSample v : configValueSampleRepo.findBySnapshotOrderByName(snapshot)) {
            map.put(v.getName(), v.getValue());
        }
        return map;
    }

    // ---------- collection ----------

    @Scheduled(fixedRate = 60_000, initialDelay = 15_000)   // every 60s, write only on change
    @Transactional
    public void capture() {
        Map<String, String> current;
        try {
            current = readTrackedVariables();
        } catch (DataAccessException ex) {
            log.warn("Config read from target failed: {}", ex.getMessage());
            return;
        }

        Optional<ConfigSnapshotEntity> latest = configSnapshotRepo.findFirstByOrderByTimestampDesc();
        Map<String, String> previous = latest.map(this::valuesOf).orElse(Map.of());
        if (current.equals(previous)) {
            return;   // nothing changed
        }

        ConfigSnapshotEntity header = new ConfigSnapshotEntity();
        header.setTimestamp(Instant.now());
        configSnapshotRepo.save(header);

        List<ConfigValueSample> rows = current.entrySet().stream().map(e -> {
            ConfigValueSample v = new ConfigValueSample();
            v.setSnapshot(header);
            v.setName(e.getKey());
            v.setValue(e.getValue());
            return v;
        }).toList();
        configValueSampleRepo.saveAll(rows);

        long changed = current.entrySet().stream()
                .filter(e -> !e.getValue().equals(previous.get(e.getKey())))
                .count();
        log.info("Config snapshot saved: {} tracked variables, {} changed", rows.size(), changed);
    }

    private Map<String, String> readTrackedVariables() {
        String inList = String.join(",", TRACKED.stream().map(n -> "'" + n + "'").toList());
        Map<String, String> map = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SHOW GLOBAL VARIABLES WHERE Variable_name IN (" + inList + ")",
                rs -> {
                    map.put(rs.getString(1), rs.getString(2));
                });
        return map;
    }
}
