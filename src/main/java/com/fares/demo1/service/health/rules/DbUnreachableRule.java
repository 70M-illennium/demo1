package com.fares.demo1.service.health.rules;

import com.fares.demo1.model.EventType;
import com.fares.demo1.model.Severity;
import com.fares.demo1.service.health.HealthContext;
import com.fares.demo1.service.health.HealthRule;
import com.fares.demo1.service.health.RuleResult;
import org.springframework.stereotype.Component;

/** Threshold: the snapshot says {@code SELECT 1} against the target failed. */
@Component
public class DbUnreachableRule implements HealthRule {

    @Override
    public EventType type() {
        return EventType.DB_UNREACHABLE;
    }

    @Override
    public RuleResult evaluate(HealthContext ctx) {
        if (ctx.db().isEmpty()) {
            return RuleResult.none();
        }
        return RuleResult.of(
                ctx.db().stream().map(s -> !s.isReachable()).toList(),
                Severity.CRITICAL,
                "database did not answer SELECT 1",
                null);
    }
}
