import { api } from '../../js/api.js';
import { h, clear, fmtDate, num, toast, modal, stat, table, pill, lineChart, barRows } from './dom.js';

const view = document.getElementById('view');
const VIEWS = [
  ['overview', 'Overview'], ['users', 'Users'], ['logins', 'Login analytics'], ['api', 'API analytics'],
  ['support', 'Support center'], ['app', 'App management'], ['security', 'Security'],
];
const nav = document.getElementById('nav');
const guard = (fn) => async (...a) => { try { return await fn(...a); } catch (e) { toast(e.message, true); } };

function go(name) {
  for (const b of nav.children) b.classList.toggle('on', b.dataset.v === name);
  history.replaceState(null, '', '#' + name);
  clear(view).append(h('p', { class: 'note' }, 'Loading...'));
  (RENDER[name] || RENDER.overview)().catch((e) => { clear(view).append(h('div', { class: 'card' }, h('b', null, 'Error: '), e.message)); });
}
for (const [id, label] of VIEWS) nav.append(h('button', { type: 'button', 'data-v': id, onclick: () => go(id) }, label));
document.getElementById('logout').addEventListener('click', async () => { try { await api('logout'); } catch { /* ignore */ } location.href = '/admin/'; });

const title = (t, extra) => h('div', { class: 'top' }, h('h1', null, t), extra || null);
const show = (...nodes) => clear(view).append(...nodes);
const ticketPill = (s) => pill(s.replace('_', ' '), { open: 'warn', in_progress: 'blue', resolved: 'ok', closed: '' }[s] || '');

