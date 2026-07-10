# Spring AI 2 Platform Baseline Design

## Context

TicketOpsAgent currently runs on Java 17, Spring Boot 3.5.15, and Spring AI 1.1.8. The existing MVP is healthy: PR #8 is merged, 79 tests pass, the acceptance script passes, and the deterministic user-facing path remains isolated from the DeepSeek shadow candidate.

Spring AI 2.0.0 became generally available on 2026-06-12. It targets Spring Boot 4.0/4.1 and makes the advisor chain the first-class composition point for tool loops, structured-output validation retries, evaluation loops, and observability. Because TicketOpsAgent still needs real vector retrieval and controlled model-driven tool execution, migrating the platform before those features avoids building new work on the legacy 1.x tool loop.

## Decision

Adopt the following platform baseline in an isolated migration PR:

- Java release 21.
- Spring Boot 4.1.0.
- Spring AI 2.0.0.
- Keep the existing DeepSeek starter and existing `spring.ai.deepseek.chat.*` property shape.
- Keep deterministic mode as the default and DeepSeek as shadow-only.

The migration is deliberately behavior-preserving. It does not add vector storage, model-controlled tool execution, MCP, production observability exporters, or a new frontend.

## Alternatives Considered

### Stay on Spring AI 1.1.8

This minimizes immediate migration work, but new tool-calling and structured-output work would be built on an older execution model. It would create avoidable rework when adopting Spring AI 2 later.

### Upgrade the platform first (selected)

This isolates dependency and runtime compatibility risk before adding AI behavior. Existing tests and acceptance evidence provide a strong regression net, and every later AI capability can use the current advisor and observability APIs.

### Upgrade and add PGvector, MCP, and model-driven tools together

This has the largest apparent feature gain but combines platform, data, model, and security changes in one blast radius. It is rejected because failures would be hard to attribute and rollback.

## Compatibility Contract

The migration must preserve:

- `POST /api/agent/chat` request and response shape, including `ticketId`.
- Deterministic mode as the default without external credentials.
- DeepSeek shadow mode remaining optional and non-user-facing.
- Existing H2 default runtime and PostgreSQL profile compatibility.
- Existing ticket, trace, tool-call, pending-action, and eval APIs.
- All 79 existing tests and `scripts/accept.ps1`.
- The audit-only `NOT_EXECUTED_MOCK_ONLY` boundary.

## Verification

1. Add an artifact contract test that reads `pom.xml` and proves the selected versions.
2. Observe the contract test fail on the old baseline.
3. Change only the platform versions and directly required compatibility code.
4. Run the targeted contract test.
5. Run `mvn test` and `mvn package`.
6. Run `scripts/accept.ps1`.
7. Start the default application without model credentials and verify an HTTP request through the existing scenario flow.
8. Scan the diff and repository for accidentally committed secrets.

## Risks And Handling

- Spring Boot 4 can expose removed or renamed framework APIs. Fix only compile/runtime incompatibilities surfaced by the existing suite.
- Spring AI 2 changes tool-loop internals. No tool-loop migration is attempted in this PR because the current user-facing path is deterministic.
- The workstation runs JDK 25 while the project targets Java 21. Maven compilation must use release 21 so the built bytecode keeps the intended baseline.
- DeepSeek live quality is not part of this migration. The API key remains an environment variable and live smoke remains optional.

## Post-Migration Construction Route

1. Replace keyword/table SOP matching with a `VectorStoreRetriever` boundary and real embeddings, while preserving source citations and low-similarity refusal.
2. Add controlled Spring AI 2 tool calling for the two read-only tools, with explicit schemas, allowlists, argument validation, call budgets, and deterministic fallback.
3. Add Micrometer/Spring AI observations for model, advisor, retrieval, and tool operations while keeping prompt and tool content disabled by default.
4. Extend evals with retrieval relevance, structured-output validity, tool-selection accuracy, prompt-injection, excessive-agency, and fallback metrics.
5. Move to POLISH only after the backend contract, demo path, README, and resume statements are all supported by runtime evidence.

## Official Sources

- Spring AI 2.0.0 GA: https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now/
- Spring AI getting started and Boot compatibility: https://docs.spring.io/spring-ai/reference/getting-started.html
- Spring AI 2 upgrade notes: https://docs.spring.io/spring-ai/reference/upgrade-notes.html
- Spring AI advisors: https://docs.spring.io/spring-ai/reference/api/advisors.html
- Spring AI observability: https://docs.spring.io/spring-ai/reference/observability/index.html
- Spring AI evaluation testing: https://docs.spring.io/spring-ai/reference/api/testing.html
- OWASP Top 10 for Agentic Applications 2026: https://genai.owasp.org/resource/owasp-top-10-for-agentic-applications-for-2026/

