package com.fares.demo1.dto;

import com.fares.demo1.model.ConfigSnapshotEntity;

import java.time.Instant;
import java.util.Map;

/**
 * API view of one config snapshot: when this configuration was first observed, and the
 * tracked GLOBAL VARIABLES as they stood then. Rows are written only on change, so a
 * list of these is the config-change history.
 */
public record ConfigSnapshotResponseDTO(
        long id,
        Instant timestamp,
        Map<String, String> values
) {

    public static ConfigSnapshotResponseDTO from(ConfigSnapshotEntity e, Map<String, String> values) {
        return new ConfigSnapshotResponseDTO(e.getId(), e.getTimestamp(), values);
    }
}
