// Tiny API client. The session lives in an HttpOnly cookie set by the Worker;
// this file never sees the session token.
export async function api(action, params = {}) {
  let res;
  try {
    res = await fetch('/admin/api', {
      method: 'POST', credentials: 'same-origin',
      headers: { 'content-type': 'application/json', 'x-requested-with': 'badshah-admin' },
      body: JSON.stringify({ action, ...params }),
    });
  } catch { throw Object.assign(new Error('Network error'), { status: 0 }); }
  let data = {}; try { data = await res.json(); } catch { /* ignore */ }
  if (!res.ok) {
    if (res.status === 401 && action !== 'login') { location.href = '/admin/'; }
    throw Object.assign(new Error(data.error || `Request failed (${res.status})`), { status: res.status });
  }
  return data;
}
