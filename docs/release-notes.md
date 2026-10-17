# SignalPilot 1.0.0 — hackathon build

Evidence-first incident command built with Java 21, Spring Boot and Spring AI MCP.

## Download and run

Requires JDK 21. Download `signalpilot-1.0.0.jar`, then run:

```sh
java -jar signalpilot-1.0.0.jar
```

Open http://127.0.0.1:8091. No account or model key is needed. The app stores local incident data in a `data` directory beside the working directory. Stop it with Ctrl+C.

`signalpilot-demo.mp4` is the English narrated demonstration. `signalpilot-source.zip` contains the full MIT-licensed source, screenshots, instructions and tests. `SHA256SUMS.txt` contains integrity checksums for all three assets.

## Included

- Evidence-linked investigation, reviewed simulated mitigation, recovery checks, stakeholder drafts and Markdown postmortems.
- Nine real Streamable HTTP MCP tools; verified protocol 2025-11-25. Dashboard includes a live handshake/discovery/tool-call diagnostic.
- Flyway-managed H2/PostgreSQL persistence, stale-write protection, replay-safe idempotency and approval invalidation.
- Separate operator/MCP credentials, bounded request bodies, origin enforcement, rate/concurrency limits and validated Bedrock output.
- 18 passing Java tests, browser and accessibility automation, plus an independent TypeScript MCP client check.
- CycloneDX SBOM, dependency/container scanning, checksums and runtime commit/artifact identity in CI.
- Submission draft, product feedback/friction log and verification evidence.

## Honest limits

Operational telemetry and mitigation are simulated. Default dashboard logic is deterministic and labeled; external MCP clients bring their model. The optional Bedrock adapter has not been invoked with live credentials. No production Alexa+ account connection, production readiness or contest award is claimed.

The project has **not been submitted to Devpost**. The participant must supply a public YouTube/Vimeo link and personal eligibility declarations before submitting. This release does not replace those requirements.
