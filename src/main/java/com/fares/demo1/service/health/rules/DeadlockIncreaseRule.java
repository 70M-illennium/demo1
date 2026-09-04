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

/** Counter delta: one or more new InnoDB deadlocks since the previous snapshot. */
@Component
public class DeadlockIncreaseRule implements HealthRule {

    @Override
    public EventType type() {
        return EventType.DEADLOCK_INCREASE;
    }

    @Override
    public RuleResult evaluate(HealthContext ctx) {
        List<DatabaseSnapshotEntity> db = ctx.db();
        if (db.size() < 2) {
            return RuleResult.none();
        }
        List<Boolean> breaches = new ArrayList<>();
        long latestDelta = 0;
        for (int i = 0; i < db.size() - 1; i++) {
            long delta = db.get(i).getInnodbDeadlocks() - db.get(i + 1).getInnodbDeadlocks();
            if (i == 0) {
                latestDelta = Math.max(delta, 0);
            }
            breaches.add(delta > 0);
        }
        return RuleResult.of(breaches, Severity.WARNING,
                latestDelta + " new deadlock(s) since the previous snapshot",
                (double) latestDelta);
    }
}
