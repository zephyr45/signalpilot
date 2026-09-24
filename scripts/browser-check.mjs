// UI regression through agent-browser. No backend state is changed outside the UI.
import { execFileSync } from "node:child_process";
import { resolve } from "node:path";
import { writeFile } from "node:fs/promises";
import { openSync, closeSync, readFileSync } from "node:fs";
const binary = resolve(
  "node_modules/agent-browser/bin/" +
    (process.platform === "win32"
      ? "agent-browser-win32-x64.exe"
      : process.platform === "darwin"
        ? "agent-browser-darwin-arm64"
        : "agent-browser-linux-x64"),
);
const report = { checkedAt: new Date().toISOString(), steps: [] };
function run(...args) {
  const log = resolve("target/browser-cli.log"),
    fd = openSync(log, "w");
  try {
    execFileSync(binary, ["--session", "signalpilot-check", ...args], {
      stdio: ["ignore", fd, fd],
      timeout: 30000,
      windowsHide: true,
    });
    return readFileSync(log, "utf8");
  } catch (e) {
    throw Error(readFileSync(log, "utf8") + "\n" + e.message);
  } finally {
    closeSync(fd);
  }
}
function js(code) {
  return run("eval", code);
}
function click(selector) {
  js(`document.querySelector(${JSON.stringify(selector)}).click()`);
  settle();
}
function settle() {
  js(
    `new Promise((resolve,reject)=>{const deadline=Date.now()+15000;const timer=setInterval(()=>{if(!busy){clearInterval(timer);resolve(true);}else if(Date.now()>deadline){clearInterval(timer);reject(Error('UI did not settle'));}},100);})`,
  );
}
function check(expression, message) {
  js(
    `(()=>{if(!(${expression}))throw Error(${JSON.stringify(message)});return true;})()`,
  );
  report.steps.push(message);
}
try {
  run("open", process.env.SIGNALPILOT_URL || "http://127.0.0.1:8091");
  run("set", "viewport", "1440", "1000");
  settle();
  click("#new-incident");
  check('document.querySelector("#modal").open', "New incident dialog opens");
  click("#modal-submit");
  settle();
  check(
    "incident.evidence.length===4",
    "Checkout scenario presents four evidence records",
  );
  click("#analyze-btn");
  check(
    'incident.actions.length===1 && document.querySelector("[data-approve]")',
    "Evidence analysis produces a reviewable action",
  );
  click("[data-ref]");
  check(
    '!document.querySelector("#tab-evidence").hidden',
    "Evidence reference opens the Evidence tab",
  );
  run("screenshot", "--full", resolve("artifacts/03-evidence.png"));
  click('[data-tab="investigation"]');
  click("[data-approve]");
  check(
    'document.querySelector("#modal").open',
    "Approval requires a review dialog",
  );
  click("#modal-submit");
  check(
    'incident.actions[0].status==="APPROVED"',
    "Human approval is persisted",
  );
  click("[data-execute]");
  click('[data-sample="healthy"]');
  check(
    '!document.querySelector("#resolve-btn")',
    "A single healthy sample does not permit resolution",
  );
  click('[data-sample="unhealthy"]');
  check(
    'incident.verifiedAt===""',
    "An unhealthy sample resets the recovery gate",
  );
  click('[data-sample="healthy"]');
  click('[data-sample="healthy"]');
  check(
    'Boolean(document.querySelector("#resolve-btn"))',
    "Two consecutive healthy samples unlock resolution",
  );
  click("#brief-btn");
  check(
    'incident.briefProvider.includes("no LLM")',
    "Default brief transparently identifies the non-LLM provider",
  );
  click("#resolve-btn");
  click("#modal-submit");
  check('incident.status==="RESOLVED"', "Incident resolves through the UI");
  run("screenshot", "--full", resolve("artifacts/04-recovery.png"));
  click('[data-tab="communications"]');
  click("#draft-btn");
  check(
    'document.querySelector("#update-text").textContent.includes("recovered")',
    "Customer update reflects verified recovery",
  );
  click('[data-tab="postmortem"]');
  settle();
  check(
    'document.querySelector("#postmortem-text").textContent.includes("Two consecutive")',
    "Postmortem includes verified recovery and timeline",
  );
  run("screenshot", "--full", resolve("artifacts/05-postmortem.png"));
  run("set", "viewport", "390", "844");
  check(
    "document.documentElement.scrollWidth<=window.innerWidth",
    "Mobile layout has no horizontal overflow",
  );
  run("screenshot", "--full", resolve("artifacts/06-mobile.png"));
  run("set", "viewport", "1440", "1000");
  click("#new-incident");
  js('document.querySelector("#scenario").value="provider"');
  click("#modal-submit");
  click("#analyze-btn");
  check(
    'incident.actions[0].title.includes("circuit breaker")',
    "Provider outage has a different mitigation from the deployment scenario",
  );
  click("#new-incident");
  js('document.querySelector("#scenario").value="unknown"');
  click("#modal-submit");
  click("#analyze-btn");
  check(
    "incident.actions.length===0",
    "Insufficient evidence leads to abstention",
  );
  click("#add-evidence-btn");
  js(
    'document.querySelector("#source").value="UI security check";document.querySelector("#summary").value="<img src=x onerror=alert(1)>"',
  );
  click("#modal-submit");
  click('[data-tab="evidence"]');
  check(
    '!document.querySelector("#evidence-cards img") && document.querySelector("#evidence-cards").textContent.includes("<img")',
    "User evidence is rendered as text, not HTML",
  );
  click("#open-mcp");
  click("#test-mcp");
  check(
    'document.querySelector("#mcp-result").textContent.includes("PASS") && document.querySelector("#mcp-result").textContent.includes("2025-11-25")',
    "Browser diagnostic initializes MCP, discovers tools and executes a real tool",
  );
  run("screenshot", resolve("artifacts/07-live-mcp.png"));
  click("#close-modal");
  report.browserErrors = run("errors").trim();
  if (report.browserErrors) throw Error(report.browserErrors);
  report.passed = true;
  await writeFile(
    "artifacts/browser-verification.json",
    JSON.stringify(report, null, 2),
  );
  console.log(JSON.stringify(report, null, 2));
} finally {
  run("close");
}
