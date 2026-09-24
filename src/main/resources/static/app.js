"use strict";
const $ = (s) => document.querySelector(s);
const esc = (s) =>
  String(s ?? "").replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
let incident = null,
  incidents = [],
  activeTab = "investigation",
  busy = false,
  toastTimer;
const id = () => crypto.randomUUID();
const clock = (t) =>
  new Date(t).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
async function api(path, method = "GET", body) {
  const token = sessionStorage.getItem("signalpilot-token");
  const r = await fetch("/api" + path, {
    method,
    headers: {
      ...(body ? { "Content-Type": "application/json" } : {}),
      ...(token ? { Authorization: "Bearer " + token } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!r.ok) {
    let e;
    try {
      e = await r.json();
    } catch {}
    if (r.status === 401)
      toast("Set your access token using the settings button.", true);
    throw new Error(e?.error || `Request failed (${r.status})`);
  }
  return r.headers.get("content-type")?.includes("application/json")
    ? r.json()
    : r.text();
}
function toast(message, error = false) {
  clearTimeout(toastTimer);
  const t = $("#toast");
  t.textContent = message;
  t.className = error ? "error" : "";
  t.hidden = false;
  toastTimer = setTimeout(() => (t.hidden = true), 6500);
}
async function task(fn) {
  if (busy) return;
  busy = true;
  document.body.setAttribute("aria-busy", "true");
  try {
    await fn();
  } catch (e) {
    toast(e.message, true);
  } finally {
    busy = false;
    document.body.removeAttribute("aria-busy");
  }
}
function modal(title, body, submitLabel, onSubmit) {
  $("#modal-title").textContent = title;
  $("#modal-body").innerHTML = body;
  $("#modal-submit").textContent = submitLabel;
  $("#modal-submit").hidden = !onSubmit;
  $("#modal-form").onsubmit = async (e) => {
    e.preventDefault();
    if (!onSubmit) return;
    await task(async () => {
      await onSubmit(new FormData(e.target));
      $("#modal").close();
    });
  };
  $("#modal").showModal();
}
$("#close-modal").onclick = $("#cancel-modal").onclick = () =>
  $("#modal").close();
function refs(ids) {
  return `<div class="refs">${ids.map((e) => `<button class="ref" data-ref="${esc(e)}">↗ ${esc(e)}</button>`).join("")}</div>`;
}
function renderList() {
  $("#incident-count").textContent = incidents.length;
  $("#incident-list").innerHTML = incidents
    .map(
      (i) =>
        `<button class="incident-link ${i.id === incident?.id ? "active" : ""}" data-incident="${esc(i.id)}" title="${esc(i.title)}">${i.status === "RESOLVED" ? "✓" : "◌"} &nbsp; ${esc(i.title)}</button>`,
    )
    .join("");
}
async function loadList() {
  incidents = await api("/incidents");
  renderList();
}
async function selectIncident(i) {
  incident = await api("/incidents/" + i);
  localStorage.setItem("signalpilot-active", i);
  $("#command-response").hidden = true;
  render();
  renderList();
  if (activeTab === "postmortem") await loadPostmortem();
}
async function changed(i) {
  incident = i;
  render();
  await loadList();
}
async function command(operation, value = "", revision = incident.revision) {
  await changed(
    await api(`/incidents/${incident.id}/commands`, "POST", {
      operation,
      value,
      revision,
      requestId: id(),
    }),
  );
}
function chart() {
  const m = incident.metrics,
    max = Math.max(15, ...m.map((x) => x.errorRate));
  const points = m.map((x, n) => [
    30 + (n / Math.max(1, m.length - 1)) * 680,
    100 - (x.errorRate / max) * 80,
  ]);
  const path = points.map((p, n) => (n ? "L" : "M") + p.join(",")).join(" ");
  $("#chart").innerHTML =
    `<defs><linearGradient id="chart-fill" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#efaa81" stop-opacity=".18"/><stop offset="100%" stop-color="#efaa81" stop-opacity="0"/></linearGradient></defs>${[20, 60, 100].map((y) => `<line x1="30" y1="${y}" x2="710" y2="${y}" stroke="#303943" stroke-dasharray="3 6"/>`).join("")}${points.length ? `<path d="${path} L710,106 L30,106 Z" fill="url(#chart-fill)"/><path d="${path}" fill="none" stroke="#efa87f" stroke-width="2.5" stroke-linejoin="round"/>` : ""}${points.map((p, n) => `<circle cx="${p[0]}" cy="${p[1]}" r="3" fill="#f0b08b"><title>${m[n].errorRate}% errors, ${m[n].p95Ms} ms p95 — ${esc(m[n].source)}</title></circle>`).join("")}`;
}
function render() {
  if (!incident) return;
  const i = incident,
    last = i.metrics.at(-1),
    resolved = i.status === "RESOLVED";
  $("#empty").hidden = true;
  $("#incident-view").hidden = false;
  $("#breadcrumb").textContent = i.id.slice(0, 8).toUpperCase();
  $("#short-id").textContent = "INC / " + i.id.slice(0, 8).toUpperCase();
  $("#incident-title").textContent = i.title;
  $("#service-name").textContent = i.service;
  $("#incident-age").textContent = "Opened " + clock(i.createdAt);
  $("#assign-btn").textContent =
    i.commander === "Unassigned" ? "Assign commander" : i.commander;
  $("#assign-btn").disabled = resolved;
  $("#status-value").className = "status-text " + (resolved ? "good" : "warn");
  $("#status-value").textContent =
    (resolved ? "✓ " : "◉ ") +
    i.status.charAt(0) +
    i.status.slice(1).toLowerCase();
  $("#status-detail").textContent =
    i.severity + " · " + (resolved ? "Recovery verified" : "Active response");
  $("#status-detail").title = i.severityReason;
  $("#error-value").textContent = last ? last.errorRate.toFixed(1) + "%" : "—";
  $("#error-value").className = last && last.errorRate >= 1 ? "warn" : "good";
  $("#latency-value").textContent = last
    ? last.p95Ms >= 1000
      ? (last.p95Ms / 1000).toFixed(1) + " s"
      : last.p95Ms + " ms"
    : "—";
  $("#evidence-value").textContent = String(i.evidence.length).padStart(2, "0");
  $("#evidence-tab-count").textContent = i.evidence.length;
  const healthy = last && last.errorRate < 1;
  $("#service-map").innerHTML =
    `<div class="service-node">Storefront<small>● Reachable</small></div><span class="connector"></span><div class="service-node ${healthy ? "" : "warning"}">${esc(i.service)}<small>● ${healthy ? "Healthy" : "Degraded / unknown"}</small></div><span class="connector"></span><div class="service-node ${i.scenario === "provider" && !healthy ? "warning" : ""}">${i.scenario === "provider" ? "Payment API" : "Database pool"}<small>● ${healthy ? "Recovered" : "Under observation"}</small></div>`;
  chart();
  $("#analyze-btn").disabled = i.status !== "INVESTIGATING";
  $("#brief-btn").disabled = resolved;
  $("#add-evidence-btn").disabled = resolved || i.status !== "INVESTIGATING";
  $("#add-note-btn").disabled = resolved;
  $("#hypotheses").innerHTML = i.hypotheses.length
    ? i.hypotheses
        .map(
          (h, n) =>
            `<article class="finding"><div class="finding-top"><span class="label">${n ? "ALTERNATIVE HYPOTHESIS" : "LEADING ASSESSMENT"}</span><span class="pill">${esc(h.strength)} evidence</span></div><h3>${esc(h.title)}</h3><p>${esc(h.explanation)}</p>${refs(h.evidenceIds)}</article>`,
        )
        .join("")
    : '<div class="placeholder">The signals are here. Analyze the evidence to build a response plan.</div>';
  $("#actions").innerHTML = i.actions.length
    ? i.actions
        .map(
          (a) =>
            `<div class="action-item"><h3>${esc(a.title)}</h3><p>${esc(a.rationale)}<br><strong>Success looks like:</strong> ${esc(a.expectedOutcome)}</p>${refs(a.evidenceIds)}<div class="action-footer"><span class="action-state">${a.status === "PROPOSED" ? "○ Awaiting approval" : a.status === "APPROVED" ? "✓ Approved by " + esc(a.approvedBy) : "✓ Simulation applied"}</span><div class="action-buttons">${a.status === "PROPOSED" ? `<button class="primary" data-approve="${esc(a.id)}">Review & approve</button>` : a.status === "APPROVED" ? `<button class="primary" data-execute="${esc(a.id)}">Run simulation</button>` : resolved ? '<span class="good">Incident resolved</span>' : `<button class="secondary" data-sample="unhealthy">Simulate failed check</button><button class="primary" data-sample="healthy">Check recovery</button>`}</div></div></div>`,
        )
        .join("")
    : i.hypotheses.length
      ? '<p class="subtle">No remediation proposed. More evidence is needed before making an operational change.</p>'
      : '<p class="subtle">Analyze evidence to generate a proposed response.</p>';
  if (i.verifiedAt && !resolved)
    $("#actions").innerHTML +=
      '<div class="action-footer"><span class="good">✓ Two healthy checks passed</span><button class="primary" id="resolve-btn">Resolve incident</button></div>';
  $("#brief-text").textContent =
    i.brief || "A concise handoff grounded in the current evidence.";
  $("#brief-source").textContent =
    i.briefProvider === "Not generated"
      ? "Local template by default · Bedrock available when configured"
      : i.briefProvider;
  $("#evidence-cards").innerHTML =
    i.evidence
      .map(
        (e) =>
          `<article class="evidence-card" id="evidence-${esc(e.id)}"><h3><span class="ref">${esc(e.id)}</span>${esc(e.kind.replaceAll("_", " "))}</h3><p>${esc(e.summary)}</p><small>${esc(e.source)} · ${clock(e.at)}</small></article>`,
      )
      .join("") || "<p>No evidence yet. Add observations to begin.</p>";
  $("#event-count").textContent = i.timeline.length + " events";
  $("#timeline").innerHTML = [...i.timeline]
    .reverse()
    .map(
      (e) =>
        `<li><time datetime="${esc(e.at)}">${clock(e.at)} · ${esc(e.actor)}</time><strong>${esc(e.type.replaceAll("_", " "))}</strong><p>${esc(e.detail)}</p></li>`,
    )
    .join("");
  const samples = i.metrics.filter(
    (m) => m.source === "Simulator recovery check",
  );
  let checks = 0;
  for (
    let n = samples.length - 1;
    n >= 0 && samples[n].errorRate < 1 && samples[n].p95Ms < 500;
    n--
  )
    checks++;
  checks = Math.min(2, checks);
  $("#recovery-indicator").textContent = i.verifiedAt
    ? "● ●  Recovery verified"
    : i.status === "MONITORING"
      ? checks
        ? "● ○  1 of 2 checks passed"
        : "○ ○  0 of 2 checks passed"
      : "○ ○  Awaiting mitigation";
}
async function createDemo(scenario = "deployment") {
  const titles = {
    deployment: "Checkout failures after v2.8 release",
    provider: "Payment provider timeout cascade",
    unknown: "Checkout degradation · cause unknown",
  };
  const i = await api("/incidents", "POST", {
    scenario,
    title: titles[scenario] || "New incident",
    service: "checkout-api",
  });
  await changed(i);
  localStorage.setItem("signalpilot-active", i.id);
  switchTab("investigation");
}
function newIncident() {
  modal(
    "Start an incident",
    '<p>Choose a reproducible scenario or bring your own evidence.</p><label for="scenario">Scenario</label><select id="scenario" name="scenario"><option value="deployment">Checkout deployment regression</option><option value="provider">Payment provider timeout cascade</option><option value="unknown">Insufficient evidence</option><option value="custom">Custom incident</option></select><label for="new-title">Custom incident title (used for custom scenario)</label><input id="new-title" name="title" maxlength="160" placeholder="What is happening?"><label for="new-service">Service (used for custom scenario)</label><input id="new-service" name="service" maxlength="80" value="checkout-api">',
    "Create incident",
    async (f) => {
      if (f.get("scenario") === "custom") {
        const i = await api("/incidents", "POST", {
          scenario: "custom",
          title: f.get("title"),
          service: f.get("service"),
        });
        await changed(i);
        localStorage.setItem("signalpilot-active", i.id);
      } else await createDemo(f.get("scenario"));
    },
  );
}
function switchTab(name) {
  activeTab = name;
  document
    .querySelectorAll("[data-tab]")
    .forEach((b) =>
      b.setAttribute("aria-selected", String(b.dataset.tab === name)),
    );
  document
    .querySelectorAll(".tab-content")
    .forEach((t) => (t.hidden = t.id !== "tab-" + name));
  if (name === "postmortem") task(loadPostmortem);
}
async function loadPostmortem() {
  $("#postmortem-text").textContent = await api(
    `/incidents/${incident.id}/postmortem`,
  );
}
$("#start-demo").onclick = () => task(() => createDemo());
$("#new-incident").onclick = newIncident;
$("#nav-incidents").onclick = () =>
  window.scrollTo({ top: 0, behavior: "smooth" });
document.addEventListener("click", (e) => {
  const b = e.target.closest("button");
  if (!b) return;
  if (b.dataset.incident) task(() => selectIncident(b.dataset.incident));
  if (b.dataset.tab) switchTab(b.dataset.tab);
  if (b.dataset.ref) {
    switchTab("evidence");
    $("#evidence-" + b.dataset.ref)?.scrollIntoView({
      behavior: "smooth",
      block: "center",
    });
  }
  if (b.dataset.approve) {
    const revision = incident.revision,
      a = incident.actions.find((a) => a.id === b.dataset.approve);
    modal(
      "Review proposed action",
      `<h3>${esc(a.title)}</h3><p>${esc(a.rationale)}</p><p class="modal-warning">This approves a simulation only. No production infrastructure will be changed. New evidence invalidates this proposal.</p><p>Expected outcome: ${esc(a.expectedOutcome)}</p>`,
      "Approve simulation",
      () => command("approve", a.id, revision),
    );
  }
  if (b.dataset.execute) task(() => command("execute", b.dataset.execute));
  if (b.dataset.sample) task(() => command("sample", b.dataset.sample));
  if (b.id === "resolve-btn") {
    const revision = incident.revision;
    modal(
      "Close with a clear handoff",
      '<p>Two recovery checks passed. Record what changed and what remains uncertain.</p><label for="resolution">Resolution summary</label><textarea id="resolution" name="resolution" required maxlength="2000">Applied the approved simulated mitigation. Two recovery checks passed. The leading hypothesis still requires independent validation.</textarea>',
      "Resolve incident",
      (f) => command("resolve", f.get("resolution"), revision),
    );
  }
});
$("#analyze-btn").onclick = () => task(() => command("analyze"));
$("#brief-btn").onclick = () =>
  task(async () => {
    toast("Generating commander briefing…");
    await changed(await api(`/incidents/${incident.id}/brief`, "POST"));
    toast("Brief ready. Provider is shown below the text.");
  });
$("#assign-btn").onclick = () => {
  const revision = incident.revision;
  modal(
    "Assign incident commander",
    '<label for="commander">Name or role</label><input id="commander" name="commander" required maxlength="80" value="On-call engineer">',
    "Assign commander",
    (f) => command("assign", f.get("commander"), revision),
  );
};
$("#add-note-btn").onclick = () => {
  const revision = incident.revision;
  modal(
    "Record a decision",
    '<label for="decision">Decision and reasoning</label><textarea id="decision" name="decision" required maxlength="2000"></textarea>',
    "Save decision",
    (f) => command("note", f.get("decision"), revision),
  );
};
$("#add-evidence-btn").onclick = () => {
  const revision = incident.revision;
  modal(
    "Add evidence",
    '<p>New evidence clears existing proposals and approvals. Reanalyze before acting.</p><label for="kind">Signal type</label><select id="kind" name="kind">' +
      [
        "NOTE",
        "ALERT",
        "DEPLOYMENT",
        "POOL_EXHAUSTION",
        "PROVIDER_DEGRADED",
        "PROVIDER_HEALTHY",
        "NO_DEPLOYMENT",
      ]
        .map((k) => `<option>${k}</option>`)
        .join("") +
      '</select><label for="source">Source</label><input id="source" name="source" required maxlength="120" placeholder="e.g. simulator / service logs"><label for="summary">Observation</label><textarea id="summary" name="summary" required maxlength="3000"></textarea>',
    "Add evidence",
    async (f) =>
      changed(
        await api(`/incidents/${incident.id}/evidence`, "POST", {
          kind: f.get("kind"),
          source: f.get("source"),
          summary: f.get("summary"),
          revision,
          requestId: id(),
        }),
      ),
  );
};
$("#draft-btn").onclick = () =>
  task(async () => {
    $("#update-text").textContent = (
      await api(
        `/incidents/${incident.id}/update?audience=` + $("#audience").value,
      )
    ).text;
  });
$("#copy-update").onclick = () =>
  task(async () => {
    await navigator.clipboard.writeText($("#update-text").textContent);
    toast("Draft copied");
  });
$("#refresh-postmortem").onclick = () => task(loadPostmortem);
$("#export-btn").onclick = () =>
  task(async () => {
    const text = await api(`/incidents/${incident.id}/postmortem`);
    const url = URL.createObjectURL(
      new Blob([text], { type: "text/markdown" }),
    );
    const a = document.createElement("a");
    a.href = url;
    a.download = `signalpilot-${incident.id.slice(0, 8)}.md`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    toast("Postmortem exported");
  });
$("#command-form").onsubmit = (e) => {
  e.preventDefault();
  task(async () => {
    const message = $("#command-input").value;
    if (!message.trim()) return;
    const r = await api(`/incidents/${incident.id}/chat`, "POST", {
      message,
      revision: incident.revision,
      requestId: id(),
    });
    await changed(r.incident);
    $("#command-response").textContent = r.reply;
    $("#command-response").hidden = false;
    $("#command-input").value = "";
  });
};
$("#voice-btn").onclick = () => {
  const Recognition =
    window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!Recognition) {
    toast(
      "Speech input is unavailable in this browser. Type a command instead.",
    );
    return;
  }
  const r = new Recognition();
  r.lang = "en-US";
  r.onresult = (e) => {
    $("#command-input").value = e.results[0][0].transcript;
    toast("Transcript ready. Review it and press Enter.");
  };
  r.onerror = () =>
    toast("Speech input failed or microphone permission was declined.", true);
  r.start();
  toast("Listening…");
};
$("#access-settings").onclick = () =>
  modal(
    "Workspace access",
    '<p>Local mode needs no token. For a remote server, enter the shared token configured by its operator. It is stored only for this browser session.</p><label for="token">Access token</label><input id="token" name="token" type="password" autocomplete="off">',
    "Save token",
    async (f) => {
      sessionStorage.setItem("signalpilot-token", f.get("token"));
      await boot();
    },
  );
$("#open-guide").onclick = () =>
  modal(
    "Evidence → decision → recovery",
    "<p><strong>1. Investigate.</strong> Launch a scenario and analyze the evidence. Every hypothesis links to source IDs; evidence strength is a rule-based label, not a calibrated probability.</p><p><strong>2. Review.</strong> Inspect a proposed action, approve it, then run the simulation. New evidence invalidates approvals.</p><p><strong>3. Verify.</strong> Collect two consecutive healthy samples. A failed sample resets the recovery gate.</p><p><strong>4. Learn.</strong> Resolve the incident, draft audience-specific updates, and export the postmortem. Everything persists on the server.</p>",
    null,
    null,
  );
$("#open-mcp").onclick = () => {
  modal(
    "Connect your AI agent",
    "<p>SignalPilot exposes nine tools through the official Spring AI MCP server. Connect a Streamable HTTP client to:</p><code>" +
      esc(location.origin) +
      '/mcp</code><p>For a remote server, send <code>Authorization: Bearer YOUR_TOKEN</code>. Use TLS. The client supplies its own AI model; this diagnostic makes real protocol calls without an LLM.</p><button type="button" id="test-mcp" class="primary">Run live MCP check</button><pre id="mcp-result" aria-live="polite"></pre><p>Approval and execution are absent from the MCP tool surface. This project supplies the self-hosted MCP path, not a production Alexa+ account connection.</p>',
    null,
    null,
  );
  $("#test-mcp").onclick = () => task(checkMcp);
};

// A bounded, read-only protocol diagnostic. AI clients use their own full MCP SDK.
async function checkMcp() {
  const output = $("#mcp-result");
  const button = $("#test-mcp");
  const log = (line) => (output.textContent += line + "\n");
  const token = sessionStorage.getItem("signalpilot-token");
  let session,
    sequence = 0,
    protocol = "2025-11-25";
  const headers = () => ({
    "Content-Type": "application/json",
    Accept: "application/json, text/event-stream",
    "MCP-Protocol-Version": protocol,
    ...(session ? { "Mcp-Session-Id": session } : {}),
    ...(token ? { Authorization: "Bearer " + token } : {}),
  });
  async function rpc(method, params, notification = false) {
    const requestId = ++sequence;
    const response = await fetch("/mcp", {
      method: "POST",
      headers: headers(),
      signal: AbortSignal.timeout(15000),
      body: JSON.stringify({
        jsonrpc: "2.0",
        ...(notification ? {} : { id: requestId }),
        method,
        params,
      }),
    });
    if (!response.ok) throw Error(`MCP ${method}: HTTP ${response.status}`);
    session = response.headers.get("Mcp-Session-Id") || session;
    if (notification) {
      await response.body?.cancel();
      return;
    }
    let message;
    if (response.headers.get("content-type")?.includes("application/json")) {
      message = await response.json();
    } else {
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";
      try {
        while (!message) {
          const { value, done } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          let boundary;
          while ((boundary = buffer.search(/\r?\n\r?\n/)) >= 0) {
            const event = buffer.slice(0, boundary);
            buffer = buffer.slice(boundary).replace(/^\r?\n\r?\n/, "");
            const data = event
              .split(/\r?\n/)
              .filter((line) => line.startsWith("data:"))
              .map((line) => line.slice(5).trimStart())
              .join("\n");
            if (data) {
              const candidate = JSON.parse(data);
              if (candidate.id === requestId) message = candidate;
            }
          }
        }
      } finally {
        await reader.cancel();
      }
    }
    if (!message || message.error)
      throw Error(message?.error?.message || "Missing MCP response");
    return message.result;
  }
  output.textContent = "";
  button.disabled = true;
  try {
    log("POST /mcp → initialize");
    const init = await rpc("initialize", {
      protocolVersion: protocol,
      capabilities: {},
      clientInfo: { name: "signalpilot-browser-diagnostic", version: "1.0.0" },
    });
    protocol = init.protocolVersion;
    log(`✓ ${init.serverInfo.name} · ${protocol} · Streamable HTTP`);
    await rpc("notifications/initialized", {}, true);
    const { tools } = await rpc("tools/list", {});
    log(
      `✓ tools/list → ${tools.length} tools\n${tools.map((t) => t.name).join(", ")}`,
    );
    const name = incident ? "draft_status_update" : "list_incidents";
    const result = await rpc("tools/call", {
      name,
      arguments: incident
        ? { incidentId: incident.id, audience: "customer" }
        : {},
    });
    if (result.isError) throw Error("MCP tool returned an error");
    log(`✓ tools/call → ${name}`);
    log(
      result.content
        .filter((c) => c.type === "text")
        .map((c) => c.text)
        .join("\n")
        .slice(0, 1400),
    );
    log("PASS — live server responses, not a prerecorded fixture.");
  } catch (error) {
    log("FAIL — " + error.message);
    throw error;
  } finally {
    button.disabled = false;
    if (session)
      await fetch("/mcp", {
        method: "DELETE",
        headers: headers(),
        signal: AbortSignal.timeout(3000),
      }).catch(() => {});
  }
}
async function boot() {
  const c = await api("/config");
  $("#provider-label").textContent =
    c.aiProvider === "bedrock"
      ? "Bedrock briefing enabled"
      : "Local evidence engine";
  await loadList();
  const saved = localStorage.getItem("signalpilot-active");
  const selected = incidents.find((i) => i.id === saved) || incidents[0];
  if (selected) await selectIncident(selected.id);
}
task(boot);
setInterval(async () => {
  if (busy || $("#modal").open || !incident) return;
  try {
    const latest = await api("/incidents/" + incident.id);
    if (latest.revision !== incident.revision) {
      incident = latest;
      render();
    }
  } catch {}
}, 8000);
