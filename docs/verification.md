# Verification evidence

Original demo checks ran September 23–25, 2026 using JDK 21.0.11 on Windows 11. The production-hardening verification below was rerun on October 17, 2026.

## Passed

- **18 Java tests:** real Spring Boot server, JDBC/Flyway and H2 integration tests plus focused structured-brief and runtime-identity tests; not mocks of the domain lifecycle.
- Approval before execution; two consecutive healthy samples before resolution; a failed sample resets the gate.
- Provider-outage scenario chooses circuit breaker; insufficient evidence abstains.
- New evidence clears previous proposals/approvals; stale revisions fail.
- Duplicate request IDs replay the original response, conflicting key reuse is rejected, and two concurrent writes at one revision cannot both succeed.
- Resolved incidents reject operational mutations.
- Cross-origin API calls rejected; effective alternate ports accepted; separate operator/MCP credentials enforced; chunked bodies over 128 KiB rejected; rate limits return `429` with `Retry-After`.
- Real MCP initialize response negotiates 2025-11-25 and exposes nine tools.
- **Independent TypeScript MCP SDK:** session initialization, tools/list, actual creation/analysis/update/postmortem calls, and missing-incident tool error. See `artifacts/mcp-verification.json`.
- **18 browser assertions:** full UI lifecycle, evidence navigation, approval, healthy/failed recovery, local-provider label, communications, postmortem, 390px mobile layout without horizontal overflow, provider/unknown scenarios, HTML-injection text rendering, and a live browser MCP handshake/discovery/tool call. No browser errors reported. See `artifacts/browser-verification.json`.
- **Accessibility automation:** Axe serious/critical scan, ARIA tab keyboard behavior, 320 px layout overflow, 44×44 px visible target sizing and browser-console checks. See `target/accessibility-verification.json` in CI artifacts.
- CycloneDX JSON SBOM generation, npm audit, pinned CI actions/base images, Trivy filesystem/container scanning, Maven-wrapper checksum verification, release checksum and build-provenance attestation are configured in CI.
- Local `npm audit --audit-level=high` reported zero vulnerabilities after updating the MCP TypeScript SDK to 1.32.1. The generated CycloneDX 1.6 SBOM contains 146 components and is embedded in the executable JAR.
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
npm run test:a11y
npx agent-browser install
node scripts/browser-check.mjs
```

The browser regression creates clearly simulated incidents. Do not run it against a workspace whose current operator is recording a demonstration. The Windows video script requires FFmpeg, ffprobe and installed System.Speech.

## Not claimed as verified

- Live Bedrock invocation/model quality: AWS credentials and model access were not supplied.
- Native Alexa+ account connectivity: the deliverable is the self-hosted MCP server path.
- Browser microphone behavior: capability and permission dependent; text input is the verified path.
- Docker runtime: Docker Desktop's engine was unavailable locally. Dockerfile and Compose are supplied, not represented as locally runtime-tested.
- Multi-user identity, distributed rate limiting, tenant isolation, durability under hardware failure, load scalability or incident time reduction.
- Public video publication: a YouTube/Vimeo URL is still required.

## Public verification

The [public repository](https://github.com/zephyr45/signalpilot) has a GitHub-recognized MIT license. The earlier [GitHub Actions run 36027965434](https://github.com/zephyr45/signalpilot/actions/runs/36027965434) passed the original 11-test build. A new workflow run is required after the hardening commit; do not treat the earlier run as evidence for the new controls.
