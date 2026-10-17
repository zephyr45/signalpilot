# SignalPilot

**Evidence → decision → verified recovery.**

[![Verify SignalPilot](https://github.com/zephyr45/signalpilot/actions/workflows/verify.yml/badge.svg)](https://github.com/zephyr45/signalpilot/actions/workflows/verify.yml)

A Java/Spring Boot incident command workbench and self-hosted MCP server for the Amazon Developer Hackathon's Alexa+ track. Investigate an outage, inspect the supporting evidence, approve a simulated response, verify recovery and export the postmortem.

![SignalPilot incident dashboard](artifacts/02-investigation.png)

## Run in two commands

Prefer a prebuilt executable? The [v1.0.0 release](https://github.com/zephyr45/signalpilot/releases/tag/v1.0.0) contains the JAR, narrated demo and source archive. Run the downloaded JAR with `java -jar signalpilot-1.0.0.jar` (JDK 21 required).

Requires **JDK 21**. Maven is downloaded by the included wrapper; Node 22 is only needed for the independent MCP and accessibility checks.

Windows PowerShell:

```powershell
.\mvnw.cmd verify
java -jar target/signalpilot-1.0.0.jar
```

macOS / Linux:

```sh
./mvnw verify
java -jar target/signalpilot-1.0.0.jar
```

Open **http://127.0.0.1:8091**. Click **Launch checkout incident**. No accounts, model keys or hardware are required. Data persists in `./data/signalpilot.mv.db`; stop the server before backing up that file.

To use another port, set `PORT` before starting the JAR. The browser origin is derived from the effective request URL, so local alternate ports work without extra origin configuration:

```powershell
$env:PORT='8092'
java -jar target/signalpilot-1.0.0.jar
```

## Judge the complete workflow

1. Launch checkout incident. Four evidence records explain the simulated alert, deployment, pool exhaustion and healthy upstream provider.
2. Click **Analyze evidence**. Inspect the leading hypothesis, alternative explanation and source IDs.
3. Click **Review & approve**, then **Approve simulation**, then **Run simulation**. These actions never contact production infrastructure.
4. Click **Check recovery** once: resolution is unavailable. Try **Simulate failed check**: the gate resets. Pass two consecutive checks to unlock resolution.
5. Resolve, open **Communications**, draft a customer update, and export the Markdown postmortem.
6. Try **New incident → Payment provider timeout cascade**: SignalPilot recommends a circuit breaker rather than a checkout rollback. **Insufficient evidence** proposes no remediation.

## Connect an MCP client

Transport: **Streamable HTTP**. Endpoint: `http://127.0.0.1:8091/mcp`. The official Spring AI server negotiates **2025-11-25**; this is checked in integration tests. This repository implements the self-hosted MCP path; it does not claim a deployed Alexa+ skill or a production Alexa account connection.

Illustrative client configuration (adapt the syntax to your MCP client):

```json
{"mcpServers":{"signalpilot":{"type":"http","url":"http://127.0.0.1:8091/mcp"}}}
```

Tools: `list_incidents`, `create_incident`, `get_incident`, `ingest_evidence`, `analyze_incident`, `record_decision`, `assign_commander`, `draft_status_update`, `generate_postmortem`.

Click **MCP → Run live MCP check** in the dashboard to watch real protocol initialization, tool discovery and a read-only tool invocation against the same server. This is a protocol diagnostic, not an AI model or prerecorded response.

Mutation tools require the current `revision` returned by `get_incident` and a fresh `requestId`. Retry the same operation with the same ID after an uncertain response. Reusing an ID with a different operation, payload, revision or actor is rejected. Approval and execution are dashboard operations, absent from the agent tool surface. The operator REST credential is rejected by MCP, and the MCP credential is rejected by the REST API.

Independent TypeScript SDK verification, against the running app:

```sh
npm ci
npm run test:mcp
npm run test:a11y
```

The MCP script performs a real session handshake, discovers tools, creates and analyzes an incident, drafts a customer update, exports an unresolved postmortem and checks missing-incident errors. It writes `artifacts/mcp-verification.json` and leaves a clearly named test incident. The accessibility check runs Axe, keyboard tab navigation, 320 px overflow and 44×44 px target checks, and fails on browser console errors.

## Where AI runs

An MCP client supplies its own model and calls the real server tools. The dashboard's default evidence evaluator and command router are deterministic and explicitly labeled; they are not an LLM. This keeps testing reproducible and never invents a provider response.

Optional **Amazon Bedrock Converse** briefing:

```powershell
$env:AI_PROVIDER='bedrock'
$env:AWS_REGION='us-east-1'
$env:BEDROCK_MODEL='amazon.nova-lite-v1:0'
# Supply an AWS profile or IAM role with bedrock:InvokeModel and access to your chosen model.
java -jar target/signalpilot-1.0.0.jar
```

Click **Generate brief**. The provider and model are displayed next to the output. Cloud errors are shown explicitly. Bedrock has a bounded timeout and token limit; evidence is marked as untrusted data, the model must return structured claims with evidence IDs, and the server rejects malformed, uncited or secret-like output before persistence. Credentials use the AWS default provider chain and are never sent to the browser. Model access and charges depend on your account. This optional path must be verified with real credentials before claiming AWS usage in a submission.

## Engineering decisions

- Java 21, Spring Boot 4.0.8, Spring AI 2.0.1, Spring JDBC, Flyway, H2/PostgreSQL and AWS SDK.
- Transactional row locks plus revision checks prevent stale or conflicting actions.
- New evidence invalidates previous proposals and approvals.
- Resolution requires two consecutive healthy observations after mitigation.
- Qualitative evidence strength and alternative hypotheses expose uncertainty.
- Evidence, timeline events and recovery metrics use indexed append-only tables; request replays retain the original response and are bounded per incident.
- List responses are paginated and histories have explicit retention ceilings.
- Responsive, keyboard-accessible UI with safe text rendering and no runtime CDN dependencies.
- Optional browser dictation fills the command box; review and send explicitly. Text works everywhere.
- CI generates a CycloneDX SBOM, audits npm dependencies, scans source and container images with Trivy, records the JAR checksum and exposes commit/artifact identity through `/actuator/info`.

## Container / remote use

Copy `.env.example` to `.env`, replace both credential placeholders with different random values, then run `docker compose up --build`. Enter the operator credential in the dashboard settings. Configure MCP clients with the separate MCP credential. Compose binds only to localhost, runs read-only without Linux capabilities, and persists `/app/data` in a named volume. The Docker build runs tests. Docker runtime verification is reported separately in `docs/verification.md`.

For a remote host set `SIGNALPILOT_BIND=0.0.0.0`, `SIGNALPILOT_OPERATOR_TOKEN`, `SIGNALPILOT_MCP_TOKEN`, and `SIGNALPILOT_ORIGIN=https://your-host`. Put the app behind HTTPS, restrict network access and persist `/app/data`. Send `Authorization: Bearer …` from each client using only its scoped credential. Port defaults to 8091; override `PORT` as needed. Requests are limited to 128 KiB, with configurable general/brief rates and per-client concurrency. These controls are process-local; there is no multi-user identity, OAuth, distributed rate limiting or tenant isolation in this hackathon version.

For PostgreSQL, set `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`. Flyway runs the versioned schema migrations at startup. H2 remains the zero-credential local default. See `.env.example` for the complete configuration surface.

## Verification and submission materials

- [Verification evidence](docs/verification.md)
- [Architecture](docs/architecture.md)
- [Submission draft](devpost-submission.md)
- [Demo script and recording](docs/demo-script.md)
- [Product feedback and friction log](docs/product-feedback.md)

All sample telemetry, fixes and recovery checks are simulated. The project is a functional hackathon prototype, not a production incident-management service. No measured customer outcomes or contest award are claimed.

MIT licensed. Built with assistance from Codex.
