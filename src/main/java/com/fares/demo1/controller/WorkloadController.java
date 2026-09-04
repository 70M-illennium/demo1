package com.fares.demo1.controller;

import com.fares.demo1.dto.QueryDigestResponseDTO;
import com.fares.demo1.dto.SessionResponseDTO;
import com.fares.demo1.dto.TableSizeResponseDTO;
import com.fares.demo1.dto.WaitResponseDTO;
import com.fares.demo1.service.WorkloadSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The target's workload as of the most recent collection cycle.
 *
 * <pre>
 *   GET /api/workload/queries    top query digests, slowest first
 *   GET /api/workload/sessions   live (non-idle) sessions, longest-running first
 *   GET /api/workload/waits      top wait events, most time first
 *   GET /api/workload/tables     per-table sizes, largest first (slow cadence)
 * </pre>
 */
@RestController
@RequestMapping("/api/workload")
@RequiredArgsConstructor
public class WorkloadController {

    private final WorkloadSnapshotService workloadSnapshotService;

    @GetMapping("/queries")
    public List<QueryDigestResponseDTO> queries() {
        return workloadSnapshotService.latestDigests().stream()
                .map(QueryDigestResponseDTO::from)
                .toList();
    }

    @GetMapping("/sessions")
    public List<SessionResponseDTO> sessions() {
        return workloadSnapshotService.latestSessions().stream()
                .map(SessionResponseDTO::from)
                .toList();
    }

    @GetMapping("/waits")
    public List<WaitResponseDTO> waits() {
        return workloadSnapshotService.latestWaits().stream()
                .map(WaitResponseDTO::from)
                .toList();
    }

    @GetMapping("/tables")
    public List<TableSizeResponseDTO> tables() {
        return workloadSnapshotService.latestTableSizes().stream()
                .map(TableSizeResponseDTO::from)
                .toList();
    }
}
