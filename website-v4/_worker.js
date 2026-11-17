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
//   BADSHAH_API_KEY            FT Fusion key — never sent to browsers
//   SUPABASE_SERVICE_ROLE_KEY  server-side only (key pool + admin actions)
//   ADMIN_PASSWORD             fallback only (primary = Supabase)
//
// Routes:
//   GET  /api/chat?model=..&q=..        FT Fusion proxy (all models free)
//   GET  /api/chat?provider=groq&q=..   Groq chat via pooled key
//   GET  /api/live-key                  pooled Gemini key for Live calls (login required)
//   POST /api/tts {text, voice}         ElevenLabs TTS via pooled key → audio/mpeg
//   POST /admin-api/<action>            Admin gateway (x-admin-password header)

const UPSTREAM = 'https://ft-fusion.shehryarteam8876.workers.dev/ft/api';
const SUPABASE_URL = 'https://aghlacmqlagmigoznxlm.supabase.co';
const FIREBASE_PROJECT = 'badshah-ai-badshah222';
const ALL_MODELS = new Set(['fusion-flex', 'fusion-max', 'fusion-uni', 'fusion-pulse', 'fusion-spark', 'fusion-lexus', 'fusion-synth', 'fusion-void', 'fusion-reaper', 'fusion-dread', 'fusion-shadow', 'fusion-abyss', 'fusion-vortex', 'fusion-astra']);
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

/* ---------------- /api/chat (FT + Groq) ---------------- */
async function handleChat(req, env, url) {
  const ip = req.headers.get('cf-connecting-ip') || 'unknown';
  if (rateLimited(ip)) return json(429, { error: 'Too many requests — thoda ruk kar try karo.' });
  const q = url.searchParams.get('q') || '';
  if (!q) return json(400, { error: 'q is empty' });
  if (q.length > MAX_Q_CHARS) return json(413, { error: 'Message too long' });

  if (url.searchParams.get('provider') === 'groq') {
    if (!env.SUPABASE_SERVICE_ROLE_KEY) return json(501, { error: 'Groq abhi setup nahi — owner ko Supabase service key lagana hai.' });
    const keys = await poolKeys(env, 'groq').catch(() => []);
    if (!keys.length) return json(501, { error: 'Groq key pool khaali hai — admin panel se key add karo.' });
    const model = url.searchParams.get('model') || 'openai/gpt-oss-120b';
    for (const k of keys) {
      const r = await fetch('https://api.groq.com/openai/v1/chat/completions', {
        method: 'POST',
        headers: { 'Authorization': 'Bearer ' + k.key_value, 'Content-Type': 'application/json' },
        body: JSON.stringify({ model, messages: [{ role: 'user', content: q }] })
      });
      if (r.ok) {
        const d = await r.json();
        await touchKey(env, k);
        return json(200, { text: (d.choices && d.choices[0] && d.choices[0].message && d.choices[0].message.content) || '', provider: 'groq', model });
      }
      if (r.status !== 401 && r.status !== 429) return json(502, { error: 'Groq error ' + r.status });
    }
    return json(502, { error: 'Saari Groq keys ki limit khatam — admin se nayi key add karwao.' });
  }

  const model = url.searchParams.get('model') || 'fusion-flex';
  if (!ALL_MODELS.has(model)) return json(400, { error: 'Unknown model: ' + model });
  const up = UPSTREAM + '?key=' + encodeURIComponent(env.BADSHAH_API_KEY || '') + '&model=' + encodeURIComponent(model) + '&q=' + encodeURIComponent(q);
  const r = await fetch(up, { headers: { 'accept': 'application/json' } });
  const text = await r.text();
  return new Response(text, { status: r.status, headers: { 'content-type': r.headers.get('content-type') || 'application/json; charset=utf-8', 'cache-control': 'no-store', ...SECURITY_HEADERS } });
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
async function handleTts(req, env) {
  const body = await req.json().catch(() => ({}));
  const text = String(body.text || '').slice(0, 800);
  if (!text) return json(400, { error: 'text is empty' });
  if (!env.SUPABASE_SERVICE_ROLE_KEY) return json(501, { error: 'TTS setup pending hai.' });
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
