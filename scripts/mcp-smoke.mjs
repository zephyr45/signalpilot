import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";
import assert from "node:assert/strict";
import { writeFile } from "node:fs/promises";

const base = process.env.SIGNALPILOT_URL || "http://127.0.0.1:8091";
const headers = process.env.SIGNALPILOT_TOKEN
  ? { Authorization: `Bearer ${process.env.SIGNALPILOT_TOKEN}` }
  : {};
const client = new Client({
  name: "signalpilot-independent-sdk-check",
  version: "1.0.0",
});
const transport = new StreamableHTTPClientTransport(new URL("/mcp", base), {
  requestInit: { headers },
});
const report = {
  checkedAt: new Date().toISOString(),
  transport: "Streamable HTTP",
  steps: [],
};
function unwrap(result) {
  assert.ok(!result.isError, JSON.stringify(result));
  const text = result.content
    .filter((c) => c.type === "text")
    .map((c) => c.text)
    .join("\n");
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}
try {
  await client.connect(transport);
  report.server = client.getServerVersion();
  report.steps.push("SDK initialize and session established");
  const { tools } = await client.listTools();
  report.tools = tools.map((t) => t.name);
  assert.equal(tools.length, 9);
  assert.ok(!tools.some((t) => /approve|execute/.test(t.name)));
  report.steps.push(
    "Nine tools discovered; operational approval is not exposed",
  );
  let i = unwrap(
    await client.callTool({
      name: "create_incident",
      arguments: {
        scenario: "deployment",
        title: "MCP client · checkout regression",
        serviceName: "checkout-api",
      },
    }),
  );
  assert.equal(i.evidence.length, 4);
  report.incidentId = i.id;
  i = unwrap(
    await client.callTool({
      name: "analyze_incident",
      arguments: {
        incidentId: i.id,
        revision: i.revision,
        requestId: crypto.randomUUID(),
      },
    }),
  );
  assert.ok(i.hypotheses[0].evidenceIds.includes("E2"));
  assert.ok(i.actions[0].title.includes("Roll back"));
  report.steps.push(
    "Real tools/call created and analyzed an incident with cited evidence",
  );
  const update = unwrap(
    await client.callTool({
      name: "draft_status_update",
      arguments: { incidentId: i.id, audience: "customer" },
    }),
  );
  assert.ok(update.includes("investigating"));
  report.steps.push(
    "Customer update returned without internal diagnostic detail",
  );
  const md = unwrap(
    await client.callTool({
      name: "generate_postmortem",
      arguments: { incidentId: i.id },
    }),
  );
  assert.ok(md.includes("Not verified"));
  report.steps.push("Unresolved postmortem stays explicitly unverified");
  const missing = await client.callTool({
    name: "get_incident",
    arguments: { incidentId: "does-not-exist" },
  });
  assert.equal(missing.isError, true);
  report.steps.push("Missing incident returned an MCP tool error");
  report.passed = true;
  await writeFile(
    "artifacts/mcp-verification.json",
    JSON.stringify(report, null, 2),
  );
  console.log(JSON.stringify(report, null, 2));
} finally {
  await transport.terminateSession().catch(() => {});
  await client.close();
}
