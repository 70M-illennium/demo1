package com.fares.demo1.service.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Runs the manual tool-use loop against whichever {@link AgentBackend} is configured
 * (Ollama, OpenAI, or Anthropic - see AgentConfig). This class knows nothing about any
 * vendor's wire format; that translation lives entirely in the backend implementations.
 */
@Service
@Slf4j
public class AgentOrchestrator {

    private static final int MAX_TURNS = 8;

    private static final String SYSTEM_PROMPT = """
            You are the health-assessment agent for a local database health monitor \
            service. You answer questions about ONE MySQL database and the host it \
            runs on, using only the tools provided - never your own general knowledge \
            about databases in the abstract.

            Rules:
            1. Every factual claim you make must come from a tool result. If a tool \
               returns no data, say so explicitly - never invent or estimate a number.
            2. If asked about something this service does not monitor (for example \
               replication lag, SQL Server-style plan cache, backup catalogs, or \
               anything not returned by your tools), say plainly that it is not \
               monitored. Do not answer it from general knowledge instead.
            3. You are read-only. You can explain what is wrong and recommend a fix in \
               plain language, but you cannot execute anything - never claim to have \
               fixed, changed, or restarted anything.
            4. Answer ONLY what was asked. If asked for one specific fact (for example \
               "what is the slowest query"), give ONLY that fact - do not add a full \
               health report, extra issues, or extra recommendations nobody asked for.
            5. When asked something broader (e.g. "is it healthy", "why is it slow"), \
               give at most 5 "issue" lines and at most 3 "recommendation" lines - the \
               most important ones only, never every minor observation in the raw data.
            6. If you need more than one tool to answer, request all of them in the \
               same turn instead of one at a time across several turns - every extra \
               turn is a full round trip.

            Output format - follow this exactly, every response, no matter how much \
            data the tools returned, no exceptions:
            - Respond with ONE valid JSON object. Nothing else: no markdown code \
              fences, no text before or after the JSON, no explanation of your \
              reasoning, no closing remarks.
            - Keys are lowercase, underscore_separated. Values are short strings or \
              numbers only - a value is a short phrase, never a multi-sentence \
              explanation.
            - Use a JSON array of short strings for anything that is naturally a list \
              (e.g. "issues", "recommendations") - at most 5 entries for "issues" and \
              at most 3 for "recommendations", the most important ones only, never \
              every minor observation in the raw data. Omit the key entirely if there \
              is nothing to report for it.
            - If asked for one specific fact, the object should contain ONLY the keys \
              relevant to that fact - no unrelated "issues" or "recommendations" keys.

            Example of a correct response to "what is the slowest query":
            {"slowest_query": "SELECT customers.* FROM customers WHERE customer_id = ?", \
            "slowest_query_avg_latency_ms": 210, "slowest_query_exec_count": 11231}

            Example of a correct response to "is it healthy":
            {"status": "healthy", "issues": ["memory_usage_percent is 98, near threshold"], \
            "recommendations": ["monitor memory usage, consider increasing capacity"]}
            """;

    private final AgentBackend backend;
    private final ObjectMapper objectMapper;
    private final Map<String, AgentTool> toolsByName;
    private final List<ToolDefinition> toolDefinitions;

    public AgentOrchestrator(AgentBackend backend, ObjectMapper objectMapper, List<AgentTool> tools) {
        this.backend = backend;
        this.objectMapper = objectMapper;
        this.toolsByName = tools.stream().collect(Collectors.toMap(AgentTool::name, t -> t));
        this.toolDefinitions = tools.stream()
                .map(t -> new ToolDefinition(t.name(), t.description(), t.inputSchema()))
                .toList();
    }

    public String ask(String question) {
        List<AgentMessage> history = new ArrayList<>();
        history.add(new AgentMessage("user", question, null, null));

        for (int turn = 0; turn < MAX_TURNS; turn++) {
            AgentMessage assistantMessage = backend.nextMessage(SYSTEM_PROMPT, history, toolDefinitions, true);
            history.add(assistantMessage);

            List<AgentMessage.ToolCall> calls = assistantMessage.toolCalls();
            if (calls == null || calls.isEmpty()) {
                return finalizeAnswer(history);
            }

            for (AgentMessage.ToolCall call : calls) {
                history.add(executeOne(call));
            }
        }

        log.warn("Agent loop hit MAX_TURNS ({}) without finishing - forcing a final answer from what was gathered.", MAX_TURNS);
        return finalizeAnswer(history);
    }

    /**
     * One extra call with {@code toolsAllowed = false}, so the model cannot make
     * another tool call and it is safe to enforce strict JSON-mode output where the
     * backend supports it (see {@code OpenAiCompatibleBackend}) - offering JSON mode
     * and letting the model call a tool in the same turn is what let it fake a tool
     * call as plain-text JSON instead of using the real tool_calls mechanism. This is
     * the one call whose output actually reaches the caller, so it's the one call
     * worth constraining.
     */
    private String finalizeAnswer(List<AgentMessage> history) {
        return backend.nextMessage(SYSTEM_PROMPT, history, toolDefinitions, false).content();
    }

    private AgentMessage executeOne(AgentMessage.ToolCall call) {
        String name = call.name();
        AgentTool tool = toolsByName.get(name);
        Object result;
        if (tool == null) {
            result = Map.of("error", "unknown tool: " + name);
        } else {
            try {
                result = Map.of("result", tool.execute(parseArguments(call.argumentsJson())));
            } catch (Exception ex) {
                log.warn("Tool '{}' failed: {}", name, ex.getMessage());
                result = Map.of("error", "tool failed: " + ex.getMessage());
            }
        }
        return new AgentMessage("tool", objectMapper.writeValueAsString(result), null, call.id());
    }

    private Map<String, Object> parseArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Map.of();
        }
        return objectMapper.readValue(argumentsJson, new TypeReference<Map<String, Object>>() {
        });
    }
}
