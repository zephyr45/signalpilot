# Verification evidence

Checked locally September 23–24, 2026 using JDK 21.0.11 on Windows 11.

## Passed

- **11 Java integration tests:** 10 incident/protocol tests and 1 access test. Real Spring Boot server, JDBC and H2; not mocks of the domain lifecycle.
- Approval before execution; two consecutive healthy samples before resolution; a failed sample resets the gate.
- Provider-outage scenario chooses circuit breaker; insufficient evidence abstains.
- New evidence clears previous proposals/approvals; stale revisions fail.
- Duplicate request IDs do not duplicate changes; two concurrent writes at one revision cannot both succeed.
- Resolved incidents reject operational mutations.
- Cross-origin API calls rejected; configured token required on API and MCP endpoints.
- Real MCP initialize response negotiates 2025-11-25 and exposes nine tools.
- **Independent TypeScript MCP SDK:** session initialization, tools/list, actual creation/analysis/update/postmortem calls, and missing-incident tool error. See `artifacts/mcp-verification.json`.
- **18 browser assertions:** full UI lifecycle, evidence navigation, approval, healthy/failed recovery, local-provider label, communications, postmortem, 390px mobile layout without horizontal overflow, provider/unknown scenarios, HTML-injection text rendering, and a live browser MCP handshake/discovery/tool call. No browser errors reported. See `artifacts/browser-verification.json`.
- H2 incidents survived stopping and restarting the application.
- Executable JAR packaged successfully after stopping the Windows process that locked the previous build.
- Final demo metadata checked: 155.81 seconds, H.264/AAC, 1440×1000; visual contact sheet and live MCP scene inspected, full English speech track preserved and non-silent. Final packaging completed September 25.

## Reproduce

```powershell
.\mvnw.cmd verify
java -jar target/signalpilot-1.0.0.jar
# In another terminal:
npm ci
npm run test:mcp
npx agent-browser install
node scripts/browser-check.mjs
```

The browser regression creates clearly simulated incidents. Do not run it against a workspace whose current operator is recording a demonstration. The Windows video script requires FFmpeg, ffprobe and installed System.Speech.

## Not claimed as verified

- Live Bedrock invocation/model quality: AWS credentials and model access were not supplied.
- Native Alexa+ account connectivity: the deliverable is the self-hosted MCP server path.
- Browser microphone behavior: capability and permission dependent; text input is the verified path.
- Docker runtime: Docker Desktop's engine was unavailable locally. Dockerfile and Compose are supplied, not represented as locally runtime-tested.
- Production security, multi-tenancy, durability under hardware failure, load scalability or incident time reduction.
- Public video publication: a YouTube/Vimeo URL is still required.

## Public verification

The [public repository](https://github.com/zephyr45/signalpilot) has a GitHub-recognized MIT license. [GitHub Actions run 36027965434](https://github.com/zephyr45/signalpilot/actions/runs/36027965434) passed on Ubuntu: wrapper build, all 11 Java tests, executable startup and the independent SDK MCP test. Its verification artifact contains Surefire reports, server log and MCP results. Browser regression was run locally; it is not claimed as part of that CI job.
