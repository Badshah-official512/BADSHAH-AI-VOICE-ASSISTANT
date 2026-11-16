// BADSHAH AI — unified AI gateway (direct HTTP integrations only, no CLI tools).
//
// The Android app talks to THIS service for chat, images, vision, files, TTS and
// STT. Gemini Live voice stays a direct WebSocket from the app (an HTTP edge
// function cannot proxy it); this gateway vends its health + model list.
//
// Auth: every call needs header  x-gateway-secret: <GATEWAY_SECRET>
// (the same secret admin-api uses). A caller may instead pass its OWN provider
// key per request (body.userKey) — BYOK passthrough, the key is never stored.
//
// Provider keys (Edge Function secrets, set by the owner):
//   GEMINI_API_KEY, ANTHROPIC_API_KEY, GROQ_API_KEY, OPENROUTER_API_KEY,
//   HF_TOKEN, GOOGLE_TTS_KEY, ELEVENLABS_API_KEY, FT_FUSION_KEY (optional —
//   the owner's gateway has a built-in default), BROWSER_ENGINE_URL (optional,
//   self-hosted browser server), GATEWAY_SECRET (required).
//
// Every route answers JSON: { ok: true, ... } or
// { ok: false, error: { provider, code, message } }.

export interface Env { get(k: string): string | undefined }
export interface Deps { env: Env; fetchFn?: typeof fetch }

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json", "cache-control": "no-store" } });
const fail = (provider: string, code: string, message: string, status = 502) =>
  json(status, { ok: false, error: { provider, code, message } });

const FT_DEFAULT_KEY = "ft-12fcb8038e7ee88b97f993063412e203fa085116bc2cd658"; // owner's own gateway (his instruction: built-in is fine)
const FT_BASE = "https://ft-fusion.shehryarteam8876.workers.dev/ft/api";

interface Ctx { env: Env; fetchFn: typeof fetch }
interface Msg { role: string; content: string }

function keyFor(ctx: Ctx, provider: string, userKey?: string): string | null {
  if (userKey && userKey.trim()) return userKey.trim();
  const map: Record<string, string> = {
    gemini: "GEMINI_API_KEY", claude: "ANTHROPIC_API_KEY", groq: "GROQ_API_KEY",
    openrouter: "OPENROUTER_API_KEY", huggingface: "HF_TOKEN",
    google: "GOOGLE_TTS_KEY", elevenlabs: "ELEVENLABS_API_KEY",
  };
  if (provider === "fusion") return ctx.env.get("FT_FUSION_KEY") ?? FT_DEFAULT_KEY;
  const name = map[provider];
  if (!name) return null;
  if (provider === "google") return ctx.env.get("GOOGLE_TTS_KEY") ?? ctx.env.get("GEMINI_API_KEY") ?? null;
  return ctx.env.get(name) ?? null;
}

/** fetch with a hard timeout (AbortController). */
async function call(ctx: Ctx, url: string, init: RequestInit, timeoutMs: number): Promise<Response> {
  const ctl = new AbortController();
  const t = setTimeout(() => ctl.abort(), timeoutMs);
  try { return await ctx.fetchFn(url, { ...init, signal: ctl.signal }); }
  catch (e) {
    if ((e as Error).name === "AbortError") throw new Error(`timeout after ${timeoutMs}ms`);
    throw e;
  } finally { clearTimeout(t); }
}

async function bodyJson(res: Response): Promise<Record<string, unknown>> {
  try { return await res.json() as Record<string, unknown>; } catch { return {}; }
}
const errText = (j: Record<string, unknown>, fallback: string): string => {
  const e = j.error as Record<string, unknown> | string | undefined;
  if (typeof e === "string") return e;
  if (e && typeof e.message === "string") return e.message;
  return fallback;
};

