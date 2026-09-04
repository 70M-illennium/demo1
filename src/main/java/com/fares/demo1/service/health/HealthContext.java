package com.fares.demo1.service.health;

import com.fares.demo1.config.HealthCheckProperties;
import com.fares.demo1.model.DatabaseSnapshotEntity;
import com.fares.demo1.model.HostSystemSnapshotEntity;

import java.util.List;
import java.util.Map;

/**
 * Everything a {@link HealthRule} needs for one evaluation cycle: the most recent
 * snapshots from each collector (newest first), the current tracked config values
 * (name -&gt; value, possibly empty), plus the tunables. The lists may be shorter than
 * {@code props.fetchCount()} early on, or empty; rules handle that.
 */
public record HealthContext(
        List<DatabaseSnapshotEntity> db,
        List<HostSystemSnapshotEntity> host,
        Map<String, String> configValues,
        HealthCheckProperties props
) {
}
