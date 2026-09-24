# Build notes

The participant selected SignalPilot after brainstorming and asked for autonomous end-to-end delivery with Java/Spring Boot, minimal interruptions, and a career-focused outcome. Existing workspace projects were excluded. This implementation lives entirely in `signalpilot/`.

## Decisions

- Chose the event's self-hosted MCP path. No production Alexa+ connection is implied.
- Used the official Spring AI transport rather than custom JSON-RPC code, and verified it independently.
- Kept core workflows credential-free with explicitly labeled simulated telemetry and deterministic analysis. Optional Bedrock does not silently replace errors with fake model output.
- Preserved operator approval and recovery gates. Did not automate real production rollbacks or send messages to third parties.
- Wrote source, tests, reproducible browser checks, a recorded demo and submission documentation.

## Verification / fixes

- Fixed a duplicate Spring bean name caught during startup testing.
- Fixed duplicate-origin construction in the access filter before tests.
- Added current-revision checks, row locks, idempotency, abstention, failed-recovery and token/origin tests.
- Confirmed independent MCP tools/call interoperability.
- Verified UI flow and mobile layout; handled untrusted evidence as text.
- Repackaged after a Windows file-lock failure and confirmed persistence on restart.
- Adapted browser CLI orchestration to avoid inherited daemon output pipes.
- Recorded and inspected the 153-second demonstration with actual UI actions and English synthesized narration.

No unobserved AWS performance, production result, or contest achievement was invented. Remaining account-dependent requirements are tracked in the submission draft.