// ---------------------------------------------------------------- chat ----
async function chatGemini(ctx: Ctx, key: string, model: string, system: string, msgs: Msg[]) {
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`;
  const payload = {
    ...(system ? { system_instruction: { parts: [{ text: system }] } } : {}),
    contents: msgs.map((m) => ({ role: m.role === "assistant" ? "model" : "user", parts: [{ text: m.content }] })),
  };
  const res = await call(ctx, url, { method: "POST", headers: { "content-type": "application/json", "x-goog-api-key": key }, body: JSON.stringify(payload) }, 60000);
  const j = await bodyJson(res);
  if (!res.ok) return fail("gemini", String(res.status), errText(j, "Gemini request failed"));
  const cand = (j.candidates as Record<string, unknown>[])?.[0];
  const parts = ((cand?.content as Record<string, unknown>)?.parts as Record<string, unknown>[]) ?? [];
  const text = parts.map((p) => p.text ?? "").join("");
  const u = (j.usageMetadata as Record<string, number>) ?? {};
  return json(200, { ok: true, provider: "gemini", model, text, usage: { input: u.promptTokenCount ?? 0, output: u.candidatesTokenCount ?? 0 } });
}

async function chatClaude(ctx: Ctx, key: string, model: string, system: string, msgs: Msg[]) {
  const payload = {
    model, max_tokens: 2048, ...(system ? { system } : {}),
    messages: msgs.map((m) => ({ role: m.role === "assistant" ? "assistant" : "user", content: m.content })),
  };
  const res = await call(ctx, "https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: { "content-type": "application/json", "x-api-key": key, "anthropic-version": "2023-06-01" },
    body: JSON.stringify(payload),
  }, 60000);
  const j = await bodyJson(res);
  if (!res.ok) return fail("claude", String(res.status), errText(j, "Claude request failed"));
  const blocks = (j.content as Record<string, unknown>[]) ?? [];
  const text = blocks.filter((b) => b.type === "text").map((b) => b.text ?? "").join("");
  const u = (j.usage as Record<string, number>) ?? {};
  return json(200, { ok: true, provider: "claude", model, text, usage: { input: u.input_tokens ?? 0, output: u.output_tokens ?? 0 } });
}

/** OpenAI-compatible chat (Groq + OpenRouter share the wire format). */
async function chatOpenAiLike(ctx: Ctx, provider: "groq" | "openrouter", key: string, model: string, system: string, msgs: Msg[], stream: boolean) {
  const base = provider === "groq" ? "https://api.groq.com/openai/v1" : "https://openrouter.ai/api/v1";
  const payload = {
    model, temperature: 0.7, ...(stream ? { stream: true } : {}),
    messages: [...(system ? [{ role: "system", content: system }] : []), ...msgs.map((m) => ({ role: m.role, content: m.content }))],
  };
  const res = await call(ctx, `${base}/chat/completions`, {
    method: "POST",
    headers: { "content-type": "application/json", authorization: `Bearer ${key}` },
    body: JSON.stringify(payload),
  }, 60000);
  if (stream && res.ok && res.body) {
    return new Response(res.body, { status: 200, headers: { "content-type": "text/event-stream", "cache-control": "no-store" } });
  }
  const j = await bodyJson(res);
  if (!res.ok) return fail(provider, String(res.status), errText(j, `${provider} request failed`));
  const choice = (j.choices as Record<string, unknown>[])?.[0];
  const text = ((choice?.message as Record<string, unknown>)?.content as string) ?? "";
  const u = (j.usage as Record<string, number>) ?? {};
  return json(200, { ok: true, provider, model, text: text.trim(), usage: { input: u.prompt_tokens ?? 0, output: u.completion_tokens ?? 0 } });
}

async function chatFusion(ctx: Ctx, key: string, model: string, system: string, msgs: Msg[]) {
  const sb = [system, ...msgs.slice(-12).map((m) => `${m.role === "user" ? "User" : "Assistant"}: ${m.content}`), "Reply as the Assistant to the last User message."].join("\n").slice(0, 9000);
  const url = `${FT_BASE}?key=${encodeURIComponent(key)}&model=${encodeURIComponent(model)}&q=${encodeURIComponent(sb)}`;
  const res = await call(ctx, url, {}, 120000);
  const j = await bodyJson(res);
  if (j.status === "error") return fail("fusion", "gateway_error", String(j.error ?? "FT Fusion error"));
  const data = (j.data as Record<string, unknown>) ?? j;
  const text = String(data.response ?? data.reply ?? data.text ?? data.answer ?? data.message ?? data.output ?? "");
  if (!text) return fail("fusion", "empty", "FT Fusion returned an empty reply");
  return json(200, { ok: true, provider: "fusion", model, text });
}

async function chatPollinations(ctx: Ctx, system: string, msgs: Msg[]) {
  // Preferred: the OpenAI-compatible gen endpoint (verified live 2026-11-16 —
  // answers anonymously; a POLLINATIONS_API_KEY raises limits when configured).
  try {
    const key = ctx.env.get("POLLINATIONS_API_KEY");
    const payload = {
      model: "openai",
      messages: [...(system ? [{ role: "system", content: system }] : []), ...msgs.map((m) => ({ role: m.role, content: m.content }))],
    };
    const res = await call(ctx, "https://gen.pollinations.ai/v1/chat/completions", {
      method: "POST",
      headers: { "content-type": "application/json", ...(key ? { authorization: `Bearer ${key}` } : {}) },
      body: JSON.stringify(payload),
    }, 90000);
    const j = await bodyJson(res);
    if (res.ok) {
      const choice = (j.choices as Record<string, unknown>[])?.[0];
      const text = ((choice?.message as Record<string, unknown>)?.content as string) ?? "";
      if (text.trim()) return json(200, { ok: true, provider: "pollinations", model: "openai", text: text.trim() });
    }
  } catch { /* fall through to the plain-text route */ }
  const prompt = [system, ...msgs.slice(-8).map((m) => `${m.role === "user" ? "User" : "Assistant"}: ${m.content}`), "Reply as the Assistant to the last User message."].join("\n").slice(0, 6000);
  const res = await call(ctx, `https://text.pollinations.ai/${encodeURIComponent(prompt)}?model=openai-fast`, {}, 90000);
  const text = await res.text();
  if (!res.ok || !text.trim()) return fail("pollinations", String(res.status), "Pollinations did not answer");
  return json(200, { ok: true, provider: "pollinations", model: "openai-fast", text: text.trim() });
}

