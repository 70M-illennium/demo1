package com.fares.demo1.controller;

import com.fares.demo1.dto.AckEventRequest;
import com.fares.demo1.dto.MonitorEventResponseDTO;
import com.fares.demo1.model.MonitorEventEntity;
import com.fares.demo1.repo.MonitorEventRepo;
import com.fares.demo1.service.MonitorEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/**
 * HTTP view of the problems the monitor has flagged, plus the two admin actions that
 * operate on an existing event by hand.
 *
 * <pre>
 *   GET /api/events?limit=50   recent events (open or resolved), newest first
 *   GET /api/events/active     events still unresolved, most severe first
 *   PUT /api/events/{id}/ack       admin: acknowledge (does not resolve), needs ADMIN
 *   PUT /api/events/{id}/resolve   admin: force-resolve by hand, needs ADMIN
 * </pre>
 *
 * <p>The GET methods still read straight from {@link MonitorEventRepo} - they're one
 * line each with no business logic, the same pattern {@code SecurityController} uses.
 * The two PUT methods go through {@link MonitorEventService} instead, because "find by
 * id or 404, mutate, maybe notify" is exactly the kind of logic a controller shouldn't
 * hold.
 */
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Tag(name = "Events", description = "Problems the monitor has flagged, plus admin actions on an existing event")
public class EventController {

    private final MonitorEventRepo monitorEventRepo;
    private final MonitorEventService monitorEventService;

    @GetMapping
    @Operation(summary = "List recent events, open or resolved, newest first")
    public List<MonitorEventResponseDTO> recent(@RequestParam(defaultValue = "50") int limit) {
        int capped = Math.min(Math.max(limit, 1), 500);
        return monitorEventRepo.findByOrderByOccurredAtDesc(PageRequest.of(0, capped)).stream()
                .map(MonitorEventResponseDTO::from)
                .toList();
    }

    @GetMapping("/active")
    @Operation(summary = "List events that are still unresolved, most severe first")
    public List<MonitorEventResponseDTO> active() {
        return monitorEventRepo.findByResolvedAtIsNullOrderByOccurredAtDesc().stream()
                .sorted(Comparator.comparing(MonitorEventEntity::getSeverity).reversed())
                .map(MonitorEventResponseDTO::from)
                .toList();
    }

    @PutMapping("/{id}/ack")
    @Operation(summary = "Acknowledge an event without resolving it (admin only)")
    public MonitorEventResponseDTO acknowledge(@PathVariable Long id,
                                                @RequestBody(required = false) AckEventRequest body) {
        String note = body != null ? body.note() : null;
        return MonitorEventResponseDTO.from(monitorEventService.acknowledge(id, note));
    }

    @PutMapping("/{id}/resolve")
    @Operation(summary = "Force-resolve an event by hand (admin only)")
    public MonitorEventResponseDTO resolve(@PathVariable Long id) {
        return MonitorEventResponseDTO.from(monitorEventService.forceResolve(id));
    }
}
