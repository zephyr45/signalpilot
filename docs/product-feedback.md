# Product feedback and observed friction

Prepared from actual build and verification work, not from imagined production usage. No live Amazon device or Bedrock call was made.

## Feedback Question 1: Which developer tools, APIs, and SDKs did you use and for what?

Java 21 and Spring Boot 4.0.8 host the incident API and static dashboard. Spring AI 2.0.1 exposes nine tools through its Streamable HTTP MCP server. The TypeScript MCP SDK 1.30.0 provides independent protocol verification. Spring JDBC and H2 persist incident aggregates and audit timelines. AWS SDK Bedrock Runtime 2.31.54 implements an optional Converse briefing adapter, compiled but not invoked with live credentials. JUnit/AssertJ test the lifecycle, concurrency and access checks. Maven and its wrapper package the app. HTML/CSS/JavaScript implement the UI. Agent-browser 0.27.0 automates browser checks and recording; FFmpeg muxes the demo and installed Windows speech synthesis narrates it. Codex implemented, tested and documented the project. The Devpost connector supplied live track and form requirements. Docker and GitHub Actions configuration are included; actual verification status is recorded separately.

## Feedback Question 2: For each tool, API, or SDK used in your project, what worked well?

Spring AI let the project use an official MCP transport rather than implementing JSON-RPC by hand. An independent SDK successfully negotiated a session and invoked the real Java tools. Spring transactions and H2 row locks supported the concurrent-update checks. The Maven wrapper makes the Java toolchain reproducible. Browser automation verified the entire incident lifecycle and mobile overflow, while FFmpeg and speech synthesis produced a 153-second demo without external media assets. The Devpost connector exposed exact form field IDs, making the draft specific to this event. AWS SDK types compiled cleanly; runtime feedback on Bedrock is not available without a live invocation.

## Feedback Question 3: For each tool, API, or SDK used in your project, what needs work?

The project encountered Windows tooling friction: an unquoted Maven -D value was split by PowerShell, and rebuilding a currently running JAR failed because Windows held it open. Agent-browser pointer interactions with controls below the viewport were inconsistent in this setup, so automated regression uses DOM-backed clicks after inspecting the interface. Its daemon could keep inherited output pipes open when launched from Node; writing generated CLI logs to a file resolved that. These are observed local integration issues, not claims of Amazon service defects. No supported assessment of Bedrock onboarding, latency or reliability is possible from a compile-only adapter.

## Feedback Question 4: For each tool, API, or SDK used in your project, how was your onboarding experience?

The initial Java scaffold compiled with available JDK 21 and Maven. A bean callback provider initially reused a component's name and prevented startup; the integration test exposed it, and giving the callback provider a distinct name fixed it. Official Spring AI documentation identified the Streamable HTTP configuration; protocol and tools/call verification passed against an independent SDK. H2 enabled credential-free local persistence. Browser installation and an explicit viewport were needed for repeatable screenshots. The public hackathon tooling requirement could be satisfied by the self-hosted MCP path without an Alexa production account. Bedrock account onboarding remains untested.

## Feedback Question 5: Would you build with these devices and services again?

Yes for Spring Boot, Spring AI and MCP: this combination fits a Java backend project and provides a testable interface for external AI agents. The strongest benefit was separating agent-accessible investigation tools from the dashboard's operational controls. H2 is useful for a reproducible single-workspace demo; a production version would need separate identity, authorization, persistence and operational design. Bedrock remains a candidate for future verified integration, not a service for which this build provides runtime evidence.

## Optional feature requests

- **Important:** publish a small Java reference MCP server plus an executable protocol conformance example alongside the Alexa+ hackathon resources. It would help participants verify the required revision and transport early.
- **Nice-to-have:** provide a standard submission checklist distinguishing a self-hosted MCP server, a simulated Alexa+ experience and a production Alexa+ integration, so demos use precise claims.

## Friction log

| Attempt | Expected / actual | Severity | Workaround | Actionable suggestion |
| --- | --- | --- | --- | --- |
| Start the Spring app after registering tool callbacks | Startup expected; duplicate bean name prevented startup | Moderate, project configuration error | Rename callback provider bean | Give example callback-provider beans names distinct from tool classes |
| Generate wrapper using an unquoted Maven property in PowerShell | Wrapper generation expected; `.9.16` interpreted as lifecycle phase | Minor | Quote the complete `-Dmaven=3.9.16` argument | Show PowerShell-safe commands separately in setup docs |
| Package while the Windows JVM ran that JAR | Build expected; repackage rename failed on locked artifact | Moderate | Stop the known app process; run a separate copied JAR during development | Explain Windows file-lock behavior in run instructions |
| Launch browser CLI from synchronous Node subprocess | Command printed success but inherited pipes stayed open | Moderate | Route generated CLI output to a file | Ensure daemon startup closes inherited output handles |
| Verify Docker container locally | Expected an available engine; Docker Desktop Linux pipe absent | Environment limitation | Verify Java build directly; mark container runtime untested | Include a documented non-Docker route |

No friction log entry asserts an issue with an Amazon device or service that was not actually exercised.
