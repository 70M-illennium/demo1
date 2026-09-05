package com.fares.demo1.controller;

import com.fares.demo1.dto.ConfigSnapshotResponseDTO;
import com.fares.demo1.service.ConfigSnapshotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only HTTP view of the collected config snapshots.
 *
 * <pre>
 *   GET /api/config/snapshots?limit=20   config changes over time, newest first
 *   GET /api/config/snapshots/latest     the current configuration (name -> value)
 * </pre>
 */
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
@Tag(name = "Config", description = "Tracked MySQL global-variable changes over time (config drift)")
public class ConfigSnapshotController {

    private final ConfigSnapshotService configSnapshotService;

    @GetMapping("/snapshots")
    @Operation(summary = "List recent config-change snapshots, newest first - a new row only exists when something actually changed")
    public List<ConfigSnapshotResponseDTO> recent(@RequestParam(defaultValue = "20") int limit) {
        int capped = Math.min(Math.max(limit, 1), 500);
        return configSnapshotService.recentSnapshots(capped).stream()
                .map(s -> ConfigSnapshotResponseDTO.from(s, configSnapshotService.valuesOf(s)))
                .toList();
    }

    @GetMapping("/snapshots/latest")
    @Operation(summary = "Get the current tracked configuration values")
    public ResponseEntity<ConfigSnapshotResponseDTO> latest() {
        return configSnapshotService.latestSnapshot()
                .map(s -> ConfigSnapshotResponseDTO.from(s, configSnapshotService.valuesOf(s)))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
