import test from 'node:test'; import assert from 'node:assert/strict';
import worker from '../src/index.js';

const env = (calls, edge) => ({
  GATEWAY_SECRET: 'gw-secret-for-tests', SUPABASE_URL: 'https://x.supabase.co',
  ASSETS: { fetch: async (r) => new Response('ASSET:' + new URL(r.url).pathname, { headers: { 'content-type': 'text/html' } }) },
});
let edgeImpl;
globalThis.fetch = async (url, init) => edgeImpl(url, init);
const api = (body, { cookie, origin = 'https://site.test', xrw = 'badshah-admin' } = {}) =>
  new Request('https://site.test/admin/api', { method: 'POST', headers: { Origin: origin, 'x-requested-with': xrw, 'CF-Connecting-IP': '7.7.7.7', ...(cookie ? { Cookie: cookie } : {}) }, body: JSON.stringify(body) });
const edgeJson = (status, obj) => new Response(JSON.stringify(obj), { status });

test('login: token moves into HttpOnly cookie and is NOT in the response body', async () => {
  let seen; edgeImpl = async (u, i) => { seen = { u, i }; return edgeJson(200, { token: 'TOK123', expires_at: new Date(Date.now() + 3600e3).toISOString() }); };
  const res = await worker.fetch(api({ action: 'login', password: 'pw' }), env());
  const body = await res.json();
  assert.equal(res.status, 200); assert.equal(body.token, undefined);
  const sc = res.headers.get('Set-Cookie');
  assert.match(sc, /^__Host-badshah_admin=TOK123;/); for (const f of ['HttpOnly', 'Secure', 'SameSite=Strict', 'Path=/']) assert.ok(sc.includes(f), f);
  assert.equal(seen.u, 'https://x.supabase.co/functions/v1/admin-api');
  assert.equal(seen.i.headers['x-gateway-secret'], 'gw-secret-for-tests');
  assert.equal(seen.i.headers['x-client-ip'], '7.7.7.7');
  assert.equal(res.headers.get('X-Frame-Options'), 'DENY');
});
test('cookie is forwarded as x-admin-token on later calls', async () => {
  let seen; edgeImpl = async (u, i) => { seen = i; return edgeJson(200, { ok: true }); };
  await worker.fetch(api({ action: 'overview' }, { cookie: '__Host-badshah_admin=TOK123' }), env());
  assert.equal(seen.headers['x-admin-token'], 'TOK123');
});
test('CSRF: wrong Origin, missing Origin and missing custom header are rejected before reaching the backend', async () => {
  let hit = 0; edgeImpl = async () => { hit++; return edgeJson(200, {}); };
  assert.equal((await worker.fetch(api({ action: 'x' }, { origin: 'https://evil.test' }), env())).status, 403);
  assert.equal((await worker.fetch(api({ action: 'x' }, { xrw: 'nope' }), env())).status, 403);
  const noOrigin = new Request('https://site.test/admin/api', { method: 'POST', headers: { 'x-requested-with': 'badshah-admin' }, body: '{}' });
  assert.equal((await worker.fetch(noOrigin, env())).status, 403);
  assert.equal(hit, 0);
});
test('GET /admin/api is refused', async () => {
  assert.equal((await worker.fetch(new Request('https://site.test/admin/api'), env())).status, 405);
});
test('401 from backend clears the cookie', async () => {
  edgeImpl = async () => edgeJson(401, { error: 'session expired' });
  const res = await worker.fetch(api({ action: 'overview' }, { cookie: '__Host-badshah_admin=OLD' }), env());
  assert.equal(res.status, 401); assert.match(res.headers.get('Set-Cookie'), /Max-Age=0/);
});
test('logout clears cookie', async () => {
  edgeImpl = async () => edgeJson(200, { ok: true });
  const res = await worker.fetch(api({ action: 'logout' }, { cookie: '__Host-badshah_admin=T' }), env());
  assert.match(res.headers.get('Set-Cookie'), /^__Host-badshah_admin=; .*Max-Age=0/);
});
test('rate-limit 429 passes through to the browser', async () => {
  edgeImpl = async () => edgeJson(429, { error: 'Too many failed attempts.' });
  assert.equal((await worker.fetch(api({ action: 'login', password: 'x' }), env())).status, 429);
});
test('dashboard pages: no cookie -> redirect to /admin/, backend never serves code', async () => {
  edgeImpl = async () => edgeJson(401, {});
  const res = await worker.fetch(new Request('https://site.test/admin/pages/dashboard.html'), env());
  assert.equal(res.status, 302); assert.equal(res.headers.get('Location'), 'https://site.test/admin/');
});
test('dashboard pages: forged cookie -> redirect', async () => {
  edgeImpl = async () => edgeJson(401, { error: 'session expired' });
  const res = await worker.fetch(new Request('https://site.test/admin/pages/js/app.js', { headers: { Cookie: '__Host-badshah_admin=forged' } }), env());
  assert.equal(res.status, 302);
});
test('dashboard pages: valid session -> served, no-store', async () => {
  edgeImpl = async () => edgeJson(200, { ok: true });
  const res = await worker.fetch(new Request('https://site.test/admin/pages/dashboard.html', { headers: { Cookie: '__Host-badshah_admin=good' } }), env());
  assert.equal(res.status, 200); assert.equal(await res.text(), 'ASSET:/admin/pages/dashboard.html'); assert.equal(res.headers.get('Cache-Control'), 'no-store');
});
test('login page is public; /admin redirects to /admin/', async () => {
  edgeImpl = async () => { throw new Error('should not call backend'); };
  assert.equal((await worker.fetch(new Request('https://site.test/admin/'), env())).status, 200);
  assert.equal((await worker.fetch(new Request('https://site.test/admin'), env())).status, 301);
});
test('secret-looking files are never served', async () => {
  for (const p of ['/google-services.json', '/x/service-account.json', '/a.env', '/m.sql', '/k.jks', '/app.js.map'])
    assert.equal((await worker.fetch(new Request('https://site.test' + p), env())).status, 404, p);
});
test('backend down -> 502 not a crash', async () => {
  edgeImpl = async () => { throw new Error('down'); };
  assert.equal((await worker.fetch(api({ action: 'overview' }, { cookie: '__Host-badshah_admin=t' }), env())).status, 502);
});
