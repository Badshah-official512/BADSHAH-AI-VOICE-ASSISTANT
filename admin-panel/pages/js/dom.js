// Safe DOM helpers: text is ALWAYS inserted as text nodes (no innerHTML => no XSS).
export function h(tag, attrs, ...kids) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v == null || v === false) continue;
    if (k === 'class') el.className = v;
    else if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2), v);
    else if (k === 'value') el.value = v;
    else if (k === 'checked' || k === 'disabled' || k === 'selected') el[k] = !!v;
    else el.setAttribute(k, v === true ? '' : String(v));
  }
  for (const kid of kids.flat()) if (kid != null && kid !== false) el.append(kid.nodeType ? kid : document.createTextNode(String(kid)));
  return el;
}
export const svg = (tag, attrs, ...kids) => {
  const el = document.createElementNS('http://www.w3.org/2000/svg', tag);
  for (const [k, v] of Object.entries(attrs || {})) el.setAttribute(k, String(v));
  for (const kid of kids.flat()) if (kid != null) el.append(kid.nodeType ? kid : document.createTextNode(String(kid)));
  return el;
};
export const fmtDate = (s) => (s ? new Date(s).toLocaleString() : '-');
export const num = (n) => Number(n ?? 0).toLocaleString();
export const clear = (el) => { while (el.firstChild) el.removeChild(el.firstChild); return el; };

export function toast(text, isErr = false) {
  const t = h('div', { class: 'toast' + (isErr ? ' err' : ''), role: 'status' }, text);
  document.body.append(t); setTimeout(() => t.remove(), isErr ? 5000 : 2800);
}
export function modal(title, body, actions = []) {
  const bg = h('div', { class: 'modal-bg', onclick: (e) => { if (e.target === bg) bg.remove(); } });
  const m = h('div', { class: 'modal', role: 'dialog', 'aria-modal': 'true' },
    h('div', { class: 'row' }, h('h2', null, title), h('button', { class: 'btn ghost sm', type: 'button', onclick: () => bg.remove() }, 'Close')),
    body, h('div', { class: 'row sect' }, actions));
  bg.append(m); document.body.append(bg); return { close: () => bg.remove(), el: m };
}
export const stat = (n, l) => h('div', { class: 'card stat' }, h('div', { class: 'n' }, typeof n === 'number' ? num(n) : n), h('div', { class: 'l' }, l));
export function table(cols, rows, onRow) {
  const tb = h('tbody');
  for (const r of rows) {
    const tr = h('tr', { class: onRow ? 'click' : null, tabindex: onRow ? 0 : null, onclick: onRow ? () => onRow(r) : null, onkeydown: onRow ? (e) => { if (e.key === 'Enter') onRow(r); } : null },
      cols.map((c) => h('td', null, c.render ? c.render(r) : (r[c.key] ?? '-'))));
    tb.append(tr);
  }
  if (!rows.length) tb.append(h('tr', null, h('td', { colspan: cols.length, class: 'note' }, 'Nothing to show yet.')));
  return h('div', { class: 'tablewrap' }, h('table', null, h('thead', null, h('tr', null, cols.map((c) => h('th', null, c.label)))), tb));
}
export const pill = (text, kind = '') => h('span', { class: 'pill ' + kind }, text);

// Dependency-free SVG line chart. series: [{name, cls, values:[..]}], labels: [..]
export function lineChart(labels, series) {
  const W = 640, H = 200, P = 28, max = Math.max(1, ...series.flatMap((s) => s.values));
  const x = (i) => P + (i * (W - P * 1.5)) / Math.max(1, labels.length - 1);
  const y = (v) => H - P - (v / max) * (H - P * 1.5);
  const root = svg('svg', { viewBox: `0 0 ${W} ${H}`, class: 'chart', role: 'img', 'aria-label': 'Activity chart' });
  root.append(svg('line', { x1: P, y1: H - P, x2: W - 8, y2: H - P, class: 'axis' }), svg('line', { x1: P, y1: 8, x2: P, y2: H - P, class: 'axis' }));
  root.append(svg('text', { x: 4, y: 14 }, num(max)), svg('text', { x: 4, y: H - P }, '0'));
  if (labels.length) { root.append(svg('text', { x: P, y: H - 8 }, labels[0].slice(5)), svg('text', { x: W - 40, y: H - 8 }, labels[labels.length - 1].slice(5))); }
  for (const s of series) root.append(svg('polyline', { class: s.cls, points: s.values.map((v, i) => `${x(i)},${y(v)}`).join(' ') }));
  return h('div', null, root, h('div', { class: 'legend' }, series.map((s) => h('span', null, h('i', { class: 'dot ' + s.dotCls }), s.name))));
}
export function barRows(items) { // [{label, value}]
  const max = Math.max(1, ...items.map((i) => i.value)), W = 560, rowH = 26;
  const root = svg('svg', { viewBox: `0 0 ${W} ${Math.max(1, items.length) * rowH}`, class: 'chart', role: 'img' });
  items.forEach((it, i) => {
    root.append(svg('text', { x: 0, y: i * rowH + 17 }, it.label),
      svg('rect', { x: 150, y: i * rowH + 5, width: Math.max(2, (it.value / max) * (W - 220)), height: 16, rx: 4, class: 'b' + ((i % 2) + 1) }),
      svg('text', { x: 156 + (it.value / max) * (W - 220), y: i * rowH + 17 }, num(it.value)));
  });
  return root;
}
