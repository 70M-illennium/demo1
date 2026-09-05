package com.fares.demo1.controller;

import com.fares.demo1.config.EndpointPolicy;
import com.fares.demo1.config.EndpointPolicyRegistry;
import com.fares.demo1.config.HealthCheckProperties;
import com.fares.demo1.dto.UpdatePolicyRequest;
import com.fares.demo1.dto.UpdateThresholdsRequest;
import com.fares.demo1.service.ActivitySnapshotService;
import com.fares.demo1.service.ConfigSnapshotService;
import com.fares.demo1.service.DatabaseSnapshotService;
import com.fares.demo1.service.ErrorLogService;
import com.fares.demo1.service.HealthCheckService;
import com.fares.demo1.service.HostSystemSnapshotService;
import com.fares.demo1.service.RetentionService;
import com.fares.demo1.service.SecurityCheckService;
import com.fares.demo1.service.WorkloadSnapshotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Admin actions that bypass the normal schedule or config file - everything here needs
 * the {@code ADMIN} role (enforced by {@code SecurityConfig}'s catch-all rule for
 * non-GET {@code /api/**}, except the two GET endpoints below, which are plain reads).
 *
 * <pre>
 *   POST  /api/admin/collect          run every collector right now, then re-evaluate
 *   POST  /api/admin/purge            run the retention purge right now
 *   GET   /api/admin/thresholds       current HealthCheckProperties values
 *   PATCH /api/admin/thresholds       change one or more threshold values, no redeploy
 *   GET   /api/admin/policies         current per-metric cached/protected flags
 *   PATCH /api/admin/policies/{key}   change one metric's flags, no redeploy
 * </pre>
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin", description = "Admin-only actions that bypass the normal schedule or config file - needs the ADMIN role except the two GET endpoints")
public class AdminController {

    private final DatabaseSnapshotService databaseSnapshotService;
    private final HostSystemSnapshotService hostSystemSnapshotService;
    private final ActivitySnapshotService activitySnapshotService;
    private final ConfigSnapshotService configSnapshotService;
    private final WorkloadSnapshotService workloadSnapshotService;
    private final SecurityCheckService securityCheckService;
    private final ErrorLogService errorLogService;
    private final HealthCheckService healthCheckService;
    private final RetentionService retentionService;
    private final HealthCheckProperties healthCheckProperties;
    private final EndpointPolicyRegistry endpointPolicyRegistry;

    /**
     * Every method called here is exactly what {@code @Scheduled} already calls on its
     * own timer - this just calls it early, on request instead of waiting up to a
     * minute (or ten) for the next tick. Useful for demos, and for seeing a threshold
     * change take effect immediately instead of waiting for the next cycle.
     */
    @PostMapping("/collect")
    @Operation(summary = "Run every collector right now instead of waiting for the next scheduled cycle, then re-evaluate thresholds")
    public String collectNow() {
        databaseSnapshotService.capture();
        hostSystemSnapshotService.capture();
        activitySnapshotService.capture();
        configSnapshotService.capture();
        workloadSnapshotService.capture();
        workloadSnapshotService.captureTableSizes();
        securityCheckService.check();
        errorLogService.poll();
        healthCheckService.evaluate();   // re-check thresholds against the fresh data
        log.info("admin-triggered collection cycle complete");
        return "collection cycle complete";
    }

    @PostMapping("/purge")
    @Operation(summary = "Run the retention purge right now instead of waiting for its schedule")
    public String purgeNow() {
        retentionService.purge();
        log.info("admin-triggered retention purge complete");
        return "retention purge complete";
    }

    @GetMapping("/thresholds")
    @Operation(summary = "Get the current health-check threshold values")
    public HealthCheckProperties currentThresholds() {
        // Returning the live @ConfigurationProperties bean directly is fine here - it's
        // a plain POJO of numbers (not an entity, nothing lazy, nothing sensitive), so
        // there's no reason to hand-write a DTO that would just repeat every field.
        return healthCheckProperties;
    }

    /**
     * Mutates the live {@link HealthCheckProperties} bean in place. That's enough for
     * the change to take effect on the very next {@code HealthCheckService.evaluate()}
     * cycle with no restart and no Spring Cloud "refresh" machinery: the properties
     * bean is an ordinary singleton, and {@code HealthCheckService} already holds a
     * reference to this exact same object - a field write here is visible to it the
     * next time it reads that field, the same way any two collaborators sharing a
     * mutable object see each other's changes.
     *
     * <p>Not persisted anywhere: a restart reverts to whatever {@code
     * application.properties} says. That's intentional - this is "tune it live for
     * now", not a replacement for the config file.
     *
     * <p>This bean is shared by every client of this one running instance - there is no
     * per-admin or per-session copy - so {@link #validate} runs against the resulting
     * state fully <em>before</em> a single setter is called. Two reasons: (1) a bad
     * value here doesn't just look wrong in a response, it can silently disable an
     * entire alert type for everyone watching this monitor (e.g. {@code openAfter <= 0}
     * used to make {@code HealthCheckService.applyHysteresis}'s {@code
     * breached.subList(0, openAfter)} either throw or vacuously open every rule - the
     * exception then aborted the whole evaluation loop for every other rule too, since
     * one try/catch wraps the entire cycle); (2) applying fields one at a time as they
     * pass validation would leave the shared bean in a half-updated state the moment a
     * later field in the same request turns out invalid.
     */
    @PatchMapping("/thresholds")
    @Operation(summary = "Change one or more health-check thresholds live, no redeploy needed")
    public HealthCheckProperties updateThresholds(@RequestBody UpdateThresholdsRequest body) {
        validate(body);

        if (body.diskWarnPercent() != null) {
            healthCheckProperties.setDiskWarnPercent(body.diskWarnPercent());
        }
        if (body.diskCritPercent() != null) {
            healthCheckProperties.setDiskCritPercent(body.diskCritPercent());
        }
        if (body.longTransactionSeconds() != null) {
            healthCheckProperties.setLongTransactionSeconds(body.longTransactionSeconds());
        }
        if (body.slowQueryRatePerMin() != null) {
            healthCheckProperties.setSlowQueryRatePerMin(body.slowQueryRatePerMin());
        }
        if (body.tmpDiskTableRatePerMin() != null) {
            healthCheckProperties.setTmpDiskTableRatePerMin(body.tmpDiskTableRatePerMin());
        }
        if (body.bufferPoolHitRatioWarnPercent() != null) {
            healthCheckProperties.setBufferPoolHitRatioWarnPercent(body.bufferPoolHitRatioWarnPercent());
        }
        if (body.bufferPoolHitRatioCritPercent() != null) {
            healthCheckProperties.setBufferPoolHitRatioCritPercent(body.bufferPoolHitRatioCritPercent());
        }
        if (body.connectionWarnPercent() != null) {
            healthCheckProperties.setConnectionWarnPercent(body.connectionWarnPercent());
        }
        if (body.connectionCritPercent() != null) {
            healthCheckProperties.setConnectionCritPercent(body.connectionCritPercent());
        }
        if (body.latencySigmaK() != null) {
            healthCheckProperties.setLatencySigmaK(body.latencySigmaK());
        }
        if (body.collectionGapSeconds() != null) {
            healthCheckProperties.setCollectionGapSeconds(body.collectionGapSeconds());
        }
        if (body.baselineWindow() != null) {
            healthCheckProperties.setBaselineWindow(body.baselineWindow());
        }
        if (body.openAfter() != null) {
            healthCheckProperties.setOpenAfter(body.openAfter());
        }
        if (body.resolveAfter() != null) {
            healthCheckProperties.setResolveAfter(body.resolveAfter());
        }
        log.info("thresholds updated by admin: {}", body);
        return healthCheckProperties;
    }

    /**
     * Every metric key and its current {@code cached}/{@code protectedAccess} flags.
     * <b>Step 1 only</b> - nothing reads these flags yet, so changing them here doesn't
     * actually cache or protect anything until the cache helper (step 2) and the
     * dynamic auth check (step 3) are wired up to consult this registry.
     */
    @GetMapping("/policies")
    @Operation(summary = "Get the current cache/protection policy flags for every metric key")
    public Map<String, EndpointPolicy> policies() {
        return endpointPolicyRegistry.all();
    }

    /**
     * Partial update for one metric key. Same validate-then-apply shape as {@link
     * #updateThresholds}: the merged result (existing flag if the request didn't send
     * one, the new value if it did) is checked before anything is written, so a request
     * either fully applies or is fully rejected - never half-applied to the one shared
     * registry every client reads.
     */
    @PatchMapping("/policies/{key}")
    @Operation(summary = "Change one metric's cache/protection flags live, no redeploy needed")
    public EndpointPolicy updatePolicy(@PathVariable String key, @RequestBody UpdatePolicyRequest body) {
        if (!endpointPolicyRegistry.exists(key)) {
            throw new EntityNotFoundException("no such metric key: " + key);
        }
        EndpointPolicy current = endpointPolicyRegistry.get(key);
        boolean cached = body.cached() != null ? body.cached() : current.cached();
        boolean protectedAccess = body.protectedAccess() != null ? body.protectedAccess() : current.protectedAccess();

        if (!protectedAccess && EndpointPolicyRegistry.isAlwaysProtected(key)) {
            throw new IllegalArgumentException(
                    "invalid policy update: '" + key + "' can never be set protectedAccess=false");
        }

        EndpointPolicy updated = new EndpointPolicy(cached, protectedAccess);
        endpointPolicyRegistry.update(key, updated);
        log.info("policy for '{}' updated by admin: {}", key, updated);
        return updated;
    }

    /**
     * Rejects the whole request if any field is out of a sane range, or if a warn/crit
     * pair would end up inverted. Cross-field checks compare against the
     * <em>effective</em> value - the request's value if it supplied one, else whatever
     * is already live - because a partial PATCH can legally touch only one side of a
     * pair (e.g. only {@code diskCritPercent}), and the resulting combination still has
     * to make sense.
     */
    private void validate(UpdateThresholdsRequest body) {
        List<String> violations = new ArrayList<>();

        checkPercent(body.diskWarnPercent(), "diskWarnPercent", violations);
        checkPercent(body.diskCritPercent(), "diskCritPercent", violations);
        checkPercent(body.bufferPoolHitRatioWarnPercent(), "bufferPoolHitRatioWarnPercent", violations);
        checkPercent(body.bufferPoolHitRatioCritPercent(), "bufferPoolHitRatioCritPercent", violations);
        checkPercent(body.connectionWarnPercent(), "connectionWarnPercent", violations);
        checkPercent(body.connectionCritPercent(), "connectionCritPercent", violations);

        checkPositive(body.longTransactionSeconds(), "longTransactionSeconds", violations);
        checkPositive(body.slowQueryRatePerMin(), "slowQueryRatePerMin", violations);
        checkPositive(body.tmpDiskTableRatePerMin(), "tmpDiskTableRatePerMin", violations);
        checkPositive(body.collectionGapSeconds(), "collectionGapSeconds", violations);
        checkPositive(body.latencySigmaK(), "latencySigmaK", violations);

        // baselineWindow feeds HealthCheckProperties.fetchCount(), which sizes the
        // PageRequest every collector-history read uses each cycle - an unbounded value
        // here would mean an unbounded query against the store on every evaluation.
        checkRange(body.baselineWindow(), "baselineWindow", 2, 500, violations);
        checkRange(body.openAfter(), "openAfter", 1, 50, violations);
        checkRange(body.resolveAfter(), "resolveAfter", 1, 50, violations);

        double diskWarn = orElse(body.diskWarnPercent(), healthCheckProperties.getDiskWarnPercent());
        double diskCrit = orElse(body.diskCritPercent(), healthCheckProperties.getDiskCritPercent());
        if (diskWarn > diskCrit) {
            violations.add("diskWarnPercent (%.1f) must be <= diskCritPercent (%.1f)".formatted(diskWarn, diskCrit));
        }

        double connWarn = orElse(body.connectionWarnPercent(), healthCheckProperties.getConnectionWarnPercent());
        double connCrit = orElse(body.connectionCritPercent(), healthCheckProperties.getConnectionCritPercent());
        if (connWarn > connCrit) {
            violations.add("connectionWarnPercent (%.1f) must be <= connectionCritPercent (%.1f)"
                    .formatted(connWarn, connCrit));
        }

        // Inverted on purpose: hit ratio is "higher is healthier", so the more severe
        // (CRITICAL) bar has to be the *lower* number - the opposite ordering from the
        // two checks above.
        double bpWarn = orElse(body.bufferPoolHitRatioWarnPercent(), healthCheckProperties.getBufferPoolHitRatioWarnPercent());
        double bpCrit = orElse(body.bufferPoolHitRatioCritPercent(), healthCheckProperties.getBufferPoolHitRatioCritPercent());
        if (bpCrit > bpWarn) {
            violations.add("bufferPoolHitRatioCritPercent (%.1f) must be <= bufferPoolHitRatioWarnPercent (%.1f)"
                    .formatted(bpCrit, bpWarn));
        }

        if (!violations.isEmpty()) {
            throw new IllegalArgumentException("invalid threshold update: " + String.join("; ", violations));
        }
    }

    private static void checkPercent(Double value, String name, List<String> out) {
        if (value != null && (value < 0.0 || value > 100.0)) {
            out.add(name + " (" + value + ") must be between 0 and 100");
        }
    }

    private static void checkPositive(Double value, String name, List<String> out) {
        if (value != null && value <= 0.0) {
            out.add(name + " (" + value + ") must be > 0");
        }
    }

    private static void checkPositive(Long value, String name, List<String> out) {
        if (value != null && value <= 0) {
            out.add(name + " (" + value + ") must be > 0");
        }
    }

    private static void checkRange(Integer value, String name, int min, int max, List<String> out) {
        if (value != null && (value < min || value > max)) {
            out.add(name + " (" + value + ") must be between " + min + " and " + max);
        }
    }

    private static double orElse(Double provided, double current) {
        return provided != null ? provided : current;
    }
}
