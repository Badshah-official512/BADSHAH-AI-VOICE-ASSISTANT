import { api } from './api.js';
const f = document.getElementById('f'), pw = document.getElementById('pw'), msg = document.getElementById('msg'), go = document.getElementById('go');
go.textContent = 'Sign in';
(async () => { try { await api('session'); location.replace('/admin/pages/dashboard.html'); } catch { /* not signed in */ } })();
f.addEventListener('submit', async (e) => {
  e.preventDefault(); msg.className = 'msg'; msg.textContent = ''; go.disabled = true;
  try {
    await api('login', { password: pw.value });
    pw.value = ''; location.replace('/admin/pages/dashboard.html');
  } catch (err) {
    msg.className = 'msg err';
    msg.textContent = err.status === 429 ? err.message : err.status === 401 ? 'Incorrect password.' : 'Could not sign in. Try again.';
    pw.select();
  } finally { go.disabled = false; }
});
