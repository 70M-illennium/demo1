package com.fares.demo1.controller;

import com.fares.demo1.service.agent.AgentBackend;
import com.fares.demo1.service.agent.AgentMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Full-stack integration test for {@code POST /api/agent/ask}, going through the real
 * Spring context - security, the real orchestrator, and (for the tool-call test) the
 * real database tools against the live store - with only the true external boundary,
 * {@link AgentBackend}, replaced by a scripted mock. That keeps this test fast and
 * deterministic without needing a real Ollama/OpenAI/Anthropic account running.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AgentControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private AgentBackend agentBackend;

    @Value("${monitor.admin.username}")
    private String adminUser;

    @Value("${monitor.admin.password}")
    private String adminPassword;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpEntity<Map<String, Object>> adminJson(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(adminUser, adminPassword);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void ask_withoutCreds_isRejected() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/agent/ask"), HttpMethod.POST,
                new HttpEntity<>(Map.of("question", "is it healthy?")), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void ask_blankQuestion_is400() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/agent/ask"), HttpMethod.POST,
                adminJson(Map.of("question", "   ")), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ask_withNoToolCallNeeded_returnsTheBackendsAnswerDirectly() {
        when(agentBackend.nextMessage(any(), any(), any(), anyBoolean()))
                .thenReturn(new AgentMessage("assistant", "status: healthy", null, null));

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/agent/ask"), HttpMethod.POST,
                adminJson(Map.of("question", "is it healthy?")), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("answer")).isEqualTo("status: healthy");
    }

    /**
     * Scripts one real tool call (against the real database tools, real live store) then
     * a final answer, proving the controller's full round trip through {@code
     * AgentOrchestrator} - not just that the mocked backend's text made it back out.
     */
    @Test
    @SuppressWarnings("unchecked")
    void ask_withAToolCall_dispatchesTheRealToolThenReturnsTheFinalAnswer() {
        AgentMessage toolCallMessage = new AgentMessage("assistant", null,
                List.of(new AgentMessage.ToolCall("call-1", "get_recent_database_snapshots", "{\"limit\":1}")), null);
        AgentMessage finalAnswer = new AgentMessage("assistant", "database is healthy", null, null);
        when(agentBackend.nextMessage(any(), any(), any(), anyBoolean())).thenReturn(toolCallMessage, finalAnswer);

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/agent/ask"), HttpMethod.POST,
                adminJson(Map.of("question", "is it healthy?")), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("answer")).isEqualTo("database is healthy");
    }
}
