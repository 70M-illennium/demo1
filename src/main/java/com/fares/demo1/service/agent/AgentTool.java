package com.fares.demo1.service.agent;

import java.util.Map;

/**
 * One capability the AI agent can call - a name, a description, a JSON schema for its
 * input, and the read it actually performs. Spring auto-discovers every implementation
 * via {@code List<AgentTool>} injection, the same pattern {@code HealthRule} already
 * uses for the alert rules - adding a new tool later means adding one class, not
 * editing a registry.
 *
 * <p>Deliberately independent of any Anthropic-SDK type: this interface only describes
 * what the tool does and how to call it. Whatever eventually drives the model
 * conversation (Tool Runner, a manual loop, or something else) adapts these into
 * whatever shape that layer needs - keeping that decision out of the tools themselves
 * means every implementation stays trivial to unit-test with no SDK, no model, and no
 * HTTP involved.
 *
 * <p>Every implementation is read-only by construction: each one calls only the
 * existing {@code @Transactional(readOnly = true)} service methods the REST
 * controllers already use - there is no write path for the agent to reach, even by
 * mistake.
 */
public interface AgentTool {

    /** Stable, unique tool name - what the model calls this capability. */
    String name();

    /** Plain-English description of what this tool returns, for the model's benefit. */
    String description();

    /** A JSON-schema "object" describing the accepted input (empty properties if none needed). */
    Map<String, Object> inputSchema();

    /** Runs the read and returns a plain data object - JSON serialization happens one layer up. */
    Object execute(Map<String, Object> input);
}