const RENDER = {
  // ------------------------------------------------------------ A. OVERVIEW
  async overview() {
    const o = await api('overview'), p = o.by_provider || {};
    show(title('Overview', h('button', { class: 'btn ghost sm', type: 'button', onclick: () => go('overview') }, 'Refresh')),
      h('div', { class: 'grid' },
        stat(o.total_users, 'Total users'), stat(o.active_24h, 'Active (24h)'), stat(o.active_7d, 'Active (7d)'), stat(o.active_30d, 'Active (30d)'),
        stat(o.new_users_24h, 'New (24h)'), stat(o.new_users_7d, 'New (7d)'), stat(o.new_users_30d, 'New (30d)'), stat(o.blocked_users, 'Blocked'),
        stat(o.ai_requests_24h, 'AI requests (24h)'), stat(o.ai_requests_7d, 'AI requests (7d)'), stat(o.ai_requests_30d, 'AI requests (30d)'), stat(o.ai_requests_total, 'AI requests (all)'),
        stat(p.gemini || 0, 'Gemini requests'), stat(p.groq || 0, 'Groq requests'),
        stat(o.conversations, 'Conversations'), stat(o.messages, 'Messages'), stat(o.open_tickets, 'Open tickets')),
      h('div', { class: 'card sect' }, h('h3', null, 'Last 30 days'),
        lineChart(o.daily.map((d) => d.day), [
          { name: 'AI requests', cls: 's1', dotCls: 'c1', values: o.daily.map((d) => d.requests) },
          { name: 'Sign-ups', cls: 's2', dotCls: 'c2', values: o.daily.map((d) => d.signups) },
          { name: 'Daily logins', cls: 's3', dotCls: 'c3', values: o.daily.map((d) => d.logins) }])),
      h('p', { class: 'note' }, 'Aggregates only. Private chat content and users\' API keys are never shown here.'));
  },

  // ------------------------------------------------------------ B/E. USERS
  async users() {
    const st = { search: '', status: 'all', offset: 0, limit: 25 };
    const holder = h('div');
    const load = guard(async () => {
      const r = await api('users.list', st);
      const pages = Math.max(1, Math.ceil(r.total / st.limit)), cur = Math.floor(st.offset / st.limit) + 1;
      clear(holder).append(
        table([
          { label: 'User', render: (u) => h('div', null, h('b', null, u.display_name || '(no name)'), h('div', { class: 'note' }, u.email || 'no email on file')) },
          { label: 'User ID (Firebase UID)', render: (u) => h('code', null, u.id) },
          { label: 'Status', render: (u) => (u.is_blocked ? pill('blocked', 'bad') : pill('active', 'ok')) },
          { label: 'Registered', render: (u) => fmtDate(u.created_at) },
          { label: 'Last login', render: (u) => fmtDate(u.last_login_at) },
          { label: 'Last activity', render: (u) => fmtDate(u.last_seen_at) },
          { label: 'Provider / model', render: (u) => `${u.selected_provider || '-'} / ${u.selected_model || '-'}` },
          { label: 'API key set', render: (u) => (u.api_configured ? pill('yes', 'ok') : pill('no', 'warn')) },
          { label: 'Voice', render: (u) => (u.selected_voice ? `${u.selected_voice} (${u.voice_engine || 'tts'})` : '-') },
        ], r.rows, (u) => userModal(u.id, load)),
        h('div', { class: 'row sect' },
          h('button', { class: 'btn ghost sm', type: 'button', disabled: st.offset === 0, onclick: () => { st.offset -= st.limit; load(); } }, 'Previous'),
          h('span', { class: 'note' }, `Page ${cur} of ${pages} - ${num(r.total)} users`),
          h('button', { class: 'btn ghost sm', type: 'button', disabled: cur >= pages, onclick: () => { st.offset += st.limit; load(); } }, 'Next')));
    });
    const search = h('input', { type: 'search', placeholder: 'Search name, email or user ID', 'aria-label': 'Search users' });
    let t; search.addEventListener('input', () => { clearTimeout(t); t = setTimeout(() => { st.search = search.value; st.offset = 0; load(); }, 300); });
    const status = h('select', { 'aria-label': 'Filter', onchange: () => { st.status = status.value; st.offset = 0; load(); } },
      [['all', 'All users'], ['active', 'Active'], ['blocked', 'Blocked']].map(([v, l]) => h('option', { value: v }, l)));
    show(title('User management'), h('div', { class: 'row' }, search, status), holder); await load();
  },

  // ------------------------------------------------------------ C. LOGIN ANALYTICS
  async logins() {
    const days = h('select', { 'aria-label': 'Range', onchange: () => draw() }, [7, 30, 90].map((d) => h('option', { value: d, selected: d === 30 }, `Last ${d} days`)));
    const holder = h('div');
    const draw = guard(async () => {
      const a = await api('analytics.login', { days: Number(days.value) });
      clear(holder).append(
        h('div', { class: 'grid' }, stat(a.successful_logins, 'Successful logins'), stat(a.failed_logins, 'Failed logins (app-reported)'), stat(a.registrations, 'Registrations')),
        h('div', { class: 'card sect' }, h('h3', null, 'By login provider'),
          barRows(Object.entries(a.by_provider).map(([k, v]) => ({ label: k, value: v }))),
          h('p', { class: 'note' }, a.failed_logins_note)),
        h('h3', { class: 'sect' }, 'Recent events'),
        table([{ label: 'When', render: (r) => fmtDate(r.created_at) }, { label: 'User', render: (r) => r.email || r.user_id }, { label: 'Event', key: 'event_type' },
          { label: 'Provider', key: 'provider' }, { label: 'Result', render: (r) => (r.success ? pill('ok', 'ok') : pill('failed', 'bad')) }, { label: 'Device', key: 'device_type' }], a.recent));
    });
    show(title('Login analytics', days), holder); await draw();
  },

  // ------------------------------------------------------------ D. API ANALYTICS
  async api() {
    const days = h('select', { 'aria-label': 'Range', onchange: () => draw() }, [7, 30, 90].map((d) => h('option', { value: d, selected: d === 30 }, `Last ${d} days`)));
    const holder = h('div');
    const draw = guard(async () => {
      const a = await api('analytics.api', { days: Number(days.value) }), t = a.totals;
      const labels = [...new Set(a.daily.map((d) => d.day))].sort();
      const ser = (prov, cls, dotCls) => ({ name: prov, cls, dotCls, values: labels.map((l) => Number(a.daily.find((d) => d.day === l && d.provider === prov)?.requests || 0)) });
      clear(holder).append(
        h('div', { class: 'grid' }, stat(t.requests, 'Requests'), stat(t.success, 'Successful'), stat(t.failures, 'Failed'), stat(t.avg_latency_ms == null ? '-' : `${t.avg_latency_ms} ms`, 'Avg latency')),
        h('div', { class: 'card sect' }, h('h3', null, 'Requests per day'), labels.length ? lineChart(labels, [ser('gemini', 's1', 'c1'), ser('groq', 's2', 'c2')]) : h('p', { class: 'note' }, 'No AI usage recorded yet.')),
        h('h3', { class: 'sect' }, 'By provider and model'),
        table([{ label: 'Provider', key: 'provider' }, { label: 'Model', key: 'model' }, { label: 'Requests', render: (r) => num(r.requests) }, { label: 'Success', render: (r) => num(r.success) },
          { label: 'Failed', render: (r) => num(r.failures) }, { label: 'Avg latency', render: (r) => (r.avg_latency_ms == null ? '-' : r.avg_latency_ms + ' ms') },
          { label: 'Input tokens', render: (r) => num(r.input_tokens) }, { label: 'Output tokens', render: (r) => num(r.output_tokens) }], a.by_model),
        h('h3', { class: 'sect' }, 'Top error codes'),
        table([{ label: 'Provider', key: 'provider' }, { label: 'Error', key: 'error_code' }, { label: 'Count', render: (r) => num(r.n) }], a.top_errors),
        h('p', { class: 'note' }, 'Tokens are shown only when the provider returned them. Usage is reported by the app; no API keys or prompts are stored.'));
    });
    show(title('API analytics', days), holder); await draw();
  },

  // ------------------------------------------------------------ F. SUPPORT
  async support() {
    const holder = h('div'); let status = '';
    const load = guard(async () => {
      const r = await api('tickets.list', status ? { status } : {});
      clear(holder).append(table([
        { label: 'Updated', render: (t) => fmtDate(t.updated_at) }, { label: 'Subject', render: (t) => h('b', null, t.subject) },
        { label: 'From', render: (t) => t.user?.email || t.user?.display_name || t.user_id }, { label: 'Category', key: 'category' },
        { label: 'Status', render: (t) => ticketPill(t.status) }, { label: 'Priority', key: 'priority' }], r.tickets, (t) => ticketModal(t.id, load)));
    });
    const sel = h('select', { 'aria-label': 'Status', onchange: () => { status = sel.value; load(); } },
      [['', 'All'], ['open', 'Open'], ['in_progress', 'In progress'], ['resolved', 'Resolved'], ['closed', 'Closed']].map(([v, l]) => h('option', { value: v }, l)));
    show(title('Support center', sel), holder); await load();
  },

  // ------------------------------------------------------------ G. APP MANAGEMENT
  async app() {
    const [ann, cfg, vo] = await Promise.all([api('announcements.list'), api('config.list'), api('voices.list')]);
    const maint = cfg.config.find((c) => c.config_key === 'maintenance')?.config_value || { enabled: false, message: '' };
    const mOn = h('input', { type: 'checkbox', checked: !!maint.enabled, id: 'mon' }), mMsg = h('input', { value: maint.message || '', maxlength: 300, placeholder: 'Message shown to users' });
    const aTitle = h('input', { maxlength: 200, placeholder: 'Title' }), aBody = h('textarea', { maxlength: 5000, placeholder: 'Announcement text' }), aPub = h('input', { type: 'checkbox', id: 'apub', checked: true });
    const cKey = h('input', { maxlength: 60, placeholder: 'config_key (a-z 0-9 _ . -)' }), cVal = h('textarea', { placeholder: '{"json": "value"}' });
    const v = { id: h('input', { maxlength: 60, placeholder: 'id e.g. deep-male' }), label: h('input', { maxlength: 80, placeholder: 'Label' }),
      category: h('select', null, ['deep', 'natural', 'professional', 'friendly', 'calm', 'cinematic', 'regional'].map((c) => h('option', { value: c }, c))),
      language: h('input', { value: 'en', maxlength: 20 }), engine: h('select', null, [h('option', { value: 'android_tts' }, 'Android TTS'), h('option', { value: 'elevenlabs' }, 'ElevenLabs')]),
      pitch: h('input', { type: 'number', step: '0.05', min: '0.5', max: '2', value: '1' }), speed: h('input', { type: 'number', step: '0.05', min: '0.5', max: '2', value: '1' }) };
    const reload = () => go('app');
    show(title('App management'),
      h('div', { class: 'two' },
        h('div', { class: 'card form' }, h('h3', null, 'Maintenance notice'), h('label', null, mOn, ' Maintenance mode on'), mMsg,
          h('button', { class: 'btn', type: 'button', onclick: guard(async () => { await api('config.set', { key: 'maintenance', value: { enabled: mOn.checked, message: mMsg.value } }); toast('Saved'); }) }, 'Save')),
        h('div', { class: 'card form' }, h('h3', null, 'New announcement'), aTitle, aBody, h('label', null, aPub, ' Publish now'),
          h('button', { class: 'btn', type: 'button', onclick: guard(async () => { await api('announcements.save', { title: aTitle.value, content: aBody.value, is_published: aPub.checked }); toast('Saved'); reload(); }) }, 'Save'))),
      h('h3', { class: 'sect' }, 'Announcements'),
      table([{ label: 'Title', render: (a) => h('b', null, a.title) }, { label: 'Text', key: 'content' }, { label: 'Status', render: (a) => (a.is_published ? pill('published', 'ok') : pill('draft', 'warn')) },
        { label: '', render: (a) => h('span', { class: 'row' },
          h('button', { class: 'btn ghost sm', type: 'button', onclick: guard(async () => { await api('announcements.save', { id: a.id, title: a.title, content: a.content, is_published: !a.is_published }); reload(); }) }, a.is_published ? 'Unpublish' : 'Publish'),
          h('button', { class: 'btn danger sm', type: 'button', onclick: guard(async () => { if (confirm('Delete this announcement?')) { await api('announcements.delete', { id: a.id }); reload(); } }) }, 'Delete')) }], ann.announcements),
      h('h3', { class: 'sect' }, 'Voice profiles'),
      h('p', { class: 'note' }, 'Presets the app offers on top of the voices actually installed on each phone. The app only shows a preset if a matching real voice exists.'),
      h('div', { class: 'card form' }, h('div', { class: 'two' }, h('div', null, h('label', null, 'ID'), v.id), h('div', null, h('label', null, 'Label'), v.label), h('div', null, h('label', null, 'Category'), v.category),
        h('div', null, h('label', null, 'Language'), v.language), h('div', null, h('label', null, 'Engine'), v.engine), h('div', null, h('label', null, 'Pitch'), v.pitch), h('div', null, h('label', null, 'Speed'), v.speed)),
        h('button', { class: 'btn', type: 'button', onclick: guard(async () => { await api('voices.save', Object.fromEntries(Object.entries(v).map(([k, el]) => [k, el.value]))); toast('Saved'); reload(); }) }, 'Save voice profile')),
      table([{ label: 'ID', key: 'id' }, { label: 'Label', key: 'label' }, { label: 'Category', key: 'category' }, { label: 'Lang', key: 'language' }, { label: 'Engine', key: 'engine' },
        { label: 'Pitch/Speed', render: (x) => `${x.pitch} / ${x.speed}` }, { label: 'On', render: (x) => (x.is_enabled ? pill('yes', 'ok') : pill('no')) },
        { label: '', render: (x) => h('button', { class: 'btn danger sm', type: 'button', onclick: guard(async () => { if (confirm('Delete voice profile?')) { await api('voices.delete', { id: x.id }); reload(); } }) }, 'Delete') }], vo.voices),
      h('h3', { class: 'sect' }, 'App configuration (non-secret only)'),
      h('div', { class: 'card form' }, cKey, cVal, h('button', { class: 'btn', type: 'button', onclick: guard(async () => {
        let value; try { value = JSON.parse(cVal.value); } catch { throw new Error('Value must be valid JSON'); }
        await api('config.set', { key: cKey.value, value }); toast('Saved'); reload(); }) }, 'Save config')),
      table([{ label: 'Key', render: (c) => h('code', null, c.config_key) }, { label: 'Value', render: (c) => h('code', null, JSON.stringify(c.config_value)) }, { label: 'Updated', render: (c) => fmtDate(c.updated_at) },
        { label: '', render: (c) => h('button', { class: 'btn ghost sm', type: 'button', onclick: () => { cKey.value = c.config_key; cVal.value = JSON.stringify(c.config_value, null, 2); cKey.scrollIntoView(); } }, 'Edit') }], cfg.config));
  },

  // ------------------------------------------------------------ H. SECURITY
  async security() {
    const s = await api('security.overview');
    const cur = h('input', { type: 'password', autocomplete: 'current-password', placeholder: 'Current password' }), nw = h('input', { type: 'password', autocomplete: 'new-password', placeholder: 'New password (12+ characters)' });
    show(title('Security'),
      h('div', { class: 'two' },
        h('div', { class: 'card form' }, h('h3', null, 'Change admin password'), cur, nw,
          h('button', { class: 'btn', type: 'button', onclick: guard(async () => { const r = await api('security.change_password', { current_password: cur.value, new_password: nw.value }); cur.value = nw.value = ''; toast(r.note); }) }, 'Change password')),
        h('div', { class: 'card form' }, h('h3', null, 'Sessions'), h('p', { class: 'note' }, 'Sessions expire after inactivity and after a fixed lifetime.'),
          h('button', { class: 'btn ghost', type: 'button', onclick: guard(async () => { const r = await api('security.revoke_others'); toast(`Signed out ${r.revoked} other session(s)`); go('security'); }) }, 'Sign out all other sessions'))),
      h('h3', { class: 'sect' }, 'Admin sessions'),
      table([{ label: 'Created', render: (x) => fmtDate(x.created_at) }, { label: 'Last seen', render: (x) => fmtDate(x.last_seen_at) }, { label: 'Expires', render: (x) => fmtDate(x.expires_at) },
        { label: 'State', render: (x) => (x.id === s.current_session_id ? pill('this session', 'blue') : x.is_active ? pill('active', 'ok') : pill('ended')) },
        { label: '', render: (x) => (x.is_active && x.id !== s.current_session_id ? h('button', { class: 'btn danger sm', type: 'button', onclick: guard(async () => { await api('security.revoke_session', { session_id: x.id }); go('security'); }) }, 'Revoke') : '') }], s.sessions),
      h('h3', { class: 'sect' }, 'Admin login history'),
      table([{ label: 'When', render: (x) => fmtDate(x.attempted_at) }, { label: 'Result', render: (x) => (x.success ? pill('success', 'ok') : pill('failed', 'bad')) }, { label: 'Source (hashed)', render: (x) => h('code', null, x.ip_ref) }], s.attempts),
      h('h3', { class: 'sect' }, 'Audit log'),
      table([{ label: 'When', render: (x) => fmtDate(x.created_at) }, { label: 'Action', render: (x) => h('code', null, x.action) }, { label: 'Target user', render: (x) => x.target_user_id || '-' }, { label: 'Details', render: (x) => h('code', null, JSON.stringify(x.details)) }], s.audit));
  },
};

