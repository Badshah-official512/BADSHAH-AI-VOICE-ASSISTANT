// BADSHAH AI — self-hosted engines adapter (Node.js, no dependencies).
//
// The open-source engines the owner listed (browser-use / Playwright / Skyvern
// for browsing, Wan2.2 / LTX-Video / CogVideo for video, F5-TTS / Fish Speech
// for voices) are SERVER programs — they are not hosted APIs. This adapter is
// the HTTP face the unified gateway (Supabase Edge Function "ai-api") expects
// from such a server:
//
//   GET  /health            → { ok: true }
//   POST /browse {url,task} → runs BROWSER_CMD (e.g. a Playwright/browser-use
//                             runner) and returns its JSON result
//   POST /video  {prompt}   → runs VIDEO_CMD (e.g. a ComfyUI/Wan workflow
//                             runner) and returns { videoUrl | jobId }
//
// Configure with environment variables; each command receives the request
// JSON on stdin and must print JSON on stdout:
//   PORT=8787 BROWSER_CMD="node ./runners/playwright-runner.js" \
//   VIDEO_CMD="node ./runners/wan-runner.js" node server.js
//
// Status: SKELETON — not deployed or tested yet (no GPU/server available).
// Deploy on the future VPS, then set BROWSER_ENGINE_URL on the gateway.

import { createServer } from "node:http";
import { spawn } from "node:child_process";

const PORT = process.env.PORT || 8787;

function runCmd(cmd, payload, timeoutMs = 120000) {
  return new Promise((resolve, reject) => {
    if (!cmd) return reject(new Error("engine command not configured"));
    const child = spawn(cmd, { shell: true, env: process.env });
    let out = "", err = "";
    const timer = setTimeout(() => { child.kill("SIGKILL"); reject(new Error("engine timeout")); }, timeoutMs);
    child.stdout.on("data", (d) => { out += d; });
    child.stderr.on("data", (d) => { err += d; });
    child.on("error", (e) => { clearTimeout(timer); reject(e); });
    child.on("close", (code) => {
      clearTimeout(timer);
      if (code !== 0) return reject(new Error(`engine exited ${code}: ${err.slice(0, 300)}`));
      try { resolve(JSON.parse(out)); } catch { resolve({ raw: out.slice(0, 2000) }); }
    });
    child.stdin.write(JSON.stringify(payload));
    child.stdin.end();
  });
}

const server = createServer(async (req, res) => {
  const send = (status, obj) => { res.writeHead(status, { "content-type": "application/json" }); res.end(JSON.stringify(obj)); };
  if (req.method === "GET" && req.url === "/health") return send(200, { ok: true, service: "badshah-self-hosted", browser: !!process.env.BROWSER_CMD, video: !!process.env.VIDEO_CMD });
  if (req.method !== "POST") return send(405, { ok: false, error: "POST only" });
  let body = "";
  req.on("data", (c) => { body += c; });
  req.on("end", async () => {
    let payload = {};
    try { payload = JSON.parse(body || "{}"); } catch { return send(400, { ok: false, error: "bad json" }); }
    try {
      if (req.url === "/browse") return send(200, await runCmd(process.env.BROWSER_CMD, payload));
      if (req.url === "/video") return send(200, await runCmd(process.env.VIDEO_CMD, payload, 600000));
      return send(404, { ok: false, error: "unknown route" });
    } catch (e) { return send(502, { ok: false, error: String(e.message || e) }); }
  });
});

server.listen(PORT, () => console.log(`badshah-self-hosted listening on :${PORT}`));
