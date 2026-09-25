# SignalPilot

Draft for **Build, Ship, Shape: Amazon Developer Hackathon**. Track: **Alexa+ — self-hosted MCP server**. This document is not a submitted Devpost entry.

## One-line Summary

An incident command workbench that gives AI agents evidence-linked investigation tools while operators control remediation and verify recovery.

## Problem

When a small engineering team faces an outage, diagnostic clues, mitigation decisions and stakeholder updates are scattered. A plausible AI explanation is not enough: responders need to see the supporting evidence, know which changes were authorized, and prove the service recovered.

## Solution

SignalPilot unifies the incident lifecycle in a Spring Boot service and an incident dashboard. An AI client can discover and invoke nine MCP tools over Streamable HTTP. The operator can inspect source evidence, review proposed actions, run a clearly labeled mitigation simulation and resolve the incident only after two consecutive healthy observations. A persisted timeline becomes a Markdown postmortem.

## Why This Matters

The project explores how AI agents can help a small on-call team without erasing uncertainty or confusing recommendations with authority. Judges can reproduce both success and failure paths without production access, hardware, or a model key. No measured reduction in incident duration or real customer outcome is claimed.

## How We Used AI

The self-hosted MCP server provides structured observations and workflow tools to a connected AI client. The client brings its model. Server tools return evidence IDs, alternate hypotheses and explicit uncertainty, giving the model durable context instead of an unstructured log dump.

The dashboard's local evidence evaluator, basic command router, status drafts and default briefing are deterministic; they are labeled accordingly and are not presented as model inference. An optional Amazon Bedrock Converse adapter generates an evidence-grounded commander brief with a provider label and bounded timeout. That cloud path has not been verified with live AWS credentials and is not claimed as an AWS mini-challenge achievement.

## How We Used Codex

Codex helped compare project directions, implement the Java service and interface, write integration tests, debug a Spring bean-name collision, verify the MCP server through a separate SDK client, test the browser lifecycle, capture screenshots, and generate an English narrated demo.

## Key Features

- Three reproducible incident scenarios plus custom evidence intake.
- Evidence-linked hypotheses, alternative explanations and abstention when evidence is insufficient.
- Human approval for simulated mitigation; new evidence invalidates old proposals and approvals.
- Revision preconditions, database row locks and request deduplication.
- Recovery gate requiring two consecutive observations below 1% errors and 500 ms p95.
- Customer, engineering and executive update drafts; nothing is sent automatically.
- Persisted incident timeline and downloadable Markdown postmortem.
- Responsive dashboard, optional browser dictation, token protection and origin checks.
- Nine real MCP tools using the official Spring AI transport implementation.

## Architecture

Java 21 → Spring Boot 4.0.8 → Spring AI 2.0.1 MCP server → shared incident service → transactional JDBC/H2 persistence. A vanilla JavaScript dashboard uses the same domain logic through REST. Optional AWS SDK Bedrock Converse produces a textual briefing and cannot mutate incident actions.

The MCP integration negotiates protocol **2025-11-25** and uses **Streamable HTTP**. There is no claim of a production Alexa+ account link, certified Alexa skill, event-sourced backend or tamper-proof audit store.

## Testing Instructions

1. Clone the repository; install JDK 21.
2. Run `./mvnw verify` (Windows: `.\mvnw.cmd verify`).
3. Run `java -jar target/signalpilot-1.0.0.jar` and open `http://127.0.0.1:8091`.
4. Launch the checkout scenario, analyze, follow an evidence link, review and approve the proposal, then run its simulation.
5. Pass one recovery check, simulate a failed check, then pass two checks. Confirm resolution appears only after the consecutive passes.
6. Resolve the incident, draft a customer update and export the postmortem.
7. Try the provider-outage scenario and the insufficient-evidence scenario to see different decisions.
8. Optionally run `npm ci && npm run test:mcp` against the running app. It uses an independent MCP SDK client to execute actual tools. `node scripts/browser-check.mjs` reruns the browser regression after installing the browser with `npx agent-browser install`.

No paid service or credentials are needed for the core workflow. Local data persists under `data/`. Bedrock and browser microphone access are optional.