/** Cloudflare Workers AI — one account token covers chat LLMs, FLUX images and
 *  embeddings. Free daily Neuron allocation on the owner's own Cloudflare account. */
function cfCreds(ctx: Ctx): { account: string; token: string } | null {
  const account = ctx.env.get("CLOUDFLARE_ACCOUNT_ID");
  const token = ctx.env.get("CLOUDFLARE_API_TOKEN");
  return account && token ? { account, token } : null;
}

async function chatCloudflare(ctx: Ctx, model: string, system: string, msgs: Msg[]) {
  const creds = cfCreds(ctx);
  if (!creds) return fail("cloudflare", "no_key", "CLOUDFLARE_ACCOUNT_ID / CLOUDFLARE_API_TOKEN are not configured on the backend", 400);
  const payload = { model, messages: [...(system ? [{ role: "system", content: system }] : []), ...msgs.map((m) => ({ role: m.role, content: m.content }))] };
  const res = await call(ctx, `https://api.cloudflare.com/client/v4/accounts/${creds.account}/ai/v1/chat/completions`, {
    method: "POST", headers: { "content-type": "application/json", authorization: `Bearer ${creds.token}` },
    body: JSON.stringify(payload),
  }, 60000);
  const j = await bodyJson(res);
  if (!res.ok) return fail("cloudflare", String(res.status), errText(j, "Cloudflare Workers AI failed"));
  const choice = (j.choices as Record<string, unknown>[])?.[0];
  const text = ((choice?.message as Record<string, unknown>)?.content as string) ?? "";
  return json(200, { ok: true, provider: "cloudflare", model, text: text.trim() });
}

// --------------------------------------------------------------- image ----
async function image(ctx: Ctx, provider: string, prompt: string, userKey?: string) {
  if (provider === "pollinations") {
    const url = `https://image.pollinations.ai/prompt/${encodeURIComponent(prompt.slice(0, 300))}?width=1024&height=1024&nologo=true&seed=${Date.now() % 100000}`;
    return json(200, { ok: true, provider, imageUrl: url });
  }
  if (provider === "cloudflare-flux") {
    const creds = cfCreds(ctx);
    if (!creds) return fail("cloudflare", "no_key", "CLOUDFLARE_ACCOUNT_ID / CLOUDFLARE_API_TOKEN are not configured on the backend", 400);
    const res = await call(ctx, `https://api.cloudflare.com/client/v4/accounts/${creds.account}/ai/run/@cf/black-forest-labs/flux-1-schnell`, {
      method: "POST", headers: { "content-type": "application/json", authorization: `Bearer ${creds.token}` },
      body: JSON.stringify({ prompt }),
    }, 120000);
    const j = await bodyJson(res);
    if (!res.ok) return fail("cloudflare", String(res.status), errText(j, "Cloudflare FLUX failed"));
    const result = (j.result as Record<string, unknown>) ?? {};
    if (typeof result.image !== "string") return fail("cloudflare", "empty", "Cloudflare FLUX returned no image");
    return json(200, { ok: true, provider, model: "@cf/black-forest-labs/flux-1-schnell", imageBase64: result.image, mimeType: "image/jpeg" });
  }
  if (provider === "fusion-uni" || provider === "fusion-astra") {
    const key = keyFor(ctx, "fusion")!;
    const url = `${FT_BASE}?key=${encodeURIComponent(key)}&model=${provider}&q=${encodeURIComponent(prompt)}`;
    const res = await call(ctx, url, {}, 120000);
    const j = await bodyJson(res);
    if (j.status === "error") return fail(provider, "gateway_error", String(j.error ?? "FT Fusion image error"));
    const img = (j.image as Record<string, unknown>) ?? {};
    const imageUrl = String(img.image_url ?? "");
    if (!imageUrl) return fail(provider, "empty", "FT Fusion returned no image URL");
    return json(200, { ok: true, provider, imageUrl });
  }
  if (provider === "huggingface") {
    const key = keyFor(ctx, "huggingface", userKey);
    if (!key) return fail("huggingface", "no_key", "HF_TOKEN is not configured on the backend (or pass userKey)", 400);
    const model = "black-forest-labs/FLUX.1-schnell";
    const res = await call(ctx, `https://router.huggingface.co/hf-inference/models/${model}`, {
      method: "POST", headers: { "content-type": "application/json", authorization: `Bearer ${key}` },
      body: JSON.stringify({ inputs: prompt }),
    }, 120000);
    if (!res.ok) { const j = await bodyJson(res); return fail("huggingface", String(res.status), errText(j, "Hugging Face request failed")); }
    const bytes = new Uint8Array(await res.arrayBuffer());
    let bin = ""; bytes.forEach((b) => { bin += String.fromCharCode(b); });
    return json(200, { ok: true, provider, model, imageBase64: btoa(bin), mimeType: res.headers.get("content-type") ?? "image/jpeg" });
  }
  return fail(provider, "unknown_provider", `Unknown image provider: ${provider}`, 400);
}

