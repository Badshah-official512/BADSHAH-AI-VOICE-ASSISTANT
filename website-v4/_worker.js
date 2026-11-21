// BADSHAH AI — secure backend v3 (Cloudflare Pages "advanced mode" worker)
//
// v3 changes (owner's order, 2026-11-18): EVERYTHING IS FREE for users.
// - All FT Fusion models are open (no paid tiers, no plan checks).
// - Owner API keys (Gemini / Groq / ElevenLabs) live in Supabase `api_keys`
//   (service-role only). The Worker rotates them; the owner manages them
//   from the Admin Panel. Users never bring their own key.
// - Admin password is verified against Supabase (verify_admin_password RPC);
//   env ADMIN_PASSWORD remains as a fallback if the RPC is unavailable.
//
// Secrets (Cloudflare):
//   (v5: FT Fusion removed — chat/image/voice/video all run on Gemini + Groq pools)
//   SUPABASE_SERVICE_ROLE_KEY  server-side only (key pool + admin actions)
//   ADMIN_PASSWORD             fallback only (primary = Supabase)
//
// Routes:
//   GET  /api/chat?model=..&q=..        FT Fusion proxy (all models free)
//   GET  /api/chat?provider=groq&q=..   Groq chat via pooled key
//   GET  /api/live-key                  pooled Gemini key for Live calls (login required)
//   POST /api/tts {text, voice}         ElevenLabs TTS via pooled key → audio/mpeg
//   POST /admin-api/<action>            Admin gateway (x-admin-password header)

/* v5 (owner order 2026-11-18): FT Fusion REMOVED completely.
   Chat = Gemini (REST) + Groq. Images = Gemini image models. Voice = Gemini TTS.
   Live calls = Gemini Live. Video = Gemini video models.
   NOTE: the owner's current Gemini keys (AQ. format) are LIVE-SCOPED — REST calls
   return 403, Live WebSocket works. So every REST feature tries Gemini first and
   falls back to a working free engine (Groq chat / Pollinations image /
   ElevenLabs voice) until a full Gemini API key is added to the pool. */
const GEMINI_CHAT_MODELS = new Set(['gemini-3.8-flash', 'gemini-3.5-flash-lite', 'gemini-3.1-pro-preview']);
const GEMINI_IMAGE_MODELS = new Set(['gemini-3.1-flash-image', 'gemini-3-pro-image']);
const GEMINI_TTS_MODEL = 'gemini-3.8-flash-tts';
const GEMINI_VIDEO_MODELS = new Set(['gemini-omni-1.1-flash', 'veo-3.1-generate-preview']);
const GEMINI_REST = 'https://generativelanguage.googleapis.com/v1beta/models/';
const SUPABASE_URL = 'https://aghlacmqlagmigoznxlm.supabase.co';
const FIREBASE_PROJECT = 'badshah-ai-badshah222';
/* (FT Fusion model set removed in v5) */
const MAX_Q_CHARS = 65000;
const RATE_LIMIT = 40;
const RATE_WINDOW_MS = 60 * 1000;
const hits = new Map();

const SECURITY_HEADERS = {
  'X-Content-Type-Options': 'nosniff',
  'Referrer-Policy': 'strict-origin-when-cross-origin',
  'X-Frame-Options': 'SAMEORIGIN',
  'Permissions-Policy': 'camera=(), geolocation=(), payment=()'
};

function json(status, obj, extra = {}) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store', 'X-Robots-Tag': 'noindex', ...SECURITY_HEADERS, ...extra }
  });
}
function rateLimited(ip) {
  const now = Date.now();
  const arr = (hits.get(ip) || []).filter(t => now - t < RATE_WINDOW_MS);
  arr.push(now); hits.set(ip, arr);
  return arr.length > RATE_LIMIT;
}

/* ---------------- Firebase ID token verify (JWKS, RS256) ---------------- */
let jwksCache = { at: 0, keys: {} };
async function getJwks() {
  if (Date.now() - jwksCache.at < 3600e3) return jwksCache.keys;
  const r = await fetch('https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com');
  const d = await r.json();
  const keys = {};
  for (const k of d.keys || []) keys[k.kid] = k;
  jwksCache = { at: Date.now(), keys };
  return keys;
}
const b64url = s => Uint8Array.from(atob(s.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(s.length / 4) * 4, '=')), c => c.charCodeAt(0));
async function verifyFirebaseToken(token) {
  try {
    const [h, p, s] = token.split('.');
    if (!h || !p || !s) return null;
    const header = JSON.parse(new TextDecoder().decode(b64url(h)));
    const payload = JSON.parse(new TextDecoder().decode(b64url(p)));
    const keys = await getJwks();
    const jwk = keys[header.kid];
    if (!jwk) return null;
    const key = await crypto.subtle.importKey('jwk', jwk, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['verify']);
    const ok = await crypto.subtle.verify('RSASSA-PKCS1-v1_5', key, b64url(s), new TextEncoder().encode(h + '.' + p));
    if (!ok) return null;
    const now = Math.floor(Date.now() / 1000);
    if (payload.exp < now || payload.aud !== FIREBASE_PROJECT || payload.iss !== 'https://securetoken.google.com/' + FIREBASE_PROJECT) return null;
    return payload;
  } catch (e) { return null; }
}

/* ---------------- Supabase (service role, server-side only) ---------------- */
async function sbAdmin(env, path, opts = {}) {
  if (!env.SUPABASE_SERVICE_ROLE_KEY) return null;
  const r = await fetch(SUPABASE_URL + '/rest/v1/' + path, {
    ...opts,
    headers: {
      'apikey': env.SUPABASE_SERVICE_ROLE_KEY,
      'Authorization': 'Bearer ' + env.SUPABASE_SERVICE_ROLE_KEY,
      'Content-Type': 'application/json',
      'Prefer': opts.prefer || 'return=representation',
      ...(opts.headers || {})
    }
  });
  if (!r.ok) throw new Error('supabase ' + r.status + ' ' + (await r.text()).slice(0, 200));
  return r.status === 204 ? null : r.json();
}
async function sbRpc(env, fn, args) {
  if (!env.SUPABASE_SERVICE_ROLE_KEY) return null;
  const r = await fetch(SUPABASE_URL + '/rest/v1/rpc/' + fn, {
    method: 'POST',
    headers: { 'apikey': env.SUPABASE_SERVICE_ROLE_KEY, 'Authorization': 'Bearer ' + env.SUPABASE_SERVICE_ROLE_KEY, 'Content-Type': 'application/json' },
    body: JSON.stringify(args || {})
  });
  if (!r.ok) throw new Error('rpc ' + fn + ' ' + r.status);
  return r.json();
}

/* ---------------- API key pool ---------------- */
async function poolKeys(env, provider) {
  const rows = await sbAdmin(env, 'api_keys?select=id,key_value,use_count&provider=eq.' + provider + '&active=eq.true&order=last_used_at.asc.nullsfirst&limit=10');
  return Array.isArray(rows) ? rows : [];
}
async function touchKey(env, row) {
  try { await sbAdmin(env, 'api_keys?id=eq.' + row.id, { method: 'PATCH', body: JSON.stringify({ use_count: (row.use_count || 0) + 1, last_used_at: new Date().toISOString() }) }); } catch (e) {}
}

