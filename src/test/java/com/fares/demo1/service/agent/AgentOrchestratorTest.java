package com.fares.demo1.service.agent;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit test - no HTTP, no real backend. {@link AgentOrchestrator} now depends only
 * on the {@link AgentBackend} interface, so its own logic (tool dispatch, error
 * handling, the MAX_TURNS guard, and the extra no-tools finalize call) is tested
 * against a hand-scripted fake backend; the wire-format translation for each real
 * backend is covered separately in {@code OpenAiCompatibleBackendTest} and
 * {@code AnthropicBackendTest}.
 *
 * <p>Every scenario below scripts one MORE response than the number of tool-bearing
 * turns: whenever the backend returns a message with no tool calls, the orchestrator
 * makes one extra "finalize" call (tools = empty) before returning - see {@code
 * AgentOrchestrator.finalizeAnswer} - so the returned answer always comes from that
 * last scripted response, not the one that ended the tool loop.
 */
class AgentOrchestratorTest {

    private static final JsonMapper OBJECT_MAPPER = JsonMapper.builder().build();

    /** Returns each queued message in order, one per call, regardless of what's asked. */
    private static class ScriptedBackend implements AgentBackend {
        private final Deque<AgentMessage> script = new ArrayDeque<>();

        ScriptedBackend respond(AgentMessage message) {
            script.addLast(message);
            return this;
        }

        @Override
        public AgentMessage nextMessage(String systemPrompt, List<AgentMessage> history, List<ToolDefinition> tools, boolean toolsAllowed) {
            AgentMessage next = script.pollFirst();
            if (next == null) {
                throw new IllegalStateException("test backend ran out of scripted responses");
            }
            return next;
        }
    }

    private static AgentMessage textAnswer(String text) {
        return new AgentMessage("assistant", text, null, null);
    }

    private static AgentMessage toolCall(String callId, String toolName, String argumentsJson) {
        return new AgentMessage("assistant", null,
                List.of(new AgentMessage.ToolCall(callId, toolName, argumentsJson)), null);
    }

    private static AgentTool fakeTool(String name, Object result) {
        return new AgentTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return "a fake tool for testing";
            }

            @Override
            public Map<String, Object> inputSchema() {
                return Map.of("type", "object", "properties", Map.of(), "required", List.of());
            }

            @Override
            public Object execute(Map<String, Object> input) {
                return result;
            }
        };
    }

    @Test
    void noToolCallNeeded_stillMakesAFinalizeCallAndReturnsItsAnswer() {
        ScriptedBackend backend = new ScriptedBackend()
                .respond(textAnswer("draft - never returned"))
                .respond(textAnswer("{\"status\":\"healthy\"}"));
        AgentOrchestrator orchestrator = new AgentOrchestrator(backend, OBJECT_MAPPER, List.of());

        assertThat(orchestrator.ask("is the database healthy?")).isEqualTo("{\"status\":\"healthy\"}");
    }

    @Test
    void oneToolCall_dispatchesToTheMatchingBeanThenReturnsTheFinalizedAnswer() {
        AgentTool tool = fakeTool("get_recent_database_snapshots", List.of(Map.of("reachable", true)));
        ScriptedBackend backend = new ScriptedBackend()
                .respond(toolCall("call-1", "get_recent_database_snapshots", "{\"limit\":3}"))
                .respond(textAnswer("draft - never returned"))
                .respond(textAnswer("database is healthy"));
        AgentOrchestrator orchestrator = new AgentOrchestrator(backend, OBJECT_MAPPER, List.of(tool));

        assertThat(orchestrator.ask("is the database healthy?")).isEqualTo("database is healthy");
    }

    @Test
    void unknownToolName_returnsAnErrorResultInsteadOfCrashingTheLoop() {
        ScriptedBackend backend = new ScriptedBackend()
                .respond(toolCall("call-1", "not_a_real_tool", "{}"))
                .respond(textAnswer("draft - never returned"))
                .respond(textAnswer("I don't have that tool"));
        AgentOrchestrator orchestrator = new AgentOrchestrator(backend, OBJECT_MAPPER, List.of());

        assertThat(orchestrator.ask("do something impossible")).isEqualTo("I don't have that tool");
    }

    @Test
    void aThrowingTool_returnsAnErrorResultInsteadOfCrashingTheLoop() {
        AgentTool broken = new AgentTool() {
            @Override
            public String name() {
                return "broken_tool";
            }

            @Override
            public String description() {
                return "always throws";
            }

            @Override
            public Map<String, Object> inputSchema() {
                return Map.of("type", "object", "properties", Map.of(), "required", List.of());
            }

            @Override
            public Object execute(Map<String, Object> input) {
                throw new IllegalStateException("boom");
            }
        };
        ScriptedBackend backend = new ScriptedBackend()
                .respond(toolCall("call-1", "broken_tool", "{}"))
                .respond(textAnswer("draft - never returned"))
                .respond(textAnswer("that tool failed"));
        AgentOrchestrator orchestrator = new AgentOrchestrator(backend, OBJECT_MAPPER, List.of(broken));

        assertThat(orchestrator.ask("trigger the broken tool")).isEqualTo("that tool failed");
    }

    @Test
    void argumentsWithNoParameters_areTreatedAsAnEmptyMap() {
        AgentTool tool = fakeTool("get_workload_summary", Map.of("queries", List.of()));
        ScriptedBackend backend = new ScriptedBackend()
                .respond(toolCall("call-1", "get_workload_summary", "{}"))
                .respond(textAnswer("draft - never returned"))
                .respond(textAnswer("no slow queries"));
        AgentOrchestrator orchestrator = new AgentOrchestrator(backend, OBJECT_MAPPER, List.of(tool));

        assertThat(orchestrator.ask("why is it slow?")).isEqualTo("no slow queries");
    }

    @Test
    void neverEndingToolUse_stopsAtMaxTurnsThenStillForcesAFinalAnswer() {
        AgentTool tool = fakeTool("get_recent_database_snapshots", List.of());
        ScriptedBackend backend = new ScriptedBackend();
        for (int i = 0; i < 8; i++) {
            backend.respond(toolCall("call-" + i, "get_recent_database_snapshots", "{}"));
        }
        backend.respond(textAnswer("best effort answer from what was gathered"));
        AgentOrchestrator orchestrator = new AgentOrchestrator(backend, OBJECT_MAPPER, List.of(tool));

        assertThat(orchestrator.ask("never finishes")).isEqualTo("best effort answer from what was gathered");
    }
}
