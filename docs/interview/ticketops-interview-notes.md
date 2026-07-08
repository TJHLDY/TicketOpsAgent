# TicketOpsAgent Interview Notes

This file keeps resume and interview wording inside the verified project boundary.

## Project One-liner

TicketOpsAgent is a Spring Boot + Spring AI backend spike for enterprise IT account and permission tickets, focused on deterministic ticket handling plus DeepSeek shadow evaluation.

Chinese version:

> TicketOpsAgent 是一个面向企业 IT 账号与权限工单的后端 Agent 原型，验证工单分类、SOP 检索、只读工具查询、pending action 人工确认边界和 DeepSeek shadow 评估闭环。

## Resume-safe Bullet

Long version:

> 基于 Spring Boot + Spring AI 构建企业 IT 工单 Agent 后端原型，跑通工单分类定级、SOP 检索、只读账号/权限查询、回复草稿、pending action 与 trace 日志；接入 DeepSeek shadow candidate 机制，实现 LLM 输出 DTO 解析、Java 白名单校验、deterministic fallback、trace audit 与一键 acceptance report，覆盖 34 条 mock shadow eval、9 条安全用例、fallback reason 统计，并支持可选 live DeepSeek smoke 验证真实模型接入。

Short version:

> 实现 DeepSeek shadow evaluator，将 LLM 决策限制在非 user-facing 链路，通过 DTO/parser/validator、fallback reason、trace audit、34 条 mock shadow eval 与可选 live smoke 验证模型候选输出的可评估、可回滚和可复现。

## STAR Story

Situation:

Enterprise IT account and permission tickets often require repetitive classification, SOP lookup, account status checks, and careful handling of write operations such as unlock or permission grant.

Task:

Build a Java backend Agent prototype that can prove an AI-assisted workflow without letting the model perform unsafe actions or change user-facing results.

Action:

I kept the deterministic Java chain as the baseline and added DeepSeek in shadow mode only. The model returns a candidate decision as JSON. The backend parses it into DTOs, validates categories, priorities, risk levels, confidence, tool names, tool arguments, pending actions, and category/action consistency. Invalid output records a fallback reason and returns the deterministic result. I added mock shadow eval cases and an acceptance script that runs tests, scans for secrets, writes metrics, and optionally runs a live DeepSeek smoke check.

Result:

The shadow stage now has 46 tests passing, 34 mock shadow eval cases, 9 of 9 safety cases passing, trace audit coverage for all cases, and `userVisibleChangedCount: 0`. This proves the LLM integration is evaluable, rollback-safe, and reproducible for the current spike.

## Technical Challenges

- Keeping LLM output non-user-facing while still useful for evaluation.
- Designing a strict Java validator instead of trusting model-generated tool calls.
- Making fallback reasons measurable rather than burying failures in logs.
- Separating stable mock eval from optional live model smoke checks.
- Preventing scope creep into real ITSM, IAM, frontend, or production automation.

## Architecture Trade-offs

Shadow mode over LLM main mode:

- Chosen because it gives model observability without changing business behavior.
- Trade-off: the system does not yet demonstrate full LLM-driven routing.

Java validator over prompt-only safety:

- Chosen because prompts are not an enforcement layer.
- Trade-off: more DTO and parser code, but safer and easier to test.

Mock eval over live-only eval:

- Chosen because it is reproducible without keys, network, latency, or provider drift.
- Trade-off: it cannot prove production model quality by itself.

Keyword/table SOP retrieval over pgvector:

- Chosen to keep the spike focused on the Agent control loop.
- Trade-off: it is not a real embedding RAG implementation yet.

## Security Boundaries

- No real enterprise ticket system integration.
- No LDAP, SSO, OA, IAM, or approval workflow integration.
- No automatic unlock, reset password, permission grant, dispatch, or ticket close.
- Write-like operations are represented only as pending actions.
- DeepSeek output remains shadow-only.
- API keys are supplied through environment variables and are not written to reports or docs.

## Likely Interview Questions

### Why shadow first instead of LLM main mode?

Because the first risk is not whether an LLM can produce an answer; it is whether model output can be evaluated, audited, and rolled back without affecting the user. Shadow mode lets the deterministic chain stay user-facing while the model candidate is compared and logged.

### Why not use real tool calling?

This spike focuses on the backend control boundary. The LLM proposes a decision, but Java owns tool allowlisting, parameter validation, execution, and audit. That avoids letting a model directly invoke privileged operations.

### Why keep validation in Java?

Because prompt instructions are not a reliable security boundary. Java validation provides deterministic checks for enums, tool names, required arguments, app codes, pending actions, approval requirements, and category/action consistency.

### Is `validationSuccessCount: 12/34` too low?

No. The eval set intentionally contains invalid and unsafe model outputs. Those cases are supposed to fail validation and fallback. For this stage, the stronger indicators are `fallbackCount: 22`, `safetyPassCount: 9 of 9`, `traceAuditPassCount: 34 of 34`, and `userVisibleChangedCount: 0`.

### What is the difference between mock eval and live smoke?

Mock eval is the reproducible acceptance gate. It runs without a real key and proves parser, validator, fallback, and audit behavior. Live smoke is optional and proves the real DeepSeek profile can reach the shadow path.

### What would be required before promoting to hybrid mode?

A new phase plan, more realistic eval data, stable prompt/schema behavior, stronger RAG retrieval, stricter regression gates, and an explicit rule for when the LLM can override deterministic decisions. Hybrid promotion should not be done as a small follow-up to this stage.

## Do Not Claim

- Enterprise-grade ticket system.
- Production LLM Agent.
- Automatic ticket handling.
- Automatic unlock or permission grant.
- LDAP, SSO, IAM, OA, or real ITSM integration.
- Real pgvector RAG.
- LLM main decision chain.
- Model accuracy reached a specific percentage.
