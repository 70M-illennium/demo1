package com.fares.demo1.service.health.rules;

import com.fares.demo1.model.EventType;
import com.fares.demo1.model.Severity;
import com.fares.demo1.service.health.HealthContext;
import com.fares.demo1.service.health.HealthRule;
import com.fares.demo1.service.health.RuleResult;
import org.springframework.stereotype.Component;

/**
 * Threshold on a ratio across two collectors: {@code threads_connected} (from the DB
 * snapshot) over {@code max_connections} (from the latest config snapshot). Config
 * changes rarely, so the newest {@code max_connections} is used for every point.
 */
@Component
public class ConnectionSaturationRule implements HealthRule {

    @Override
    public EventType type() {
        return EventType.CONNECTION_SATURATION;
    }

    @Override
    public RuleResult evaluate(HealthContext ctx) {
        String maxConnRaw = ctx.configValues().get("max_connections");
        if (ctx.db().isEmpty() || maxConnRaw == null) {
            return RuleResult.none();
        }
        int maxConnections;
        try {
            maxConnections = Integer.parseInt(maxConnRaw.trim());
        } catch (NumberFormatException ex) {
            return RuleResult.none();
        }
        if (maxConnections <= 0) {
            return RuleResult.none();
        }
        double warn = ctx.props().getConnectionWarnPercent();
        double crit = ctx.props().getConnectionCritPercent();
        double latestPct = 100.0 * ctx.db().get(0).getThreadsConnected() / maxConnections;

        return RuleResult.of(
                ctx.db().stream()
                        .map(s -> 100.0 * s.getThreadsConnected() / maxConnections >= warn)
                        .toList(),
                latestPct >= crit ? Severity.CRITICAL : Severity.WARNING,
                String.format("%d/%d connections in use (%.0f%%, warn %.0f%%)",
                        ctx.db().get(0).getThreadsConnected(), maxConnections, latestPct, warn),
                latestPct);
    }
}
