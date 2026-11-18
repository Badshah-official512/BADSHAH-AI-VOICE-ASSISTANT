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
async function groqAnswer(env, model, q) {
  if (!env.SUPABASE_SERVICE_ROLE_KEY) return null;
  const keys = await poolKeys(env, 'groq').catch(() => []);
  for (const k of keys) {
    const r = await fetch('https://api.groq.com/openai/v1/chat/completions', {
      method: 'POST',
      headers: { 'Authorization': 'Bearer ' + k.key_value, 'Content-Type': 'application/json' },
      body: JSON.stringify({ model, messages: [{ role: 'user', content: q }] })
    });
    if (r.ok) {
      const d = await r.json();
      await touchKey(env, k);
      return ((d.choices && d.choices[0] && d.choices[0].message && d.choices[0].message.content) || '');
    }
    if (r.status !== 401 && r.status !== 429) return null;
  }
  return null;
}
async function geminiAnswer(env, model, q) {
  if (!env.SUPABASE_SERVICE_ROLE_KEY) return null;
  const keys = await poolKeys(env, 'gemini').catch(() => []);
  for (const k of keys) {
    const r = await fetch(GEMINI_REST + model + ':generateContent', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-goog-api-key': k.key_value },
      body: JSON.stringify({ contents: [{ parts: [{ text: q }] }] })
    });
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
  const ip = req.headers.get('cf-connecting-ip') || 'unknown';
  if (rateLimited(ip)) return json(429, { error: 'Too many requests — thoda ruk kar try karo.' });
  const q = url.searchParams.get('q') || '';
  if (!q) return json(400, { error: 'q is empty' });
  if (q.length > MAX_Q_CHARS) return json(413, { error: 'Message too long' });
  const provider = url.searchParams.get('provider') || '';

  if (provider === 'groq') {
    const model = url.searchParams.get('model') || 'openai/gpt-oss-120b';
    const text = await groqAnswer(env, model, q);
    if (text !== null) return json(200, { text, provider: 'groq', model });
    return json(502, { error: 'Groq abhi jawab nahi de raha — thodi dair baad try karo.' });
  }

  const model = url.searchParams.get('model') || 'gemini-3.8-flash';
  if (!GEMINI_CHAT_MODELS.has(model)) return json(400, { error: 'Unknown model: ' + model });
  const gtext = await geminiAnswer(env, model, q);
  if (gtext) return json(200, { text: gtext, provider: 'gemini', model });
  const ftext = await groqAnswer(env, 'openai/gpt-oss-120b', q);
  if (ftext !== null) return json(200, { text: ftext, provider: 'groq', model: 'openai/gpt-oss-120b', note: 'gemini_pending_full_key' });
  return json(502, { error: 'Abhi koi chat engine jawab nahi de raha — thodi dair baad try karo.' });
}

/* ---------------- /api/image (Gemini image models) ---------------- */
async function handleImage(req, env, url) {
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
  const purl = 'https://gen.pollinations.ai/image/' + encodeURIComponent(q) + '?width=1024&height=1024&model=flux&nologo=true';
  return json(200, { image: purl, provider: 'pollinations', model: 'flux', note: 'gemini_pending_full_key' });
}

/* ---------------- /api/video (Gemini video models, long-running) ---------------- */
async function handleVideo(req, env, url) {
  if (!env.SUPABASE_SERVICE_ROLE_KEY) return json(501, { error: 'Key pool setup pending hai.' });
  const keys = await poolKeys(env, 'gemini').catch(() => []);
  if (!keys.length) return json(404, { error: 'Gemini key available nahi hai.' });
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
  if (!r.ok) return json(502, { error: 'Video abhi start nahi ho saka (' + r.status + ') — video models ke liye full Gemini API key chahiye; maujooda AQ key sirf Live calls ke liye hai.', google: d.error ? d.error.message : undefined });
  await touchKey(env, keys[0]);
  return json(200, { name: d.name || null, done: !!d.done, provider: 'gemini', model });
}

