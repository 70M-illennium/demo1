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
            4. When asked whether the database is healthy, answer directly: a clear \
               status, what (if anything) is wrong, and a concrete, specific \
               recommendation grounded in the data you actually retrieved - not a \
               generic best-practice list.
            5. If you need more than one tool to answer, request all of them in the \
               same turn instead of one at a time across several turns - every extra \
               turn is a full round trip.

            Output format - follow this exactly, every response:
            - Plain text only. No markdown (no #, *, **, or bullet characters), no \
              emoji, no headers, no preamble, no closing remarks.
            - One fact per line, formatted as "key: value" (lowercase, \
              underscore_separated keys).
            - Use the same key on multiple lines for lists (e.g. several "issue: ..." \
              lines, several "recommendation: ..." lines) - one per line, never \
              comma-joined onto one line.
            - Nothing else on the line: no bolding, no trailing punctuation beyond the \
              value itself.
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
            AgentMessage assistantMessage = backend.nextMessage(SYSTEM_PROMPT, history, toolDefinitions);
            history.add(assistantMessage);

            List<AgentMessage.ToolCall> calls = assistantMessage.toolCalls();
            if (calls == null || calls.isEmpty()) {
                return assistantMessage.content();
            }

            for (AgentMessage.ToolCall call : calls) {
                history.add(executeOne(call));
            }
        }

        log.warn("Agent loop hit MAX_TURNS ({}) without finishing - returning what it has so far.", MAX_TURNS);
        return "(stopped after " + MAX_TURNS + " tool-use turns without a final answer)";
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