/* ---------------- /api/chat (Gemini + Groq) ---------------- */
const CHAT_SYSTEM = 'You are BADSHAH AI — a friendly, smart AI assistant created by Zain Shah (Badshah). If anyone asks who you are, say you are BADSHAH AI — never say you are ChatGPT, GPT, Llama or any other product. Reply in the SAME language and script as the user: if the user writes Roman Urdu, reply in Roman Urdu using Latin letters only — never use Devanagari (Hindi) script unless the user writes in it. If the user writes Urdu script, reply in Urdu script. Keep answers helpful, natural and to the point.';
async function fetchT(url, opts, ms) {
  const c = new AbortController(); const t = setTimeout(() => c.abort(), ms || 20000);
  try { return await fetch(url, { ...(opts || {}), signal: c.signal }); } finally { clearTimeout(t); }
}
async function groqAnswer(env, model, q) {
  if (!env.SUPABASE_SERVICE_ROLE_KEY) return null;
  const keys = await poolKeys(env, 'groq').catch(() => []);
  for (const k of keys) {
    const r = await fetchT('https://api.groq.com/openai/v1/chat/completions', {
      method: 'POST',
      headers: { 'Authorization': 'Bearer ' + k.key_value, 'Content-Type': 'application/json' },
      body: JSON.stringify({ model, messages: [{ role: 'system', content: CHAT_SYSTEM }, { role: 'user', content: q }] })
    }, 25000).catch(() => null);
    if (!r) return null;
    if (r.ok) {
      const d = await r.json();
      await touchKey(env, k);
      return ((d.choices && d.choices[0] && d.choices[0].message && d.choices[0].message.content) || '');
    }
    if (r.status !== 401 && r.status !== 429) return null;
  }
  return null;
}
async function oaiAnswer(env, provider, baseUrl, model, q) {
  if (!env.SUPABASE_SERVICE_ROLE_KEY) return null;
  const keys = await poolKeys(env, provider).catch(() => []);
  for (const k of keys) {
    const r = await fetchT(baseUrl + '/chat/completions', {
      method: 'POST',
      headers: { 'Authorization': 'Bearer ' + k.key_value, 'Content-Type': 'application/json' },
      body: JSON.stringify({ model, messages: [{ role: 'system', content: CHAT_SYSTEM }, { role: 'user', content: q }] })
    }, 25000).catch(() => null);
    if (!r) return null;
    if (r.ok) {
      const d = await r.json();
      await touchKey(env, k);
      return ((d.choices && d.choices[0] && d.choices[0].message && d.choices[0].message.content) || '');
    }
    if (r.status !== 401 && r.status !== 429) return null;
  }
  return null;
}
async function workersAiAnswer(env, q) {
  if (!env.AI || !env.AI.run) return null;
  try {
    const d = await env.AI.run('@cf/openai/gpt-oss-120b', { messages: [{ role: 'system', content: CHAT_SYSTEM }, { role: 'user', content: q }] });
    return (d && (d.response || d.result)) || null;
  } catch (e) { return null; }
}
async function geminiAnswer(env, model, q) {
  if (!env.SUPABASE_SERVICE_ROLE_KEY) return null;
  const keys = await poolKeys(env, 'gemini').catch(() => []);
  for (const k of keys) {
    const r = await fetchT(GEMINI_REST + model + ':generateContent', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-goog-api-key': k.key_value },
      body: JSON.stringify({ systemInstruction: { parts: [{ text: CHAT_SYSTEM }] }, contents: [{ parts: [{ text: q }] }] })
    }, 10000).catch(() => null);
    if (!r) continue;
    if (r.ok) {
      const d = await r.json();
      await touchKey(env, k);
      const parts = (((d.candidates || [])[0] || {}).content || {}).parts || [];
      return parts.map(p => p.text || '').join('').trim();
    }
    if (r.status !== 401 && r.status !== 403 && r.status !== 429 && r.status !== 404) return null;
  }
  return null;
}
async function handleChat(req, env, url) {
  { const g = await gate(env, 'chat'); if (g) return g; }
  const ip = req.headers.get('cf-connecting-ip') || 'unknown';
  if (rateLimited(ip)) return json(429, { error: 'Too many requests — thoda ruk kar try karo.' });
  let q = url.searchParams.get('q') || '';
  if (!q && req.method === 'POST') { const pb = await req.json().catch(() => ({})); q = String(pb.q || ''); }
  if (!q) return json(400, { error: 'q is empty' });
  if (q.length > MAX_Q_CHARS) return json(413, { error: 'Message too long' });
  const provider = url.searchParams.get('provider') || '';

  if (provider === 'groq') {
    const model = url.searchParams.get('model') || 'openai/gpt-oss-120b';
    const text = await groqAnswer(env, model, q);
    if (text !== null) return json(200, { text, provider: 'groq', model });
    return json(502, { error: 'Groq abhi jawab nahi de raha — thodi dair baad try karo.' });
  }

  if (provider === 'openrouter') {
    const model = url.searchParams.get('model') || 'openrouter/free';
    const text = await oaiAnswer(env, 'openrouter', 'https://openrouter.ai/api/v1', model, q);
    if (text !== null) return json(200, { text, provider: 'openrouter', model });
    return json(502, { error: 'OpenRouter abhi jawab nahi de raha — Admin Panel mein key check karo, ya thodi dair baad try karo.' });
  }

  if (provider === 'mistral') {
    const model = url.searchParams.get('model') || 'mistral-small-latest';
    const text = await oaiAnswer(env, 'mistral', 'https://api.mistral.ai/v1', model, q);
    if (text !== null) return json(200, { text, provider: 'mistral', model });
    return json(502, { error: 'Mistral abhi jawab nahi de raha — Admin Panel mein key check karo, ya thodi dair baad try karo.' });
  }

  const model = url.searchParams.get('model') || 'gemini-3.8-flash';
  if (!GEMINI_CHAT_MODELS.has(model)) return json(400, { error: 'Unknown model: ' + model });
  const gtext = await geminiAnswer(env, model, q);
  if (gtext) return json(200, { text: gtext, provider: 'gemini', model });
  const ftext = await groqAnswer(env, 'openai/gpt-oss-120b', q);
  if (ftext !== null) return json(200, { text: ftext, provider: 'groq', model: 'openai/gpt-oss-120b', note: 'gemini_pending_full_key' });
  const otext = await oaiAnswer(env, 'openrouter', 'https://openrouter.ai/api/v1', 'openrouter/free', q);
  if (otext !== null) return json(200, { text: otext, provider: 'openrouter', model: 'openrouter/free', note: 'failover' });
  const mtext = await oaiAnswer(env, 'mistral', 'https://api.mistral.ai/v1', 'mistral-small-latest', q);
  if (mtext !== null) return json(200, { text: mtext, provider: 'mistral', model: 'mistral-small-latest', note: 'failover' });
  const wtext = await workersAiAnswer(env, q);
  if (wtext !== null) return json(200, { text: wtext, provider: 'workersai', model: '@cf/openai/gpt-oss-120b', note: 'failover' });
  return json(502, { error: 'Abhi koi chat engine jawab nahi de raha — thodi dair baad try karo.' });
}

/* ---------------- extra image/video providers (v6.1) ----------------
   Image order: Gemini -> Cloudflare Workers AI (binding "AI") -> Stability (pool key) -> Replicate FLUX (pool key) -> Pollinations (free, no key).
   Video: Replicate MiniMax video-01 (pool key "replicate") when Gemini video is unavailable. */