// ------------------------------------------- vision + file analysis -------
async function vision(ctx: Ctx, provider: string, model: string, prompt: string, imageBase64: string, mimeType: string, userKey?: string) {
  const key = keyFor(ctx, provider, userKey);
  if (!key) return fail(provider, "no_key", `${provider} key is not configured on the backend (or pass userKey)`, 400);
  if (provider === "gemini") {
    const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`;
    const payload = { contents: [{ parts: [{ text: prompt }, { inline_data: { mime_type: mimeType, data: imageBase64 } }] }] };
    const res = await call(ctx, url, { method: "POST", headers: { "content-type": "application/json", "x-goog-api-key": key }, body: JSON.stringify(payload) }, 90000);
    const j = await bodyJson(res);
    if (!res.ok) return fail("gemini", String(res.status), errText(j, "Gemini vision failed"));
    const cand = (j.candidates as Record<string, unknown>[])?.[0];
    const parts = ((cand?.content as Record<string, unknown>)?.parts as Record<string, unknown>[]) ?? [];
    return json(200, { ok: true, provider, model, text: parts.map((p) => p.text ?? "").join("") });
  }
  if (provider === "claude") {
    const payload = {
      model, max_tokens: 2048,
      messages: [{ role: "user", content: [
        { type: "image", source: { type: "base64", media_type: mimeType, data: imageBase64 } },
        { type: "text", text: prompt },
      ] }],
    };
    const res = await call(ctx, "https://api.anthropic.com/v1/messages", {
      method: "POST", headers: { "content-type": "application/json", "x-api-key": key, "anthropic-version": "2023-06-01" },
      body: JSON.stringify(payload),
    }, 90000);
    const j = await bodyJson(res);
    if (!res.ok) return fail("claude", String(res.status), errText(j, "Claude vision failed"));
    const blocks = (j.content as Record<string, unknown>[]) ?? [];
    return json(200, { ok: true, provider, model, text: blocks.filter((b) => b.type === "text").map((b) => b.text ?? "").join("") });
  }
  return fail(provider, "unknown_provider", `Vision is implemented for gemini and claude, not ${provider}`, 400);
}

// ----------------------------------------------------------------- tts ----
async function tts(ctx: Ctx, provider: string, text: string, voice: string, userKey?: string) {
  const key = keyFor(ctx, provider === "google" ? "google" : provider, userKey);
  if (!key) return fail(provider, "no_key", `${provider} key is not configured on the backend (or pass userKey)`, 400);
  if (provider === "google") {
    const res = await call(ctx, "https://texttospeech.googleapis.com/v1/text:synthesize", {
      method: "POST", headers: { "content-type": "application/json", "x-goog-api-key": key },
      body: JSON.stringify({ input: { text }, voice: { name: voice || "en-US-Journey-D", languageCode: (voice || "en-US").slice(0, 5) }, audioConfig: { audioEncoding: "MP3" } }),
    }, 60000);
    const j = await bodyJson(res);
    if (!res.ok) return fail("google", String(res.status), errText(j, "Google TTS failed"));
    return json(200, { ok: true, provider, audioBase64: j.audioContent, mimeType: "audio/mpeg" });
  }
  if (provider === "elevenlabs") {
    const voiceId = voice || "21m00Tcm4TlvDq8ikWAM";
    const res = await call(ctx, `https://api.elevenlabs.io/v1/text-to-speech/${voiceId}`, {
      method: "POST", headers: { "content-type": "application/json", "xi-api-key": key },
      body: JSON.stringify({ text, model_id: "eleven_multilingual_v2" }),
    }, 60000);
    if (!res.ok) { const j = await bodyJson(res); return fail("elevenlabs", String(res.status), errText(j, "ElevenLabs TTS failed")); }
    const bytes = new Uint8Array(await res.arrayBuffer());
    let bin = ""; bytes.forEach((b) => { bin += String.fromCharCode(b); });
    return json(200, { ok: true, provider, audioBase64: btoa(bin), mimeType: "audio/mpeg" });
  }
  return fail(provider, "unknown_provider", `TTS is implemented for google, elevenlabs, groq and gemini-tts, not ${provider}`, 400);
}

