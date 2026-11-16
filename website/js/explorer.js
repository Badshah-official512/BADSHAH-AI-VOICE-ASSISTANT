// Project explorer: expandable tree + full-text search + safe markdown viewer. No innerHTML anywhere.
const $ = (s) => document.querySelector(s);
const el = (t, a = {}, ...k) => { const e = document.createElement(t); for (const [x, v] of Object.entries(a)) { if (x === 'class') e.className = v; else if (x.startsWith('on')) e.addEventListener(x.slice(2), v); else if (v != null) e.setAttribute(x, v); } e.append(...k.flat().filter((n) => n != null)); return e; };
const viewer = $('#viewer'), treeEl = $('#tree');

const data = await (await fetch('files.json')).json();
const files = data.files, byPath = new Map(files.map((f) => [f.path, f]));

// ---- tree ----
const root = {};
for (const f of files) { let n = root; const parts = f.path.split('/'); parts.forEach((p, i) => { n.c ??= {}; n = n.c[p] ??= i === parts.length - 1 ? { file: f } : {}; }); }
function build(node, open) {
  const ul = el('ul', { class: 'node' + (open ? ' root' : '') });
  const names = Object.keys(node.c || {}).sort((a, b) => (!!node.c[b].c - !!node.c[a].c) || a.localeCompare(b));
  for (const name of names) {
    const ch = node.c[name];
    if (ch.file) {
      const b = el('button', { type: 'button', class: ch.file.content == null ? 'nocontent' : '', title: ch.file.path, onclick: () => openFile(ch.file.path) }, name);
      ul.append(el('li', { class: 'file', 'data-path': ch.file.path }, b));
    } else {
      const li = el('li', { class: 'dir' + (open ? ' open' : '') });
      li.append(el('button', { type: 'button', onclick: () => li.classList.toggle('open') }, name + '/'), build(ch));
      ul.append(li);
    }
  }
  return ul;
}
treeEl.append(build(root, true));
function revealInTree(p) {
  for (const b of treeEl.querySelectorAll('button.on')) b.classList.remove('on');
  const li = [...treeEl.querySelectorAll('li.file')].find((x) => x.dataset.path === p); if (!li) return;
  li.firstChild.classList.add('on'); for (let n = li.parentElement; n && n !== treeEl; n = n.parentElement) if (n.classList?.contains('dir')) n.classList.add('open');
  li.scrollIntoView({ block: 'nearest' });
}

