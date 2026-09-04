package com.fares.demo1.model;

/**
 * How bad a {@link MonitorEventEntity} is. Declared low to high so
 * {@link Enum#ordinal()} / natural ordering means "more severe last" - the event
 * views reverse it to show the worst first.
 */
public enum Severity {
    INFO,
    WARNING,
    CRITICAL
}
