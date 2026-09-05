package com.fares.demo1.service.agent.tools;

import com.fares.demo1.dto.SecurityFindingResponseDTO;
import com.fares.demo1.model.SecurityFinding;
import com.fares.demo1.service.SecurityCheckService;
import com.fares.demo1.service.agent.AgentTool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Wraps {@link SecurityCheckService#latestFindings}, same shape as {@code SecurityController}. */
@Component
@RequiredArgsConstructor
public class GetSecurityFindingsTool implements AgentTool {

    private final SecurityCheckService securityCheckService;

    @Override
    public String name() {
        return "get_security_findings";
    }

    @Override
    public String description() {
        return "The target's security posture as of the most recent check cycle - weak "
                + "settings and risky accounts, most severe first. Use for questions "
                + "about security posture or hardening.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of(), "required", List.of());
    }

    @Override
    public Object execute(Map<String, Object> input) {
        return securityCheckService.latestFindings().stream()
                .sorted(Comparator.comparing(SecurityFinding::getSeverity).reversed())
                .map(SecurityFindingResponseDTO::from)
                .toList();
    }
}
