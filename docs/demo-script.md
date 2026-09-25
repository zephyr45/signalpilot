# SignalPilot demo — 2:36

Prepared recording: `artifacts/signalpilot-demo.mp4` (H.264/AAC, 1440×1000). English narration uses the installed Windows synthesis voice. All visible telemetry and operational outcomes are labeled simulations.

## Story

1. **Problem and incident:** a small engineering team sees checkout failure following a release. Show the incident summary, error rate, latency and source evidence.
2. **Evidence:** analyze and follow E2/E3. Explain why deployment-associated pool exhaustion leads, why an alternative remains possible, and why evidence strength is not a probability.
3. **Decision:** review the proposed rollback, approve it, then execute the simulation. Explain revision checks and approval invalidation.
4. **Recovery:** one healthy sample, a failed sample, then two consecutive passes. Show resolution only becomes available after verified recovery.
5. **Communication and learning:** resolve, generate a customer-safe update and inspect the postmortem.
6. **Integration:** run the live browser MCP diagnostic. Show the 2025-11-25 handshake, nine discovered tools and an actual `draft_status_update` tool response. Explain local deterministic defaults and optional Bedrock; no production Alexa account or live LLM is implied.

Full narration source: `scripts/narrate.ps1`. Recording source: `scripts/record-demo.mjs`.

## Re-record

Start the application, then on Windows:

```powershell
npm ci
npx agent-browser install
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/narrate.ps1
node scripts/record-demo.mjs
```

The script creates a new scenario through the UI, records actual browser interactions, and muxes narration with FFmpeg. It does not replace the video with fabricated screens. The resulting file must be uploaded **publicly to YouTube or Vimeo** for the official submission. Re-recording is optional; the ready-made MP4 is usable now.

## Suggested video title

SignalPilot — Evidence, Decisions, Verified Recovery | Amazon Developer Hackathon

## Suggested video description

SignalPilot is a Java/Spring Boot incident command workbench and self-hosted MCP server. This demo shows evidence-linked analysis, human-approved simulated mitigation, recovery verification and postmortem export. The server negotiates MCP 2025-11-25 over Streamable HTTP. Telemetry and operational actions are simulated. Local analysis is deterministic; external MCP clients provide their own model, and an optional Bedrock briefing adapter is included. Built for the Alexa+ self-hosted MCP track.
