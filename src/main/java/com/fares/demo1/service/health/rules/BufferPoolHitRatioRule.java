package com.fares.demo1.service.health.rules;

import com.fares.demo1.model.DatabaseSnapshotEntity;
import com.fares.demo1.model.EventType;
import com.fares.demo1.model.Severity;
import com.fares.demo1.service.health.HealthContext;
import com.fares.demo1.service.health.HealthRule;
import com.fares.demo1.service.health.RuleResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Interval-delta: buffer-pool hit ratio from the read-counter deltas over each interval,
 * not their lifetime totals - a lifetime ratio stays near 100% and hides a recent drop.
 */
@Component
public class BufferPoolHitRatioRule implements HealthRule {

    @Override
    public EventType type() {
        return EventType.BUFFER_POOL_HIT_RATIO_LOW;
    }

    @Override
    public RuleResult evaluate(HealthContext ctx) {
        List<DatabaseSnapshotEntity> db = ctx.db();
        if (db.size() < 2) {
            return RuleResult.none();
        }
        double warn = ctx.props().getBufferPoolHitRatioWarnPercent();
        double crit = ctx.props().getBufferPoolHitRatioCritPercent();

        List<Boolean> breaches = new ArrayList<>();
        double latestRatio = 100.0;
        for (int i = 0; i < db.size() - 1; i++) {
            long requests = db.get(i).getInnodbBufferPoolReadRequests()
                    - db.get(i + 1).getInnodbBufferPoolReadRequests();
            long fromDisk = db.get(i).getInnodbBufferPoolReads()
                    - db.get(i + 1).getInnodbBufferPoolReads();
            if (requests <= 0 || fromDisk < 0) {
                breaches.add(false);   // idle interval or counter reset - nothing to judge
                if (i == 0) {
                    latestRatio = 100.0;
                }
                continue;
            }
            double ratio = (1.0 - (double) fromDisk / requests) * 100.0;
            if (i == 0) {
                latestRatio = ratio;
            }
            breaches.add(ratio < warn);
        }
        return RuleResult.of(breaches,
                latestRatio < crit ? Severity.CRITICAL : Severity.WARNING,
                String.format("buffer-pool hit ratio %.2f%% over the last interval (warn %.0f%%)",
                        latestRatio, warn),
                latestRatio);
    }
}
