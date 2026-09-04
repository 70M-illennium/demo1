package com.fares.demo1.service.health.rules;

import com.fares.demo1.model.DatabaseSnapshotEntity;
import com.fares.demo1.model.EventType;
import com.fares.demo1.model.Severity;
import com.fares.demo1.service.health.HealthContext;
import com.fares.demo1.service.health.HealthMaths;
import com.fares.demo1.service.health.HealthRule;
import com.fares.demo1.service.health.RuleResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Deviation: flag connect latency that sits far outside its own recent baseline. Each
 * of the last few snapshots is judged against the {@code baselineWindow} snapshots that
 * preceded it, so hysteresis still has a per-snapshot flag. No-op until there is a full
 * baseline plus a few points on top.
 */
@Component
public class ConnectLatencyAnomalyRule implements HealthRule {

    @Override
    public EventType type() {
        return EventType.CONNECT_LATENCY_ANOMALY;
    }

    @Override
    public RuleResult evaluate(HealthContext ctx) {
        List<DatabaseSnapshotEntity> db = ctx.db();
        int window = ctx.props().getBaselineWindow();
        int resolveAfter = ctx.props().getResolveAfter();

        List<Boolean> breaches = new ArrayList<>();
        double latestValue = 0.0;
        double latestLimit = 0.0;
        for (int i = 0; i < resolveAfter && i + 1 + window <= db.size(); i++) {
            double value = db.get(i).getConnectLatencyMs();
            double[] baseline = db.subList(i + 1, i + 1 + window).stream()
                    .mapToDouble(DatabaseSnapshotEntity::getConnectLatencyMs).toArray();
            double mean = HealthMaths.mean(baseline);
            double limit = mean + ctx.props().getLatencySigmaK() * HealthMaths.stddev(baseline, mean);
            if (i == 0) {
                latestValue = value;
                latestLimit = limit;
            }
            breaches.add(limit > 0 && value > limit && value > 1.0);   // ignore sub-ms noise
        }
        if (breaches.isEmpty()) {
            return RuleResult.none();
        }
        return RuleResult.of(breaches, Severity.WARNING,
                String.format("connect latency %.0fms, recent baseline suggests <%.0fms",
                        latestValue, latestLimit),
                latestValue);
    }
}
