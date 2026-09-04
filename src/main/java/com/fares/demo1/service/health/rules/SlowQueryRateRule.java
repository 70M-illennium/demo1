package com.fares.demo1.service.health.rules;

import com.fares.demo1.model.DatabaseSnapshotEntity;
import com.fares.demo1.model.EventType;
import com.fares.demo1.model.Severity;
import com.fares.demo1.service.health.HealthContext;
import com.fares.demo1.service.health.HealthMaths;
import com.fares.demo1.service.health.HealthRule;
import com.fares.demo1.service.health.RuleResult;
import org.springframework.stereotype.Component;

/** Rate: {@code Slow_queries} is a lifetime counter, so the signal is how fast it climbs. */
@Component
public class SlowQueryRateRule implements HealthRule {

    @Override
    public EventType type() {
        return EventType.SLOW_QUERY_RATE_HIGH;
    }

    @Override
    public RuleResult evaluate(HealthContext ctx) {
        double limit = ctx.props().getSlowQueryRatePerMin();
        HealthMaths.Rate rate = HealthMaths.counterRate(
                ctx.db(), DatabaseSnapshotEntity::getSlowQueries, limit);
        if (rate == null) {
            return RuleResult.none();
        }
        return RuleResult.of(rate.breaches(), Severity.WARNING,
                String.format("slow-query rate %.1f/min (limit %.0f/min)", rate.latest(), limit),
                rate.latest());
    }
}
