package com.fares.demo1.service.agent.anthropic;

import com.fares.demo1.service.agent.AgentMessage;
import com.fares.demo1.service.agent.ToolDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Exercises {@link AnthropicBackend} for real at the HTTP layer via
 * {@link MockRestServiceServer}, since translating to/from Anthropic's block-based
 * content shape (and grouping tool results into a single user message) is exactly the
 * behavior worth proving, not just that some method got called.
 */
class AnthropicBackendTest {

    private static final JsonMapper OBJECT_MAPPER = JsonMapper.builder().build();

    private MockRestServiceServer mockServer;

    private AnthropicBackend backendWith() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://backend.test");
        mockServer = MockRestServiceServer.createServer(builder);
        return new AnthropicBackend(builder.build(), "test-model", "test-key", OBJECT_MAPPER);
    }

    @Test
    void sendsTheAuthHeadersAndSystemAsATopLevelField() {
        AnthropicBackend backend = backendWith();
        mockServer.expect(requestTo("http://backend.test/v1/messages"))
                .andExpect(header("x-api-key", "test-key"))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andExpect(jsonPath("$.system").value("be helpful"))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content[0].type").value("text"))
                .andExpect(jsonPath("$.messages[0].content[0].text").value("hi"))
                .andRespond(withSuccess("""
                        {"content":[{"type":"text","text":"ok"}],"stop_reason":"end_turn"}
                        """, MediaType.APPLICATION_JSON));

        AgentMessage answer = backend.nextMessage("be helpful", List.of(new AgentMessage("user", "hi", null, null)), List.of());

        assertThat(answer.content()).isEqualTo("ok");
        mockServer.verify();
    }

    @Test
    void toolDefinitionsUseInputSchemaNotParameters() {
        AnthropicBackend backend = backendWith();
        ToolDefinition tool = new ToolDefinition("get_thing", "gets a thing", Map.of("type", "object"));
        mockServer.expect(requestTo("http://backend.test/v1/messages"))
                .andExpect(jsonPath("$.tools[0].name").value("get_thing"))
                .andExpect(jsonPath("$.tools[0].description").value("gets a thing"))
                .andExpect(jsonPath("$.tools[0].input_schema.type").value("object"))
                .andRespond(withSuccess("""
                        {"content":[{"type":"text","text":"ok"}],"stop_reason":"end_turn"}
                        """, MediaType.APPLICATION_JSON));

        backend.nextMessage("sys", List.of(new AgentMessage("user", "hi", null, null)), List.of(tool));

        mockServer.verify();
    }

    @Test
    void toolUseBlockInTheResponse_isParsedIntoAnAgentMessageToolCall() {
        AnthropicBackend backend = backendWith();
        mockServer.expect(requestTo("http://backend.test/v1/messages"))
                .andRespond(withSuccess("""
                        {"content":[{"type":"tool_use","id":"toolu_1","name":"get_thing","input":{"limit":3}}],\
                        "stop_reason":"tool_use"}
                        """, MediaType.APPLICATION_JSON));

        AgentMessage answer = backend.nextMessage("sys", List.of(new AgentMessage("user", "hi", null, null)), List.of());

        assertThat(answer.content()).isNull();
        assertThat(answer.toolCalls()).hasSize(1);
        AgentMessage.ToolCall call = answer.toolCalls().get(0);
        assertThat(call.id()).isEqualTo("toolu_1");
        assertThat(call.name()).isEqualTo("get_thing");
        assertThat(call.argumentsJson()).isEqualTo("{\"limit\":3}");
        mockServer.verify();
    }

    @Test
    void anAssistantToolCallFollowedByItsResult_translatesToAToolUseBlockThenAToolResultBlock() {
        AnthropicBackend backend = backendWith();
        AgentMessage assistantCall = new AgentMessage("assistant", null,
                List.of(new AgentMessage.ToolCall("toolu_1", "get_thing", "{\"limit\":3}")), null);
        AgentMessage toolResult = new AgentMessage("tool", "{\"result\":42}", null, "toolu_1");

        mockServer.expect(requestTo("http://backend.test/v1/messages"))
                .andExpect(jsonPath("$.messages[1].role").value("assistant"))
                .andExpect(jsonPath("$.messages[1].content[0].type").value("tool_use"))
                .andExpect(jsonPath("$.messages[1].content[0].id").value("toolu_1"))
                .andExpect(jsonPath("$.messages[1].content[0].name").value("get_thing"))
                .andExpect(jsonPath("$.messages[1].content[0].input.limit").value(3))
                .andExpect(jsonPath("$.messages[2].role").value("user"))
                .andExpect(jsonPath("$.messages[2].content[0].type").value("tool_result"))
                .andExpect(jsonPath("$.messages[2].content[0].tool_use_id").value("toolu_1"))
                .andExpect(jsonPath("$.messages[2].content[0].content").value("{\"result\":42}"))
                .andRespond(withSuccess("""
                        {"content":[{"type":"text","text":"done"}],"stop_reason":"end_turn"}
                        """, MediaType.APPLICATION_JSON));

        backend.nextMessage("sys", List.of(new AgentMessage("user", "hi", null, null), assistantCall, toolResult), List.of());

        mockServer.verify();
    }

    @Test
    void multipleConsecutiveToolResults_areGroupedIntoOneUserMessage() {
        AnthropicBackend backend = backendWith();
        AgentMessage assistantCall = new AgentMessage("assistant", null,
                List.of(new AgentMessage.ToolCall("t1", "tool_one", "{}"),
                        new AgentMessage.ToolCall("t2", "tool_two", "{}")),
                null);
        AgentMessage result1 = new AgentMessage("tool", "\"r1\"", null, "t1");
        AgentMessage result2 = new AgentMessage("tool", "\"r2\"", null, "t2");

        mockServer.expect(requestTo("http://backend.test/v1/messages"))
                .andExpect(jsonPath("$.messages[2].role").value("user"))
                .andExpect(jsonPath("$.messages[2].content[0].tool_use_id").value("t1"))
                .andExpect(jsonPath("$.messages[2].content[1].tool_use_id").value("t2"))
                .andExpect(jsonPath("$.messages.length()").value(3))
                .andRespond(withSuccess("""
                        {"content":[{"type":"text","text":"done"}],"stop_reason":"end_turn"}
                        """, MediaType.APPLICATION_JSON));

        backend.nextMessage("sys",
                List.of(new AgentMessage("user", "hi", null, null), assistantCall, result1, result2),
                List.of());

        mockServer.verify();
    }
}
