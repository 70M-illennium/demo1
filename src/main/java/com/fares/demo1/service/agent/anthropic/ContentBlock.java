package com.fares.demo1.service.agent.anthropic;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One block of Anthropic Messages API content. Anthropic represents a message's
 * content as a list of typed blocks rather than a single string, so one flexible
 * record covers all three kinds this agent needs ({@code type} says which fields are
 * populated): "text" (uses {@code text}), "tool_use" (uses {@code id}/{@code name}/
 * {@code input} - a tool call the model is making), and "tool_result" (uses
 * {@code toolUseId}/{@code content} - our reply to one of those calls).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContentBlock(
        String type,
        String text,
        String id,
        String name,
        Object input,
        @JsonProperty("tool_use_id") String toolUseId,
        String content
) {
}
