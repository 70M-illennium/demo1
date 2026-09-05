package com.fares.demo1.service.agent;

import com.fares.demo1.service.agent.anthropic.AnthropicBackend;
import com.fares.demo1.service.agent.openai.OpenAiCompatibleBackend;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Picks the one {@link AgentBackend} to use, based on {@code monitor.ai.provider}.
 * "ollama" (the default) needs nothing but a local Ollama server; "openai" and
 * "anthropic" are bring-your-own-key options for a user who already has one of those
 * accounts and would rather not install anything.
 */
@Configuration
public class AgentConfig {

    @Bean
    public AgentBackend agentBackend(
            @Value("${monitor.ai.provider}") String provider,
            @Value("${monitor.ai.ollama-base-url}") String ollamaBaseUrl,
            @Value("${monitor.ai.ollama-model}") String ollamaModel,
            @Value("${monitor.ai.openai-api-key:}") String openAiApiKey,
            @Value("${monitor.ai.openai-model:}") String openAiModel,
            @Value("${monitor.ai.anthropic-api-key:}") String anthropicApiKey,
            @Value("${monitor.ai.anthropic-model:}") String anthropicModel,
            ObjectMapper objectMapper) {

        return switch (provider.strip().toLowerCase()) {
            case "ollama" -> new OpenAiCompatibleBackend(RestClient.create(ollamaBaseUrl), ollamaModel, null);
            case "openai" -> {
                requireConfigured(openAiApiKey, "monitor.ai.openai-api-key");
                requireConfigured(openAiModel, "monitor.ai.openai-model");
                yield new OpenAiCompatibleBackend(RestClient.create("https://api.openai.com"), openAiModel, openAiApiKey);
            }
            case "anthropic" -> {
                requireConfigured(anthropicApiKey, "monitor.ai.anthropic-api-key");
                requireConfigured(anthropicModel, "monitor.ai.anthropic-model");
                yield new AnthropicBackend(RestClient.create("https://api.anthropic.com"), anthropicModel, anthropicApiKey, objectMapper);
            }
            default -> throw new IllegalStateException(
                    "Unknown monitor.ai.provider '" + provider + "' - expected ollama, openai, or anthropic");
        };
    }

    private static void requireConfigured(String value, String property) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(property + " must be set to use this provider");
        }
    }
}