function b64(buf) { let s = ''; const u = new Uint8Array(buf); for (let i = 0; i < u.length; i += 0x8000) s += String.fromCharCode.apply(null, u.subarray(i, i + 0x8000)); return btoa(s); }
async function altImage(env, q) {
  try { if (env.AI) { const o = await env.AI.run('@cf/black-forest-labs/flux-1-schnell', { prompt: q, steps: 4 }); if (o && o.image) return { image: 'data:image/jpeg;base64,' + o.image, provider: 'cloudflare', model: 'flux-1-schnell' }; } } catch (e) {}
  if (!env.SUPABASE_SERVICE_ROLE_KEY) return null;
  try { const ks = await poolKeys(env, 'stability').catch(() => []);
    for (const k of ks) { const f = new FormData(); f.append('prompt', q); f.append('output_format', 'jpeg');
      const r = await fetch('https://api.stability.ai/v2beta/stable-image/generate/core', { method: 'POST', headers: { Authorization: 'Bearer ' + k.key_value, Accept: 'image/*' }, body: f });
      if (r.ok) { await touchKey(env, k); return { image: 'data:image/jpeg;base64,' + b64(await r.arrayBuffer()), provider: 'stability', model: 'core' }; } } } catch (e) {}
  try { const ks = await poolKeys(env, 'replicate').catch(() => []);
    for (const k of ks) { const r = await fetch('https://api.replicate.com/v1/models/black-forest-labs/flux-schnell/predictions', { method: 'POST', headers: { Authorization: 'Bearer ' + k.key_value, 'Content-Type': 'application/json', Prefer: 'wait' }, body: JSON.stringify({ input: { prompt: q } }) });
      const d = await r.json().catch(() => ({})); const out = Array.isArray(d.output) ? d.output[0] : d.output;
      if (r.ok && out) { await touchKey(env, k); return { image: out, provider: 'replicate', model: 'flux-schnell' }; } } } catch (e) {}
  return null;
}
async function replicateVideo(env, req, url, prompt) {
  const ks = await poolKeys(env, 'replicate').catch(() => []); if (!ks.length) return null; const key = ks[0].key_value;
  const name = url.searchParams.get('name') || '';
  if (name.startsWith('rep:')) {
    const r = await fetch('https://api.replicate.com/v1/predictions/' + name.slice(4), { headers: { Authorization: 'Bearer ' + key } }); const d = await r.json().catch(() => ({}));
    if (!r.ok) return json(502, { error: 'Video status nahi mila (' + r.status + ').' });
    if (d.status === 'failed' || d.status === 'canceled') return json(502, { error: 'Video nahi ban saki: ' + (d.error || d.status) });
    return json(200, { done: d.status === 'succeeded', url: Array.isArray(d.output) ? d.output[0] : d.output, provider: 'replicate' });
  }
  const r = await fetch('https://api.replicate.com/v1/models/minimax/video-01/predictions', { method: 'POST', headers: { Authorization: 'Bearer ' + key, 'Content-Type': 'application/json' }, body: JSON.stringify({ input: { prompt } }) });
  const d = await r.json().catch(() => ({})); if (!r.ok || !d.id) return null;
  await touchKey(env, ks[0]); return json(200, { name: 'rep:' + d.id, done: false, provider: 'replicate', model: 'minimax/video-01' });
}

/* ---------------- /api/image (Gemini image models) ---------------- */
async function handleImage(req, env, url) {
  { const g = await gate(env, 'image'); if (g) return g; }
  const ip = req.headers.get('cf-connecting-ip') || 'unknown';
  if (rateLimited(ip)) return json(429, { error: 'Too many requests — thoda ruk kar try karo.' });
  const q = url.searchParams.get('q') || '';
  if (!q) return json(400, { error: 'q is empty' });
  const model = url.searchParams.get('model') || 'gemini-3.1-flash-image';
  if (!GEMINI_IMAGE_MODELS.has(model)) return json(400, { error: 'Unknown image model: ' + model });
  if (env.SUPABASE_SERVICE_ROLE_KEY) {
    const keys = await poolKeys(env, 'gemini').catch(() => []);
    for (const k of keys) {
      const r = await fetch(GEMINI_REST + model + ':generateContent', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'x-goog-api-key': k.key_value },
        body: JSON.stringify({ contents: [{ parts: [{ text: q }] }], generationConfig: { responseModalities: ['TEXT', 'IMAGE'] } })
      });
      if (r.ok) {
        const d = await r.json();
        const parts = (((d.candidates || [])[0] || {}).content || {}).parts || [];
        const img = parts.find(p => p.inlineData && p.inlineData.data);
        if (img) { await touchKey(env, k); return json(200, { image: 'data:' + (img.inlineData.mimeType || 'image/png') + ';base64,' + img.inlineData.data, provider: 'gemini', model }); }
      }
      if (r.status !== 401 && r.status !== 403 && r.status !== 429 && r.status !== 404) break;
    }
  }
  { const alt = await altImage(env, q); if (alt) return json(200, alt); }
  // v7.3: owner ki Pollinations image key (Supabase pool) — key kabhi frontend par nahi jati, image yahin se proxy hoti hai
  if (env.SUPABASE_SERVICE_ROLE_KEY) {
    const pks = await poolKeys(env, 'pollinations-image').catch(() => []);
    for (const k of pks) {
      try {
        const r = await fetchT('https://gen.pollinations.ai/image/' + encodeURIComponent(q) + '?width=1024&height=1024&model=flux&nologo=true', { headers: { 'Authorization': 'Bearer ' + k.key_value } }, 45000);
        if (r.ok) { const buf = await r.arrayBuffer(); await touchKey(env, k);
          let bin = ''; const bytes = new Uint8Array(buf); const CH = 32768;
          for (let i = 0; i < bytes.length; i += CH) bin += String.fromCharCode.apply(null, bytes.subarray(i, i + CH));
          return json(200, { image: 'data:image/jpeg;base64,' + btoa(bin), provider: 'pollinations-key', model: 'flux' }); }
        if (r.status !== 401 && r.status !== 403 && r.status !== 429) break;
      } catch (e) {}
    }
  }
  const purl = 'https://gen.pollinations.ai/image/' + encodeURIComponent(q) + '?width=1024&height=1024&model=flux&nologo=true';
  return json(200, { image: purl, provider: 'pollinations', model: 'flux', note: 'gemini_pending_full_key' });
}

