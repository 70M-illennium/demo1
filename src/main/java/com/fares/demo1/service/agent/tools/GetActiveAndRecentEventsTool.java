package com.fares.demo1.service.agent.tools;

import com.fares.demo1.dto.MonitorEventResponseDTO;
import com.fares.demo1.model.MonitorEventEntity;
import com.fares.demo1.repo.MonitorEventRepo;
import com.fares.demo1.service.agent.AgentTool;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Wraps {@link MonitorEventRepo}'s two read queries, same as {@code EventController} -
 * the richest signal available, since it's already the app's own curated "something's
 * wrong" layer rather than raw numbers the model would have to judge unassisted.
 */
@Component
@RequiredArgsConstructor
public class GetActiveAndRecentEventsTool implements AgentTool {

    private static final int DEFAULT_LIMIT = 20;

    private final MonitorEventRepo monitorEventRepo;

    public record EventsView(List<MonitorEventResponseDTO> active, List<MonitorEventResponseDTO> recent) {
    }

    @Override
    public String name() {
        return "get_active_and_recent_events";
    }

    @Override
    public String description() {
        return "Currently-active alerts (most severe first) plus recent events, open or "
                + "resolved, newest first. Use for questions about warnings, alerts, or 'what happened'.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "limit", Map.of("type", "integer",
                                "description", "How many recent events to return (default 20). Does not limit the active list.")),
                "required", List.of());
    }

    @Override
    public Object execute(Map<String, Object> input) {
        int limit = AgentToolInputs.intOrDefault(input, "limit", DEFAULT_LIMIT);

        List<MonitorEventResponseDTO> active = monitorEventRepo.findByResolvedAtIsNullOrderByOccurredAtDesc().stream()
                .sorted(Comparator.comparing(MonitorEventEntity::getSeverity).reversed())
                .map(MonitorEventResponseDTO::from)
                .toList();

        List<MonitorEventResponseDTO> recent = monitorEventRepo.findByOrderByOccurredAtDesc(PageRequest.of(0, limit)).stream()
                .map(MonitorEventResponseDTO::from)
                .toList();

        return new EventsView(active, recent);
    }
}