## Public Demo Link

Not hosted. The rules do not require a hosted website; use the reproducible local run instructions and recorded demo. Do not put a localhost URL in the public website field.

## Public Repository Link

[https://github.com/zephyr45/signalpilot](https://github.com/zephyr45/signalpilot) — verified public, with a GitHub-recognized MIT license and complete source/run instructions.

## Demo Video

Prepared file: `artifacts/signalpilot-demo.mp4` — **approximately 2 minutes 36 seconds** (155.81 seconds), 1440×1000, H.264 video with English synthesized narration. Shows the running application, evidence, approval, simulated mitigation, failed and successful recovery checks, communications, postmortem, and live MCP initialization, discovery and tool execution.

**Remaining required external action:** upload this file to YouTube or Vimeo with public visibility and put the resulting URL here. A GitHub video asset does not replace the required YouTube/Vimeo link.

## Screenshot Shot List

1. `artifacts/02-investigation.png` — incident overview, telemetry, cited hypotheses and response plan.
2. `artifacts/03-evidence.png` — evidence provenance and source IDs.
3. `artifacts/04-recovery.png` — completed lifecycle and recovery checks.
4. `artifacts/05-postmortem.png` — exported incident knowledge.
5. `artifacts/06-mobile.png` — mobile layout.

## Submission Readiness Notes

The application, public source and local demo assets are complete. See `docs/verification.md` for exact tests and limits. The mandatory public YouTube/Vimeo URL is still needed before the entry is ready to send. Required personal eligibility attestations must come from the participant. This draft neither changes nor replaces any existing Devpost entry on the account.

## Known Limitations

All operational telemetry and remediation are simulated. Hypothesis strength is rule-based and not a calibrated probability. Evidence sources are supplied by callers rather than independently authenticated. The cloud briefing and native microphone path are not verified with live accounts/devices. Single-workspace token authentication is not a multi-user authorization system. Docker configuration is included but the local Docker engine was unavailable. No production availability or scale claim is made.

## TODO Official Form Fields

These IDs and labels were rechecked against the live Devpost form on 2026-09-24. Re-fetch before final submission in case the form changes.

| Field ID | Official field | Draft answer |
| --- | --- | --- |
| 28285 | Submitter Type | Individual |
| 28286 | Organization Name (if applicable) | N/A |
| 28287 | Submitter Country of Residence | Participant must provide; do not infer from timezone |
| 28288 | If you or any team members reside in Canada, please indicate the province below. | Participant to confirm; N/A if not in Canada |
| 28289 | Which Primary Track(s) are you submitting your project into? | Alexa+ |
| 28290 | Provide a URL to your code repository for judging and testing on GitHub. | https://github.com/zephyr45/signalpilot |
| 28291 | Is your project new or existing prior to August 31, 2026? | New |
| 28292 | If you are submitting an existing project, please explain what and how you updated it during the submission period. | Not applicable; fresh project built September 23–24 |
| 28293 | Are you submitting for the AWS Builder Mini Challenge? | No — live AWS usage not verified |
| 28294 | AWS Builder Mini Challenge Submission Requirement: Which AWS services did you incorporate and how? | N/A — not entering. Optional Bedrock Converse adapter is present, but no verified cloud invocation is claimed. |
| 28295 | Are you submitting for the Open Source Mini Challenge? | No — no separate additional contribution is claimed |
| 28296–28299 | Open Source Mini Challenge fields | Omit when not entering |
| 28300 | [Optional] Feature Requests | See `docs/product-feedback.md` |
| 28301 | [Optional] Friction Log | https://github.com/zephyr45/signalpilot/blob/main/docs/product-feedback.md |
| 28302 | [Optional] Project Testing Link | Omit; local instructions provided |
| 28303–28307 | Feedback Questions 1–5 | Ready-to-paste answers in `docs/product-feedback.md` |
| 28308 | Age | Participant must explicitly attest |
| 28309 | Eligible Jurisdiction | Participant must explicitly attest |
| 28310 | Employee | Participant must explicitly attest |

The video URL is also required. The form does not ask for a Codex session ID.
