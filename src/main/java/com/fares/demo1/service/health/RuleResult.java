package com.fares.demo1.service.health;

import com.fares.demo1.model.Severity;

import java.util.List;

/**
 * What a {@link HealthRule} reports for one cycle.
 *
 * @param breaches per-snapshot breach flags, index 0 = latest. The orchestrator applies
 *                 the open/resolve hysteresis to this list. Empty means "not enough data
 *                 to judge" - the rule is skipped this cycle.
 * @param severity severity to use if the event opens
 * @param message  human text with the actual numbers
 * @param value    the value that tripped it, for the API/graphs (nullable)
 */
public record RuleResult(List<Boolean> breaches, Severity severity, String message, Double value) {

    private static final RuleResult NONE = new RuleResult(List.of(), null, null, null);

    /** Not enough history yet, or nothing to judge. */
    public static RuleResult none() {
        return NONE;
    }

    public static RuleResult of(List<Boolean> breaches, Severity severity, String message, Double value) {
        return new RuleResult(breaches, severity, message, value);
    }
}
