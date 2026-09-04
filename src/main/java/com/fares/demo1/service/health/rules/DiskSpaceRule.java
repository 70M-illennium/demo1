package com.fares.demo1.service.health.rules;

import com.fares.demo1.model.EventType;
import com.fares.demo1.model.Severity;
import com.fares.demo1.service.health.HealthContext;
import com.fares.demo1.service.health.HealthRule;
import com.fares.demo1.service.health.RuleResult;
import org.springframework.stereotype.Component;

/** Threshold: host filesystem usage over the warn / crit limits. */
@Component
public class DiskSpaceRule implements HealthRule {

    @Override
    public EventType type() {
        return EventType.DISK_SPACE_LOW;
    }

    @Override
    public RuleResult evaluate(HealthContext ctx) {
        if (ctx.host().isEmpty()) {
            return RuleResult.none();
        }
        double warn = ctx.props().getDiskWarnPercent();
        double crit = ctx.props().getDiskCritPercent();
        double latest = ctx.host().get(0).getDiskUsagePercent();
        return RuleResult.of(
                ctx.host().stream().map(s -> s.getDiskUsagePercent() >= warn).toList(),
                latest >= crit ? Severity.CRITICAL : Severity.WARNING,
                String.format("disk usage %.1f%% (warn %.0f%%, crit %.0f%%)", latest, warn, crit),
                latest);
    }
}
