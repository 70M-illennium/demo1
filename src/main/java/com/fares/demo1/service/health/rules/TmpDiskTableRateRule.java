package com.fares.demo1.service.health.rules;

import com.fares.demo1.model.DatabaseSnapshotEntity;
import com.fares.demo1.model.EventType;
import com.fares.demo1.model.Severity;
import com.fares.demo1.service.health.HealthContext;
import com.fares.demo1.service.health.HealthMaths;
import com.fares.demo1.service.health.HealthRule;
import com.fares.demo1.service.health.RuleResult;
import org.springframework.stereotype.Component;

/** Rate: internal on-disk temp tables being created faster than the limit. */
@Component
public class TmpDiskTableRateRule implements HealthRule {

    @Override
    public EventType type() {
        return EventType.TMP_DISK_TABLE_RATE_HIGH;
    }

    @Override
    public RuleResult evaluate(HealthContext ctx) {
        double limit = ctx.props().getTmpDiskTableRatePerMin();
        HealthMaths.Rate rate = HealthMaths.counterRate(
                ctx.db(), DatabaseSnapshotEntity::getCreatedTmpDiskTables, limit);
        if (rate == null) {
            return RuleResult.none();
        }
        return RuleResult.of(rate.breaches(), Severity.WARNING,
                String.format("on-disk temp tables %.1f/min (limit %.0f/min)", rate.latest(), limit),
                rate.latest());
    }
}
