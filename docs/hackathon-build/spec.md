# Scope, product requirements and technical decisions

SignalPilot is an incident-command workbench for a small software team. A responder needs a coherent view of observations, uncertain hypotheses, decisions and recovery. The demo models checkout failure after a deployment and a contrasting upstream-provider outage.

## Acceptance criteria

A judge can run the app with Java 21, create a scenario, see cited evidence, approve and simulate a mitigation, fail or pass recovery checks, resolve only after two healthy samples, and export a truthful postmortem. An independent MCP client can discover nine tools and actually invoke them over Streamable HTTP with 2025-11-25 negotiation. No credentials are needed for that workflow.

## Architecture

- Spring Boot 4.0.8 / Java 21; Spring AI 2.0.1 implements MCP transport and schemas.
- Vanilla HTML/CSS/JavaScript served by Spring Boot; no frontend bundler or runtime CDN.
- H2 file-backed database stores incident aggregates as JSON. Spring JDBC transactions and row locks serialize writes. Revision preconditions reject stale decisions. Per-incident request IDs deduplicate retries.
- Evidence evaluator has explicit rules and conservative abstention. Strength labels are qualitative rule matches, not calibrated model confidence. Sources remain caller-supplied and are not independently authenticated.
- Optional Bedrock Converse generates a textual briefing. It cannot mutate incidents or approve actions. No silent fallback is used for cloud errors. Without it, briefs are labeled local templates; MCP clients bring their own model.
- API and MCP share a token gate when configured. Default binding is loopback. Cross-origin requests are rejected; public deployment needs HTTPS, a unique token and its configured origin. The token is a single-workspace demo control, not enterprise user authorization.
- Audit timeline is persisted with the aggregate. It is not event sourcing, tamper-proof storage, or a distributed event bus.

## Lifecycle

INVESTIGATING → human approves proposal → simulation executes → MONITORING → two consecutive samples below 1% errors and 500 ms p95 → RESOLVED.

Adding evidence while investigating clears proposals, hypotheses and any approvals. Reanalysis also invalidates prior approval. Failed recovery samples reset the gate. Resolved incidents reject mutations. Evidence and incident names render as text, never executable HTML.

## Intentional boundaries

No production rollback, outbound paging, status-page publishing, real Alexa account link, multi-tenancy, claimed time-saved statistics, or fabricated model output. Browser dictation is optional and depends on browser support. AWS Builder is not claimed unless a real Bedrock call is verified. The main deliverable is the self-hosted MCP server, which is independently testable.

## Sources

- https://amazonappdev2026.devpost.com/ (live Devpost connector, checked 2026-09-23)
- https://docs.spring.io/spring-ai/reference/api/mcp/mcp-streamable-http-server-boot-starter-docs.html
- https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now/