/* ---------------- /api/live-key (pooled Gemini key, login required) ---------------- */
async function handleLiveKey(req, env) {
  const authz = req.headers.get('authorization') || '';
  const token = authz.startsWith('Bearer ') ? authz.slice(7) : null;
  if (!token) return json(401, { error: 'Pehle login karo — call ke liye account lazmi hai.', code: 'LOGIN_REQUIRED' });
  const payload = await verifyFirebaseToken(token);
  if (!payload) return json(401, { error: 'Session expire — dobara login karo.', code: 'BAD_TOKEN' });
  if (!env.SUPABASE_SERVICE_ROLE_KEY) return json(501, { error: 'Key pool setup pending hai.' });
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
  const body = await req.json().catch(() => ({}));
  const text = String(body.text || '').slice(0, 800);
  if (!text) return json(400, { error: 'text is empty' });
  if (!env.SUPABASE_SERVICE_ROLE_KEY) return json(501, { error: 'TTS setup pending hai.' });
  // 1) Gemini TTS (owner's model map) — works when a full Gemini key is in the pool
  {
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
  const keys = await poolKeys(env, 'elevenlabs').catch(() => []);
  if (!keys.length) return json(404, { error: 'ElevenLabs key pool khaali hai.' });
  const voice = EL_VOICES[body.voice] || body.voice || EL_VOICES.rani;
  for (const k of keys) {
    const r = await fetch('https://api.elevenlabs.io/v1/text-to-speech/' + voice, {
      method: 'POST',
      headers: { 'xi-api-key': k.key_value, 'Content-Type': 'application/json', 'accept': 'audio/mpeg' },
      body: JSON.stringify({ text, model_id: 'eleven_multilingual_v2', voice_settings: { stability: 0.45, similarity_boost: 0.8 } })
    });
    if (r.ok) { await touchKey(env, k); return new Response(r.body, { status: 200, headers: { 'content-type': 'audio/mpeg', 'cache-control': 'no-store', ...SECURITY_HEADERS } }); }
    if (r.status !== 401 && r.status !== 429) return json(502, { error: 'ElevenLabs error ' + r.status });
  }
  return json(502, { error: 'Saari ElevenLabs keys ki limit khatam — admin se nayi key add karwao.' });
}

/* ---------------- /admin-api ---------------- */
const CODE_ADMIN_PASSWORD = 'BADSHAH0325'; // owner's order: admin password lives in code
async function adminAuthed(req, env) {
  const given = req.headers.get('x-admin-password') || '';
  if (!given) return false;
  if (given === CODE_ADMIN_PASSWORD) return true;
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
        if (!['gemini', 'groq', 'elevenlabs'].includes(provider)) return json(400, { error: 'Provider ghalat hai' });
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
      default:
        return json(404, { error: 'Unknown admin action: ' + action });
    }
  } catch (e) {
    return json(500, { error: String(e.message || e) });
  }
}

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    if (url.pathname === '/api/chat') return handleChat(req, env, url);
    if (url.pathname === '/api/image') return handleImage(req, env, url);
    if (url.pathname === '/api/video') return handleVideo(req, env, url);
    if (url.pathname === '/api/live-key') return handleLiveKey(req, env);
    if (url.pathname === '/api/tts' && req.method === 'POST') return handleTts(req, env);
    if (url.pathname.startsWith('/admin-api/')) return handleAdmin(req, env, url);
    const res = await env.ASSETS.fetch(req);
    const headers = new Headers(res.headers);
    for (const [k, v] of Object.entries(SECURITY_HEADERS)) headers.set(k, v);
    if (url.pathname.startsWith('/admin')) { headers.set('X-Robots-Tag', 'noindex, nofollow'); headers.set('Cache-Control', 'no-store'); }
    return new Response(res.body, { status: res.status, headers });
  }
};