/** TTS providers that do not fit the google/elevenlabs key shape above. */
async function ttsAlt(ctx: Ctx, provider: string, text: string, voice: string, model: string, userKey?: string) {
  if (provider === "groq") {
    const key = keyFor(ctx, "groq", userKey);
    if (!key) return fail("groq", "no_key", "GROQ_API_KEY is not configured on the backend (or pass userKey)", 400);
    const res = await call(ctx, "https://api.groq.com/openai/v1/audio/speech", {
      method: "POST", headers: { "content-type": "application/json", authorization: `Bearer ${key}` },
      body: JSON.stringify({ model: model || "canopylabs/orpheus-v1-english", voice: voice || "tara", input: text, response_format: "wav" }),
    }, 60000);
    if (!res.ok) { const j = await bodyJson(res); return fail("groq", String(res.status), errText(j, "Groq TTS failed")); }
    const bytes = new Uint8Array(await res.arrayBuffer());
    let bin = ""; bytes.forEach((b) => { bin += String.fromCharCode(b); });
    return json(200, { ok: true, provider, model: model || "canopylabs/orpheus-v1-english", audioBase64: btoa(bin), mimeType: "audio/wav" });
  }
  if (provider === "gemini-tts") {
    const key = keyFor(ctx, "gemini", userKey);
    if (!key) return fail("gemini-tts", "no_key", "GEMINI_API_KEY is not configured on the backend (or pass userKey)", 400);
    const m = model || "gemini-2.5-flash-preview-tts";
    const payload = {
      contents: [{ parts: [{ text }] }],
      generationConfig: { responseModalities: ["AUDIO"], speechConfig: { voiceConfig: { prebuiltVoiceConfig: { voiceName: voice || "Charon" } } } },
    };
    const res = await call(ctx, `https://generativelanguage.googleapis.com/v1beta/models/${m}:generateContent`, {
      method: "POST", headers: { "content-type": "application/json", "x-goog-api-key": key },
      body: JSON.stringify(payload),
    }, 60000);
    const j = await bodyJson(res);
    if (!res.ok) return fail("gemini-tts", String(res.status), errText(j, "Gemini TTS failed"));
    const cand = (j.candidates as Record<string, unknown>[])?.[0];
    const parts = ((cand?.content as Record<string, unknown>)?.parts as Record<string, unknown>[]) ?? [];
    const inline = parts.map((p) => p.inlineData as Record<string, unknown>).find((d) => d?.data);
    if (!inline) return fail("gemini-tts", "empty", "Gemini TTS returned no audio");
    return json(200, { ok: true, provider, model: m, audioBase64: inline.data, mimeType: String(inline.mimeType ?? "audio/pcm;rate=24000"), note: "Raw PCM 24kHz when mimeType is audio/L16 — play via AudioTrack, not MediaPlayer." });
  }
  return fail(provider, "unknown_provider", `Unknown TTS provider: ${provider}`, 400);
}

// ----------------------------------------------------------------- stt ----
async function stt(ctx: Ctx, provider: string, audioBase64: string, mimeType: string, userKey?: string) {
  const key = keyFor(ctx, provider, userKey);
  if (!key) return fail(provider, "no_key", `${provider} key is not configured on the backend (or pass userKey)`, 400);
  const bin = atob(audioBase64);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  if (provider === "groq") {
    const form = new FormData();
    form.append("file", new Blob([bytes], { type: mimeType }), "audio.m4a");
    form.append("model", "whisper-large-v3-turbo");
    const res = await call(ctx, "https://api.groq.com/openai/v1/audio/transcriptions", {
      method: "POST", headers: { authorization: `Bearer ${key}` }, body: form,
    }, 90000);
    const j = await bodyJson(res);
    if (!res.ok) return fail("groq", String(res.status), errText(j, "Groq STT failed"));
    return json(200, { ok: true, provider, model: "whisper-large-v3-turbo", text: String(j.text ?? "") });
  }
  if (provider === "gemini") {
    const url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";
    const payload = { contents: [{ parts: [{ text: "Transcribe this audio exactly, word for word. Return only the transcription." }, { inline_data: { mime_type: mimeType, data: audioBase64 } }] }] };
    const res = await call(ctx, url, { method: "POST", headers: { "content-type": "application/json", "x-goog-api-key": key }, body: JSON.stringify(payload) }, 90000);
    const j = await bodyJson(res);
    if (!res.ok) return fail("gemini", String(res.status), errText(j, "Gemini STT failed"));
    const cand = (j.candidates as Record<string, unknown>[])?.[0];
    const parts = ((cand?.content as Record<string, unknown>)?.parts as Record<string, unknown>[]) ?? [];
    return json(200, { ok: true, provider, model: "gemini-2.5-flash", text: parts.map((p) => p.text ?? "").join("").trim() });
  }
  return fail(provider, "unknown_provider", `STT is implemented for groq and gemini, not ${provider}`, 400);
}