/* ---------------- /api/video (Gemini video models, long-running) ---------------- */
async function handleVideo(req, env, url) {
  { const g = await gate(env, 'video'); if (g) return g; }
  if (!env.SUPABASE_SERVICE_ROLE_KEY) return json(501, { error: 'Key pool setup pending hai.' });
  const opName0 = url.searchParams.get('name') || '';
  // v7.3: Agnes AI video (owner ki asal video key — platform.agnes-ai.com; pool provider 'agnes')
  const akeys = await poolKeys(env, 'agnes').catch(() => []);
  if (req.method === 'GET' && opName0.startsWith('agn:')) {
    let job = null;
    try { job = JSON.parse(decodeURIComponent(escape(atob(opName0.slice(4).replace(/-/g, '+').replace(/_/g, '/'))))); } catch (e) {}
    if (!job || !job.v) return json(400, { error: 'Video token ghalat hai.' });
    const k = akeys.find(x => String(x.id) === String(job.k)) || akeys[0];
    if (!k) return json(404, { error: 'Agnes key pool mein nahi hai.' });
    try {
      let r = await fetchT('https://apihub.agnes-ai.com/agnesapi?video_id=' + encodeURIComponent(job.v) + '&model_name=agnes-video-2.5-flash', { headers: { 'Authorization': 'Bearer ' + k.key_value } }, 30000);
      if (r.status === 404) r = await fetchT('https://apihub.agnes-ai.com/v1/agnesapi?video_id=' + encodeURIComponent(job.v) + '&model_name=agnes-video-2.5-flash', { headers: { 'Authorization': 'Bearer ' + k.key_value } }, 30000);
      const d = await r.json().catch(() => ({}));
      const st = String(d.status || (d.data && d.data.status) || '').toLowerCase();
      if (st === 'failed') return json(502, { error: 'Agnes par video fail ho gayi — doosra prompt try karo.' });
      if (st === 'completed' || st === 'succeeded' || st === 'success') {
        const s = JSON.stringify(d); const m = s.match(/https?:[^"\\\s]+\.mp4[^"\\\s]*/i) || s.match(/https?:[^"\\\s]+/i);
        if (m) { await touchKey(env, k); return json(200, { done: true, url: m[0], provider: 'agnes' }); }
      }
      return json(200, { done: false });
    } catch (e) { return json(200, { done: false }); }
  }
  if (req.method === 'POST' && akeys.length) {
    const b = await req.clone().json().catch(() => ({}));
    const pr = String(b.prompt || '').slice(0, 1000);
    if (pr) {
      for (const k of akeys) {
        try {
          const r = await fetchT('https://apihub.agnes-ai.com/v1/videos', { method: 'POST', headers: { 'Authorization': 'Bearer ' + k.key_value, 'Content-Type': 'application/json' }, body: JSON.stringify({ model: 'agnes-video-2.5-flash', prompt: pr, mode: 'text', seconds: '5', size: '720P', aspect_ratio: '9:16' }) }, 45000);
          const d = await r.json().catch(() => ({}));
          const vid = d.video_id || (d.data && d.data.video_id) || d.id || d.task_id || (d.data && (d.data.id || d.data.task_id));
          if (vid) { await touchKey(env, k); const tok = 'agn:' + btoa(unescape(encodeURIComponent(JSON.stringify({ v: String(vid), k: k.id })))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, ''); return json(200, { name: tok, done: false, provider: 'agnes', model: 'agnes-video-2.5-flash' }); }
          const code = String(d.code || (d.error && d.error.code) || '');
          if (code === 'video_queue_full') return json(429, { error: 'Agnes ki free video queue abhi full hai — thodi dair baad phir try karo.' });
          if (code === 'insufficient_user_quota') return json(402, { error: 'Agnes account ka quota khatam hai.' });
        } catch (e) {}
      }
      return json(502, { error: 'Agnes par video start nahi hui — thodi dair baad try karo.' });
    }
  }
  if (req.method === 'GET' && opName0.startsWith('rep:')) { const rv = await replicateVideo(env, req, url, ''); if (rv) return rv; }
  // v7.3: Pollinations video (owner ki keys, Supabase pool) — POST par stateless token, GET poll par ya to MP4 binary ya {done:false}
  const vkeys = [...await poolKeys(env, 'pollinations-video').catch(() => []), ...await poolKeys(env, 'pollinations-image').catch(() => [])];
  if (req.method === 'GET' && opName0.startsWith('pol:')) {
    let job = null;
    try { job = JSON.parse(decodeURIComponent(escape(atob(opName0.slice(4).replace(/-/g, '+').replace(/_/g, '/'))))); } catch (e) {}
    if (!job || !job.p) return json(400, { error: 'Video token ghalat hai.' });
    if (!vkeys.length) return json(404, { error: 'Video key pool mein nahi hai.' });
    for (const k of vkeys) {
      try {
        const r = await fetchT('https://gen.pollinations.ai/video/' + encodeURIComponent(job.p) + '?model=' + encodeURIComponent(job.m || 'seedance-2.5-flash'), { headers: { 'Authorization': 'Bearer ' + k.key_value } }, 50000);
        if (r.ok) { const buf = await r.arrayBuffer(); await touchKey(env, k); return new Response(buf, { status: 200, headers: { 'Content-Type': 'video/mp4', 'Cache-Control': 'no-store' } }); }
        if (r.status === 401 || r.status === 403) return json(401, { error: 'Is key par video generation ki ijazat nahi hai — Pollinations par video ke liye key par balance/entitlement chahiye. Sahi video key Admin Panel mein dalo to video chal jayega.' });
      } catch (e) {}
    }
    return json(200, { done: false });
  }
  if (req.method === 'POST' && vkeys.length) {
    const b = await req.clone().json().catch(() => ({}));
    const pr = String(b.prompt || '').slice(0, 1000);
    if (pr) { const mdl = String(b.model || '').includes('ltx') ? 'ltx-2.5-pro' : 'seedance-2.5-flash';
      const tok = 'pol:' + btoa(unescape(encodeURIComponent(JSON.stringify({ p: pr, m: mdl })))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
      return json(200, { name: tok, done: false, provider: 'pollinations', model: mdl }); }
  }
  const keys = await poolKeys(env, 'gemini').catch(() => []);
  if (!keys.length) { const b0 = await req.clone().json().catch(() => ({})); const rv = await replicateVideo(env, req, url, String(b0.prompt || '').slice(0, 1000)); if (rv) return rv; return json(404, { error: 'Video ke liye Gemini ya Replicate key available nahi hai.' }); }
  const key = keys[0].key_value;
  const opName = url.searchParams.get('name') || '';
  if (req.method === 'GET' && opName) {
    const r = await fetch('https://generativelanguage.googleapis.com/v1beta/' + opName, { headers: { 'x-goog-api-key': key } });
    const d = await r.json().catch(() => ({}));
    if (!r.ok) return json(r.status === 403 ? 403 : 502, { error: 'Video status nahi mila (' + r.status + ').', google: d.error ? d.error.message : undefined });
    return json(200, d);
  }
  const body = await req.json().catch(() => ({}));
  const prompt = String(body.prompt || '').slice(0, 1000);
  if (!prompt) return json(400, { error: 'prompt is empty' });
  const model = String(body.model || 'gemini-omni-1.1-flash');
  if (!GEMINI_VIDEO_MODELS.has(model)) return json(400, { error: 'Unknown video model: ' + model });
  const r = await fetch(GEMINI_REST + model + ':predictLongRunning', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'x-goog-api-key': key },
    body: JSON.stringify({ instances: [{ prompt }] })
  });
  const d = await r.json().catch(() => ({}));
  if (!r.ok) { const rv = await replicateVideo(env, req, url, prompt); if (rv) return rv; }
  if (!r.ok) return json(502, { error: 'Video abhi start nahi ho saka (' + r.status + ') — video models ke liye full Gemini API key chahiye; maujooda AQ key sirf Live calls ke liye hai.', google: d.error ? d.error.message : undefined });
  await touchKey(env, keys[0]);
  return json(200, { name: d.name || null, done: !!d.done, provider: 'gemini', model });
}

/* ---------------- /api/live-key (pooled Gemini key, login required) ---------------- */
async function handleLiveKey(req, env) {
  { const g = await gate(env, 'call'); if (g) return g; }
  const authz = req.headers.get('authorization') || '';
  const token = authz.startsWith('Bearer ') ? authz.slice(7) : null;
  if (!token) return json(401, { error: 'Pehle login karo — call ke liye account lazmi hai.', code: 'LOGIN_REQUIRED' });
  const payload = await verifyFirebaseToken(token);
  if (!payload) return json(401, { error: 'Session expire — dobara login karo.', code: 'BAD_TOKEN' });
  if (!env.SUPABASE_SERVICE_ROLE_KEY) return json(501, { error: 'Key pool setup pending hai.' });
  try { const bu = await sbAdmin(env, 'web_users?select=blocked&firebase_uid=eq.' + encodeURIComponent(payload.user_id || payload.sub) + '&limit=1'); if (bu && bu[0] && bu[0].blocked) return json(403, { error: 'Aapka account admin ne block kiya hai.', code: 'BLOCKED' }); } catch (e) {}
  const keys = await poolKeys(env, 'gemini').catch(() => []);
  if (!keys.length) return json(404, { error: 'Abhi koi Gemini key available nahi — thodi dair baad try karo.' });
  await touchKey(env, keys[0]);
  return json(200, { key: keys[0].key_value });
}

/* ---------------- /api/tts (ElevenLabs pool) ---------------- */
const EL_VOICES = { rani: '21m00Tcm4TlvDq8ikWAM', badshah: 'pNInz6obpgDQGcFmaJgB' };
function pcmToWav(b64, rate) {
  const bin = atob(b64); const pcm = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) pcm[i] = bin.charCodeAt(i);
  const out = new Uint8Array(44 + pcm.length); const v = new DataView(out.buffer);
  const wstr = (o, s) => { for (let i = 0; i < s.length; i++) v.setUint8(o + i, s.charCodeAt(i)); };
  wstr(0, 'RIFF'); v.setUint32(4, 36 + pcm.length, true); wstr(8, 'WAVEfmt '); v.setUint32(16, 16, true);
  v.setUint16(20, 1, true); v.setUint16(22, 1, true); v.setUint32(24, rate, true); v.setUint32(28, rate * 2, true);
  v.setUint16(32, 2, true); v.setUint16(34, 16, true); wstr(36, 'data'); v.setUint32(40, pcm.length, true);
  out.set(pcm, 44); return out;
}
async function handleTts(req, env) {
  { const g = await gate(env, 'voice'); if (g) return g; }
  const body = await req.json().catch(() => ({}));
  const text = String(body.text || '').slice(0, 800);
  if (!text) return json(400, { error: 'text is empty' });
  if (!env.SUPABASE_SERVICE_ROLE_KEY) return json(501, { error: 'TTS setup pending hai.' });
  // 1) Gemini TTS (owner's model map) — skipped when the caller forces ElevenLabs
  if (body.provider !== 'elevenlabs') {
    const gvoice = body.voice === 'badshah' ? 'Charon' : 'Achernar';
    const gkeys = await poolKeys(env, 'gemini').catch(() => []);
    for (const k of gkeys) {
      const r = await fetch(GEMINI_REST + GEMINI_TTS_MODEL + ':generateContent', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'x-goog-api-key': k.key_value },
        body: JSON.stringify({ contents: [{ parts: [{ text }] }], generationConfig: { responseModalities: ['AUDIO'], speechConfig: { voiceConfig: { prebuiltVoiceConfig: { voiceName: gvoice } } } } })
      });
      if (r.ok) {
        const d = await r.json();
        const parts = (((d.candidates || [])[0] || {}).content || {}).parts || [];
        const au = parts.find(p => p.inlineData && p.inlineData.data);
        if (au) { await touchKey(env, k); return new Response(pcmToWav(au.inlineData.data, 24000), { status: 200, headers: { 'content-type': 'audio/wav', 'cache-control': 'no-store', ...SECURITY_HEADERS } }); }
      }
      if (r.status !== 401 && r.status !== 403 && r.status !== 429 && r.status !== 404) break;
    }
  }
  // 2) ElevenLabs pool (working voice today)
  const userKey = body.userKey ? String(body.userKey).trim() : '';
  const keys = userKey.length >= 20 ? [{ key_value: userKey }] : await poolKeys(env, 'elevenlabs').catch(() => []);
  if (!keys.length) return json(404, { error: 'ElevenLabs key nahi mili — apni key dalo.' });
  const voice = EL_VOICES[body.voice] || body.voice || EL_VOICES.badshah;
  for (const k of keys) {
    const r = await fetch('https://api.elevenlabs.io/v1/text-to-speech/' + voice, {
      method: 'POST',
      headers: { 'xi-api-key': k.key_value, 'Content-Type': 'application/json', 'accept': 'audio/mpeg' },
      body: JSON.stringify({ text, model_id: 'eleven_multilingual_v2', voice_settings: { stability: 0.45, similarity_boost: 0.8 } })
    });
    if (r.ok) { if (!userKey) { try { await touchKey(env, k); } catch (e) {} } return new Response(r.body, { status: 200, headers: { 'content-type': 'audio/mpeg', 'cache-control': 'no-store', ...SECURITY_HEADERS } }); }
    if (r.status !== 401 && r.status !== 429) return json(502, { error: 'ElevenLabs error ' + r.status });
  }
  return json(502, { error: 'Saari ElevenLabs keys ki limit khatam — admin se nayi key add karwao.' });
}

