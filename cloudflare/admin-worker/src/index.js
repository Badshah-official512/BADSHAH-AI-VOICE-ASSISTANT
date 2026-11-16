// BADSHAH AI - Cloudflare Worker: serves the website, protects /admin, and is
// the ONLY thing allowed to talk to the Supabase `admin-api` Edge Function.
//
//  Browser --(HttpOnly cookie)--> Worker --(x-gateway-secret + x-admin-token)--> Edge Function
//
//  * The admin session token never reaches page JavaScript (HttpOnly cookie).
//  * /admin/pages/* (the dashboard code) is only served to a valid session.
//  * CSRF: SameSite=Strict + same-origin Origin check + custom header.
//  * HARDENED: this code never throws an unhandled exception (no Error 1101).
//    If the website files (static assets) are not attached yet, it shows a
//    friendly "setup not complete" page instead of crashing.
const COOKIE = '__Host-badshah_admin';
const MAX_BODY = 65536;

const SECURITY_HEADERS = {
  'Content-Security-Policy': "default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'",
  'X-Frame-Options': 'DENY',
  'X-Content-Type-Options': 'nosniff',
  'Referrer-Policy': 'no-referrer',
  'Strict-Transport-Security': 'max-age=63072000; includeSubDomains',
  'Permissions-Policy': 'camera=(), microphone=(), geolocation=()',
};
// The public docs explorer needs inline-free JS too, so the same CSP applies everywhere.

function withHeaders(res, extra = {}) {
  const r = new Response(res.body, res);
  for (const [k, v] of Object.entries({ ...SECURITY_HEADERS, ...extra })) r.headers.set(k, v);
  return r;
}
const jsonRes = (status, obj, extra = {}) =>
  withHeaders(new Response(JSON.stringify(obj), { status, headers: { 'content-type': 'application/json' } }), { 'Cache-Control': 'no-store', ...extra });

function getCookie(req, name) {
  const m = (req.headers.get('Cookie') || '').split(/;\s*/).find((c) => c.startsWith(name + '='));
  return m ? decodeURIComponent(m.slice(name.length + 1)) : '';
}
const cookieHeader = (value, maxAge) =>
  `${COOKIE}=${encodeURIComponent(value)}; Path=/; Secure; HttpOnly; SameSite=Strict; Max-Age=${maxAge}`;

async function callEdge(env, body, token, ip) {
  const headers = {
    'content-type': 'application/json',
    'x-gateway-secret': env.GATEWAY_SECRET,
    'x-client-ip': ip,
  };
  if (token) headers['x-admin-token'] = token;
  return fetch(`${env.SUPABASE_URL}/functions/v1/admin-api`, { method: 'POST', headers, body: JSON.stringify(body) });
}

async function handleApi(request, env) {
  if (request.method !== 'POST') return jsonRes(405, { error: 'method not allowed' });
  const origin = request.headers.get('Origin');
  let originOk = false;
  try { originOk = !!origin && new URL(origin).host === new URL(request.url).host; } catch { originOk = false; }
  if (!originOk) return jsonRes(403, { error: 'bad origin' });
  if (request.headers.get('x-requested-with') !== 'badshah-admin') return jsonRes(403, { error: 'bad request' });
  const raw = await request.text();
  if (raw.length > MAX_BODY) return jsonRes(413, { error: 'too large' });
  let body; try { body = JSON.parse(raw); } catch { return jsonRes(400, { error: 'invalid JSON' }); }
  if (!env.GATEWAY_SECRET || !env.SUPABASE_URL) return jsonRes(500, { error: 'gateway not configured' });

  const ip = request.headers.get('CF-Connecting-IP') || 'unknown';
  const token = getCookie(request, COOKIE);
  let upstream;
  try { upstream = await callEdge(env, body, token, ip); } catch { return jsonRes(502, { error: 'backend unreachable' }); }
  let data; try { data = await upstream.json(); } catch { return jsonRes(502, { error: 'bad backend response' }); }

  if (body.action === 'login' && upstream.status === 200 && data.token) {
    const ttl = Math.max(60, Math.floor((new Date(data.expires_at).getTime() - Date.now()) / 1000));
    const { token: t, ...safe } = data; // token goes into the cookie ONLY
    return jsonRes(200, safe, { 'Set-Cookie': cookieHeader(t, ttl) });
  }
  if (body.action === 'logout') return jsonRes(upstream.status, data, { 'Set-Cookie': cookieHeader('', 0) });
  const extra = upstream.status === 401 ? { 'Set-Cookie': cookieHeader('', 0) } : {};
  return jsonRes(upstream.status, data, extra);
}

async function sessionValid(request, env) {
  const token = getCookie(request, COOKIE);
  if (!token) return false;
  try {
    const r = await callEdge(env, { action: 'session' }, token, request.headers.get('CF-Connecting-IP') || 'unknown');
    return r.status === 200;
  } catch { return false; }
}

const SETUP_PAGE = `<!doctype html><html><head><meta charset="utf-8"><title>BADSHAH AI</title>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#0C0918;color:#EDE9FE;font-family:system-ui,sans-serif;padding:24px}
.card{max-width:520px;background:#171130;border:1px solid #8B5CF6;border-radius:20px;padding:28px;text-align:center}
h1{margin:0 0 10px;font-size:22px}p{color:#B79CFF;line-height:1.6}</style></head>
<body><div class="card"><h1>BADSHAH AI</h1>
<p>Worker is running, but the website files are not attached yet.<br>
Upload the website bundle files (index.html, files.json and the admin folder) as this Worker's static assets, then refresh.</p>
</div></body></html>`;

async function serveAsset(env, request, extra = {}) {
  if (!env || !env.ASSETS || typeof env.ASSETS.fetch !== 'function') {
    return withHeaders(new Response(SETUP_PAGE, { status: 503, headers: { 'content-type': 'text/html; charset=utf-8' } }), { 'Cache-Control': 'no-store', ...extra });
  }
  try {
    const res = await env.ASSETS.fetch(request);
    return withHeaders(res, extra);
  } catch {
    return withHeaders(new Response(SETUP_PAGE, { status: 503, headers: { 'content-type': 'text/html; charset=utf-8' } }), { 'Cache-Control': 'no-store', ...extra });
  }
}

export default {
  async fetch(request, env) {
    try {
      const url = new URL(request.url);
      const path = url.pathname;

      if (path === '/admin/api') return handleApi(request, env);
      if (path === '/admin') return withHeaders(Response.redirect(url.origin + '/admin/', 301));

      // Server-side access protection: dashboard code only for valid sessions.
      if (path.startsWith('/admin/pages/')) {
        if (!(await sessionValid(request, env))) {
          return withHeaders(Response.redirect(url.origin + '/admin/', 302), { 'Cache-Control': 'no-store' });
        }
        return serveAsset(env, request, { 'Cache-Control': 'no-store' });
      }

      // Never serve secret-looking files, even if one slipped into the bundle.
      if (/\.(env|pem|key|jks|keystore|sql)$|service[-_]account|google-services\.json|\.map$/i.test(path)) {
        return withHeaders(new Response('Not found', { status: 404 }));
      }
      return serveAsset(env, request);
    } catch {
      // Last-resort guard: a plain 500 page, never an unhandled exception (Error 1101).
      return new Response('BADSHAH AI: temporary problem. Please try again in a moment.', { status: 500, headers: { 'content-type': 'text/plain; charset=utf-8' } });
    }
  },
};
