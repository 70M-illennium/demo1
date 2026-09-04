package com.fares.demo1.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * How long the per-minute snapshots are kept. Bound from {@code monitor.retention.*}.
 * Config snapshots (rare, small, written on change) are never pruned; unresolved events
 * are never pruned.
 */
@Component
@ConfigurationProperties("monitor.retention")
@Getter
@Setter
public class RetentionProperties {

    /** Full-resolution days to keep. */
    private int days = 10;

    /** Extra margin on top of {@link #days} before a row is eligible for deletion. */
    private int graceHours = 2;

    /** Whether the periodic purge runs at all. */
    private boolean enabled = true;

    public Duration maxAge() {
        return Duration.ofDays(days).plusHours(graceHours);
    }
}