/* ---------------- /api/voices (ElevenLabs voice list for the call voice picker) ---------------- */
async function handleVoices(req, env) {
  if (!env.SUPABASE_SERVICE_ROLE_KEY) return json(501, { error: 'Key pool setup pending hai.' });
  const keys = await poolKeys(env, 'elevenlabs').catch(() => []);
  if (!keys.length) return json(404, { error: 'ElevenLabs key pool khaali hai.' });
  for (const k of keys) {
    const r = await fetch('https://api.elevenlabs.io/v1/voices', { headers: { 'xi-api-key': k.key_value } });
    if (r.ok) {
      const d = await r.json();
      const voices = (d.voices || []).slice(0, 40).map(v => ({ id: v.voice_id, name: v.name, cat: v.category || '' }));
      return json(200, { voices });
    }
    if (r.status !== 401 && r.status !== 429) return json(502, { error: 'ElevenLabs error ' + r.status });
  }
  return json(502, { error: 'ElevenLabs voices nahi mil sakin.' });
}

/* ---------------- /api/stt (Groq Whisper via pool — for ElevenLabs call mode) ---------------- */
async function handleStt(req, env) {
  if (!env.SUPABASE_SERVICE_ROLE_KEY) return json(501, { error: 'Key pool setup pending hai.' });
  const buf = await req.arrayBuffer().catch(() => null);
  if (!buf || buf.byteLength < 1200) return json(400, { error: 'audio missing/too short' });
  if (buf.byteLength > 8 * 1024 * 1024) return json(413, { error: 'audio too large' });
  const keys = await poolKeys(env, 'groq').catch(() => []);
  if (!keys.length) return json(404, { error: 'Groq key pool khaali hai.' });
  const ct = req.headers.get('content-type') || 'audio/wav';
  const ext = ct.includes('webm') ? 'webm' : ct.includes('mp4') ? 'm4a' : ct.includes('mpeg') ? 'mp3' : 'wav';
  for (const k of keys) {
    const fd = new FormData();
    fd.append('file', new Blob([buf], { type: ct }), 'audio.' + ext);
    fd.append('model', 'whisper-large-v3-turbo');
    fd.append('response_format', 'json');
    const r = await fetch('https://api.groq.com/openai/v1/audio/transcriptions', { method: 'POST', headers: { 'Authorization': 'Bearer ' + k.key_value }, body: fd });
    if (r.ok) { const d = await r.json(); await touchKey(env, k); return json(200, { text: (d.text || '').trim() }); }
    if (r.status !== 401 && r.status !== 429) { const t = await r.text().catch(() => ''); return json(502, { error: 'STT error ' + r.status + ' ' + t.slice(0, 120) }); }
  }
  return json(502, { error: 'Saari Groq keys busy hain — dobara bolo.' });
}