// ------------------------------------------------------------ modals
async function userModal(id, reload) {
  const d = await api('users.detail', { user_id: id }).catch((e) => { toast(e.message, true); return null; });
  if (!d || !d.profile) return;
  const p = d.profile, pr = d.preferences || {}, name = h('input', { value: p.display_name || '', maxlength: 120, 'aria-label': 'Display name' });
  const kv = (k, v) => [h('dt', null, k), h('dd', null, v ?? '-')];
  const m = modal(p.display_name || p.email || p.id, h('div', null,
    h('dl', { class: 'kv' }, kv('User ID', p.id), kv('Email', p.email || 'not available'), kv('Status', p.is_blocked ? 'BLOCKED' : 'active'), kv('Registered', fmtDate(p.created_at)), kv('Last activity', fmtDate(p.last_seen_at)),
      kv('Provider / model', `${pr.selected_provider || '-'} / ${pr.selected_model || '-'}`), kv('Voice', `${pr.selected_voice || '-'} (${pr.voice_engine || '-'}), rate ${pr.speech_rate ?? '-'}, pitch ${pr.speech_pitch ?? '-'}`),
      kv('API status', d.providers.length ? d.providers.map((x) => `${x.provider}: ${x.is_configured ? 'configured' : 'not set'} (${x.connection_status})`).join('; ') : 'none'),
      kv('Conversations', num(d.counts.conversations)), kv('Messages', num(d.counts.messages)), kv('AI requests', num(d.counts.ai_requests)), kv('Tickets', num(d.counts.tickets))),
    h('p', { class: 'note' }, 'Chat content and API keys are intentionally not available to administrators.'),
    h('div', { class: 'row sect' }, h('div', null, h('label', { class: 'note' }, 'Display name'), name),
      h('button', { class: 'btn ghost sm', type: 'button', onclick: guard(async () => { await api('users.update', { user_id: id, display_name: name.value }); toast('Saved'); reload(); m.close(); }) }, 'Save name')),
    h('h3', null, 'Recent logins'),
    table([{ label: 'When', render: (x) => fmtDate(x.created_at) }, { label: 'Event', key: 'event_type' }, { label: 'Provider', key: 'provider' }, { label: 'Device', key: 'device_type' }], d.recent_logins)),
    [p.is_blocked
      ? h('button', { class: 'btn', type: 'button', onclick: guard(async () => { await api('users.unblock', { user_id: id }); toast('User unblocked'); reload(); m.close(); }) }, 'Unblock')
      : h('button', { class: 'btn danger', type: 'button', onclick: guard(async () => { if (confirm('Block this user? They will be unable to use the app.')) { await api('users.block', { user_id: id }); toast('User blocked'); reload(); m.close(); } }) }, 'Block'),
      h('button', { class: 'btn danger', type: 'button', onclick: guard(async () => {
        const c = prompt('Permanently delete this user and ALL their data.\nType the exact user ID to confirm:\n\n' + id);
        if (c === null) return; const r = await api('users.delete', { user_id: id, confirm: c }); toast('Deleted'); alert(r.note); reload(); m.close(); }) }, 'Delete user...')]);
}

