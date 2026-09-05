package com.fares.demo1.controller;

import com.fares.demo1.dto.AskAgentRequest;
import com.fares.demo1.dto.AskAgentResponse;
import com.fares.demo1.service.agent.AgentOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP front door to the AI health-assessment agent - the same {@link AgentOrchestrator}
 * the CLI uses, so this works with whichever backend is configured (Ollama, OpenAI, or
 * Anthropic; see AgentConfig) with zero extra code here.
 *
 * <pre>
 *   POST /api/agent/ask   {"question": "..."}   ->   {"answer": "..."}
 * </pre>
 *
 * <p>Needs the {@code ADMIN} role, same as every other non-GET {@code /api/**} endpoint
 * (SecurityConfig's catch-all rule already covers this - nothing extra to wire up), and
 * a stricter rate limit than the rest of the API (see RateLimitFilter): each call can
 * mean several tool-backed database reads plus a full LLM round trip, real cost/latency
 * the other endpoints don't have.
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentOrchestrator agentOrchestrator;

    @PostMapping("/ask")
    public AskAgentResponse ask(@RequestBody AskAgentRequest body) {
        if (body == null || body.question() == null || body.question().isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        return new AskAgentResponse(agentOrchestrator.ask(body.question()));
    }
}