/* ---------------- /admin-api ---------------- */
/* Admin password lives ONLY in Supabase (owner order 2026-11-18) — verified via verify_admin_password RPC. */
async function adminAuthed(req, env) {
  const given = req.headers.get('x-admin-password') || '';
  if (!given) return false;
  try {
    const ok = await sbRpc(env, 'verify_admin_password', { p_password: given });
    if (ok === true) return true;
    if (ok === false) return false;
  } catch (e) { /* RPC unavailable → fallback below */ }
  const want = env.ADMIN_PASSWORD || '';
  if (!want || given.length !== want.length) return false;
  let diff = 0; for (let i = 0; i < want.length; i++) diff |= given.charCodeAt(i) ^ want.charCodeAt(i);
  return diff === 0;
}
function maskKey(v) { if (!v) return ''; return v.length <= 10 ? '••••' : v.slice(0, 6) + '••••••' + v.slice(-4); }
async function handleAdmin(req, env, url) {
  if (req.method === 'OPTIONS') return new Response(null, { status: 204, headers: CORS });
  if (!env.SUPABASE_SERVICE_ROLE_KEY) return json(501, { error: 'SUPABASE_SERVICE_ROLE_KEY secret set nahi hai — admin ke liye lazmi hai.' });
  if (!(await adminAuthed(req, env))) return json(401, { error: 'Wrong admin password' });
  const action = url.pathname.replace('/admin-api/', '');
  const body = req.method === 'POST' ? await req.json().catch(() => ({})) : {};
  try {
    switch (action) {
      case 'stats': {
        const [users, openTickets, keys, pairings] = await Promise.all([
          sbAdmin(env, 'profiles?select=id,created_at').catch(() => []),
          sbAdmin(env, 'support_tickets?select=id&status=eq.open').catch(() => []),
          sbAdmin(env, 'api_keys?select=provider,active').catch(() => []),
          sbAdmin(env, 'computer_pairings?select=id').catch(() => [])
        ]);
        const list = Array.isArray(users) ? users : [];
        const now = Date.now(), day = 86400e3;
        const keyStats = {};
        for (const k of (keys || [])) { keyStats[k.provider] = keyStats[k.provider] || { total: 0, active: 0 }; keyStats[k.provider].total++; if (k.active) keyStats[k.provider].active++; }
        return json(200, { ok: true, totalUsers: list.length,
          newToday: list.filter(u => now - new Date(u.created_at).getTime() < day).length,
          new7d: list.filter(u => now - new Date(u.created_at).getTime() < 7 * day).length,
          openTickets: (openTickets || []).length, pairings: (pairings || []).length, keyStats });
      }
      case 'users': {
        const q = body.q ? '&or=(display_name.ilike.*' + encodeURIComponent(body.q) + '*,email.ilike.*' + encodeURIComponent(body.q) + '*)' : '';
        const rows = await sbAdmin(env, 'profiles?select=*&order=created_at.desc&limit=100' + q);
        return json(200, { ok: true, rows });
      }
      case 'user-block': {
        await sbAdmin(env, 'profiles?firebase_uid=eq.' + encodeURIComponent(body.firebase_uid), { method: 'PATCH', body: JSON.stringify({ blocked: !!body.blocked }) });
        return json(200, { ok: true });
      }
      case 'tickets': {
        const rows = await sbAdmin(env, 'support_tickets?select=*&order=created_at.desc&limit=100');
        return json(200, { ok: true, rows });
      }
      case 'ticket-reply': {
        await sbAdmin(env, 'support_tickets?id=eq.' + encodeURIComponent(body.id), { method: 'PATCH', body: JSON.stringify({ status: 'answered', admin_reply: String(body.reply || '') }) });
        return json(200, { ok: true });
      }
      case 'announce': {
        await sbAdmin(env, 'announcements', { method: 'POST', body: JSON.stringify({ title: String(body.title || 'Announcement'), body: String(body.body || ''), active: true }) });
        return json(200, { ok: true });
      }
      case 'keys-list': {
        const rows = await sbAdmin(env, 'api_keys?select=id,provider,key_value,label,active,use_count,last_used_at,created_at&order=created_at.desc');
        return json(200, { ok: true, rows: (rows || []).map(r => ({ ...r, key_value: maskKey(r.key_value) })) });
      }
      case 'keys-add': {
        const provider = String(body.provider || '');
        if (!['gemini', 'groq', 'elevenlabs', 'stability', 'replicate', 'openrouter', 'mistral', 'pollinations-image', 'pollinations-video', 'agnes'].includes(provider)) return json(400, { error: 'Provider ghalat hai' });
        if (!body.key_value || String(body.key_value).length < 10) return json(400, { error: 'Key mukammal nahi lagti' });
        await sbAdmin(env, 'api_keys', { method: 'POST', body: JSON.stringify({ provider, key_value: String(body.key_value).trim(), label: String(body.label || 'key'), active: true }) });
        return json(200, { ok: true });
      }
      case 'keys-toggle': {
        await sbAdmin(env, 'api_keys?id=eq.' + encodeURIComponent(body.id), { method: 'PATCH', body: JSON.stringify({ active: !!body.active }) });
        return json(200, { ok: true });
      }
      case 'keys-delete': {
        await sbAdmin(env, 'api_keys?id=eq.' + encodeURIComponent(body.id), { method: 'DELETE', prefer: 'return=minimal' });
        return json(200, { ok: true });
      }

      /* ---------- v6 admin actions ---------- */
      case 'overview': {
        const a = await sbRpc(env, 'bh_analytics', { p_days: 14 }).catch(() => null);
        const [openTickets, keys, live] = await Promise.all([
          sbAdmin(env, 'support_tickets?select=id&status=eq.open').catch(() => []),
          sbAdmin(env, 'api_keys?select=provider,active').catch(() => []),
          sbAdmin(env, 'site_sessions?select=sid,country,device,path_last,uid,name,last_seen,first_seen&last_seen=gte.' + new Date(Date.now() - 120000).toISOString() + '&order=last_seen.desc&limit=8').catch(() => [])
        ]);
        const keyStats = {};
        for (const k of (keys || [])) { keyStats[k.provider] = keyStats[k.provider] || { total: 0, active: 0 }; keyStats[k.provider].total++; if (k.active) keyStats[k.provider].active++; }
        return json(200, { ok: true, migrated: !!a, analytics: a, openTickets: (openTickets || []).length, keyStats, live: live || [] });
      }
      case 'analytics': {
        const a = await sbRpc(env, 'bh_analytics', { p_days: Number(body.days) || 14 }).catch(() => null);
        if (!a) return json(200, { ok: false, error: 'Analytics tables nahi mile — Supabase SQL Editor mein sql/20261118000006_analytics_control.sql run karo.' });
        return json(200, { ok: true, ...a });
      }
      case 'live': {
        const rows = await sbAdmin(env, 'site_sessions?select=*&last_seen=gte.' + new Date(Date.now() - 120000).toISOString() + '&order=last_seen.desc&limit=100').catch(() => []);
        return json(200, { ok: true, now: (rows || []).length, rows: rows || [] });
      }
      case 'web-users': {
        let f = 'web_users?select=*&order=last_seen.desc&limit=' + (Math.min(Number(body.limit) || 100, 300));
        if (body.q) { const q = encodeURIComponent(String(body.q).replace(/[*,()]/g, '')); f += '&or=(email.ilike.*' + q + '*,name.ilike.*' + q + '*)'; }
        if (body.filter === 'active') f += '&last_seen=gte.' + new Date(Date.now() - 86400e3).toISOString();
        if (body.filter === 'blocked') f += '&blocked=eq.true';
        if (body.filter === 'new') f += '&first_seen=gte.' + new Date(Date.now() - 7 * 86400e3).toISOString();
        const [rows, live] = await Promise.all([
          sbAdmin(env, f).catch(() => null),
          sbAdmin(env, 'site_sessions?select=uid&uid=not.is.null&last_seen=gte.' + new Date(Date.now() - 120000).toISOString()).catch(() => [])
        ]);
        if (rows === null) return json(200, { ok: false, error: 'web_users table nahi mili — migration 006 run karo.' });
        const on = new Set((live || []).map(x => x.uid));
        return json(200, { ok: true, rows: rows.map(u => ({ ...u, online: on.has(u.firebase_uid) })) });
      }
      case 'web-user-update': {
        const patch = {}; if (typeof body.blocked === 'boolean') patch.blocked = body.blocked; if (typeof body.note === 'string') patch.note = body.note.slice(0, 500);
        await sbAdmin(env, 'web_users?firebase_uid=eq.' + encodeURIComponent(body.firebase_uid), { method: 'PATCH', body: JSON.stringify(patch), prefer: 'return=minimal' });
        return json(200, { ok: true });
      }
      case 'web-user-delete': {
        await sbAdmin(env, 'web_users?firebase_uid=eq.' + encodeURIComponent(body.firebase_uid), { method: 'DELETE', prefer: 'return=minimal' });
        return json(200, { ok: true });
      }
      case 'settings-get': {
        cfgCache.at = 0; const c = await getConfig(env);
        return json(200, { ok: true, config: c });
      }
      case 'settings-set': {
        const c = body.config; if (!c || typeof c !== 'object' || JSON.stringify(c).length > 60000) return json(400, { error: 'Config invalid ya bahut bada hai' });
        await sbAdmin(env, 'site_settings?on_conflict=key', { method: 'POST', prefer: 'resolution=merge-duplicates,return=minimal', body: JSON.stringify({ key: 'config', value: c, updated_at: new Date().toISOString() }) });
        cfgCache = { at: 0, val: c };
        return json(200, { ok: true });
      }
      case 'announcements-list': {
        const rows = await sbAdmin(env, 'announcements?select=*&order=created_at.desc&limit=50').catch(() => []);
        return json(200, { ok: true, rows: rows || [] });
      }
      case 'announcement-toggle': {
        await sbAdmin(env, 'announcements?id=eq.' + encodeURIComponent(body.id), { method: 'PATCH', body: JSON.stringify({ active: !!body.active }), prefer: 'return=minimal' });
        return json(200, { ok: true });
      }
      case 'announcement-delete': {
        await sbAdmin(env, 'announcements?id=eq.' + encodeURIComponent(body.id), { method: 'DELETE', prefer: 'return=minimal' });
        return json(200, { ok: true });
      }
      case 'commands': {
        const rows = await sbAdmin(env, 'device_commands?select=*&order=created_at.desc&limit=50').catch(() => []);
        return json(200, { ok: true, rows: rows || [] });
      }
      case 'health': {
        const out = {};
        for (const t of ['site_sessions', 'site_events', 'web_users', 'site_settings', 'device_commands', 'announcements', 'api_keys', 'support_tickets', 'profiles']) {
          out[t] = await sbAdmin(env, t + '?select=*&limit=1').then(() => true).catch(() => false);
        }
        return json(200, { ok: true, tables: out, workerKey: true });
      }
      default:
        return json(404, { error: 'Unknown admin action: ' + action });
    }
  } catch (e) {
    return json(500, { error: String(e.message || e) });
  }
}


