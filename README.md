# FlowStack

Generic AI-native workflow builder built on Spring Boot, MySQL 8, Redis, and Ollama. FlowStack lets you compose input, script, AI, output, and notification nodes (similar to n8n) to automate AI-first processes.

## Feature Highlights
- Workflow definition CRUD with versioning, metadata, nodes, and edges persisted via JPA (schema bootstrapped using `src/main/resources/db/migration/V1__init.sql`).
- Execution orchestrator that walks the workflow graph, executes each node through a pluggable registry, and records run history.
- Built-in node types for Input, JavaScript, Python, HTTP requests, Email (Gmail via Spring Mail), Ollama prompt calls, ChatGPT (OpenAI) calls, Output aggregation, and Notifications.
- Redis-backed context cache (future queue) plus MySQL persistence for definitions, runs, and node runs.
- Ollama client wrapper for on-device LLM prompts with configurable model + prompt templates (default `gemma3:1b`, change via `flowstack.ollama.default-model`), including `{{variable}}` interpolation from the workflow context.
- Auto-generated OpenAPI docs via Springdoc (`/v3/api-docs`) with Swagger UI (`/swagger-ui.html`).

## Tech Stack
- Java 17, Spring Boot 3.5, Spring Data JPA, Validation, Actuator
- Redis (cache + future queue), MySQL 8 (state + history)
- Testcontainers for integration tests (TBD)

## Project Structure
```
src/main/java/com/ankush/workflowEngine
├── config/           # Async/cache + Jackson + Ollama properties
├── controller/       # REST APIs for workflows and runs
├── domain/           # JPA entities (definitions, nodes, edges, runs)
├── dto/              # Request/response payloads
├── enums/            # Status and node type enums
├── execution/        # Workflow executor, context, and node exec models
├── mapper/           # Entity ↔ DTO conversion helpers
├── registry/         # Node executor interface + registry + executors
├── repository/       # Spring Data repositories
├── service/          # Workflow definition + run services
└── support/          # Ollama client and utility helpers
```

## Getting Started
1. **Prerequisites**: Java 17, Maven, Docker (for MySQL + Redis + Ollama) or native installs.
2. **Configure Datastores**:
   - MySQL 8: install locally (Homebrew, apt, installer, etc.), create `flowstack` DB/user with password `flowstack`, grant privileges, and start the service on `localhost:3306`. Apply the schema once via `mysql -u flowstack -p flowstack < src/main/resources/db/migration/V1__init.sql`.
   - Redis 7+: install natively (`brew install redis`, apt package, or Windows service) and start it on `localhost:6379`.
   - Ollama: install locally (https://ollama.com/download) and pull your preferred model (default is `gemma3:1b`, change `flowstack.ollama.default-model` as needed).
3. **Run the app**: `./mvnw spring-boot:run`.
4. **Hit APIs** (all resource identifiers are UUIDs):
   - `POST /api/workflows` to create a workflow definition.
   - `POST /api/runs/{workflowId}` (UUID) with input payload to trigger execution.
   - `GET /api/runs/{runId}` (UUID) to inspect status/history.
   - `GET /swagger-ui.html` for interactive API docs (powered by springdoc-openapi).
5. **Email Node Setup**:
   - Configure Gmail/App Password via Spring Mail properties (e.g., `spring.mail.host=smtp.gmail.com`, `spring.mail.port=587`, `spring.mail.username=...`, `spring.mail.password=...`, `spring.mail.properties.mail.smtp.auth=true`, `spring.mail.properties.mail.smtp.starttls.enable=true`).
   - Use the `EMAIL` node type with `to`, `subject`, `body`, and optional `cc`, `bcc`, `from`. Templates support `{{variables}}` from the workflow context.
6. **ChatGPT Node Setup**:
   - Provide OpenAI credentials via `flowstack.openai.*` (e.g., set environment variables `FLOWSTACK_OPENAI_API_KEY`, override `flowstack.openai.default-model`, `flowstack.openai.base-url` if pointing to a compatible endpoint).
   - Use the `CHATGPT` node type with `prompt`, optional `model`, `temperature`. Prompts support `{{variables}}`; responses arrive under `nodeKey::response`.

## Next Steps
- Replace placeholder script executors with real JS/Python sandbox runtimes (Graal).
- Add Redis Streams queue + backpressure for large workflows.
- Introduce connector SDK, UI schema metadata, and advanced branching/resume capabilities.
- Harden Ollama node with templating, guardrails, and streaming support.
