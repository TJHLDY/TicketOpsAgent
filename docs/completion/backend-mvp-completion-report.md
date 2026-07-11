# Backend MVP Completion Report

Overall verdict: COMPLETE for the scoped TicketOpsAgent backend MVP.

This verdict means the agreed mock-only, controllable, auditable Java backend loop is implemented and evidenced. It does not mean production readiness, real enterprise integration, autonomous write execution, or LLM-controlled business decisions.

## MVP Requirement Matrix

| Requirement | Status | Implementation evidence | Verification evidence |
| --- | --- | --- | --- |
| Ticket creation | Complete | `TicketService`, JDBC `ticket`, `OPEN` initial state, `/api/agent/chat` | `TicketServicePersistenceTest`, `BackendApiProductizationTest`, live scenarios |
| Classification and risk | Complete | category, priority, and risk enums plus deterministic decision service | 15 deterministic Eval cases and five scenario acceptance cases |
| SOP RAG | Complete for prototype | JDBC SOP source, Spring AI `TokenTextSplitter`, embedding model, `SimpleVectorStore`, source and chunk citations, low-similarity refusal | bilingual retrieval, long-SOP later-chunk retrieval, stale-chunk deletion, ONNX smoke, refusal tests |
| Read-only tools | Complete | `ReadOnlyToolExecutor`, allowlist, exact argument schemas, requester binding, category policy, one-call budget, mock account and permission tools | controlled-tool tests, rejection tests, live account and permission scenarios |
| Draft persistence | Complete | `INTERNAL_SUGGESTION` and `USER_REPLY_DRAFT` in `ticket_message`, read-only messages API | transaction rollback, history preservation, Unicode/length boundary, live response-to-storage equality |
| Pending actions | Complete for audit-only boundary | unlock/grant/reset/close proposals stored as `pending_action`; approve/reject only changes review state | state-machine tests and `NOT_EXECUTED_MOCK_ONLY` live/API evidence |
| Audit and observability | Complete for local prototype | trace, tool logs, pending actions, drafts, rejection reasons, bounded Micrometer metrics, loopback Actuator | repository/API tests, metrics tests, separate management-port live smoke, sensitive marker scan |
| Evaluation and reproducibility | Complete | 15 deterministic cases, 34 mock shadow cases, five scenario flows, acceptance and demo scripts | 133 tests, `scripts\accept.ps1`, JSON/Markdown reports, repeat-run evidence |
| Docker Compose | Complete for dependency startup | PostgreSQL 16 service with health check and persistent volume | compose configuration plus PostgreSQL profile; default H2 path remains available |

## Initial Spike Checklist

| Initial item | Result |
| --- | --- |
| Spring Boot project skeleton | Complete on Java 21, Spring Boot 4.1.0, Spring AI 2.0.0 |
| Base modules | Complete as `ticket`, `agent`, `rag`, `tools`, `web`, and `observability`; shared contracts live with their owning package instead of a generic `common` package |
| Minimum tables | Complete: `ticket`, `ticket_message`, `sop_document`, `agent_trace`, `tool_call_log`, `pending_action`, `mock_user_account`, `mock_user_permission` |
| Five SOP/FAQ documents | Complete in `data.sql` |
| Ten mock tickets | Satisfied as executable inputs rather than seeded ticket rows: 15 deterministic Eval tickets, 34 shadow Eval tickets, and five live scenario templates |
| Five mock accounts | Complete in `data.sql` |
| `getAccountStatus` | Complete through controlled read-only execution |
| SOP retrieval | Complete with embedding, vector search, chunking, citation, refresh, and refusal |
| Agent classification and advice | Complete |
| Pending action persistence | Complete and audit-only |
| Trace persistence | Complete, including retrieval, tool, rejection, draft, and shadow evidence |
| Minimum Eval cases | Exceeded: 15 deterministic plus 34 shadow cases |
| Docker Compose dependencies | Complete for PostgreSQL; application runs locally against H2 or the PostgreSQL profile |

## End-To-End Acceptance

The minimum acceptance input, an OA account-locked request, produces and persists:

- `category=ACCOUNT_LOCKED`;
- `priority=P2`;
- `riskLevel=NEEDS_APPROVAL`;
- parent SOP `SOP-ACCOUNT-LOCKED` and chunk citation;
- controlled `getAccountStatus` call returning `LOCKED`;
- internal handling suggestion;
- `UNLOCK_ACCOUNT` pending action with `NOT_EXECUTED_MOCK_ONLY`;
- user reply draft;
- trace, tool, pending-action, and message audit records.

The suite also proves permission lookup, MFA handling, prompt-injection rejection, non-IT refusal, low-similarity refusal, requester mismatch rejection, and shadow fallback without changing the deterministic user-facing result.

## Evidence Commands

```powershell
mvn test
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File scripts\accept.ps1
mvn package -DskipTests
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File scripts\demo-scenarios.ps1 -BaseUrl http://127.0.0.1:18080
```

Latest closeout evidence:

- 133 tests PASS;
- acceptance report PASS;
- executable JAR package PASS;
- five live scenarios PASS;
- account-lock citation and trace include parent and chunk identity;
- strict secret, bidi, and diff checks PASS.

## Resume-Safe Implemented Claim

The repository supports the following minimum claim:

> Built a Spring Boot and Spring AI prototype for enterprise IT account and permission tickets, implementing ticket triage, SOP vector retrieval with chunk-level source citations and low-confidence refusal, validated read-only tool execution, persisted handling/reply drafts, audit-only pending actions, trace logging, bounded local metrics, and deterministic/shadow Eval workflows.

Required qualifiers:

- deterministic Java remains the user-facing decision path;
- DeepSeek is shadow-only;
- tools and enterprise data are mock-only;
- `SimpleVectorStore` and offline embedding are prototype infrastructure;
- pending actions are never executed against a real system.

## Not production

TicketOpsAgent is not a production ITSM system. It has no real LDAP, SSO, IAM, OA, ticket-system, mail, or notification integration; no login/RBAC/multi-tenant boundary; no production vector database or indexing operations; no SLA; and no real unlock, password reset, permission grant, dispatch, or close-ticket execution.

## Next Stage

The scoped backend MVP no longer has an identified requirement gap. The next product-construction stage can be a complete operator-facing frontend over the existing APIs. Production integrations, LLM main/hybrid decisioning, pgvector, and real write execution remain separate future projects, not unfinished MVP work.