/* =====================================================================
   v6 — analytics, live users, web users, site/app control (admin-driven)
   Needs sql/20261118000006_analytics_control.sql (all handlers fail soft if missing).
   ===================================================================== */
const CORS = { 'access-control-allow-origin': '*', 'access-control-allow-headers': 'content-type, authorization, x-admin-password', 'access-control-allow-methods': 'GET, POST, OPTIONS' };
let cfgCache = { at: 0, val: {} };
async function getConfig(env) {
  if (Date.now() - cfgCache.at < 20000) return cfgCache.val;
  try {
    const rows = await sbAdmin(env, 'site_settings?select=value&key=eq.config&limit=1');
    cfgCache = { at: Date.now(), val: (rows && rows[0] && rows[0].value) || {} };
  } catch (e) { cfgCache = { at: Date.now() - 12000, val: cfgCache.val || {} }; }
  return cfgCache.val;
}
/* Admin can pause the whole service (maintenance) or single features. */
async function gate(env, feature) {
  const c = await getConfig(env);
  if (c.maintenance && c.maintenance.enabled) return json(503, { error: c.maintenance.message || 'Site maintenance chal rahi hai — thodi dair baad aao.', code: 'MAINTENANCE' });
  if (feature && c.features && c.features[feature] === false) return json(503, { error: 'Ye feature abhi admin ne band kiya hua hai.', code: 'FEATURE_OFF' });
  return null;
}
async function handleConfig(req, env) {
  const c = await getConfig(env);
  return json(200, { ok: true, config: c }, { 'cache-control': 'public, max-age=20', ...CORS });
}
function parseUA(ua) {
  ua = ua || '';
  const device = /ipad|tablet/i.test(ua) ? 'tablet' : /mobi|android|iphone/i.test(ua) ? 'mobile' : 'desktop';
  const browser = /edg\//i.test(ua) ? 'Edge' : /opr\/|opera/i.test(ua) ? 'Opera' : /samsungbrowser/i.test(ua) ? 'Samsung' : /firefox/i.test(ua) ? 'Firefox' : /chrome|crios/i.test(ua) ? 'Chrome' : /safari/i.test(ua) ? 'Safari' : /wv\)/i.test(ua) ? 'App' : 'Other';
  const os = /android/i.test(ua) ? 'Android' : /iphone|ipad|ios/i.test(ua) ? 'iOS' : /windows/i.test(ua) ? 'Windows' : /mac os/i.test(ua) ? 'macOS' : /linux/i.test(ua) ? 'Linux' : 'Other';
  return { device, browser: /wv\)/i.test(ua) && device === 'mobile' ? 'App/WebView' : browser, os };
}
const trackHits = new Map();
const clip = (v, n) => (v == null ? null : String(v).slice(0, n));
async function handleTrack(req, env) {
  if (req.method === 'OPTIONS') return new Response(null, { status: 204, headers: CORS });
  if (!env.SUPABASE_SERVICE_ROLE_KEY) return json(200, { ok: false }, CORS);
  const ip = req.headers.get('cf-connecting-ip') || 'x';
  const now = Date.now(); const arr = (trackHits.get(ip) || []).filter(t => now - t < 60000); arr.push(now); trackHits.set(ip, arr);
  if (arr.length > 150) return json(429, { ok: false }, CORS);
  let b = {}; try { b = JSON.parse(await req.text()); } catch (e) { return json(400, { ok: false }, CORS); }
  const sid = String(b.sid || ''); if (!/^[A-Za-z0-9_-]{8,48}$/.test(sid)) return json(400, { ok: false }, CORS);
  const type = ['start', 'pageview', 'heartbeat', 'event'].includes(b.type) ? b.type : 'heartbeat';
  const ua = parseUA(req.headers.get('user-agent'));
  const country = clip(req.headers.get('cf-ipcountry'), 4);
  const city = clip(req.cf && req.cf.city, 60);
  let ref = ''; try { if (b.ref) { const u = new URL(b.ref); if (u.host !== new URL(req.url).host) ref = u.host; } } catch (e) {}
  const path = clip(b.path, 120), uid = clip(b.uid, 80), vid = clip(b.vid, 48);
  try {
    if (type === 'start') {
      await sbAdmin(env, 'site_sessions?on_conflict=sid', { method: 'POST', prefer: 'resolution=ignore-duplicates,return=minimal',
        body: JSON.stringify({ sid, vid, uid, email: clip(b.email, 120), name: clip(b.name, 80), landing: path, path_last: path, referrer: ref, country, city, device: ua.device, browser: ua.browser, os: ua.os, is_new: !!b.isNew }) });
    } else {
      const patch = { last_seen: new Date().toISOString() };
      if (path) patch.path_last = path; if (b.n) patch.pages = Math.min(500, Number(b.n) || 1);
      if (uid) { patch.uid = uid; patch.email = clip(b.email, 120); patch.name = clip(b.name, 80); }
      await sbAdmin(env, 'site_sessions?sid=eq.' + encodeURIComponent(sid), { method: 'PATCH', prefer: 'return=minimal', body: JSON.stringify(patch) });
    }
    if (type === 'start' || type === 'pageview' || type === 'event') {
      await sbAdmin(env, 'site_events', { method: 'POST', prefer: 'return=minimal',
        body: JSON.stringify({ sid, vid, uid, type: type === 'event' ? clip(b.name || 'event', 40) : 'pageview', path, title: clip(b.title, 120), meta: b.meta && typeof b.meta === 'object' ? b.meta : null }) });
    }
  } catch (e) { return json(200, { ok: false }, CORS); }
  return json(200, { ok: true }, CORS);
}
/* signed-in user registers / refreshes itself — drives "kitne log sign up hue" */
async function handleProfileSync(req, env) {
  if (req.method === 'OPTIONS') return new Response(null, { status: 204, headers: CORS });
  const authz = req.headers.get('authorization') || '';
  const token = authz.startsWith('Bearer ') ? authz.slice(7) : null;
  const payload = token ? await verifyFirebaseToken(token) : null;
  if (!payload) return json(401, { error: 'login required' }, CORS);
  if (!env.SUPABASE_SERVICE_ROLE_KEY) return json(200, { ok: true, blocked: false }, CORS);
  const b = await req.json().catch(() => ({}));
  const uid = payload.user_id || payload.sub, email = clip(payload.email, 120), name = clip(b.name || payload.name, 80);
  const provider = clip(payload.firebase && payload.firebase.sign_in_provider, 30);
  try {
    const rows = await sbAdmin(env, 'web_users?select=*&firebase_uid=eq.' + encodeURIComponent(uid) + '&limit=1');
    if (!rows || !rows.length) {
      await sbAdmin(env, 'web_users', { method: 'POST', prefer: 'return=minimal', body: JSON.stringify({ firebase_uid: uid, email, name, provider }) });
      return json(200, { ok: true, blocked: false, isNew: true }, CORS);
    }
    const patch = { last_seen: new Date().toISOString() };
    if (email) patch.email = email; if (name) patch.name = name; if (provider) patch.provider = provider;
    if (b.login) patch.logins = (rows[0].logins || 0) + 1;
    await sbAdmin(env, 'web_users?firebase_uid=eq.' + encodeURIComponent(uid), { method: 'PATCH', prefer: 'return=minimal', body: JSON.stringify(patch) });
    return json(200, { ok: true, blocked: !!rows[0].blocked, isNew: false }, CORS);
  } catch (e) { return json(200, { ok: true, blocked: false, note: 'migration_006_pending' }, CORS); }
}
/* ---------------- /api/dost (Dost Room — group chat, v7.3) ---------------- */
async function handleDost(req, env, url) {
  const authz = req.headers.get('authorization') || '';
  const token = authz.startsWith('Bearer ') ? authz.slice(7) : null;
  if (!token) return json(401, { error: 'Dost Room ke liye pehle login karo.' });
  const payload = await verifyFirebaseToken(token);
  if (!payload) return json(401, { error: 'Session expire — dobara login karo.' });
  if (!env.SUPABASE_SERVICE_ROLE_KEY) return json(501, { error: 'Server setup pending hai.' });
  const room = (url.searchParams.get('room') || 'aam').slice(0, 40);
  if (req.method === 'GET') {
    const rows = await sbAdmin(env, 'dost_messages?select=name,text,created_at&room=eq.' + encodeURIComponent(room) + '&order=created_at.desc&limit=50');
    return json(200, { items: (rows || []).slice().reverse() });
  }
  const body = await req.json().catch(() => ({}));
  const text = String(body.text || '').trim().slice(0, 500);
  const name = String(body.name || 'Dost').slice(0, 40);
  if (!text) return json(400, { error: 'Message khaali hai.' });
  await sbAdmin(env, 'dost_messages', { method: 'POST', body: JSON.stringify({ room, name, text, uid: payload.user_id || payload.sub || '' }) });
  return json(200, { ok: true });
}

