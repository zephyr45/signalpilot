# Career material — use verified claims only

## Resume project entry

**SignalPilot — Java/Spring Boot MCP incident-command workbench**

- Built a Java 21/Spring Boot service exposing nine incident-management tools over Streamable HTTP using Spring AI; verified MCP 2025-11-25 interoperability with an independent TypeScript SDK client.
- Implemented transactional incident persistence, revision checks, idempotent commands, approval invalidation and a recovery gate requiring consecutive healthy observations.
- Delivered a responsive dashboard, evidence-linked hypotheses, audience-specific status drafts and Markdown postmortems; verified core behavior with 11 integration tests and 18 browser assertions.

Do not claim a hackathon award, production deployment, real customer impact, event sourcing, or measured recovery-time reduction unless separately established.

## LinkedIn draft

I built SignalPilot for the Amazon Developer Hackathon: a Java/Spring Boot incident command workbench with a self-hosted MCP server.

The interesting engineering problem was keeping an AI-assisted response accountable. Every hypothesis points to evidence. New evidence invalidates previous approvals. A proposed fix does not mean recovery: the incident can close only after consecutive healthy checks.

The project includes nine MCP tools, transactional persistence, concurrency protection, a responsive UI and a reproducible checkout-outage simulator. I verified the server with an independent MCP client, 11 Java integration tests and 18 browser checks.

I used Codex extensively for implementation and verification. The default demo is deterministic and clearly labeled; AI clients connect through MCP. An optional Bedrock briefing adapter is included, with live cloud verification still pending.

Repository: [insert verified repository URL]
Demo: [insert public YouTube/Vimeo URL]

#Java #SpringBoot #MCP #BackendDevelopment #AmazonDeveloper #Hackathon

Publish only after links are valid. Replace “built for” with an award or finalist claim only if the organizer actually grants it.

## Interview preparation

Be ready to explain: why an MCP server still needs a client model; row locks versus revision checks; the difference between persisted timelines and event sourcing; when idempotency helps; why evidence invalidates approval; why deterministic simulation is useful but cannot establish production diagnosis quality; what user-level authorization would add beyond the shared demo token.
