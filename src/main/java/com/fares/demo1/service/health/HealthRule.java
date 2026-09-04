package com.fares.demo1.service.health;

import com.fares.demo1.model.EventType;

/**
 * One health check. Implementations are Spring beans; {@code HealthCheckService}
 * collects them all and runs each one every cycle, then applies the open/resolve
 * hysteresis to the {@link RuleResult#breaches()} list a rule returns.
 *
 * <p>A rule is pure: it reads the {@link HealthContext} and returns a verdict. It never
 * touches the database or opens events itself.
 */
public interface HealthRule {

    /** The event type this rule owns. At most one rule per type. */
    EventType type();

    RuleResult evaluate(HealthContext context);
}
