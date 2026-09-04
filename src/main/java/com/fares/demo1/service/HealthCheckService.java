package com.fares.demo1.service;

import com.fares.demo1.config.HealthCheckProperties;
import com.fares.demo1.model.DatabaseSnapshotEntity;
import com.fares.demo1.model.EventType;
import com.fares.demo1.model.HostSystemSnapshotEntity;
import com.fares.demo1.model.MonitorEventEntity;
import com.fares.demo1.model.Severity;
import com.fares.demo1.repo.DatabaseSnapshotRepo;
import com.fares.demo1.repo.HostSystemSnapshotRepo;
import com.fares.demo1.repo.MonitorEventRepo;
import com.fares.demo1.service.health.HealthContext;
import com.fares.demo1.service.health.HealthRule;
import com.fares.demo1.service.health.RuleResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every cycle: pull the recent snapshots each collector stored, hand them to every
 * {@link HealthRule}, and turn the breach flags a rule returns into
 * {@link MonitorEventEntity} rows.
 *
 * <p>This class never touches the monitored database - it only reads our own history.
 * The rules are pure; the open/resolve hysteresis and notifications live here.
 */
@Service
@Slf4j
public class HealthCheckService {

    private final DatabaseSnapshotRepo databaseSnapshotRepo;
    private final HostSystemSnapshotRepo hostSystemSnapshotRepo;
    private final ConfigSnapshotService configSnapshotService;
    private final MonitorEventRepo monitorEventRepo;
    private final Notifier notifier;
    private final HealthCheckProperties props;
    private final List<HealthRule> rules;

    public HealthCheckService(DatabaseSnapshotRepo databaseSnapshotRepo,
                              HostSystemSnapshotRepo hostSystemSnapshotRepo,
                              ConfigSnapshotService configSnapshotService,
                              MonitorEventRepo monitorEventRepo,
                              Notifier notifier,
                              HealthCheckProperties props,
                              List<HealthRule> rules) {
        this.databaseSnapshotRepo = databaseSnapshotRepo;
        this.hostSystemSnapshotRepo = hostSystemSnapshotRepo;
        this.configSnapshotService = configSnapshotService;
        this.monitorEventRepo = monitorEventRepo;
        this.notifier = notifier;
        this.props = props;
        this.rules = rules;
    }

    @Scheduled(fixedRate = 60_000, initialDelay = 20_000)   // just after the collectors' cycle
    public void evaluate() {
        try {
            int fetch = props.fetchCount();
            List<DatabaseSnapshotEntity> db = databaseSnapshotRepo
                    .findByOrderByTimestampDesc(PageRequest.of(0, fetch));
            List<HostSystemSnapshotEntity> host = hostSystemSnapshotRepo
                    .findByOrderByTimestampDesc(PageRequest.of(0, fetch));
            Map<String, String> configValues = configSnapshotService.latestSnapshot()
                    .map(configSnapshotService::valuesOf)
                    .orElse(Map.of());

            recordCollectionGaps(db);

            HealthContext ctx = new HealthContext(db, host, configValues, props);
            for (HealthRule rule : rules) {
                RuleResult result = rule.evaluate(ctx);
                if (!result.breaches().isEmpty()) {
                    applyHysteresis(rule.type(), result);
                }
            }
        } catch (Exception ex) {
            log.error("Health evaluation failed", ex);   // never let one bad cycle kill the scheduler
        }
    }

    /**
     * Look for stretches where no DB snapshot was stored - the service was down or
     * wedged. Each such gap is recorded once as a {@link EventType#COLLECTION_GAP}
     * event that is already resolved (it has both ends), so it shows up as history
     * rather than an active alert. Counters ({@code Questions}, {@code Slow_queries})
     * partially self-heal across a gap; gauges (connections, latency) do not.
     */
    private void recordCollectionGaps(List<DatabaseSnapshotEntity> db) {
        long limit = props.getCollectionGapSeconds();
        for (int i = 0; i < db.size() - 1; i++) {
            Instant end = db.get(i).getTimestamp();
            Instant start = db.get(i + 1).getTimestamp();
            long gapSeconds = Duration.between(start, end).getSeconds();
            if (gapSeconds <= limit || monitorEventRepo.existsByTypeAndOccurredAt(EventType.COLLECTION_GAP, start)) {
                continue;
            }
            MonitorEventEntity event = new MonitorEventEntity();
            event.setType(EventType.COLLECTION_GAP);
            event.setSeverity(Severity.WARNING);
            event.setMessage(String.format("no snapshots collected for %s (%s -> %s)",
                    humanDuration(gapSeconds), start, end));
            event.setMetricValue((double) gapSeconds);
            event.setOccurredAt(start);
            event.setLastSeenAt(end);
            event.setResolvedAt(end);   // the gap is already over
            monitorEventRepo.save(event);
            notifier.onOpened(event);
        }
    }

    private static String humanDuration(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return m > 0 ? m + "m " + s + "s" : s + "s";
    }

    /**
     * Open an event after {@code openAfter} consecutive breaches; resolve it after
     * {@code resolveAfter} consecutive clears. {@code result.breaches()} is newest-first.
     */
    private void applyHysteresis(EventType type, RuleResult result) {
        List<Boolean> breached = result.breaches();
        int openAfter = props.getOpenAfter();
        int resolveAfter = props.getResolveAfter();

        Optional<MonitorEventEntity> existing = monitorEventRepo.findFirstByTypeAndResolvedAtIsNull(type);
        boolean shouldOpen = breached.size() >= openAfter
                && breached.subList(0, openAfter).stream().allMatch(Boolean::booleanValue);
        boolean shouldResolve = breached.size() >= resolveAfter
                && breached.subList(0, resolveAfter).stream().noneMatch(Boolean::booleanValue);
        Instant now = Instant.now();

        if (existing.isEmpty()) {
            if (shouldOpen) {
                MonitorEventEntity event = new MonitorEventEntity();
                event.setType(type);
                event.setSeverity(result.severity());
                event.setMessage(result.message());
                event.setMetricValue(result.value());
                event.setOccurredAt(now);
                event.setLastSeenAt(now);
                monitorEventRepo.save(event);
                notifier.onOpened(event);
            }
            return;
        }

        MonitorEventEntity event = existing.get();
        if (shouldResolve) {
            event.setResolvedAt(now);
            monitorEventRepo.save(event);
            notifier.onResolved(event);
        } else if (breached.get(0)) {
            event.setLastSeenAt(now);
            event.setMetricValue(result.value());
            event.setMessage(result.message());
            if (result.severity().ordinal() > event.getSeverity().ordinal()) {   // e.g. warn -> crit
                event.setSeverity(result.severity());
            }
            monitorEventRepo.save(event);
        }
    }
}
