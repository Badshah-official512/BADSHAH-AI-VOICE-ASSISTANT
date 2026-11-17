// BADSHAH AI — secure backend v2 (Cloudflare Pages "advanced mode" worker)
//
// Secrets (Cloudflare → Settings → Environment variables / Secrets):
//   BADSHAH_API_KEY            FT Fusion key (already set) — NEVER sent to browsers
//   ADMIN_PASSWORD             admin panel password (owner sets it)
//   SUPABASE_SERVICE_ROLE_KEY  used ONLY here, server-side, for plan checks + admin actions
//
// Routes:
//   GET  /api/chat?model=..&q=..     AI proxy. Free models open (rate-limited).
//                                    Paid models need a Firebase ID token + active paid plan.
//   POST /admin-api/<action>         Admin gateway. Header: x-admin-password.
//   everything else                  static site (env.ASSETS)

const UPSTREAM = 'https://ft-fusion.shehryarteam8876.workers.dev/ft/api';
const SUPABASE_URL = 'https://aghlacmqlagmigoznxlm.supabase.co';
const FIREBASE_PROJECT = 'badshah-ai-badshah222';

const FREE_MODELS = new Set(['fusion-flex', 'fusion-max', 'fusion-uni']);
const PAID_MODELS = new Set(['fusion-pulse', 'fusion-spark', 'fusion-lexus', 'fusion-synth', 'fusion-void', 'fusion-reaper', 'fusion-dread', 'fusion-shadow', 'fusion-abyss', 'fusion-vortex', 'fusion-astra']);
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
    return payload; // { sub (uid), email, name, ... }
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
async function userPlan(env, uid) {
  // profiles row keyed by firebase uid; paid if plan != free and not expired
  try {
    const rows = await sbAdmin(env, 'profiles?select=plan,plan_expires_at,blocked&firebase_uid=eq.' + encodeURIComponent(uid));
    if (rows && rows[0]) {
      const row = rows[0];
      if (row.blocked) return { plan: 'blocked' };
      const exp = row.plan_expires_at ? new Date(row.plan_expires_at).getTime() : null;
      if (row.plan && row.plan !== 'free' && (!exp || exp > Date.now())) return { plan: 'paid', expires: row.plan_expires_at };
    }
  } catch (e) { /* fall through to free */ }
  return { plan: 'free' };
}

/* ---------------- /api/chat ---------------- */
async function handleChat(req, env, url) {
  const ip = req.headers.get('cf-connecting-ip') || 'unknown';
  if (rateLimited(ip)) return json(429, { error: 'Too many requests — thoda ruk kar try karo.' });
  const model = url.searchParams.get('model') || 'fusion-flex';
  const q = url.searchParams.get('q') || '';
  if (!FREE_MODELS.has(model) && !PAID_MODELS.has(model)) return json(400, { error: 'Unknown model: ' + model });
  if (!q) return json(400, { error: 'q is empty' });
  if (q.length > MAX_Q_CHARS) return json(413, { error: 'Message too long' });

  if (PAID_MODELS.has(model)) {
    const authz = req.headers.get('authorization') || '';
    const token = authz.startsWith('Bearer ') ? authz.slice(7) : null;
    if (!token) return json(401, { error: 'Paid model hai — pehle login karo.', code: 'LOGIN_REQUIRED' });
    const payload = await verifyFirebaseToken(token);
    if (!payload) return json(401, { error: 'Session expire ho gaya — dobara login karo.', code: 'BAD_TOKEN' });
    if (!env.SUPABASE_SERVICE_ROLE_KEY) return json(501, { error: 'Paid plans abhi setup ho rahe hain — owner se rabta karo.', code: 'SETUP_PENDING' });
    const p = await userPlan(env, payload.sub);
    if (p.plan === 'blocked') return json(403, { error: 'Ye account blocked hai.', code: 'BLOCKED' });
    if (p.plan !== 'paid') return json(402, { error: 'Ye Paid model hai — plan upgrade karo.', code: 'UPGRADE_REQUIRED' });
  }

  const up = UPSTREAM + '?key=' + encodeURIComponent(env.BADSHAH_API_KEY || '') + '&model=' + encodeURIComponent(model) + '&q=' + encodeURIComponent(q);
  const r = await fetch(up, { headers: { 'accept': 'application/json' } });
  const text = await r.text();
  return new Response(text, { status: r.status, headers: { 'content-type': r.headers.get('content-type') || 'application/json; charset=utf-8', 'cache-control': 'no-store', ...SECURITY_HEADERS } });
}

