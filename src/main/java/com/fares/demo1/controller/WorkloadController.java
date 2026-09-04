package com.fares.demo1.controller;

import com.fares.demo1.dto.QueryDigestResponseDTO;
import com.fares.demo1.dto.SessionResponseDTO;
import com.fares.demo1.dto.TableSizeResponseDTO;
import com.fares.demo1.dto.WaitResponseDTO;
import com.fares.demo1.repo.QueryDigestSampleRepo;
import com.fares.demo1.repo.SessionSampleRepo;
import com.fares.demo1.repo.TableSizeSampleRepo;
import com.fares.demo1.repo.WaitSampleRepo;
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

    private final QueryDigestSampleRepo queryDigestSampleRepo;
    private final SessionSampleRepo sessionSampleRepo;
    private final WaitSampleRepo waitSampleRepo;
    private final TableSizeSampleRepo tableSizeSampleRepo;

    @GetMapping("/queries")
    public List<QueryDigestResponseDTO> queries() {
        return queryDigestSampleRepo.findLatestCycle().stream()
                .map(QueryDigestResponseDTO::from)
                .toList();
    }

    @GetMapping("/sessions")
    public List<SessionResponseDTO> sessions() {
        return sessionSampleRepo.findLatestCycle().stream()
                .map(SessionResponseDTO::from)
                .toList();
    }

    @GetMapping("/waits")
    public List<WaitResponseDTO> waits() {
        return waitSampleRepo.findLatestCycle().stream()
                .map(WaitResponseDTO::from)
                .toList();
    }

    @GetMapping("/tables")
    public List<TableSizeResponseDTO> tables() {
        return tableSizeSampleRepo.findLatestCycle().stream()
                .map(TableSizeResponseDTO::from)
                .toList();
    }
}
