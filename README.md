# demo1 - Database Health Monitor

A Spring Boot service that watches a MySQL 8.0 database and the host it runs on. It
periodically collects health and performance data (availability, connections, buffer
pool, locking, storage, workload, security posture, config drift, host CPU/memory/disk),
keeps history, raises events when thresholds are crossed, and exposes all of it over a
REST API. It also ships a bonus AI agent that can answer plain-English questions about
the monitored database - "is it healthy?", "what's running right now?", "any security
issues?" - by calling the same read endpoints as tools, either from a terminal chat or
over its own REST endpoint (so it works from Postman too).

Everything runs locally via Docker Compose - no cloud account required for the core
monitor, and the AI agent defaults to a free, self-hosted model (Ollama) rather than a
paid API.

## Prerequisites

- Docker and Docker Compose
- Java 17 (only needed if you run the app outside Docker - see below; the project
  ships `./mvnw` so a separate Maven install isn't required)
- Optional, for the AI agent: [Ollama](https://ollama.com) installed locally with a
  model pulled (e.g. `ollama pull qwen2.5:14b`), **or** an API key from OpenAI or
  Anthropic if you'd rather use one of those instead

## 1. Configure

Copy the example environment file and adjust if you want different credentials (the
defaults all work out of the box for local use):

```
cp .env.example .env
```

## 2. Start the databases

The monitor needs two MySQL instances: the "target" it watches, and its own history
store (kept separate on purpose, so a problem on the target can't take down the
monitor's own storage).

```
docker compose up -d mysql monitor-store
```

Wait for both to report healthy:

```
docker compose ps
```

## 3. Run the app

**Locally (recommended - required if you want the AI agent to reach a local Ollama):**

```
./mvnw spring-boot:run
```

The app starts on `http://localhost:8080`. Wait for `Started Demo1Application` in the
log, then check it's alive:

```
curl http://localhost:8080/api/database/snapshots/latest
```

**Or, the whole stack in Docker** (app included):

```
docker compose up -d --build
```

Note: if you run the app this way, the AI agent can't reach a locally-installed Ollama
(the container can't see `localhost` on your host machine) - use the OpenAI or
Anthropic provider instead in this mode (see below), or run the app locally per the
option above.

## 4. Explore the API

- Swagger UI: `http://localhost:8080/swagger-ui.html` - browse every endpoint without
  reading controller source.
- Every `GET /api/**` endpoint is a read; some require login depending on live policy
  (see `EndpointPolicyRegistry` / `GET /api/admin/policies`).
- Every non-GET `/api/**` endpoint (admin actions, acknowledging events, asking the AI
  agent) needs HTTP Basic auth: username and password both default to `fares`
  (`MONITOR_ADMIN_USERNAME` / `MONITOR_ADMIN_PASSWORD` in `.env`).

## 5. Use the AI agent

The agent answers questions using only real data from the monitor's own tools - it
never guesses, and it says plainly when something isn't monitored. It works against
whichever provider you configure in `.env`:

```
# .env - pick ONE, default is ollama
AI_PROVIDER=ollama          # free, self-hosted, no key - default
# AI_PROVIDER=openai        # bring your own OpenAI key
# AI_PROVIDER=anthropic     # bring your own Anthropic key
```

**Option A - interactive terminal chat:**

```
./mvnw spring-boot:run -Dspring-boot.run.profiles=cli
```

Ask it questions directly at the `>` prompt, e.g. `is the database healthy?` or
`what queries are running right now?`. Type `exit` to quit.

**Option B - REST, e.g. from Postman:**

```
POST http://localhost:8080/api/agent/ask
Authorization: Basic (fares / fares)
Content-Type: application/json

{"question": "is the database healthy?"}
```

Response:

```json
{"answer": "status: healthy\nissue: ...\nrecommendation: ..."}
```

This endpoint has its own stricter rate limit (5 requests/minute) separate from the
rest of the API, since each call can mean several database reads plus a full LLM
round trip.

## 6. Run the tests

Databases must be up first (same as step 2):

```
docker compose up -d mysql monitor-store
./mvnw test
```

## Shutting down

```
docker compose stop     # stop containers, keep data
docker compose down     # stop and remove containers (data volumes are kept)
```

## Project structure

```
controller/    REST endpoints - thin, no business logic beyond validation
service/       collection, health-check evaluation, retention, the AI agent
  agent/       the AI agent: AgentOrchestrator (provider-agnostic loop),
               AgentBackend implementations (ollama/openai/anthropic), tools/
  agent/tools/ read-only tools the AI agent calls (one per monitor capability)
repo/          Spring Data JPA repositories
model/         JPA entities
dto/           API request/response shapes - entities are never exposed directly
config/        security, rate limiting, per-metric cache/auth policy
cli/           the AI agent's interactive terminal entry point
```