/* ---------------- /api/news (Khabrein — free RSS, v7.2) ---------------- */
async function handleNews() {
  const feeds = [['Dawn', 'https://www.dawn.com/feeds/home'], ['BBC Urdu', 'https://www.bbc.com/urdu/index.xml'], ['Geo Urdu', 'https://urdu.geo.tv/rss/']];
  const items = [];
  for (const [src, furl] of feeds) {
    try {
      const r = await fetch(furl, { headers: { 'User-Agent': 'Mozilla/5.0 BADSHAH-AI' } });
      const x = await r.text();
      const blocks = x.split('<item').slice(1, 7);
      for (const b of blocks) {
        const t = (b.match(/<title>(?:<!\[CDATA\[)?([\s\S]*?)(?:\]\]>)?<\/title>/) || [])[1];
        const l = (b.match(/<link>([\s\S]*?)<\/link>/) || [])[1];
        if (t && l) items.push({ src, title: t.replace(/<[^>]+>/g, '').trim(), link: l.trim() });
      }
    } catch (e) { }
  }
  return json(200, { items }, { 'cache-control': 'public, max-age=300' });
}

/* ---------------- /api/memories (Yaad — long-term memory, migration 003) ---------------- */
async function handleMemories(req, env, url) {
  if (req.method === 'OPTIONS') return new Response(null, { status: 204, headers: CORS });
  const authz = req.headers.get('authorization') || '';
  const token = authz.startsWith('Bearer ') ? authz.slice(7) : null;
  const payload = token ? await verifyFirebaseToken(token) : null;
  if (!payload) return json(401, { error: 'login required' }, CORS);
  if (!env.SUPABASE_SERVICE_ROLE_KEY) return json(503, { error: 'backend key missing' }, CORS);
  const uid = payload.user_id || payload.sub;
  try {
    if (req.method === 'GET') {
      const rows = await sbAdmin(env, 'memories?select=id,kind,text,created_at&user_id=eq.' + encodeURIComponent(uid) + '&order=created_at.desc&limit=100');
      return json(200, { memories: Array.isArray(rows) ? rows : [] }, CORS);
    }
    if (req.method === 'POST') {
      const b = await req.json().catch(() => ({}));
      const text = String(b.text || '').trim().slice(0, 300);
      if (!text) return json(400, { error: 'text khaali hai' }, CORS);
      const kind = ['note', 'zaat', 'kaam', 'pasand'].includes(b.kind) ? b.kind : 'note';
      const rows = await sbAdmin(env, 'memories', { method: 'POST', body: JSON.stringify({ user_id: uid, kind, text }) });
      return json(200, { ok: true, memory: Array.isArray(rows) ? rows[0] : null }, CORS);
    }
    if (req.method === 'DELETE') {
      const id = url.searchParams.get('id') || '';
      if (!id) return json(400, { error: 'id chahiye' }, CORS);
      await sbAdmin(env, 'memories?id=eq.' + encodeURIComponent(id) + '&user_id=eq.' + encodeURIComponent(uid), { method: 'DELETE', prefer: 'return=minimal' });
      return json(200, { ok: true }, CORS);
    }
    return json(405, { error: 'method not allowed' }, CORS);
  } catch (e) { return json(500, { error: 'memory server masla' }, CORS); }
}
/* device-control queue (web -> Android app / desktop agent) */
async function handleDeviceCmd(req, env) {
  if (req.method === 'OPTIONS') return new Response(null, { status: 204, headers: CORS });
  const authz = req.headers.get('authorization') || '';
  const payload = authz.startsWith('Bearer ') ? await verifyFirebaseToken(authz.slice(7)) : null;
  if (!payload) return json(401, { error: 'Pehle login karo.' }, CORS);
  const b = await req.json().catch(() => ({}));
  const uid = payload.user_id || payload.sub;
  try {
    if (b.action === 'list') { const rows = await sbAdmin(env, 'device_commands?select=*&firebase_uid=eq.' + encodeURIComponent(uid) + '&order=created_at.desc&limit=15'); return json(200, { ok: true, rows }, CORS); }
    const command = String(b.command || '').trim().slice(0, 500); if (!command) return json(400, { error: 'command khaali hai' }, CORS);
    const gz = await gate(env, 'phoneControl'); if (gz) return gz;
    await sbAdmin(env, 'device_commands', { method: 'POST', prefer: 'return=minimal', body: JSON.stringify({ firebase_uid: uid, target: b.target === 'computer' ? 'computer' : 'phone', command }) });
    return json(200, { ok: true }, CORS);
  } catch (e) { return json(200, { ok: false, error: 'Device queue abhi tayyar nahi (migration 006 baqi).' }, CORS); }
}

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    if (url.pathname === '/api/config') return handleConfig(req, env);
    if (url.pathname === '/api/track') return handleTrack(req, env);
    if (url.pathname === '/api/profile-sync') return handleProfileSync(req, env);
    if (url.pathname === '/api/memories') return handleMemories(req, env, url);
    if (url.pathname === '/api/news') return handleNews();
    if (url.pathname === '/api/dost') return handleDost(req, env, url);
    if (url.pathname === '/api/device') return handleDeviceCmd(req, env);
    if (url.pathname === '/api/chat') return handleChat(req, env, url);
    if (url.pathname === '/api/image') return handleImage(req, env, url);
    if (url.pathname === '/api/video') return handleVideo(req, env, url);
    if (url.pathname === '/api/live-key') return handleLiveKey(req, env);
    if (url.pathname === '/api/tts' && req.method === 'POST') return handleTts(req, env);
    if (url.pathname === '/api/voices') return handleVoices(req, env);
    if (url.pathname === '/api/stt' && req.method === 'POST') return handleStt(req, env);
    if (url.pathname.startsWith('/admin-api/')) return handleAdmin(req, env, url);
    const res = await env.ASSETS.fetch(req);
    const headers = new Headers(res.headers);
    for (const [k, v] of Object.entries(SECURITY_HEADERS)) headers.set(k, v);
    if (url.pathname.startsWith('/admin')) { headers.set('X-Robots-Tag', 'noindex, nofollow'); headers.set('Cache-Control', 'no-store'); }
    return new Response(res.body, { status: res.status, headers });
  }
};
