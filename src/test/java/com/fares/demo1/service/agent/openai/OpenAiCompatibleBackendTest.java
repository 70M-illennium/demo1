package com.fares.demo1.service.agent.openai;

import com.fares.demo1.service.agent.AgentMessage;
import com.fares.demo1.service.agent.ToolDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Exercises {@link OpenAiCompatibleBackend} for real at the HTTP layer via Spring's
 * {@link MockRestServiceServer}, since this class's whole job is producing correct
 * request JSON and parsing response JSON - a hand-mocked RestClient chain wouldn't
 * prove either of those.
 */
class OpenAiCompatibleBackendTest {

    private MockRestServiceServer mockServer;

    private OpenAiCompatibleBackend backendWith(String apiKey) {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://backend.test");
        mockServer = MockRestServiceServer.createServer(builder);
        return new OpenAiCompatibleBackend(builder.build(), "test-model", apiKey);
    }

    @Test
    void noApiKey_sendsNoAuthorizationHeader_forOllama() {
        OpenAiCompatibleBackend backend = backendWith(null);
        mockServer.expect(requestTo("http://backend.test/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}]}
                        """, MediaType.APPLICATION_JSON));

        AgentMessage answer = backend.nextMessage("be helpful", List.of(new AgentMessage("user", "hi", null, null)), List.of());

        assertThat(answer.content()).isEqualTo("ok");
        mockServer.verify();
    }

    @Test
    void apiKeyGiven_sendsBearerAuthorizationHeader_forOpenAi() {
        OpenAiCompatibleBackend backend = backendWith("sk-test-key");
        mockServer.expect(requestTo("http://backend.test/v1/chat/completions"))
                .andExpect(header("Authorization", "Bearer sk-test-key"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}]}
                        """, MediaType.APPLICATION_JSON));

        backend.nextMessage("be helpful", List.of(new AgentMessage("user", "hi", null, null)), List.of());

        mockServer.verify();
    }

    @Test
    void systemPromptIsSentAsTheFirstMessage() {
        OpenAiCompatibleBackend backend = backendWith(null);
        mockServer.expect(requestTo("http://backend.test/v1/chat/completions"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("be helpful"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("hi"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}]}
                        """, MediaType.APPLICATION_JSON));

        backend.nextMessage("be helpful", List.of(new AgentMessage("user", "hi", null, null)), List.of());

        mockServer.verify();
    }

    @Test
    void toolDefinitionsAreTranslatedToTheFunctionWireShape() {
        OpenAiCompatibleBackend backend = backendWith(null);
        ToolDefinition tool = new ToolDefinition("get_thing", "gets a thing", Map.of("type", "object"));
        mockServer.expect(requestTo("http://backend.test/v1/chat/completions"))
                .andExpect(jsonPath("$.tools[0].type").value("function"))
                .andExpect(jsonPath("$.tools[0].function.name").value("get_thing"))
                .andExpect(jsonPath("$.tools[0].function.description").value("gets a thing"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}]}
                        """, MediaType.APPLICATION_JSON));

        backend.nextMessage("sys", List.of(new AgentMessage("user", "hi", null, null)), List.of(tool));

        mockServer.verify();
    }

    @Test
    void toolCallInTheResponse_isParsedIntoAnAgentMessageToolCall() {
        OpenAiCompatibleBackend backend = backendWith(null);
        mockServer.expect(requestTo("http://backend.test/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"role":"assistant","content":null,"tool_calls":\
                        [{"id":"call-1","type":"function","function":{"name":"get_thing","arguments":"{\\"limit\\":3}"}}]},\
                        "finish_reason":"tool_calls"}]}
                        """, MediaType.APPLICATION_JSON));

        AgentMessage answer = backend.nextMessage("sys", List.of(new AgentMessage("user", "hi", null, null)), List.of());

        assertThat(answer.content()).isNull();
        assertThat(answer.toolCalls()).hasSize(1);
        AgentMessage.ToolCall call = answer.toolCalls().get(0);
        assertThat(call.id()).isEqualTo("call-1");
        assertThat(call.name()).isEqualTo("get_thing");
        assertThat(call.argumentsJson()).isEqualTo("{\"limit\":3}");
        mockServer.verify();
    }

    @Test
    void aToolResultMessage_isSentBackWithItsOwnRoleAndToolCallId() {
        OpenAiCompatibleBackend backend = backendWith(null);
        AgentMessage toolResult = new AgentMessage("tool", "{\"result\":42}", null, "call-1");
        mockServer.expect(requestTo("http://backend.test/v1/chat/completions"))
                .andExpect(jsonPath("$.messages[1].role").value("tool"))
                .andExpect(jsonPath("$.messages[1].tool_call_id").value("call-1"))
                .andExpect(jsonPath("$.messages[1].content").value("{\"result\":42}"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"role":"assistant","content":"done"},"finish_reason":"stop"}]}
                        """, MediaType.APPLICATION_JSON));

        backend.nextMessage("sys", List.of(toolResult), List.of());

        mockServer.verify();
    }
}
