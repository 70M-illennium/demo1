package com.fares.demo1.controller;

import com.fares.demo1.dto.ActivitySnapshotResponseDTO;
import com.fares.demo1.service.ActivitySnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.function.Function;

/**
 * Read-only HTTP view of the collected activity snapshots.
 *
 * <pre>
 *   GET /api/activity/snapshots?limit=20     most recent snapshots, newest first
 *   GET /api/activity/snapshots/latest       the single most recent snapshot
 *   GET /api/activity/snapshots/latest/http  just that section of the latest snapshot
 *   GET /api/activity/snapshots/latest/jvm
 * </pre>
 */
@RestController
@RequestMapping("/api/activity")
@RequiredArgsConstructor
public class ActivitySnapshotController {

    private final ActivitySnapshotService activitySnapshotService;

    @GetMapping("/snapshots")
    public List<ActivitySnapshotResponseDTO> recent(@RequestParam(defaultValue = "20") int limit) {
        int capped = Math.min(Math.max(limit, 1), 500);
        return activitySnapshotService.recentSnapshots(capped).stream()
                .map(ActivitySnapshotResponseDTO::from)
                .toList();
    }

    @GetMapping("/snapshots/latest")
    public ResponseEntity<ActivitySnapshotResponseDTO> latest() {
        return latestSection(Function.identity());
    }

    @GetMapping("/snapshots/latest/http")
    public ResponseEntity<ActivitySnapshotResponseDTO.Http> http() {
        return latestSection(ActivitySnapshotResponseDTO::http);
    }

    @GetMapping("/snapshots/latest/jvm")
    public ResponseEntity<ActivitySnapshotResponseDTO.Jvm> jvm() {
        return latestSection(ActivitySnapshotResponseDTO::jvm);
    }

    /**
     * Shared plumbing for the "latest snapshot" endpoints: fetch the latest snapshot,
     * map it to the response DTO, then apply {@code section} to pick the piece to return
     * (or the whole DTO via {@link Function#identity()}). 204 when nothing is collected yet.
     */
    private <T> ResponseEntity<T> latestSection(Function<ActivitySnapshotResponseDTO, T> section) {
        return activitySnapshotService.latestSnapshot()
                .map(ActivitySnapshotResponseDTO::from)
                .map(section)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