// --------------------------------------------------------------- health ---
async function healthOne(ctx: Ctx, provider: string): Promise<Record<string, unknown>> {
  const t0 = Date.now();
  const done = (status: string, detail?: string) => ({ provider, status, ms: Date.now() - t0, ...(detail ? { detail: detail.slice(0, 160) } : {}) });
  try {
    switch (provider) {
      case "gemini": {
        const key = keyFor(ctx, "gemini"); if (!key) return done("no_key_configured");
        const res = await call(ctx, "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1", { headers: { "x-goog-api-key": key } }, 12000);
        return done(res.ok ? "ok" : "error", res.ok ? undefined : `HTTP ${res.status}`);
      }
      case "claude": {
        const key = keyFor(ctx, "claude"); if (!key) return done("no_key_configured");
        const res = await call(ctx, "https://api.anthropic.com/v1/models", { headers: { "x-api-key": key, "anthropic-version": "2023-06-01" } }, 12000);
        return done(res.ok ? "ok" : "error", res.ok ? undefined : `HTTP ${res.status}`);
      }
      case "groq": {
        const key = keyFor(ctx, "groq"); if (!key) return done("no_key_configured");
        const res = await call(ctx, "https://api.groq.com/openai/v1/models", { headers: { authorization: `Bearer ${key}` } }, 12000);
        return done(res.ok ? "ok" : "error", res.ok ? undefined : `HTTP ${res.status}`);
      }
      case "openrouter": {
        const key = keyFor(ctx, "openrouter");
        const res = await call(ctx, "https://openrouter.ai/api/v1/models", key ? { headers: { authorization: `Bearer ${key}` } } : {}, 12000);
        return done(res.ok ? "ok" : "error", res.ok ? undefined : `HTTP ${res.status}`);
      }
      case "fusion": {
        const key = keyFor(ctx, "fusion")!;
        const res = await call(ctx, `${FT_BASE}?key=${encodeURIComponent(key)}&model=fusion-flex&q=${encodeURIComponent("hi")}`, {}, 20000);
        const j = await bodyJson(res);
        return done(j.status === "error" ? "error" : "ok", j.status === "error" ? String(j.error) : undefined);
      }
      case "pollinations": {
        const res = await call(ctx, "https://gen.pollinations.ai/v1/chat/completions", {
          method: "POST", headers: { "content-type": "application/json" },
          body: JSON.stringify({ model: "openai", messages: [{ role: "user", content: "hi" }] }),
        }, 20000);
        return done(res.ok ? "ok" : "error", res.ok ? undefined : `HTTP ${res.status}`);
      }
      case "cloudflare": {
        const creds = cfCreds(ctx); if (!creds) return done("no_key_configured");
        const res = await call(ctx, `https://api.cloudflare.com/client/v4/accounts/${creds.account}/ai/models/search?per_page=1`, { headers: { authorization: `Bearer ${creds.token}` } }, 12000);
        return done(res.ok ? "ok" : "error", res.ok ? undefined : `HTTP ${res.status}`);
      }
      case "huggingface": {
        const key = keyFor(ctx, "huggingface"); if (!key) return done("no_key_configured");
        const res = await call(ctx, "https://huggingface.co/api/whoami-v2", { headers: { authorization: `Bearer ${key}` } }, 12000);
        return done(res.ok ? "ok" : "error", res.ok ? undefined : `HTTP ${res.status}`);
      }
      case "elevenlabs": {
        const key = keyFor(ctx, "elevenlabs"); if (!key) return done("no_key_configured");
        const res = await call(ctx, "https://api.elevenlabs.io/v1/voices", { headers: { "xi-api-key": key } }, 12000);
        return done(res.ok ? "ok" : "error", res.ok ? undefined : `HTTP ${res.status}`);
      }
      case "google-tts": return done(keyFor(ctx, "google") ? "configured_unverified" : "no_key_configured");
      case "veo-video": return done(keyFor(ctx, "gemini") ? "paid_configured_unverified" : "paid_no_key_configured");
      case "browser-engine": {
        const base = ctx.env.get("BROWSER_ENGINE_URL");
        if (!base) return done("self_hosted_not_configured");
        const res = await call(ctx, `${base}/health`, {}, 8000);
        return done(res.ok ? "ok" : "error", res.ok ? undefined : `HTTP ${res.status}`);
      }
      default: return done("unknown");
    }
  } catch (e) { return done("error", (e as Error).message); }
}