async function ticketModal(id, reload) {
  const d = await api('tickets.get', { ticket_id: id }).catch((e) => { toast(e.message, true); return null; });
  if (!d) return;
  const t = d.ticket, reply = h('textarea', { maxlength: 5000, placeholder: 'Write a reply to the user', 'aria-label': 'Reply' });
  const st = h('select', { 'aria-label': 'Status after reply' }, [['in_progress', 'Mark in progress'], ['resolved', 'Mark resolved'], ['closed', 'Close'], ['open', 'Keep open']].map(([v, l]) => h('option', { value: v }, l)));
  const thread = h('div', { class: 'thread' }, h('div', { class: 'bub user' }, t.message, h('small', null, `${d.user?.email || t.user_id} - ${fmtDate(t.created_at)}`)),
    d.messages.map((x) => h('div', { class: 'bub ' + x.sender_type }, x.message, h('small', null, `${x.sender_type} - ${fmtDate(x.created_at)}`))));
  const m = modal(t.subject, h('div', null, h('div', { class: 'row' }, ticketPill(t.status), pill(t.category || 'general'), pill(t.priority || 'normal')), thread, reply, h('div', { class: 'row' }, st)),
    [h('button', { class: 'btn', type: 'button', onclick: guard(async () => {
      const r = await api('tickets.reply', { ticket_id: id, message: reply.value, status: st.value });
      toast(r.email.status === 'sent' ? 'Reply sent (app + email)' : `Reply saved for the app. Email ${r.email.status}${r.email.error ? ': ' + r.email.error : ''}`); reload(); m.close(); }) }, 'Send reply'),
     h('button', { class: 'btn ghost', type: 'button', onclick: guard(async () => { await api('tickets.status', { ticket_id: id, status: 'resolved' }); toast('Resolved'); reload(); m.close(); }) }, 'Resolve without reply')]);
}

go(location.hash.slice(1) || 'overview');
