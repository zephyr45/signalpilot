import { execFileSync } from "node:child_process";
import { openSync, closeSync, readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
const binary = resolve(
  "node_modules/agent-browser/bin/agent-browser-win32-x64.exe",
);
function run(...args) {
  const file = resolve("target/demo-cli.log"),
    fd = openSync(file, "w");
  try {
    execFileSync(binary, ["--session", "signalpilot-demo", ...args], {
      stdio: ["ignore", fd, fd],
      timeout: 30000,
      windowsHide: true,
    });
    return readFileSync(file, "utf8");
  } catch (e) {
    throw Error(readFileSync(file, "utf8") + "\n" + e.message);
  } finally {
    closeSync(fd);
  }
}
const js = (s) => run("eval", s);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
function settle() {
  js(
    `new Promise((resolve,reject)=>{const d=Date.now()+15000;const t=setInterval(()=>{if(!busy){clearInterval(t);resolve(true);}else if(Date.now()>d){clearInterval(t);reject(Error('UI timeout'));}},100);})`,
  );
}
function click(s) {
  js(`document.querySelector(${JSON.stringify(s)}).click()`);
  settle();
}
function scroll(s) {
  js(
    `document.querySelector(${JSON.stringify(s)}).scrollIntoView({behavior:'smooth',block:'center'})`,
  );
}
const durations = Array.from({ length: 6 }, (_, n) =>
  Number(
    execFileSync(
      "ffprobe",
      [
        "-v",
        "error",
        "-show_entries",
        "format=duration",
        "-of",
        "default=noprint_wrappers=1:nokey=1",
        `artifacts/narration-${n + 1}.wav`,
      ],
      { encoding: "utf8" },
    ).trim(),
  ),
);
async function scene(n, fn) {
  const start = Date.now();
  console.log(`Recording scene ${n + 1}`);
  await fn();
  await sleep(Math.max(0, durations[n] * 1000 - (Date.now() - start)));
}
let recording = false;
try {
  run("open", "http://127.0.0.1:8091");
  run("set", "viewport", "1440", "1000");
  settle();
  click("#new-incident");
  click("#modal-submit");
  js("window.scrollTo(0,0)");
  run("record", "start", resolve("artifacts/demo-raw.webm"));
  recording = true;
  settle();
  await scene(0, async () => {
    js("window.scrollTo(0,0)");
  });
  await scene(1, async () => {
    click("#analyze-btn");
    scroll("#hypotheses");
    await sleep(7000);
    click("[data-ref]");
    await sleep(7000);
    click('[data-tab="investigation"]');
    scroll("#hypotheses");
  });
  await scene(2, async () => {
    scroll("#actions");
    click("[data-approve]");
    await sleep(8000);
    click("#modal-submit");
    await sleep(3000);
    click("[data-execute]");
    scroll("#actions");
  });
  await scene(3, async () => {
    click('[data-sample="healthy"]');
    await sleep(4000);
    click('[data-sample="unhealthy"]');
    await sleep(4000);
    click('[data-sample="healthy"]');
    await sleep(4000);
    click('[data-sample="healthy"]');
    scroll("#actions");
  });
  await scene(4, async () => {
    click("#resolve-btn");
    click("#modal-submit");
    click('[data-tab="communications"]');
    click("#draft-btn");
    scroll("#update-text");
    await sleep(8000);
    click('[data-tab="postmortem"]');
    scroll("#postmortem-text");
  });
  await scene(5, async () => {
    js("window.scrollTo(0,0)");
    click("#open-mcp");
    click("#test-mcp");
    js(
      `(()=>{if(!document.querySelector('#mcp-result').textContent.includes('PASS'))throw Error('Live MCP demo failed');})()`,
    );
  });
  run("record", "stop");
  recording = false;
  writeFileSync(
    "artifacts/narration-list.txt",
    durations.map((_, i) => `file 'narration-${i + 1}.wav'`).join("\n"),
  );
  execFileSync(
    "ffmpeg",
    [
      "-y",
      "-f",
      "concat",
      "-safe",
      "0",
      "-i",
      "artifacts/narration-list.txt",
      "-c:a",
      "pcm_s16le",
      "artifacts/narration.wav",
    ],
    { stdio: "ignore", windowsHide: true },
  );
  execFileSync(
    "ffmpeg",
    [
      "-y",
      "-i",
      "artifacts/demo-raw.webm",
      "-i",
      "artifacts/narration.wav",
      "-map",
      "0:v:0",
      "-map",
      "1:a:0",
      "-vf",
      "pad=ceil(iw/2)*2:ceil(ih/2)*2",
      "-c:v",
      "libx264",
      "-preset",
      "fast",
      "-crf",
      "22",
      "-c:a",
      "aac",
      "-b:a",
      "128k",
      "-movflags",
      "+faststart",
      "-shortest",
      "artifacts/signalpilot-demo.mp4",
    ],
    { stdio: "ignore", timeout: 120000, windowsHide: true },
  );
  console.log(
    "Created artifacts/signalpilot-demo.mp4; narration length " +
      durations.reduce((a, b) => a + b, 0).toFixed(1) +
      " seconds",
  );
} finally {
  if (recording) run("record", "stop");
  run("close");
}
