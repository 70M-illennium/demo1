package com.fares.demo1.service;

import com.fares.demo1.model.SecurityFinding;
import com.fares.demo1.model.Severity;
import com.fares.demo1.repo.SecurityFindingRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a handful of read-only security-posture checks against the target and stores
 * whatever they flag as {@link SecurityFinding} rows for the cycle. Slow cadence -
 * these are config- and account-level, they do not change minute to minute.
 *
 * <p>Needs {@code SELECT} on {@code mysql.user} for the account checks (granted in
 * {@code mysql-init.sql}); the config checks only need {@code SELECT} on
 * {@code performance_schema} / the session.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SecurityCheckService {

    private static final Duration LATEST_CACHE_TTL = Duration.ofMinutes(10);   // matches the check() cadence

    private final SecurityFindingRepo securityFindingRepo;
    private final SnapshotWriteBuffer writeBuffer;
    private final MetricCache metricCache;

    @Qualifier("targetJdbcTemplate")
    private final JdbcTemplate jdbcTemplate;

    // ---------- reads for the API ----------

    /** Every finding from the most recent check cycle. Goes through {@link MetricCache} under {@code "security.findings"}. */
    @Transactional(readOnly = true)
    public List<SecurityFinding> latestFindings() {
        return metricCache.getOrLoad("security.findings", LATEST_CACHE_TTL, securityFindingRepo::findLatestCycle);
    }

    // ---------- collection ----------

    @Scheduled(fixedRate = 600_000, initialDelay = 35_000)   // every 10 min
    public void check() {
        Instant now = Instant.now();
        List<SecurityFinding> findings = new ArrayList<>();
        try {
            checkGlobalSettings(now, findings);
            checkAccounts(now, findings);
        } catch (DataAccessException ex) {
            log.warn("Security check skipped - target unreachable: {}", ex.getMessage());
            return;
        }

        log.info("Security check: {} finding(s)", findings.size());
        if (!findings.isEmpty()) {
            writeBuffer.save(() -> securityFindingRepo.saveAll(findings));
        }
    }

    // ---------- checks ----------

    private void checkGlobalSettings(Instant now, List<SecurityFinding> out) {
        if (booleanVar("require_secure_transport") == Boolean.FALSE) {
            out.add(finding(now, "WEAK_CONFIG", Severity.WARNING,
                    "require_secure_transport is OFF - clients may connect without TLS"));
        }
        if (booleanVar("local_infile") == Boolean.TRUE) {
            out.add(finding(now, "WEAK_CONFIG", Severity.WARNING,
                    "local_infile is ON - LOAD DATA LOCAL can read files from a connecting client"));
        }
        if (booleanVar("skip_name_resolve") == Boolean.FALSE) {
            out.add(finding(now, "WEAK_CONFIG", Severity.INFO,
                    "skip_name_resolve is OFF - host-name grants trigger reverse DNS on each connect"));
        }
        String sqlMode = stringVar("sql_mode");
        if (sqlMode != null && !sqlMode.contains("STRICT_TRANS_TABLES") && !sqlMode.contains("STRICT_ALL_TABLES")) {
            out.add(finding(now, "WEAK_CONFIG", Severity.WARNING,
                    "sql_mode has no STRICT mode - invalid values are silently coerced"));
        }
        boolean passwordValidation = !jdbcTemplate.queryForList(
                "SHOW GLOBAL VARIABLES LIKE 'validate_password%'").isEmpty();
        if (!passwordValidation) {
            out.add(finding(now, "WEAK_CONFIG", Severity.WARNING,
                    "no password-validation component/plugin installed - weak passwords are accepted"));
        }
    }

    private void checkAccounts(Instant now, List<SecurityFinding> out) {
        List<String[]> noPassword = jdbcTemplate.query(
                "SELECT user, host FROM mysql.user "
                        + "WHERE authentication_string = '' AND user NOT LIKE 'mysql.%'",
                (rs, i) -> new String[]{rs.getString(1), rs.getString(2)});
        for (String[] u : noPassword) {
            out.add(finding(now, "ACCOUNT_NO_PASSWORD", Severity.CRITICAL,
                    "account '" + u[0] + "'@'" + u[1] + "' has no password"));
        }

        List<String[]> wildcardHost = jdbcTemplate.query(
                "SELECT user, host FROM mysql.user "
                        + "WHERE host = '%' AND user NOT LIKE 'mysql.%'",
                (rs, i) -> new String[]{rs.getString(1), rs.getString(2)});
        for (String[] u : wildcardHost) {
            out.add(finding(now, "ACCOUNT_WILDCARD_HOST", Severity.WARNING,
                    "account '" + u[0] + "'@'%' can connect from any host"));
        }
    }

    // ---------- helpers ----------

    private String stringVar(String name) {
        List<String> v = jdbcTemplate.query(
                "SHOW GLOBAL VARIABLES LIKE ?", (rs, i) -> rs.getString(2), name);
        return v.isEmpty() ? null : v.get(0);
    }

    /** ON/1/YES -> true, OFF/0/NO -> false, missing -> null. */
    private Boolean booleanVar(String name) {
        String v = stringVar(name);
        if (v == null) {
            return null;
        }
        return v.equalsIgnoreCase("ON") || v.equals("1") || v.equalsIgnoreCase("YES");
    }

    private static SecurityFinding finding(Instant now, String category, Severity severity, String detail) {
        SecurityFinding f = new SecurityFinding();
        f.setCapturedAt(now);
        f.setCategory(category);
        f.setSeverity(severity);
        f.setDetail(detail);
        return f;
    }
}