// --------------------------------------------------------------- router ---
export function createHandler(deps: Deps) {
  const ctx: Ctx = { env: deps.env, fetchFn: deps.fetchFn ?? fetch };
  return async (req: Request): Promise<Response> => {
    const url = new URL(req.url);
    const path = url.pathname.replace(/^\/ai-api/, "") || "/";
    const gateway = ctx.env.get("GATEWAY_SECRET");
    if (!gateway || req.headers.get("x-gateway-secret") !== gateway) {
      return json(401, { ok: false, error: { provider: "gateway", code: "unauthorized", message: "Missing or wrong x-gateway-secret" } });
    }
    try {
      if (req.method === "GET" && path === "/health") {
        const providers = ["gemini", "claude", "groq", "openrouter", "fusion", "pollinations", "cloudflare", "huggingface", "elevenlabs", "google-tts", "veo-video", "browser-engine"];
        const results = await Promise.all(providers.map((p) => healthOne(ctx, p)));
        return json(200, { ok: true, service: "badshah-ai-api", results });
      }
      if (req.method !== "POST") return fail("gateway", "method", "Use POST (GET only for /health)", 405);
      const b = await req.json().catch(() => ({})) as Record<string, unknown>;
      const userKey = typeof b.userKey === "string" ? b.userKey : undefined;
      const system = String(b.system ?? "");
      const msgs = (Array.isArray(b.messages) ? b.messages : []) as Msg[];

      if (path === "/chat") {
        const provider = String(b.provider ?? "fusion");
        const stream = b.stream === true;
        if (provider === "pollinations") return chatPollinations(ctx, system, msgs);
        if (provider === "cloudflare") return chatCloudflare(ctx, String(b.model ?? "@cf/meta/llama-3.3-70b-instruct-fp8-fast"), system, msgs);
        if (provider === "fusion") return chatFusion(ctx, keyFor(ctx, "fusion")!, String(b.model ?? "fusion-flex"), system, msgs);
        const key = keyFor(ctx, provider, userKey);
        if (!key) return fail(provider, "no_key", `${provider} key is not configured on the backend (or pass userKey)`, 400);
        if (provider === "gemini") {
          if (stream) {
            const model = String(b.model ?? "gemini-2.5-flash");
            const res = await call(ctx, `https://generativelanguage.googleapis.com/v1beta/models/${model}:streamGenerateContent?alt=sse`, {
              method: "POST", headers: { "content-type": "application/json", "x-goog-api-key": key },
              body: JSON.stringify({ ...(system ? { system_instruction: { parts: [{ text: system }] } } : {}), contents: msgs.map((m) => ({ role: m.role === "assistant" ? "model" : "user", parts: [{ text: m.content }] })) }),
            }, 60000);
            if (res.ok && res.body) return new Response(res.body, { status: 200, headers: { "content-type": "text/event-stream", "cache-control": "no-store" } });
            const j = await bodyJson(res); return fail("gemini", String(res.status), errText(j, "Gemini stream failed"));
          }
          return chatGemini(ctx, key, String(b.model ?? "gemini-2.5-flash"), system, msgs);
        }
        if (provider === "claude") return chatClaude(ctx, key, String(b.model ?? "claude-sonnet-4-5"), system, msgs);
        if (provider === "groq" || provider === "openrouter") {
          const def = provider === "groq" ? "llama-3.3-70b-versatile" : "meta-llama/llama-3.3-70b-instruct:free";
          return chatOpenAiLike(ctx, provider, key, String(b.model ?? def), system, msgs, stream);
        }
        return fail(provider, "unknown_provider", `Unknown chat provider: ${provider}`, 400);
      }
      if (path === "/image") return image(ctx, String(b.provider ?? "fusion-uni"), String(b.prompt ?? ""), userKey);
      if (path === "/vision") return vision(ctx, String(b.provider ?? "gemini"), String(b.model ?? (String(b.provider ?? "gemini") === "claude" ? "claude-sonnet-4-5" : "gemini-2.5-flash")), String(b.prompt ?? "Describe this image."), String(b.imageBase64 ?? ""), String(b.mimeType ?? "image/jpeg"), userKey);
      if (path === "/file") return vision(ctx, "gemini", String(b.model ?? "gemini-2.5-flash"), String(b.prompt ?? "Analyze this file."), String(b.fileBase64 ?? ""), String(b.mimeType ?? "application/pdf"), userKey);
      if (path === "/tts") {
        const p = String(b.provider ?? "google");
        if (p === "groq" || p === "gemini-tts") return ttsAlt(ctx, p, String(b.text ?? ""), String(b.voice ?? ""), String(b.model ?? ""), userKey);
        return tts(ctx, p, String(b.text ?? ""), String(b.voice ?? ""), userKey);
      }
      if (path === "/stt") return stt(ctx, String(b.provider ?? "groq"), String(b.audioBase64 ?? ""), String(b.mimeType ?? "audio/m4a"), userKey);
      if (path === "/video") {
        if (String(b.provider ?? "") === "pollinations") {
          // Pollinations video (Wan) — endpoint verified 2026-11-16; REQUIRES a
          // Pollinations API key (401 without one). Bytes are streamed through.
          const key = (typeof userKey === "string" && userKey) || ctx.env.get("POLLINATIONS_API_KEY");
          if (!key) return fail("pollinations", "no_key", "Pollinations video needs an API key (free account key at enter.pollinations.ai/keys) — set POLLINATIONS_API_KEY on the backend or pass userKey", 400);
          const model = String(b.model ?? "wan-fast");
          const res = await call(ctx, `https://gen.pollinations.ai/video/${encodeURIComponent(String(b.prompt ?? ""))}?model=${encodeURIComponent(model)}`, {
            headers: { authorization: `Bearer ${key}` },
          }, 300000);
          if (!res.ok || !res.body) { const j = await bodyJson(res); return fail("pollinations", String(res.status), errText(j, "Pollinations video failed")); }
          return new Response(res.body, { status: 200, headers: { "content-type": res.headers.get("content-type") ?? "video/mp4", "cache-control": "no-store" } });
        }
        const key = keyFor(ctx, "gemini", userKey);
        if (!key) return fail("veo-video", "paid_no_key", "Video generation (Google Veo) is a PAID API and needs GEMINI_API_KEY on the backend. Self-hosted Wan/LTX adapters are documented separately.", 400);
        const model = String(b.model ?? "veo-3.0-generate-001");
        const res = await call(ctx, `https://generativelanguage.googleapis.com/v1beta/models/${model}:predictLongRunning`, {
          method: "POST", headers: { "content-type": "application/json", "x-goog-api-key": key },
          body: JSON.stringify({ instances: [{ prompt: String(b.prompt ?? "") }] }),
        }, 60000);
        const j = await bodyJson(res);
        if (!res.ok) return fail("veo-video", String(res.status), errText(j, "Veo request failed"));
        return json(200, { ok: true, provider: "veo-video", model, operation: j.name, note: "Poll GET /video/status?name=<operation> until done=true" });
      }
      if (path === "/video/status") {
        const key = keyFor(ctx, "gemini", userKey);
        if (!key) return fail("veo-video", "paid_no_key", "Needs GEMINI_API_KEY on the backend", 400);
        const name = String(b.name ?? "");
        const res = await call(ctx, `https://generativelanguage.googleapis.com/v1beta/${name}`, { headers: { "x-goog-api-key": key } }, 30000);
        const j = await bodyJson(res);
        if (!res.ok) return fail("veo-video", String(res.status), errText(j, "Veo status failed"));
        return json(200, { ok: true, provider: "veo-video", done: j.done === true, operation: j });
      }
      if (path === "/memory") {
        // Long-term memories (migration 003) via PostgREST with the service key
        // that Supabase injects into every Edge Function's environment.
        const sbUrl = ctx.env.get("SUPABASE_URL");
        const sbKey = ctx.env.get("SUPABASE_SERVICE_ROLE_KEY");
        if (!sbUrl || !sbKey) return fail("memory", "not_configured", "SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY are not available to this function", 500);
        const headers = { "content-type": "application/json", apikey: sbKey, authorization: `Bearer ${sbKey}` };
        if (req.method === "GET") {
          const uid = url.searchParams.get("userId") ?? "";
          const res = await call(ctx, `${sbUrl}/rest/v1/memories?user_id=eq.${encodeURIComponent(uid)}&order=created_at.desc&limit=50`, { headers }, 20000);
          if (!res.ok) return fail("memory", String(res.status), "Could not read memories");
          return json(200, { ok: true, memories: await res.json().catch(() => []) });
        }
        const uid = String(b.userId ?? "");
        const text = String(b.text ?? "").trim();
        if (!uid || !text) return fail("memory", "bad_request", "userId and text are required", 400);
        const res = await call(ctx, `${sbUrl}/rest/v1/memories`, {
          method: "POST", headers: { ...headers, prefer: "return=representation" },
          body: JSON.stringify({ user_id: uid, kind: String(b.kind ?? "note"), text: text.slice(0, 2000) }),
        }, 20000);
        if (!res.ok) return fail("memory", String(res.status), "Could not save the memory (is migration 003 applied?)");
        return json(200, { ok: true, saved: await res.json().catch(() => null) });
      }
      if (path === "/browse") {
        const base = ctx.env.get("BROWSER_ENGINE_URL");
        if (!base) return fail("browser-engine", "self_hosted_not_configured", "Browser automation is SELF-HOSTED (browser-use/Playwright server). Set BROWSER_ENGINE_URL when that server runs — see backend/self-hosted/.", 501);
        const res = await call(ctx, `${base}/browse`, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ url: String(b.url ?? ""), task: String(b.task ?? "") }) }, 120000);
        const j = await bodyJson(res);
        if (!res.ok) return fail("browser-engine", String(res.status), errText(j, "Browser engine failed"));
        return json(200, { ok: true, provider: "browser-engine", result: j });
      }
      return fail("gateway", "not_found", `No route for ${path}`, 404);
    } catch (e) {
      return fail("gateway", "exception", (e as Error).message?.slice(0, 200) ?? "unknown error");
    }
  };
}
