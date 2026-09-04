package com.fares.demo1.controller;

import com.fares.demo1.dto.MonitorEventResponseDTO;
import com.fares.demo1.model.MonitorEventEntity;
import com.fares.demo1.repo.MonitorEventRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/**
 * Read-only HTTP view of the problems the monitor has flagged.
 *
 * <pre>
 *   GET /api/events?limit=50   recent events (open or resolved), newest first
 *   GET /api/events/active     events still unresolved, most severe first
 * </pre>
 */
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final MonitorEventRepo monitorEventRepo;

    @GetMapping
    public List<MonitorEventResponseDTO> recent(@RequestParam(defaultValue = "50") int limit) {
        int capped = Math.min(Math.max(limit, 1), 500);
        return monitorEventRepo.findByOrderByOccurredAtDesc(PageRequest.of(0, capped)).stream()
                .map(MonitorEventResponseDTO::from)
                .toList();
    }

    @GetMapping("/active")
    public List<MonitorEventResponseDTO> active() {
        return monitorEventRepo.findByResolvedAtIsNullOrderByOccurredAtDesc().stream()
                .sorted(Comparator.comparing(MonitorEventEntity::getSeverity).reversed())
                .map(MonitorEventResponseDTO::from)
                .toList();
    }
}