// ---- markdown (safe subset) ----
function inline(text, parent) {
  const re = /(`[^`]+`)|(\*\*[^*]+\*\*)|(\[[^\]]+\]\([^)]+\))/g; let last = 0, m;
  while ((m = re.exec(text))) {
    parent.append(text.slice(last, m.index)); const t = m[0];
    if (t[0] === '`') parent.append(el('code', {}, t.slice(1, -1)));
    else if (t.startsWith('**')) parent.append(el('b', {}, t.slice(2, -2)));
    else { const [, label, href] = /\[([^\]]+)\]\(([^)]+)\)/.exec(t);
      if (/^https:\/\//.test(href)) parent.append(el('a', { href, rel: 'noopener noreferrer', target: '_blank' }, label));
      else { const target = resolve(href); parent.append(target ? el('a', { href: '#', onclick: (e) => { e.preventDefault(); openFile(target); } }, label) : label); } }
    last = m.index + t.length;
  }
  parent.append(text.slice(last));
}
let currentDir = '';
function resolve(href) { const p = href.split('#')[0]; if (!p) return null; const parts = (currentDir ? currentDir.split('/') : []).concat(p.split('/')); const out = []; for (const x of parts) { if (x === '..') out.pop(); else if (x !== '.') out.push(x); } const r = out.join('/'); return byPath.has(r) ? r : null; }
function markdown(src) {
  const box = el('div', { class: 'md' }); const lines = src.replace(/\r/g, '').split('\n'); let i = 0;
  while (i < lines.length) {
    const L = lines[i];
    if (/^```/.test(L)) { const buf = []; i++; while (i < lines.length && !/^```/.test(lines[i])) buf.push(lines[i++]); i++; box.append(el('pre', {}, el('code', {}, buf.join('\n')))); continue; }
    let m;
    if ((m = /^(#{1,4})\s+(.*)$/.exec(L))) { const h = el('h' + m[1].length); inline(m[2], h); box.append(h); i++; continue; }
    if (/^\|.*\|\s*$/.test(L) && /^\|[\s:|-]+\|\s*$/.test(lines[i + 1] || '')) {
      const rows = []; const head = L; i += 2; while (i < lines.length && /^\|.*\|\s*$/.test(lines[i])) rows.push(lines[i++]);
      const cells = (r) => r.trim().replace(/^\||\|$/g, '').split('|').map((c) => c.trim());
      const t = el('table'), thead = el('thead', {}, el('tr', {}, cells(head).map((c) => { const th = el('th'); inline(c, th); return th; })));
      const tb = el('tbody'); for (const r of rows) tb.append(el('tr', {}, cells(r).map((c) => { const td = el('td'); inline(c, td); return td; }))); t.append(thead, tb); box.append(t); continue;
    }
    if (/^\s*([-*]|\d+\.)\s+/.test(L)) { const ord = /^\s*\d+\./.test(L), ul = el(ord ? 'ol' : 'ul'); while (i < lines.length && /^\s*([-*]|\d+\.)\s+/.test(lines[i])) { const li = el('li'); inline(lines[i].replace(/^\s*([-*]|\d+\.)\s+/, ''), li); ul.append(li); i++; } box.append(ul); continue; }
    if (/^>\s?/.test(L)) { const bq = el('blockquote'); const buf = []; while (i < lines.length && /^>\s?/.test(lines[i])) buf.push(lines[i++].replace(/^>\s?/, '')); const p = el('p'); inline(buf.join(' '), p); bq.append(p); box.append(bq); continue; }
    if (!L.trim()) { i++; continue; }
    const buf = []; while (i < lines.length && lines[i].trim() && !/^(#{1,4}\s|```|>|\s*([-*]|\d+\.)\s|\|)/.test(lines[i])) buf.push(lines[i++]);
    const p = el('p'); inline(buf.join(' '), p); box.append(p);
  }
  return box;
}

// ---- viewer ----
function openFile(p, push = true) {
  const f = byPath.get(p); if (!f) return;
  currentDir = p.includes('/') ? p.slice(0, p.lastIndexOf('/')) : '';
  viewer.replaceChildren(el('div', { class: 'crumb' }, p + '  -  ' + (f.size / 1024).toFixed(1) + ' KB'));
  if (f.content == null) viewer.append(el('p', { class: 'note' }, 'Not shown here (binary, secret-bearing, or larger than 80 KB). Open it in the source ZIP.'));
  else if (/\.md$/i.test(p)) viewer.append(markdown(f.content));
  else viewer.append(el('pre', {}, el('code', {}, f.content)));
  revealInTree(p); viewer.scrollTop = 0; if (push) history.replaceState(null, '', '#' + encodeURIComponent(p));
}

// ---- search ----
function snippet(content, q) {
  const i = content.toLowerCase().indexOf(q); if (i < 0) return null;
  const s = content.slice(Math.max(0, i - 60), i + q.length + 90).replace(/\s+/g, ' ');
  const j = s.toLowerCase().indexOf(q); return el('div', { class: 's' }, s.slice(0, j), el('mark', {}, s.slice(j, j + q.length)), s.slice(j + q.length));
}
function search(raw) {
  const q = raw.trim().toLowerCase(); if (q.length < 2) { return; }
  const hits = [];
  for (const f of files) {
    const nameHit = f.path.toLowerCase().includes(q), sn = f.content ? snippet(f.content, q) : null;
    if (nameHit || sn) hits.push({ f, sn, score: (nameHit ? 2 : 0) + (/^documentation\/|README/i.test(f.path) ? 1 : 0) });
  }
  hits.sort((a, b) => b.score - a.score);
  const ul = el('ul', { class: 'results' });
  for (const h of hits.slice(0, 60)) ul.append(el('li', { tabindex: 0, onclick: () => openFile(h.f.path), onkeydown: (e) => { if (e.key === 'Enter') openFile(h.f.path); } }, el('div', { class: 'p' }, h.f.path), h.sn));
  viewer.replaceChildren(el('h2', {}, `${hits.length} result${hits.length === 1 ? '' : 's'} for "${raw.trim()}"`), hits.length ? ul : el('p', { class: 'note' }, 'No matches.'));
}
let t; $('#q').addEventListener('input', (e) => { clearTimeout(t); t = setTimeout(() => (e.target.value.trim().length >= 2 ? search(e.target.value) : null), 200); });
const start = decodeURIComponent(location.hash.slice(1)); if (byPath.has(start)) openFile(start, false); else if (byPath.has('README.md')) openFile('README.md', false);
