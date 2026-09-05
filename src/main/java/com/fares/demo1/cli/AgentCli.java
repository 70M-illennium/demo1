package com.fares.demo1.cli;

import com.fares.demo1.service.agent.AgentOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * The interactive front door to {@link AgentOrchestrator} - a plain terminal loop, no
 * web UI, deliberately. Only runs under the {@code cli} Spring profile
 * ({@code --spring.profiles.active=cli}), so a normal boot (Docker, the REST API) never
 * starts an interactive prompt that has nobody attached to type into it. Everything
 * else - the web server, the scheduled collectors - keeps running exactly as normal
 * alongside this; the CLI is an additional way in, not a replacement for the API.
 *
 * <p>Each question is independent - {@link AgentOrchestrator#ask} starts a fresh
 * conversation every call, so there's no "why?" follow-up memory between questions
 * yet. That's a deliberate first cut, not an oversight: threading conversation history
 * across turns is a real (small) addition, worth doing once this baseline works.
 *
 * <p>Fires one canned question immediately on startup, before the interactive prompt -
 * a quick "is this actually working" check so you see a real answer (or a real error)
 * without having to type anything first. On the Gemini free tier this counts as one
 * of the day's limited requests just like any other question, so it's not free to run
 * repeatedly - restarting the CLI to re-check costs one unit of quota every time.
 */
@Component
@Profile("cli")
@RequiredArgsConstructor
@Slf4j
public class AgentCli implements CommandLineRunner {

    private static final String STARTUP_CHECK_QUESTION = "is the database healthy right now?";

    private final AgentOrchestrator agentOrchestrator;

    @Override
    public void run(String... args) throws IOException {
        System.out.println("Database health agent. Ask a question in plain English, or 'exit' to quit.");
        System.out.println("Startup check: " + STARTUP_CHECK_QUESTION);
        answer(STARTUP_CHECK_QUESTION);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            prompt();
            String line;
            while ((line = reader.readLine()) != null) {
                String question = line.trim();
                if (question.isEmpty()) {
                    prompt();
                    continue;
                }
                if (question.equalsIgnoreCase("exit") || question.equalsIgnoreCase("quit")) {
                    break;
                }
                answer(question);
                prompt();
            }
        }
        System.out.println("bye.");
    }

    private void answer(String question) {
        try {
            System.out.println(agentOrchestrator.ask(question));
        } catch (Exception ex) {
            // one bad question (or a missing/invalid API key) should not kill the session
            log.warn("Agent question failed: {}", ex.getMessage());
            System.out.println("(that question failed: " + ex.getMessage() + ")");
        }
    }

    private static void prompt() {
        System.out.print("> ");
        System.out.flush();
    }
}
