package com.fares.demo1.service.agent;

/**
 * A tool's shape, independent of any vendor's wire format (OpenAI-style calls this
 * field "parameters", Anthropic calls it "input_schema" - both just want an
 * {@link AgentTool#inputSchema()} JSON-schema object).
 */
public record ToolDefinition(String name, String description, Object parameters) {
}