/* ---------------- /admin-api ---------------- */
function adminAuthed(req, env) {
  const given = req.headers.get('x-admin-password') || '';
  const want = env.ADMIN_PASSWORD || '';
  if (!want || given.length !== want.length) return false;
  let diff = 0; for (let i = 0; i < want.length; i++) diff |= given.charCodeAt(i) ^ want.charCodeAt(i);
  return diff === 0;
}
async function handleAdmin(req, env, url) {
  if (!env.ADMIN_PASSWORD) return json(501, { error: 'ADMIN_PASSWORD secret set nahi hai (Cloudflare settings).' });
  if (!adminAuthed(req, env)) return json(401, { error: 'Wrong admin password' });
  if (!env.SUPABASE_SERVICE_ROLE_KEY) return json(501, { error: 'SUPABASE_SERVICE_ROLE_KEY secret set nahi hai — admin actions ke liye lazmi hai.' });
  const action = url.pathname.replace('/admin-api/', '');
  const body = req.method === 'POST' ? await req.json().catch(() => ({})) : {};
  try {
    switch (action) {
      case 'stats': {
        const [users, pending, openTickets, approved] = await Promise.all([
          sbAdmin(env, 'profiles?select=id,plan,plan_expires_at,created_at', { headers: { 'Prefer': 'count=exact' }, prefer: 'count=exact' }).catch(() => []),
          sbAdmin(env, 'payment_requests?select=id&status=eq.pending').catch(() => []),
          sbAdmin(env, 'support_tickets?select=id&status=eq.open').catch(() => []),
          sbAdmin(env, 'payment_requests?select=amount&status=eq.approved').catch(() => [])
        ]);
        const list = Array.isArray(users) ? users : [];
        const now = Date.now(), day = 86400e3;
        const paidActive = list.filter(u => u.plan && u.plan !== 'free' && (!u.plan_expires_at || new Date(u.plan_expires_at).getTime() > now)).length;
        const revenue = (approved || []).reduce((s, r) => s + (Number(r.amount) || 0), 0);
        return json(200, { ok: true, totalUsers: list.length, paidUsers: paidActive, freeUsers: list.length - paidActive,
          newToday: list.filter(u => now - new Date(u.created_at).getTime() < day).length,
          new7d: list.filter(u => now - new Date(u.created_at).getTime() < 7 * day).length,
          pendingPayments: (pending || []).length, openTickets: (openTickets || []).length, revenue });
      }
      case 'payments': {
        const rows = await sbAdmin(env, 'payment_requests?select=*&order=created_at.desc&limit=100');
        return json(200, { ok: true, rows });
      }
      case 'payment-approve': {
        const rows = await sbAdmin(env, 'payment_requests?select=*&id=eq.' + encodeURIComponent(body.id));
        if (!rows || !rows[0]) return json(404, { error: 'Request nahi mili' });
        const pr = rows[0];
        if (!pr.firebase_uid) return json(400, { error: 'Is request par user uid nahi — pehle user ko manually plan do.' });
        const months = Number(pr.months) || 1;
        const prof = await sbAdmin(env, 'profiles?select=plan_expires_at&firebase_uid=eq.' + encodeURIComponent(pr.firebase_uid)).catch(() => []);
        const base = prof && prof[0] && prof[0].plan_expires_at && new Date(prof[0].plan_expires_at).getTime() > Date.now() ? new Date(prof[0].plan_expires_at) : new Date();
        const expiry = new Date(base.getTime() + months * 30 * 86400e3).toISOString();
        await sbAdmin(env, 'profiles?firebase_uid=eq.' + encodeURIComponent(pr.firebase_uid), { method: 'PATCH', body: JSON.stringify({ plan: 'paid', plan_expires_at: expiry }) });
        await sbAdmin(env, 'payment_requests?id=eq.' + encodeURIComponent(body.id), { method: 'PATCH', body: JSON.stringify({ status: 'approved' }) });
        return json(200, { ok: true, expiry });
      }
      case 'payment-reject': {
        await sbAdmin(env, 'payment_requests?id=eq.' + encodeURIComponent(body.id), { method: 'PATCH', body: JSON.stringify({ status: 'rejected' }) });
        return json(200, { ok: true });
      }
      case 'users': {
        const q = body.q ? '&or=(display_name.ilike.*' + encodeURIComponent(body.q) + '*,email.ilike.*' + encodeURIComponent(body.q) + '*)' : '';
        const rows = await sbAdmin(env, 'profiles?select=*&order=created_at.desc&limit=100' + q);
        return json(200, { ok: true, rows });
      }
      case 'user-plan': { // manual extend: { firebase_uid, months }
        const months = Number(body.months) || 1;
        const prof = await sbAdmin(env, 'profiles?select=plan_expires_at&firebase_uid=eq.' + encodeURIComponent(body.firebase_uid));
        const base = prof && prof[0] && prof[0].plan_expires_at && new Date(prof[0].plan_expires_at).getTime() > Date.now() ? new Date(prof[0].plan_expires_at) : new Date();
        const expiry = new Date(base.getTime() + months * 30 * 86400e3).toISOString();
        await sbAdmin(env, 'profiles?firebase_uid=eq.' + encodeURIComponent(body.firebase_uid), { method: 'PATCH', body: JSON.stringify({ plan: 'paid', plan_expires_at: expiry }) });
        return json(200, { ok: true, expiry });
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
    if (url.pathname.startsWith('/admin-api/')) return handleAdmin(req, env, url);
    const res = await env.ASSETS.fetch(req);
    const headers = new Headers(res.headers);
    for (const [k, v] of Object.entries(SECURITY_HEADERS)) headers.set(k, v);
    if (url.pathname.startsWith('/admin')) { headers.set('X-Robots-Tag', 'noindex, nofollow'); headers.set('Cache-Control', 'no-store'); }
    return new Response(res.body, { status: res.status, headers });
  }
};
