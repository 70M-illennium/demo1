package com.fares.demo1.dto;

/** Response for {@code POST /api/agent/ask} - the agent's answer, plain text (see AgentOrchestrator's system prompt). */
public record AskAgentResponse(String answer) {
}
