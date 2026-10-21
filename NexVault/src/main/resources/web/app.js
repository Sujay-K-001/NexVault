(() => {
'use strict';

/* ------------------------------------------------------------------ helpers */
const NS = 'http://www.w3.org/2000/svg';
const $ = (s, r = document) => r.querySelector(s);
const LABELS = ['Very weak', 'Weak', 'Fair', 'Strong', 'Excellent'];
const CAT_HUE = { Social: 215, Email: 240, Finance: 138, Shopping: 28, Work: 262, Entertainment: 330, Development: 295, Other: 220 };
const motion = () => (S.status && S.status.motion) || 'full';
const reduced = () => motion() === 'off';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
function anim(el, kf, opt = {}) {
  if (reduced() || !el || !el.animate) return Promise.resolve();
  try { return el.animate(kf, { fill: 'none', ...opt }).finished.catch(() => {}); } catch { return Promise.resolve(); }
}

const ICONS = {
  dashboard: '<rect x="3" y="3" width="7" height="9" rx="1.5"/><rect x="14" y="3" width="7" height="5" rx="1.5"/><rect x="14" y="12" width="7" height="9" rx="1.5"/><rect x="3" y="16" width="7" height="5" rx="1.5"/>',
  vault: '<rect x="3" y="4" width="18" height="16" rx="3"/><circle cx="12" cy="12" r="3.2"/><path d="M12 8.8V7M12 17v-1.8M8.8 12H7M17 12h-1.8"/>',
  key: '<circle cx="8" cy="15" r="4.5"/><path d="M11.3 11.7 20 3M16 7l3 3M14 9l2 2"/>',
  shield: '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><path d="M9 12l2 2 4-4"/>',
  sliders: '<path d="M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6"/>',
  search: '<circle cx="11" cy="11" r="7.5"/><path d="M21 21l-4.4-4.4"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  eye: '<path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/>',
  eyeoff: '<path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><path d="M1 1l22 22"/>',
  copy: '<rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>',
  trash: '<path d="M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2m3 0v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6M10 11v6M14 11v6"/>',
  edit: '<path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z"/>',
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>',
  moon: '<path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"/>',
  lock: '<rect x="3" y="11" width="18" height="11" rx="2.5"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/>',
  power: '<path d="M18.4 6.6a9 9 0 1 1-12.8 0M12 2v10"/>',
  user: '<path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>',
  link: '<path d="M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.7 1.7"/><path d="M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7"/>',
  check: '<path d="M20 6L9 17l-5-5"/>',
  x: '<path d="M18 6L6 18M6 6l12 12"/>',
  warn: '<path d="M10.3 3.9L1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0zM12 9v4M12 17h.01"/>',
  clock: '<circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/>',
  refresh: '<path d="M23 4v6h-6M1 20v-6h6"/><path d="M3.5 9a9 9 0 0 1 14.9-3.4L23 10M1 14l4.6 4.4A9 9 0 0 0 20.5 15"/>',
  layers: '<path d="M12 2 2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"/>',
  chevL: '<path d="M15 18l-6-6 6-6"/>', chevR: '<path d="M9 18l6-6-6-6"/>',
  download: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3"/>',
  radar: '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="4.5"/><path d="M12 12 19 5"/>',
  life: '<circle cx="12" cy="12" r="10"/><circle cx="12" cy="12" r="4"/><path d="M4.9 4.9l4.3 4.3M14.8 14.8l4.3 4.3M19.1 4.9l-4.3 4.3M9.2 14.8l-4.3 4.3"/>',
  help: '<circle cx="12" cy="12" r="10"/><path d="M9.1 9a3 3 0 0 1 5.8 1c0 2-3 3-3 3M12 17h.01"/>',
};
function icon(name, size = 20) {
  const s = document.createElementNS(NS, 'svg');
  s.setAttribute('viewBox', '0 0 24 24'); s.setAttribute('class', 'ic');
  s.setAttribute('width', size); s.setAttribute('height', size); s.setAttribute('aria-hidden', 'true');
  s.innerHTML = ICONS[name] || '';   // static, trusted markup only
  return s;
}
function h(tag, props, ...kids) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(props || {})) {
    if (v == null || v === false) continue;
    if (k === 'class') el.className = v;
    else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
    else if (v === true) el.setAttribute(k, '');
    else el.setAttribute(k, v);
  }
  put(el, ...kids);
  return el;
}
function put(el, ...kids) { el.append(...kids.flat(Infinity).filter((k) => k != null && k !== false)); return el; }
const hueOf = (s) => { let x = 0; for (const c of s || '?') x = (x * 31 + c.codePointAt(0)) >>> 0; let hu = x % 360; if (hu > 140 && hu < 215) hu += 80; return hu % 360; };   // skips the teal/cyan range
function avatar(name, size = '') {
  const a = h('div', { class: 'avatar ' + size, 'aria-hidden': 'true' }, ((name || '?').trim()[0] || '?').toUpperCase());
  a.style.setProperty('--h', hueOf(name));
  return a;
}
function tag(text, cls = '', hue) {
  const t = h('span', { class: 'tag ' + cls }, text);
  if (hue != null) t.style.setProperty('--h', hue);
  return t;
}
function timeAgo(ts) {
  const s = Math.max(0, (Date.now() - ts) / 1000);
  if (s < 45) return 'just now';
  if (s < 3600) return Math.round(s / 60) + ' min ago';
  if (s < 86400) return Math.round(s / 3600) + ' h ago';
  if (s < 86400 * 30) return Math.round(s / 86400) + ' d ago';
  return new Date(ts).toLocaleDateString();
}
const fmtDate = (ts) => new Date(ts).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });
const debounce = (fn, ms) => { let t; return (...a) => { clearTimeout(t); t = setTimeout(() => fn(...a), ms); }; };

/* -------------------------------------------------------------------- state */
const S = {
  status: null, view: 'dashboard', entries: [], q: '', cat: 'all', sort: 'name', wrong: 0, fx: false, enterCards: false,
  gen: { length: 20, upper: true, lower: true, digits: true, symbols: true, avoidAmbiguous: false },
  lockAt: 0, offline: false, profile: {}, lockoutAt: 0,
};
let TOKEN = null;
let lastPing = 0;

class ApiError extends Error { constructor(status, msg) { super(msg); this.status = status; } }
async function api(path, method = 'GET', body) {
  const opt = { method, headers: { 'X-NexVault-Token': TOKEN } };
  if (method !== 'GET') opt.headers['Content-Type'] = 'application/json';
  if (body !== undefined) opt.body = JSON.stringify(body);
  let res;
  try { res = await fetch(path, opt); }
  catch { throw new ApiError(0, 'Cannot reach NexVault. Is the app still running?'); }
  let data = null;
  try { data = await res.json(); } catch { /* no body */ }
  if (!res.ok) {
    const e = new ApiError(res.status, (data && data.error) || 'Request failed');
    if (data) { e.locked = !!data.locked; e.fallback = !!data.fallback; e.data = data; }
    throw e;
  }
  return data;
}
function fail(e) {
  if (e && e.locked) { if (!S.fx) refreshStatus().then(() => vaultFx('close', render)).catch(() => {}); toast('The vault is locked.', 'err'); return; }
  toast((e && e.message) || 'Something went wrong.', 'err');
}
async function refreshStatus() {
  S.status = await api('/api/status');
  document.documentElement.dataset.theme = S.status.theme === 'dark' ? 'dark' : 'light';
  document.documentElement.dataset.motion = S.status.motion || 'full';
  if (S.status.lockoutSeconds > 0) S.lockoutAt = Date.now() + S.status.lockoutSeconds * 1000; else S.lockoutAt = 0;
  S.lockAt = Date.now() + S.status.secondsUntilLock * 1000;
  return S.status;
}

/* --------------------------------------------------------------- feedback UI */
function toast(msg, type = 'ok') {
  const t = h('div', { class: 'toast ' + (type === 'err' ? 'err' : ''), role: 'status' }, icon(type === 'err' ? 'warn' : 'check', 18), h('span', {}, msg));
  $('#toasts').append(t);
  setTimeout(() => { t.classList.add('out'); setTimeout(() => t.remove(), 260); }, type === 'err' ? 5200 : 2800);
}

const modals = [];
function modal(build, { small = false, persistent = false } = {}) {
  const prevFocus = document.activeElement;
  const scrim = h('div', { class: 'scrim' });
  const box = h('div', { class: 'modal glass' + (small ? ' small' : ''), role: 'dialog', 'aria-modal': 'true' });
  const closers = [];
  let closed = false;
  const m = {
    box,
    onClose: (f) => closers.push(f),
    close() {
      if (closed) return;
      closed = true;
      document.removeEventListener('keydown', onKey, true);
      modals.splice(modals.indexOf(m), 1);
      scrim.classList.add('out');
      setTimeout(() => scrim.remove(), 230);
      closers.forEach((f) => f());
      if (prevFocus && prevFocus.focus && document.contains(prevFocus)) prevFocus.focus();
    },
  };
  const onKey = (e) => {
    if (e.key === 'Escape' && !persistent && modals[modals.length - 1] === m) { e.stopPropagation(); m.close(); }
  };
  document.addEventListener('keydown', onKey, true);
  scrim.addEventListener('mousedown', (e) => { if (e.target === scrim && !persistent && modals[modals.length - 1] === m) m.close(); });
  scrim.append(box);
  document.body.append(scrim);
  modals.push(m);
  build(m);
  const f = box.querySelector('[data-autofocus]') || box.querySelector('input,textarea,select,button');
  if (f) f.focus();
  return m;
}
function confirmDialog({ title, message, confirm = 'Confirm', danger = false }) {
  return new Promise((resolve) => {
    let result = false;
    modal((m) => {
      m.onClose(() => resolve(result));
      put(m.box,
        h('div', { class: 'modal-head' }, h('h2', {}, title)),
        h('p', { class: 'lead' }, message),
        h('div', { class: 'modal-foot' },
          h('button', { class: 'btn', type: 'button', onclick: () => m.close() }, 'Cancel'),
          h('button', { class: 'btn ' + (danger ? 'danger' : 'primary'), 'data-autofocus': true, type: 'button', onclick: () => { result = true; m.close(); } }, confirm)));
    }, { small: true });
  });
}
function closeAllModals() { [...modals].forEach((m) => m.close()); }

/* ---------------------------------------------------------- reusable widgets */
function iconBtn(name, label, onclick, extra = '') {
  return h('button', { class: 'icon-btn ' + extra, type: 'button', 'aria-label': label, title: label, onclick }, icon(name, 18));
}
function passwordField({ label, id, placeholder = '', autofocus = false, onInput, withGenerate = false, onGenerate }) {
  const input = h('input', {
    class: 'input', id, type: 'password', placeholder, autocomplete: 'new-password', spellcheck: 'false',
    autocapitalize: 'off', autocorrect: 'off', 'data-lpignore': 'true', 'data-autofocus': autofocus || null,
  });
  if (onInput) input.addEventListener('input', () => onInput(input.value));
  let visible = false;
  const eye = iconBtn('eye', 'Show password', () => {
    visible = !visible;
    input.type = visible ? 'text' : 'password';
    eye.replaceChildren(icon(visible ? 'eyeoff' : 'eye', 18));
    eye.setAttribute('aria-label', visible ? 'Hide password' : 'Show password');
    eye.title = eye.getAttribute('aria-label');
  }, 'flat');
  const wrap = h('div', { class: 'pw-wrap' }, input, eye);
  const row = withGenerate ? h('div', { class: 'pw-row' }, wrap, iconBtn('refresh', 'Generate a strong password', onGenerate)) : wrap;
  const el = h('div', { class: 'field' }, label ? h('label', { for: id }, label) : null, row);
  return { el, input };
}
function strengthMeter() {
  const bars = [0, 1, 2, 3].map(() => h('i'));
  const meter = h('div', { class: 'meter', 'data-s': '-1' }, bars);
  const name = h('span', {}, 'Enter a password');
  const bits = h('span', { class: 'bits' });
  const tip = h('p', { class: 'hint' });
  const el = h('div', { class: 'strength', 'aria-live': 'polite' }, meter, h('div', { class: 'strength-label' }, name, bits), tip);
  let seq = 0;
  const paint = (score, label, b, fb) => {
    meter.dataset.s = score;
    bars.forEach((x, i) => x.classList.toggle('on', score >= 0 && i <= score));
    name.textContent = label; bits.textContent = b ? Math.round(b) + ' bits' : ''; tip.textContent = fb || '';
  };
  const update = debounce(async (pw) => {
    const my = ++seq;
    if (!pw) { paint(-1, 'Enter a password', 0, ''); return; }
    try { const r = await api('/api/strength', 'POST', { password: pw }); if (my === seq) paint(r.score, r.label, r.bits, r.feedback[0] || ''); } catch { /* ignore */ }
  }, 140);
  el.set = (pw) => { if (!pw) { seq++; paint(-1, 'Enter a password', 0, ''); } else update(pw); };
  el.setStatic = (score) => paint(score, LABELS[score], 0, '');
  return el;
}
function switchEl(label, checked, onchange) {
  const input = h('input', { type: 'checkbox', 'aria-label': label });
  input.checked = !!checked;
  input.addEventListener('change', () => onchange(input.checked, input));
  return h('label', { class: 'switch' }, input, h('span', { class: 'track' }));
}
function pageHead(title, sub) { return h('div', { class: 'page-head' }, h('h2', {}, title), sub ? h('p', {}, sub) : null); }

/* ----------------------------------------------------------------- clipboard */
function pulse(card) { if (card) { card.classList.remove('copied'); void card.offsetWidth; card.classList.add('copied'); setTimeout(() => card.classList.remove('copied'), 800); } }
async function copyEntry(id, field, card) {
  try {
    const r = await api('/api/clipboard', 'POST', { entryId: id, field });
    pulse(card);
    toast(field === 'username' ? 'Username copied' : `Password copied. Clipboard clears in ${r.clearAfter}s`);
  } catch (e) {
    if (e.fallback) {
      try {
        const v = field === 'username' ? (await api('/api/entries/' + id)).username : (await api(`/api/entries/${id}/password`)).password;
        await navigator.clipboard.writeText(v);
        pulse(card); toast(field === 'username' ? 'Username copied' : 'Password copied');
        if (field !== 'username') setTimeout(() => navigator.clipboard.writeText('').catch(() => {}), (e.data.clearAfter || 20) * 1000);
      } catch { toast('Could not access the clipboard.', 'err'); }
    } else fail(e);
  }
}
async function copyText(text, what = 'Copied') {
  try {
    const r = await api('/api/clipboard', 'POST', { text });
    toast(`${what}. Clipboard clears in ${r.clearAfter}s`);
  } catch (e) {
    if (e.fallback) {
      try { await navigator.clipboard.writeText(text); toast(what); setTimeout(() => navigator.clipboard.writeText('').catch(() => {}), (e.data.clearAfter || 20) * 1000); }
      catch { toast('Could not access the clipboard.', 'err'); }
    } else fail(e);
  }
}

/* ------------------------------------------- modules: cinematic fx, agent, avatars */
const FX = window.NVFX({ h, icon, ICONS, NS, S, sleep });
const splash = FX.splash, vaultFx = FX.vaultFx;
window.NVAgent.trackPointer(() => !reduced());
function showAgent(slot, mood, text) { slot.replaceChildren(window.NVAgent.view(h, mood, text)); }

/* ------------------------------------------------------- profile, avatars, logos */
async function loadProfile() { try { S.profile = await api('/api/profile'); } catch { S.profile = S.profile || {}; } return S.profile; }
function profileAvatar(size = 40) {
  const p = S.profile || {};
  const a = h('span', { class: 'av', 'aria-hidden': 'true' });
  a.style.setProperty('--s', size + 'px');
  if (p.avatarKind === 'custom' && p.avatarData) { const i = new Image(); i.alt = ''; i.src = p.avatarData; a.append(i); }
  else if (p.avatarKind === 'builtin' && window.NVAvatars.svg(p.avatarId)) a.append(window.NVAvatars.svg(p.avatarId));
  else if (p.name) a.append((p.name.trim()[0] || '?').toUpperCase());
  else a.append(icon('user', Math.round(size * 0.5)));
  return a;
}
const KNOWN = { github: 'github.com', gitlab: 'gitlab.com', google: 'google.com', gmail: 'gmail.com', youtube: 'youtube.com', netflix: 'netflix.com', spotify: 'spotify.com', amazon: 'amazon.com', apple: 'apple.com', icloud: 'icloud.com',
  microsoft: 'microsoft.com', outlook: 'outlook.com', facebook: 'facebook.com', instagram: 'instagram.com', twitter: 'x.com', x: 'x.com', linkedin: 'linkedin.com', reddit: 'reddit.com', discord: 'discord.com', slack: 'slack.com',
  dropbox: 'dropbox.com', paypal: 'paypal.com', stripe: 'stripe.com', adobe: 'adobe.com', steam: 'steampowered.com', twitch: 'twitch.tv', tiktok: 'tiktok.com', snapchat: 'snapchat.com', pinterest: 'pinterest.com',
  whatsapp: 'whatsapp.com', telegram: 'telegram.org', zoom: 'zoom.us', notion: 'notion.so', figma: 'figma.com', stackoverflow: 'stackoverflow.com', cloudflare: 'cloudflare.com', openai: 'openai.com', anthropic: 'anthropic.com',
  ebay: 'ebay.com', walmart: 'walmart.com', uber: 'uber.com', airbnb: 'airbnb.com', booking: 'booking.com', disneyplus: 'disneyplus.com', hulu: 'hulu.com', wikipedia: 'wikipedia.org', medium: 'medium.com', yahoo: 'yahoo.com', proton: 'proton.me' };
function domainOf(e) {
  try { if (e.url) return new URL(e.url).hostname.replace(/^www\./, '').toLowerCase(); } catch { /* fall through */ }
  return KNOWN[(e.service || '').toLowerCase().replace(/[^a-z0-9]/g, '')] || '';
}
const logoCache = new Map();
let logoActive = 0; const logoQueue = [];
function logoSlot(fn) { return new Promise((res) => { const run = () => { logoActive++; fn().then(res, () => res(null)).finally(() => { logoActive--; const n = logoQueue.shift(); if (n) n(); }); }; if (logoActive < 3) run(); else logoQueue.push(run); }); }
function loadLogo(domain) {
  if (!S.status || S.status.logosEnabled === false || !S.status.unlocked) return Promise.resolve(null);
  if (!logoCache.has(domain)) {
    logoCache.set(domain, logoSlot(async () => {
      for (let i = 0; i < 4; i++) {
        const r = await api('/api/logo?domain=' + encodeURIComponent(domain));
        if (r.status === 'ok') return r.dataUrl;
        if (r.status !== 'pending') return null;
        await sleep(2500);
      }
      return null;
    }).then((u) => { if (!u) setTimeout(() => logoCache.delete(domain), 600000); return u; }));
  }
  return logoCache.get(domain);
}
function entryAvatar(e, size = '') {
  const a = avatar(e.service, size);
  const d = domainOf(e);
  if (d) loadLogo(d).then((url) => {
    if (!url) return;
    const img = new Image(); img.alt = '';
    img.onload = () => { if (img.naturalWidth >= 32) { a.classList.add('has-logo'); a.replaceChildren(img); } };
    img.src = url;
  }).catch(() => {});
  return a;
}

/* -------------------------------------------------------------- auth screens */
function brand(withText = true) {
  return h('div', { class: 'brand' }, h('div', { class: 'brand-mark' }, icon('shield', 24)),
    withText ? h('div', {}, h('h1', {}, 'NexVault'), h('small', {}, 'Local password vault')) : null);
}
function fmtClock(sec) { const hh = Math.floor(sec / 3600), mm = Math.floor((sec % 3600) / 60), ss = sec % 60; return `${hh}:${String(mm).padStart(2, '0')}:${String(ss).padStart(2, '0')}`; }
function agentForError(e, n) {
  if (e.status === 429) return ['cooldown', `Easy there, rookie. Access is locked${e.data && e.data.lockoutSeconds ? ' for ' + fmtClock(e.data.lockoutSeconds) : ''}. Go drink some water.`];
  return window.NVAgent.lineFor(n);
}
function authScreen(mode) {
  const setup = mode === 'setup';
  const p = S.profile || {};
  const meter = setup ? strengthMeter() : null;
  const pw = passwordField({ label: 'Master password', id: 'mp', autofocus: true, placeholder: setup ? 'At least 10 characters' : 'Enter your master password', onInput: setup ? (v) => meter.set(v) : null });
  const pw2 = setup ? passwordField({ label: 'Confirm master password', id: 'mp2', placeholder: 'Type it again' }) : null;
  const err = h('p', { class: 'form-error', role: 'alert' });
  const slot = h('div', { class: 'agent-slot' });
  const attemptsEl = h('p', { class: 'attempts', id: 'attempts', 'aria-live': 'polite' });
  const lockSlot = h('div', { class: 'agent-slot' }), timerEl = h('div', { class: 'lock-timer', id: 'lock-timer', role: 'timer', 'aria-live': 'off' }, '0:00:00'), barI = h('i');
  const panel = h('div', { class: 'lockout hidden', id: 'lockout' }, lockSlot, timerEl, h('p', { class: 'sub' }, 'Vault access is disabled for security. This lock survives restarting the app.'), h('div', { class: 'lock-bar' }, barI));
  const card = h('div', { class: 'auth-card glass' });
  let busy = false, lockTotal = 0, shown = false, rw = null;
  const forgot = setup ? null : h('button', { class: 'link-btn', type: 'button', id: 'forgot-btn', onclick: openForgot }, 'Forgot master password?');
  const remaining = () => (S.lockoutAt ? Math.max(0, Math.ceil((S.lockoutAt - Date.now()) / 1000)) : 0);
  function paintLock() {
    if (setup) return;
    const r = remaining(), locked = r > 0;
    panel.classList.toggle('hidden', !locked);
    pw.input.disabled = locked; btn.disabled = locked || busy; if (forgot) forgot.disabled = locked; if (rw) rw.refresh();
    if (locked) {
      lockTotal = Math.max(lockTotal, r);
      timerEl.textContent = fmtClock(r); barI.style.setProperty('--w', (r / lockTotal * 100).toFixed(1) + '%');
      if (!shown) { shown = true; slot.replaceChildren(); attemptsEl.textContent = ''; showAgent(lockSlot, 'cooldown', 'Too many wrong guesses. I am locking this place down. Come back when the timer hits zero.'); }
    } else if (shown) {
      shown = false; lockTotal = 0; lockSlot.replaceChildren(); S.wrong = 0; attemptsEl.textContent = ''; refreshStatus().catch(() => {}); pw.input.focus();
    }
  }
  function paintAttempts(left) {
    if (left == null) { if (!attemptsEl.dataset.rw) attemptsEl.textContent = ''; return; }
    attemptsEl.removeAttribute('data-rw');
    attemptsEl.textContent = `${left} attempt${left === 1 ? '' : 's'} left before a 1-hour lockout`;
    attemptsEl.className = 'attempts' + (left <= 1 ? ' bad' : left <= 2 ? ' warn' : '');
  }
  async function submit() {
    if (busy || remaining() > 0) return;
    err.textContent = '';
    const v = pw.input.value;
    if (!v) { err.textContent = 'Please enter your master password.'; return; }
    if (setup) {
      if (v.length < 10) { err.textContent = 'The master password must be at least 10 characters.'; return; }
      if (v !== pw2.input.value) { err.textContent = 'The two passwords do not match.'; return; }
    }
    busy = true; btn.disabled = true; btn.lastChild.textContent = setup ? 'Creating…' : 'Unlocking…';
    try {
      const r = await api(setup ? '/api/setup' : '/api/unlock', 'POST', { password: v });
      pw.input.value = ''; if (pw2) pw2.input.value = '';
      S.wrong = 0;
      await refreshStatus();
      if (setup) await showRecoveryKey(r.recoveryKey, { first: true });
      await enterVault();
      toast(setup ? 'Your vault is ready' : 'Vault unlocked');
    } catch (e) {
      card.classList.remove('shake'); void card.offsetWidth; card.classList.add('shake');
      if (!setup && e.status === 429) {
        S.lockoutAt = Date.now() + ((e.data && e.data.lockoutSeconds) || 3600) * 1000; S.status.lockoutSeconds = (e.data && e.data.lockoutSeconds) || 3600; pw.input.value = '';
        paintLock();
      } else if (!setup && e.status === 401) {
        S.wrong++;
        const [mood, text] = agentForError(e, S.wrong);
        showAgent(slot, mood, text); paintAttempts(e.data && e.data.attemptsLeft);
        pw.input.select();
      } else err.textContent = e.message;
    } finally { busy = false; btn.lastChild.textContent = setup ? 'Create vault' : 'Unlock'; btn.disabled = remaining() > 0; }
  }
  const btn = h('button', { class: 'btn primary wide', type: 'button', id: 'auth-submit', onclick: submit }, icon(setup ? 'shield' : 'lock', 18), h('span', {}, setup ? 'Create vault' : 'Unlock'));
  [pw.input, pw2 && pw2.input].filter(Boolean).forEach((i) => i.addEventListener('keydown', (e) => { if (e.key === 'Enter') submit(); }));
  const first = (p.name || '').trim().split(/\s+/)[0];
  put(card,
    brand(),
    setup ? h('h2', {}, 'Create your vault') : h('div', { class: 'greet' }, p.avatarKind && p.avatarKind !== 'none' || p.name ? profileAvatar(54) : null, h('h2', { id: 'auth-title' }, first ? `Welcome back, ${first}` : 'Welcome back')),
    h('p', { class: 'lead' }, setup
      ? 'Choose one strong master password. It encrypts everything and is never stored. You will also get a recovery key in case you forget it.'
      : 'Enter your master password to decrypt your vault.'),
    panel, pw.el, meter, pw2 ? h('div', { class: 'field' }, pw2.el) : null, err, attemptsEl, slot, btn, forgot);
  if (!setup) {
    rw = FX.runaway(btn, { evasive: () => pw.input.value.length === 0 && remaining() === 0,
      hint: (on) => { if (!attemptsEl.textContent || attemptsEl.dataset.rw) { attemptsEl.className = 'attempts'; attemptsEl.textContent = on ? 'Type your master password to catch it.' : ''; if (on) attemptsEl.dataset.rw = '1'; else attemptsEl.removeAttribute('data-rw'); } } });
    pw.input.addEventListener('input', () => rw.refresh());
    rw.refresh();
  }
  if (!setup && (S.status.failedAttempts || 0) > 0 && !S.lockoutAt) paintAttempts(Math.max(0, S.status.maxAttempts - S.status.failedAttempts));
  paintLock();
  const tick = setInterval(() => { if (!card.isConnected) { clearInterval(tick); if (rw) rw.cleanup(); return; } paintLock(); }, 1000);
  return h('div', { class: 'auth' }, card);
}

function showRecoveryKey(key, { first = false } = {}) {
  return new Promise((resolve) => {
    modal((m) => {
      m.onClose(() => resolve());
      const cont = h('button', { class: 'btn primary', type: 'button', id: 'rk-continue', disabled: true, onclick: () => m.close() }, icon('check', 18), first ? 'Open my vault' : 'Done');
      const saved = switchEl('I stored my recovery key somewhere safe', false, (on) => { cont.disabled = !on; });
      const copy = async () => { try { await navigator.clipboard.writeText(key); toast('Recovery key copied'); } catch { copyText(key, 'Recovery key copied'); } };
      const download = () => {
        const blob = new Blob([`NexVault recovery key\n\n${key}\n\nWhoever has this key can reset your master password and open your vault.\nStore it offline (printed or on a USB stick), never in the same place as your vault file.\n`], { type: 'text/plain' });
        const url = URL.createObjectURL(blob);
        const a = h('a', { href: url, download: 'NexVault-recovery-key.txt' });
        document.body.append(a); a.click(); a.remove();
        setTimeout(() => URL.revokeObjectURL(url), 4000);
      };
      put(m.box,
        h('div', { class: 'modal-head' }, h('div', { class: 'brand-mark' }, icon('life', 24)), h('h2', {}, first ? 'Save your recovery key' : 'Your new recovery key')),
        h('p', { class: 'lead' }, 'If you ever forget your master password, this key is the only way back in. NexVault cannot recover it for you and will not show it again.'),
        h('div', { class: 'rkey neu-in', id: 'rkey', 'aria-label': 'Recovery key' }, key.split('-').map((g) => h('span', {}, g))),
        h('div', { class: 'btn-row' },
          h('button', { class: 'btn', type: 'button', id: 'rk-copy', onclick: copy }, icon('copy', 18), 'Copy'),
          h('button', { class: 'btn', type: 'button', id: 'rk-download', onclick: download }, icon('download', 18), 'Download .txt')),
        h('div', { class: 'opt', style: null }, h('span', {}, 'I stored my recovery key somewhere safe'), saved),
        h('div', { class: 'modal-foot' }, cont));
    }, { persistent: true });
  });
}

function openForgot() {
  modal((m) => {
    const body = h('div');
    m.box.append(body);
    const head = (title, back) => h('div', { class: 'modal-head' }, h('h2', {}, title), back ? iconBtn('chevL', 'Back', menu, 'flat') : null, iconBtn('x', 'Close', () => m.close(), 'flat'));
    function menu() {
      const rec = S.status.hasRecovery, hint = S.status.hasHint;
      body.replaceChildren(head('Forgot your master password?'),
        h('p', { class: 'lead' }, 'Your data is encrypted with a key only you can unlock, so nobody (including NexVault) can read it without one of these.'),
        h('button', { class: 'opt-card', type: 'button', id: 'fg-recovery', disabled: !rec || null, onclick: recover }, h('span', { class: 'ic-box' }, icon('life', 22)), h('span', {}, h('b', {}, 'Use my recovery key'), h('small', {}, rec ? 'Reset your master password. All your data stays.' : 'This vault has no recovery key.'))),
        h('button', { class: 'opt-card', type: 'button', id: 'fg-hint', disabled: !hint || null, onclick: showHint }, h('span', { class: 'ic-box' }, icon('help', 22)), h('span', {}, h('b', {}, 'Show my password hint'), h('small', {}, hint ? 'A reminder you wrote earlier.' : 'No hint was set.'))),
        h('button', { class: 'opt-card danger', type: 'button', id: 'fg-erase', onclick: erase }, h('span', { class: 'ic-box' }, icon('trash', 22)), h('span', {}, h('b', {}, 'Erase vault and start over'), h('small', {}, 'Deletes everything. Only if nothing else works.'))));
    }
    async function showHint() {
      let hint = '';
      try { hint = (await api('/api/hint')).hint; } catch (e) { fail(e); }
      body.replaceChildren(head('Your hint', true), h('div', { class: 'hint-box neu-in', id: 'hint-text' }, hint || 'No hint was set.'),
        h('p', { class: 'hint' }, 'Still stuck? Go back and use your recovery key.'));
    }
    function recover() {
      const slot = h('div');
      const key = h('input', { class: 'input mono', id: 'rk-input', placeholder: 'XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX', autocomplete: 'off', spellcheck: 'false', autocapitalize: 'characters', 'data-autofocus': true });
      key.addEventListener('input', () => { const v = key.value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 32); key.value = v.replace(/(.{4})(?=.)/g, '$1-'); });
      const meter = strengthMeter();
      const n1 = passwordField({ label: 'New master password', id: 'rn1', onInput: (v) => meter.set(v) });
      const n2 = passwordField({ label: 'Confirm new master password', id: 'rn2' });
      const err = h('p', { class: 'form-error', role: 'alert' });
      let wrong = 0, busy = false;
      const go = h('button', { class: 'btn primary', type: 'button', id: 'rk-reset', onclick: async () => {
        if (busy) return;
        err.textContent = '';
        if (!key.value.trim()) { err.textContent = 'Enter your recovery key.'; return; }
        if (n1.input.value.length < 10) { err.textContent = 'The new master password must be at least 10 characters.'; return; }
        if (n1.input.value !== n2.input.value) { err.textContent = 'The two passwords do not match.'; return; }
        busy = true; go.disabled = true;
        try {
          await api('/api/recover', 'POST', { recoveryKey: key.value, next: n1.input.value });
          n1.input.value = n2.input.value = key.value = '';
          S.wrong = 0;
          await refreshStatus();
          m.close();
          await enterVault();
          toast('Master password reset. Welcome back.');
        } catch (e) {
          if (e.status === 401 || e.status === 429) {
            wrong++;
            showAgent(slot, e.status === 429 ? 'cooldown' : (wrong > 1 ? 'deadpan' : 'suspicious'),
              e.status === 429 ? agentForError(e, wrong)[1] : (wrong > 1 ? 'Still not in my files. Check each character, detective.' : 'That key is not in my files. Check for typos, detective.'));
          } else err.textContent = e.message;
        } finally { busy = false; go.disabled = false; }
      } }, icon('key', 18), 'Reset master password');
      body.replaceChildren(head('Use your recovery key', true), h('p', { class: 'lead' }, 'Enter the key you saved when you created the vault, then choose a new master password.'),
        h('div', { class: 'field' }, h('label', { for: 'rk-input' }, 'Recovery key'), key), n1.el, meter, h('div', { class: 'field' }, n2.el), err, slot, h('div', { class: 'modal-foot' }, go));
      key.focus();
    }
    function erase() {
      const input = h('input', { class: 'input', id: 'erase-input', placeholder: 'Type ERASE to confirm', autocomplete: 'off', spellcheck: 'false', 'data-autofocus': true });
      const err = h('p', { class: 'form-error', role: 'alert' });
      const go = h('button', { class: 'btn danger', type: 'button', id: 'erase-go', onclick: async () => {
        if (input.value.trim() !== 'ERASE') { err.textContent = 'Type ERASE exactly to continue.'; return; }
        try { await api('/api/reset', 'POST', { confirm: 'ERASE' }); await refreshStatus(); m.close(); render(); toast('Vault erased. Create a new one.'); } catch (e) { err.textContent = e.message; }
      } }, icon('trash', 18), 'Erase everything');
      body.replaceChildren(head('Erase and start over?', true), h('p', { class: 'lead' }, 'This permanently deletes every saved login and the recovery key. It cannot be undone.'), h('div', { class: 'field' }, input), err, h('div', { class: 'modal-foot' }, go));
      input.focus();
    }
    menu();
  });
}

/* ---------------------------------------------------------------------- shell */
const VIEWS = {};
const NAV = [['dashboard', 'Dashboard', 'dashboard'], ['vault', 'Vault', 'vault'], ['generator', 'Generator', 'key'], ['security', 'Security Center', 'shield'], ['settings', 'Settings', 'sliders'], ['info', 'FAQ & Info', 'help']];

function shell() {
  const collapsed = !!S.status.sidebarCollapsed;
  const search = h('input', { class: 'input', id: 'search', type: 'search', placeholder: 'Search your vault', autocomplete: 'off', spellcheck: 'false', 'aria-label': 'Search your vault' });
  search.addEventListener('input', debounce(() => { S.q = search.value; if (S.view !== 'vault') go('vault'); else renderEntryList(); }, 110));
  const themeBtn = h('button', { class: 'nav', type: 'button', id: 'theme-btn', onclick: toggleTheme });
  paintThemeBtn(themeBtn);
  const toggle = iconBtn(collapsed ? 'chevR' : 'chevL', collapsed ? 'Expand sidebar' : 'Collapse sidebar', toggleSidebar, 'flat side-toggle');
  toggle.id = 'side-toggle';
  return h('div', { class: 'shell' + (collapsed ? ' collapsed' : ''), id: 'shell' },
    h('aside', { class: 'side glass' }, h('div', { class: 'side-head' }, brand(), toggle),
      h('nav', { 'aria-label': 'Main' }, NAV.map(([id, label, ic]) => h('button', { class: 'nav', type: 'button', 'data-view': id, 'aria-label': label, title: label, onclick: () => go(id) }, icon(ic), h('span', {}, label)))),
      h('div', { class: 'side-foot' },
        userChip(),
        themeBtn,
        h('button', { class: 'nav', type: 'button', id: 'lock-btn', title: 'Lock vault (Alt+L)', 'aria-keyshortcuts': 'Alt+L', onclick: lockNow }, icon('lock'), h('span', {}, 'Lock vault')),
        h('button', { class: 'nav', type: 'button', id: 'quit-btn', title: 'Quit NexVault', onclick: quitApp }, icon('power'), h('span', {}, 'Quit')))),
    h('main', { class: 'main' },
      h('div', { class: 'topbar' },
        h('div', { class: 'search' }, icon('search', 18), search),
        h('span', { class: 'lockchip hidden', id: 'lockchip' }, icon('clock', 16), h('span', {}, '')),
        h('span', { class: 'top-spacer' }),
        h('button', { class: 'btn primary', type: 'button', id: 'add-btn', title: 'Add entry (Alt+N)', 'aria-keyshortcuts': 'Alt+N', onclick: () => openForm() }, icon('plus', 18), h('span', {}, 'Add entry')),
        h('button', { class: 'top-avatar', type: 'button', id: 'top-avatar', 'aria-label': 'Profile and settings', title: 'Profile and settings', onclick: () => go('settings') }, profileAvatar(40))),
      h('section', { class: 'view', id: 'view', 'aria-live': 'polite' })));
}
function userChip() {
  const p = S.profile || {};
  return h('button', { class: 'user-chip', type: 'button', id: 'user-chip', title: 'Profile and settings', onclick: () => go('settings') }, profileAvatar(40),
    h('div', { class: 'uc-text' }, h('b', {}, p.name || 'Master'), h('small', {}, p.email || 'Set up your profile')));
}
function repaintProfile() {
  const c = $('#user-chip'); if (c) c.replaceWith(userChip());
  const t = $('#top-avatar'); if (t) t.replaceChildren(profileAvatar(40));
}
async function toggleSidebar() {
  const next = !S.status.sidebarCollapsed;
  S.status.sidebarCollapsed = next;
  $('#shell').classList.toggle('collapsed', next);
  const b = $('#side-toggle');
  b.replaceChildren(icon(next ? 'chevR' : 'chevL', 18));
  b.setAttribute('aria-label', next ? 'Expand sidebar' : 'Collapse sidebar'); b.title = b.getAttribute('aria-label');
  try { await api('/api/settings', 'PUT', { sidebarCollapsed: next }); } catch (e) { fail(e); }
}
function paintThemeBtn(b) {
  const dark = S.status.theme !== 'light';
  b.replaceChildren(icon(dark ? 'sun' : 'moon'), h('span', {}, dark ? 'Light theme' : 'Dark theme'));
  b.title = dark ? 'Switch to light theme' : 'Switch to dark theme';
}
async function toggleTheme() {
  const next = S.status.theme === 'light' ? 'dark' : 'light';
  try {
    await api('/api/settings', 'PUT', { theme: next });
    S.status.theme = next;
    document.documentElement.dataset.theme = next;
    const b = $('#theme-btn'); if (b) paintThemeBtn(b);
    const seg = $('#theme-seg'); if (seg) seg.querySelectorAll('button').forEach((x) => x.classList.toggle('on', x.dataset.v === next));
  } catch (e) { fail(e); }
}
async function go(view, { soft = false } = {}) {
  S.view = view;
  document.querySelectorAll('.nav[data-view]').forEach((b) => { b.classList.toggle('on', b.dataset.view === view); if (b.dataset.view === view) b.setAttribute('aria-current', 'page'); else b.removeAttribute('aria-current'); });
  const c = $('#view');
  if (!c) return;
  try {
    const node = await VIEWS[view]();
    if (S.view !== view || $('#view') !== c) return;
    const old = c.firstElementChild;
    if (!soft && old) await anim(old, [{ opacity: 1, transform: 'none' }, { opacity: 0, transform: 'translateY(-8px) scale(.995)' }], { duration: 130, easing: 'ease-in', fill: 'forwards' });
    c.replaceChildren(node);
    if (!soft) { c.scrollTop = 0; anim(node, [{ opacity: 0, transform: 'translateY(16px)' }, { opacity: 1, transform: 'none' }], { duration: 420, easing: 'cubic-bezier(.2,.8,.2,1)' }); }
  } catch (e) { fail(e); }
}
function render() {
  closeAllModals();
  const app = $('#app');
  if (!S.status.initialized) app.replaceChildren(authScreen('setup'));
  else if (!S.status.unlocked) app.replaceChildren(authScreen('unlock'));
  else { app.replaceChildren(shell()); go(VIEWS[S.view] ? S.view : 'dashboard', { soft: true }); }
}
async function enterVault() { S.view = 'dashboard'; await vaultFx('open', render); }
async function lockNow() {
  if (S.fx) return;
  S.fx = true;
  try { await api('/api/lock', 'POST', {}); await refreshStatus(); } catch (e) { S.fx = false; fail(e); return; }
  S.fx = false;
  await vaultFx('close', render);
  toast('Vault locked');
}
async function quitApp() {
  const ok = await confirmDialog({ title: 'Quit NexVault?', message: 'The vault will be locked and the app will close. Your data stays safely encrypted on disk.', confirm: 'Quit' });
  if (!ok) return;
  try { await api('/api/quit', 'POST', {}); } catch { /* server may already be gone */ }
  stopTimers();
  $('#app').replaceChildren(h('div', { class: 'auth' }, h('div', { class: 'auth-card glass' }, brand(), h('h2', {}, 'NexVault has closed'), h('p', { class: 'lead' }, 'Your vault is locked. You can close this window.'))));
  try { sessionStorage.removeItem('nv.t'); window.close(); } catch { /* ignore */ }
}

/* ------------------------------------------------------------------ dashboard */
function scoreDial(d) {
  const tone = d.score == null ? 'none' : d.score >= 85 ? 'good' : d.score >= 70 ? 'ok' : d.score >= 50 ? 'warn' : 'bad';
  const R = 94, C = 2 * Math.PI * R;
  const arc = document.createElementNS(NS, 'circle');
  for (const [k, v] of Object.entries({ class: 'arc', cx: 112, cy: 112, r: R })) arc.setAttribute(k, v);
  arc.style.strokeDasharray = C; arc.style.strokeDashoffset = C;
  const svg = document.createElementNS(NS, 'svg'); svg.setAttribute('viewBox', '0 0 224 224'); svg.setAttribute('aria-hidden', 'true'); svg.append(arc);
  requestAnimationFrame(() => requestAnimationFrame(() => { arc.style.strokeDashoffset = C * (1 - (d.score || 0) / 100); }));
  return h('div', { class: 'dial', 'data-tone': tone, role: 'img', 'aria-label': `Security score ${d.score} out of 100, ${d.scoreLabel}` }, svg,
    h('div', { class: 'dial-num' }, h('b', {}, d.score == null ? '–' : d.score), h('span', {}, d.scoreLabel)));
}
function summaryText(d) {
  const bits = [d.breached && `${d.breached} breached`, d.weak && `${d.weak} weak`, d.reused && `${d.reused} reused`, d.stale && `${d.stale} not changed in 180+ days`].filter(Boolean);
  return bits.length ? bits.join(', ') + '.' : 'Every password is strong, unique and recently updated.';
}
VIEWS.dashboard = async function () {
  const d = await api('/api/dashboard');
  if (d.total === 0) {
    return h('div', {}, pageHead('Overview', S.profile && S.profile.name ? `Welcome back, ${S.profile.name}` : 'Your vault at a glance'), h('div', { class: 'card glass' }, emptyState(
      'Your vault is empty', 'Add your first login and NexVault will start scoring your password health.',
      [h('button', { class: 'btn primary', type: 'button', onclick: () => openForm() }, icon('plus', 18), 'Add your first entry'),
       h('button', { class: 'btn', type: 'button', onclick: () => go('generator') }, icon('key', 18), 'Open generator')])),
      d.activity.length ? h('div', { class: 'dash-lower' }, activityCard(d)) : null);
  }
  const dial = h('div', { class: 'card glass dial-card' }, scoreDial(d),
    h('div', {}, h('h3', {}, 'Security score'), h('p', {}, summaryText(d)), h('div', { class: 'btn-row' }, h('button', { class: 'btn sm', type: 'button', onclick: () => go('security') }, icon('shield', 16), 'Open Security Center'))));
  const stat = (label, n, ic, cls) => h('div', { class: 'stat neu ' + cls }, h('div', { class: 'ic-box' }, icon(ic, 18)), h('b', {}, n), h('span', {}, label));
  const max = Math.max(1, ...Object.values(d.byCategory));
  const cats = Object.entries(d.byCategory).filter(([, n]) => n > 0).sort((a, b) => b[1] - a[1]);
  const catCard = h('div', { class: 'card glass' }, h('h3', {}, 'By category'),
    h('div', { class: 'bars' }, cats.map(([c, n]) => {
      const i = h('i'); i.style.setProperty('--h', CAT_HUE[c] ?? 220);
      requestAnimationFrame(() => requestAnimationFrame(() => i.style.setProperty('--w', (n / max * 100) + '%')));
      return h('div', { class: 'bar-row' }, h('span', {}, c), h('div', { class: 'bar' }, i), h('b', {}, n));
    })));
  const attn = h('div', { class: 'card glass' },
    h('h3', {}, 'Needs attention', d.attentionCount > 6 ? h('button', { class: 'btn sm ghost', type: 'button', onclick: () => go('security') }, `View all ${d.attentionCount}`) : null),
    d.attention.length === 0
      ? h('div', { class: 'ok-box' }, icon('check', 20), 'Nothing to fix. Nice work.')
      : h('div', { class: 'list' }, d.attention.map((a) => h('div', { class: 'row neu-in' }, entryAvatar(a, 'sm'),
          h('div', { class: 'grow' }, h('div', { class: 't' }, a.service), h('div', { class: 's' }, a.issues.join(' · '))),
          h('button', { class: 'btn sm', type: 'button', onclick: () => openForm(a.id) }, 'Fix')))));
  return h('div', {},
    pageHead('Overview', S.profile && S.profile.name ? `Welcome back, ${S.profile.name}` : 'Your vault at a glance'),
    h('div', { class: 'dash-grid' }, dial,
      h('div', { class: 'dash-right' },
        h('div', { class: 'stats' }, stat('Entries', d.total, 'layers', 'ok'), stat('Weak passwords', d.weak, 'warn', d.weak ? 'bad' : ''), stat('Reused', d.reused, 'copy', d.reused ? 'warn' : ''), stat('Found in breaches', d.breached, 'radar', d.breached ? 'bad' : ''), stat('Not changed in 180+ days', d.stale, 'clock', d.stale ? 'warn' : '')),
        catCard)),
    h('div', { class: 'dash-lower' }, attn, activityCard(d)),
    d.corrupt ? h('p', { class: 'hint' }, `${d.corrupt} entr${d.corrupt === 1 ? 'y' : 'ies'} could not be decrypted and ${d.corrupt === 1 ? 'was' : 'were'} skipped.`) : null);
};
function activityCard(d) {
  return h('div', { class: 'card glass' }, h('h3', {}, 'Recent activity'),
    d.activity.length === 0 ? h('p', { class: 'hint' }, 'Nothing yet.')
      : h('div', { class: 'list' }, d.activity.map((a) => h('div', { class: 'row' }, h('span', { class: 'dot' }),
          h('div', { class: 'grow' }, h('div', { class: 't' }, a.action), a.label ? h('div', { class: 's' }, a.label) : null),
          h('span', { class: 's ts', title: fmtDate(a.ts) }, timeAgo(a.ts))))));
}
function emptyState(title, text, actions) {
  const art = document.createElementNS(NS, 'svg');
  for (const [k, v] of Object.entries({ class: 'art', viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '1.2', 'stroke-linecap': 'round', 'stroke-linejoin': 'round', 'aria-hidden': 'true' })) art.setAttribute(k, v);
  art.innerHTML = ICONS.vault;
  return h('div', { class: 'empty' }, art, h('h3', {}, title), h('p', {}, text), h('div', { class: 'out-actions' }, actions));
}

/* ---------------------------------------------------------------------- vault */
async function loadEntries() { S.entries = await api('/api/entries?sort=name'); }
VIEWS.vault = async function () {
  await loadEntries();
  const root = h('div', {}, pageHead('Vault', 'Every login, encrypted on this device'), h('div', { id: 'vault-body' }));
  S.enterCards = true;
  renderVaultBody(root.querySelector('#vault-body'));
  return root;
};
function renderVaultBody(body = $('#vault-body')) {
  if (!body) return;
  const counts = { all: S.entries.length };
  for (const e of S.entries) counts[e.category] = (counts[e.category] || 0) + 1;
  if (S.cat !== 'all' && !counts[S.cat]) S.cat = 'all';
  const chips = ['all', ...S.status.categories.filter((c) => counts[c])].map((c) => h('button', {
    class: 'chip' + (S.cat === c ? ' on' : ''), type: 'button', 'aria-pressed': S.cat === c ? 'true' : 'false', 'data-cat': c,
    onclick: () => { S.cat = c; renderVaultBody(); },
  }, c === 'all' ? 'All' : c, h('small', {}, counts[c])));
  const sort = h('select', { class: 'input sel', 'aria-label': 'Sort entries', onchange: (e) => { S.sort = e.target.value; renderEntryList(); } },
    [['name', 'Name A–Z'], ['recent', 'Recently changed'], ['strength', 'Weakest first']].map(([v, l]) => h('option', { value: v, selected: S.sort === v || null }, l)));
  body.replaceChildren(h('div', { class: 'toolbar' }, h('div', { class: 'chips' }, chips), sort), h('div', { class: 'cards', id: 'entry-list' }));
  const enter = S.enterCards; S.enterCards = false;
  renderEntryList(body.querySelector('#entry-list'), enter);
}
function filteredEntries() {
  const q = S.q.trim().toLowerCase();
  const list = S.entries.filter((e) => (S.cat === 'all' || e.category === S.cat) && (!q || [e.service, e.username, e.url, e.category].some((x) => (x || '').toLowerCase().includes(q))));
  const by = { name: (a, b) => a.service.localeCompare(b.service, undefined, { sensitivity: 'base' }), recent: (a, b) => b.updatedAt - a.updatedAt, strength: (a, b) => a.strength - b.strength || a.service.localeCompare(b.service) };
  return list.sort(by[S.sort] || by.name);
}
function renderEntryList(box = $('#entry-list'), enter = false) {
  if (!box) return;
  const list = filteredEntries();
  const wrap = (node) => { box.classList.remove('cards'); box.replaceChildren(node); };
  if (S.entries.length === 0) {
    wrap(h('div', { class: 'card glass' }, emptyState('No entries yet', 'Store your first login. Everything is encrypted with AES-256-GCM before it touches the disk.',
      [h('button', { class: 'btn primary', type: 'button', onclick: () => openForm() }, icon('plus', 18), 'Add entry')])));
    return;
  }
  if (list.length === 0) {
    wrap(h('div', { class: 'card glass' }, emptyState('No matches', S.q ? `Nothing in your vault matches “${S.q}”.` : 'No entries in this category.',
      [h('button', { class: 'btn', type: 'button', onclick: () => { S.q = ''; S.cat = 'all'; const s = $('#search'); if (s) s.value = ''; renderVaultBody(); } }, 'Clear filters')])));
    return;
  }
  box.classList.add('cards');
  box.replaceChildren(...list.map((e, i) => entryCard(e, i, enter)));
}
function attachTilt(card) {
  if (reduced() || window.matchMedia('(hover: none)').matches) return;
  let raf = 0;
  card.addEventListener('pointermove', (ev) => {
    if (raf) return;
    raf = requestAnimationFrame(() => {
      raf = 0;
      const r = card.getBoundingClientRect(), x = (ev.clientX - r.left) / r.width, y = (ev.clientY - r.top) / r.height;
      card.style.setProperty('--ry', ((x - 0.5) * 11).toFixed(2) + 'deg'); card.style.setProperty('--rx', ((0.5 - y) * 9).toFixed(2) + 'deg');
      card.style.setProperty('--mx', (x * 100).toFixed(1) + '%'); card.style.setProperty('--my', (y * 100).toFixed(1) + '%');
    });
  });
  card.addEventListener('pointerleave', () => { card.style.setProperty('--rx', '0deg'); card.style.setProperty('--ry', '0deg'); });
}
function entryCard(e, i, enter) {
  const pips = [];
  const pip = (cls, label) => { const p = h('span', { class: 'pip ' + cls, title: label, role: 'img', 'aria-label': label }); return p; };
  if (e.breached) pips.push(pip('bad', 'Found in a data breach'));
  if (e.weak) pips.push(pip('bad', 'Weak password'));
  if (e.reused) pips.push(pip('warn', 'Reused password'));
  if (e.stale) pips.push(pip('', 'Not changed in 180+ days'));
  const cat = h('span', { class: 'ec-cat' }, h('i'), e.category);
  cat.firstChild.style.setProperty('--h', CAT_HUE[e.category] ?? 220);
  const card = h('article', { class: 'ecard' + (enter ? ' enter' : ''), 'data-id': e.id },
    h('div', { class: 'ec-top' }, cat, h('span', { class: 'ec-pips' }, pips.slice(0, 3))),
    h('div', { class: 'ec-body' }, entryAvatar(e, 'lg'), h('div', { class: 'ec-title' }, e.service), h('div', { class: 'ec-user' }, e.username || 'No username')),
    h('button', { class: 'ecard-main', type: 'button', onclick: () => openDetail(e.id), 'aria-label': `Open ${e.service}` }),
    h('div', { class: 'ec-actions' },
      h('button', { class: 'btn primary', type: 'button', 'aria-label': `Copy password for ${e.service}`, onclick: () => copyEntry(e.id, 'password', card) }, icon('copy', 16), 'Password'),
      iconBtn('user', `Copy username for ${e.service}`, () => copyEntry(e.id, 'username', card)),
      e.url ? iconBtn('link', `Open ${e.service} website`, () => window.open(e.url, '_blank', 'noopener')) : null),
    h('div', { class: 'ec-bar', 'data-s': e.strength, title: `Password strength: ${LABELS[e.strength]}` }));
  card.style.setProperty('--i', Math.min(i, 14));
  attachTilt(card);
  return card;
}
async function refreshAfterChange() {
  await refreshStatus().catch(() => {});
  if (S.view === 'vault') { await loadEntries(); renderVaultBody(); } else await go(S.view, { soft: true });
}

/* --------------------------------------------------------------- entry detail */
async function openDetail(id) {
  let d;
  try { d = await api('/api/entries/' + id); } catch (e) { fail(e); return; }
  let shown = false, timer = null;
  const pwText = h('span', { class: 'mono' }, '••••••••••••');
  const eye = iconBtn('eye', 'Show password', async () => {
    if (shown) return hide();
    try {
      pwText.textContent = (await api(`/api/entries/${id}/password`)).password;
      shown = true; eye.replaceChildren(icon('eyeoff', 18)); eye.setAttribute('aria-label', 'Hide password');
      timer = setTimeout(hide, 15000);
    } catch (e) { fail(e); }
  }, 'flat');
  function hide() { clearTimeout(timer); shown = false; pwText.textContent = '••••••••••••'; eye.replaceChildren(icon('eye', 18)); eye.setAttribute('aria-label', 'Show password'); }
  const meter = strengthMeter(); meter.setStatic(d.strength);
  const kv = (k, v, actions) => h('div', { class: 'kv neu-in' }, h('div', { class: 'grow' }, h('div', { class: 'k' }, k), h('div', { class: 'v' }, v)), actions);
  modal((m) => {
    m.onClose(() => { clearTimeout(timer); pwText.textContent = ''; });
    put(m.box,
      h('div', { class: 'modal-head' }, entryAvatar(d, 'lg'), h('div', { class: 'grow' }, h('h2', {}, d.service), tag(d.category, '', CAT_HUE[d.category] ?? 220)), iconBtn('x', 'Close', () => m.close(), 'flat')),
      kv('Username', d.username || '—', d.username ? iconBtn('copy', 'Copy username', () => copyEntry(id, 'username'), 'flat') : null),
      kv('Password', pwText, h('div', { class: 'e-actions' }, eye, iconBtn('copy', 'Copy password', () => copyEntry(id, 'password'), 'flat'))),
      d.url ? kv('Website', h('a', { href: d.url, target: '_blank', rel: 'noopener noreferrer' }, d.url), iconBtn('link', 'Open website', () => window.open(d.url, '_blank', 'noopener'), 'flat')) : null,
      d.notes ? kv('Notes', h('div', { class: 'notes' }, d.notes)) : null,
      h('div', { class: 'label' }, 'Password strength'), meter,
      d.breached ? h('p', { class: 'form-error' }, `This password appeared in known data breaches (${d.breachCount.toLocaleString()} times). Change it now.`) : null,
      d.reused ? h('p', { class: 'hint' }, 'This password is also used by another entry.') : null,
      h('p', { class: 'meta-line' }, `Added ${fmtDate(d.createdAt)} · Updated ${fmtDate(d.updatedAt)}`),
      h('div', { class: 'modal-foot' },
        h('button', { class: 'btn danger', type: 'button', onclick: async () => {
          const ok = await confirmDialog({ title: `Delete “${d.service}”?`, message: 'This permanently removes the entry from your vault. This cannot be undone.', confirm: 'Delete', danger: true });
          if (!ok) return;
          try { await api('/api/entries/' + id, 'DELETE'); m.close(); toast('Entry deleted'); await refreshAfterChange(); } catch (e) { fail(e); }
        } }, icon('trash', 18), 'Delete'),
        h('span', { class: 'spacer' }),
        h('button', { class: 'btn primary', type: 'button', onclick: () => { m.close(); openForm(id); } }, icon('edit', 18), 'Edit')));
  });
}

/* ----------------------------------------------------------------- entry form */
async function openForm(id, preset = {}) {
  let d = { service: '', username: '', url: '', notes: '', category: 'Other', ...preset };
  if (id) { try { d = await api('/api/entries/' + id); } catch (e) { fail(e); return; } }
  const editing = !!id;
  const meter = strengthMeter();
  const service = h('input', { class: 'input', id: 'f-service', value: d.service, maxlength: 100, placeholder: 'e.g. GitHub', autocomplete: 'off', 'data-autofocus': true });
  const user = h('input', { class: 'input', id: 'f-user', value: d.username, maxlength: 200, placeholder: 'name@example.com', autocomplete: 'off', spellcheck: 'false' });
  const pw = passwordField({
    label: 'Password', id: 'f-pw', placeholder: editing ? 'Leave blank to keep the current password' : 'Enter or generate a password',
    onInput: (v) => meter.set(v), withGenerate: true,
    onGenerate: async () => {
      try { const r = await api('/api/generate', 'POST', S.gen); pw.input.value = r.password; pw.input.type = 'text'; meter.set(r.password); toast(`Generated a ${r.password.length}-character password`); }
      catch (e) { fail(e); }
    },
  });
  if (preset.password) { pw.input.value = preset.password; meter.set(preset.password); }
  const url = h('input', { class: 'input', id: 'f-url', value: d.url, maxlength: 500, placeholder: 'example.com', autocomplete: 'off', spellcheck: 'false' });
  const cat = h('select', { class: 'input', id: 'f-cat' }, S.status.categories.map((c) => h('option', { value: c, selected: c === d.category || null }, c)));
  const notes = h('textarea', { class: 'input', id: 'f-notes', maxlength: 5000, placeholder: 'Anything else worth remembering' }, d.notes);
  const err = h('p', { class: 'form-error', role: 'alert' });
  const field = (label, input, idv) => h('div', { class: 'field' }, h('label', { for: idv }, label), input);
  modal((m) => {
    const save = h('button', { class: 'btn primary', type: 'button', onclick: async () => {
      err.textContent = '';
      if (!service.value.trim()) { err.textContent = 'Please enter a service name.'; service.focus(); return; }
      if (!editing && !pw.input.value) { err.textContent = 'Please enter or generate a password.'; pw.input.focus(); return; }
      const body = { service: service.value, username: user.value, url: url.value, category: cat.value, notes: notes.value };
      if (pw.input.value) body.password = pw.input.value;
      save.disabled = true;
      try {
        if (editing) await api('/api/entries/' + id, 'PUT', body); else await api('/api/entries', 'POST', body);
        pw.input.value = '';
        m.close(); toast(editing ? 'Changes saved' : 'Entry added');
        await refreshAfterChange();
      } catch (e) { err.textContent = e.message; save.disabled = false; if (e.locked) fail(e); }
    } }, icon('check', 18), editing ? 'Save changes' : 'Add entry');
    m.onClose(() => { pw.input.value = ''; });
    put(m.box,
      h('div', { class: 'modal-head' }, h('h2', {}, editing ? 'Edit entry' : 'New entry'), iconBtn('x', 'Close', () => m.close(), 'flat')),
      h('div', { class: 'two' }, field('Service', service, 'f-service'), field('Category', cat, 'f-cat')),
      field('Username or email', user, 'f-user'),
      h('div', {}, pw.el, meter),
      h('div', { class: 'field' }, h('label', { for: 'f-url' }, 'Website (optional)'), url),
      field('Notes (optional)', notes, 'f-notes'),
      err,
      h('div', { class: 'modal-foot' }, h('button', { class: 'btn', type: 'button', onclick: () => m.close() }, 'Cancel'), save));
    m.box.addEventListener('keydown', (e) => { if (e.key === 'Enter' && e.target.tagName === 'INPUT') { e.preventDefault(); save.click(); } });
  });
}

/* ------------------------------------------------------------------ generator */
VIEWS.generator = async function () {
  const g = S.gen;
  const out = h('div', { class: 'out-pw neu-in', id: 'gen-out', 'aria-live': 'polite' });
  const meter = strengthMeter();
  let current = '';
  const paint = (pw) => {
    current = pw;
    out.replaceChildren(...[...pw].map((c) => /[0-9]/.test(c) ? h('span', { class: 'd' }, c) : /[A-Za-z]/.test(c) ? document.createTextNode(c) : h('span', { class: 's' }, c)));
  };
  const regen = async () => {
    try { const r = await api('/api/generate', 'POST', g); paint(r.password); meter.setStatic(r.strength.score); }
    catch (e) { fail(e); }
  };
  const regenSoon = debounce(regen, 90);
  const len = h('input', { type: 'range', min: 8, max: 64, value: g.length, 'aria-label': 'Password length' });
  const num = h('input', { class: 'input', type: 'number', min: 8, max: 128, value: g.length, 'aria-label': 'Password length (number)' });
  const setLen = (v) => { v = Math.max(8, Math.min(128, parseInt(v, 10) || 20)); g.length = v; len.value = Math.min(v, 64); num.value = v; regenSoon(); };
  len.addEventListener('input', () => setLen(len.value));
  num.addEventListener('change', () => setLen(num.value));
  const opt = (label, key, desc) => h('div', { class: 'opt' }, h('div', {}, h('div', {}, label), desc ? h('div', { class: 's hint' }, desc) : null),
    switchEl(label, g[key], (on, input) => {
      const classes = ['upper', 'lower', 'digits', 'symbols'];
      if (!on && classes.includes(key) && classes.filter((k) => g[k]).length === 1) { input.checked = true; toast('Keep at least one character type selected.', 'err'); return; }
      g[key] = on; regenSoon();
    }));
  const view = h('div', {}, pageHead('Generator', 'Cryptographically secure passwords, made locally'),
    h('div', { class: 'gen' },
      h('div', { class: 'card glass' }, h('h3', {}, 'Options'),
        h('div', { class: 'label' }, 'Length'), h('div', { class: 'len-row' }, len, num),
        opt('Uppercase letters', 'upper', 'A–Z'), opt('Lowercase letters', 'lower', 'a–z'), opt('Numbers', 'digits', '0–9'),
        opt('Symbols', 'symbols', '!@#$%…'), opt('Avoid look-alike characters', 'avoidAmbiguous', 'Skips I, l, 1, O, 0, o')),
      h('div', { class: 'card glass out' }, h('h3', {}, 'Your password'), out, meter,
        h('div', { class: 'out-actions' },
          h('button', { class: 'btn primary', type: 'button', id: 'gen-regen', onclick: regen }, icon('refresh', 18), 'Regenerate'),
          h('button', { class: 'btn', type: 'button', id: 'gen-copy', onclick: () => current && copyText(current, 'Password copied') }, icon('copy', 18), 'Copy'),
          h('button', { class: 'btn', type: 'button', id: 'gen-save', onclick: () => current && openForm(null, { password: current }) }, icon('plus', 18), 'Save as entry')))));
  regen();
  return view;
};

/* ------------------------------------------------------------- security center */
function openRegen() {
  modal((m) => {
    const cur = passwordField({ label: 'Master password', id: 'rg-pw', autofocus: true });
    const err = h('p', { class: 'form-error', role: 'alert' });
    const go = h('button', { class: 'btn primary', type: 'button', id: 'rg-go', onclick: async () => {
      err.textContent = '';
      if (!cur.input.value) { err.textContent = 'Enter your master password.'; return; }
      go.disabled = true;
      try {
        const r = await api('/api/recovery/generate', 'POST', { current: cur.input.value });
        cur.input.value = '';
        m.close();
        await showRecoveryKey(r.recoveryKey);
        await refreshStatus(); await go2();
      } catch (e) { err.textContent = e.message; go.disabled = false; }
    } }, icon('life', 18), 'Create new key');
    const go2 = () => (S.view === 'security' ? window.__refreshSecurity() : Promise.resolve());
    cur.input.addEventListener('keydown', (e) => { if (e.key === 'Enter') go.click(); });
    put(m.box, h('div', { class: 'modal-head' }, h('h2', {}, 'Create a recovery key'), iconBtn('x', 'Close', () => m.close(), 'flat')),
      h('p', { class: 'lead' }, 'This replaces your current recovery key. The old key stops working immediately. Confirm with your master password.'),
      cur.el, err, h('div', { class: 'modal-foot' }, h('button', { class: 'btn', type: 'button', onclick: () => m.close() }, 'Cancel'), go));
  }, { small: true });
}
VIEWS.security = async function () {
  const root = h('div', {});
  async function build() {
    const [d, a, b0] = await Promise.all([api('/api/dashboard'), api('/api/audit'), api('/api/breach')]);
    let b = b0;
    const week = 7 * 86400000;
    const fresh = b.checkedAt > 0 && Date.now() - b.checkedAt < week;
    const checks = [
      { state: d.hasRecovery ? 'ok' : 'todo', title: 'Recovery key active', sub: d.hasRecovery ? 'You can reset a forgotten master password.' : 'Create one so a forgotten master password is not fatal.', act: d.hasRecovery ? null : ['Create', openRegen] },
      { state: d.weak ? 'bad' : 'ok', title: 'No weak passwords', sub: d.weak ? `${d.weak} to strengthen` : 'Everything is strong enough.' },
      { state: d.reused ? 'bad' : 'ok', title: 'No reused passwords', sub: d.reused ? `${d.reused} entries share a password` : 'Every password is unique.' },
      { state: !b.checkedAt ? 'todo' : b.breached.length ? 'bad' : 'ok', title: 'No breached passwords', sub: !b.checkedAt ? 'Run a breach scan below.' : b.breached.length ? `${b.breached.length} exposed in known leaks` : 'Nothing found in known leaks.' },
      { state: fresh ? 'ok' : 'todo', title: 'Breach scan is recent', sub: b.checkedAt ? `Last scan ${timeAgo(b.checkedAt)}` : 'Never scanned' },
      { state: S.status.autoLockMinutes <= 10 ? 'ok' : 'todo', title: 'Auto-lock within 10 minutes', sub: `Currently ${S.status.autoLockMinutes} min` },
      { state: S.status.alertsReady ? 'ok' : 'todo', title: 'Breach alert e-mail configured', sub: S.status.alertsReady ? 'You are e-mailed after repeated wrong passwords.' : 'Add your e-mail and an SMTP server in Settings.', act: S.status.alertsReady ? null : ['Set up', () => go('settings')] },
      { state: 'ok', title: 'Lockout protection on', sub: '5 wrong attempts lock the vault for 1 hour, even across restarts.' },
    ];
    const done = checks.filter((c) => c.state === 'ok').length;
    const bar = h('i'); requestAnimationFrame(() => requestAnimationFrame(() => bar.style.setProperty('--w', (done / checks.length * 100) + '%')));
    const checklist = h('div', { class: 'card glass' }, h('h3', {}, 'Security checklist', h('span', { class: 'tag num ' + (done === checks.length ? '' : 'warn') }, `${done} of ${checks.length}`)), h('div', { class: 'progress' }, bar),
      h('div', { class: 'check-list' }, checks.map((c) => h('div', { class: 'check neu-in ' + c.state }, icon(c.state === 'ok' ? 'check' : 'warn', 20), h('div', { class: 'grow' }, c.title, h('small', {}, c.sub)),
        c.act ? h('button', { class: 'btn sm', type: 'button', onclick: c.act[1] }, c.act[0]) : null))));
    const top = h('div', { class: 'sc-top' }, h('div', { class: 'card glass dial-card' }, scoreDial(d), h('div', {}, h('h3', {}, 'Security score'), h('p', {}, summaryText(d)))), checklist);

    const holder = h('div', { class: 'sec-sec' });
    const paintBreach = () => holder.replaceChildren(breachCard());
    function breachCard() {
      const never = !b.checkedAt, hits = b.breached;
      const state = never ? 'todo' : hits.length ? 'bad' : 'ok';
      const err = h('p', { class: 'form-error', role: 'alert' });
      const card = h('div', { class: 'card glass scan-card', id: 'breach-card' });
      const scan = h('button', { class: 'btn primary', type: 'button', id: 'scan-btn', onclick: async () => {
        err.textContent = ''; scan.disabled = true; card.classList.add('scanning'); scan.lastChild.textContent = 'Scanning…';
        try { b = await api('/api/breach/scan', 'POST', {}); toast(b.breached.length ? `${b.breached.length} exposed password${b.breached.length > 1 ? 's' : ''} found` : 'No exposed passwords found'); await build(); }
        catch (e) { err.textContent = e.message; scan.disabled = false; card.classList.remove('scanning'); scan.lastChild.textContent = 'Scan now'; }
      } }, icon('radar', 18), h('span', {}, never ? 'Run first scan' : 'Scan now'));
      const auto = h('div', { class: 'opt' }, h('span', {}, 'Check automatically every 7 days'), switchEl('Check automatically', b.auto, async (on) => {
        try { Object.assign(S.status, await api('/api/settings', 'PUT', { breachAuto: on })); toast(on ? 'Automatic breach monitoring on' : 'Automatic breach monitoring off'); } catch (e) { fail(e); }
      }));
      put(card, h('h3', {}, 'Breach monitoring'),
        h('div', { class: 'big-status ' + state }, h('div', { class: 'badge' }, icon(state === 'ok' ? 'shield' : state === 'bad' ? 'warn' : 'radar', 26)),
          h('div', {}, h('b', {}, never ? 'Not checked yet' : hits.length ? `${hits.length} password${hits.length > 1 ? 's' : ''} found in known breaches` : 'No exposed passwords found'),
            h('span', {}, never ? 'Compare your passwords with publicly leaked ones.' : ['Last scan ', h('span', { class: 'ts' }, timeAgo(b.checkedAt)), ' · ', h('span', { class: 'ts' }, `${b.scanned} entries checked`)]))),
        hits.length ? h('div', { class: 'list' }, hits.map((e) => h('div', { class: 'row neu-in' }, entryAvatar(e, 'sm'), h('div', { class: 'grow' }, h('div', { class: 't' }, e.service), h('div', { class: 's' }, `Seen ${e.count.toLocaleString()} times in leaks`)),
          h('button', { class: 'btn sm', type: 'button', onclick: () => openForm(e.id) }, 'Change')))) : null,
        err, h('div', { class: 'btn-row' }, scan), auto,
        h('p', { class: 'privacy-note' }, 'Private by design: only the first 5 characters of a password’s SHA-1 hash are sent (k-anonymity). Your passwords and full hashes never leave this device.'));
      return card;
    }
    paintBreach();

    const recovery = h('div', { class: 'card glass sec-sec' }, h('h3', {}, 'Account recovery'),
      h('div', { class: 'big-status ' + (d.hasRecovery ? 'ok' : 'todo') }, h('div', { class: 'badge' }, icon('life', 26)),
        h('div', {}, h('b', {}, d.hasRecovery ? 'Recovery key is active' : 'No recovery key'), h('span', {}, S.status.hasHint ? 'A password hint is also set.' : 'No password hint set. You can add one in Settings.'))),
      h('div', { class: 'btn-row' }, h('button', { class: 'btn', type: 'button', id: 'regen-btn', onclick: openRegen }, icon('refresh', 18), d.hasRecovery ? 'Replace recovery key' : 'Create recovery key'),
        h('button', { class: 'btn ghost', type: 'button', onclick: () => go('settings') }, 'Hint and master password')));

    const row = (e, sub) => h('div', { class: 'row neu-in' }, entryAvatar(e, 'sm'), h('div', { class: 'grow' }, h('div', { class: 't' }, e.service), h('div', { class: 's' }, sub || e.username || '')),
      h('button', { class: 'btn sm', type: 'button', onclick: () => openForm(e.id) }, 'Change'));
    const ok = (t) => h('div', { class: 'ok-box' }, icon('check', 20), t);
    const section = (title, count, body) => h('div', { class: 'card glass sec-sec' }, h('h3', {}, title, h('span', { class: 'tag num ' + (count ? 'warn' : '') }, count ? count + ' to review' : 'All clear')), body);
    root.replaceChildren(pageHead('Security Center', 'Your vault health, breach monitoring and recovery options'), top, holder, recovery,
      section('Weak passwords', a.weak.length, a.weak.length ? h('div', { class: 'list' }, a.weak.map((e) => row(e, `${LABELS[e.strength]} · ${e.username || 'no username'}`))) : ok('No weak passwords found.')),
      section('Reused passwords', a.reusedGroups.reduce((n, g) => n + g.length, 0), a.reusedGroups.length ? a.reusedGroups.map((g) => h('div', { class: 'group neu' }, h('span', { class: 'label' }, `Same password used ${g.length} times`), h('div', { class: 'list' }, g.map((e) => row(e))))) : ok('Every password is unique.')),
      section(`Not changed in ${a.staleDays}+ days`, a.stale.length, a.stale.length ? h('div', { class: 'list' }, a.stale.map((e) => row(e, 'Last changed ' + timeAgo(e.updatedAt)))) : ok('Nothing is overdue for a change.')));
  }
  window.__refreshSecurity = build;
  await build();
  return root;
};

/* ------------------------------------------------------------------- settings */
function resizeImage(file, size) {
  return createImageBitmap(file).then((bmp) => {
    const c = document.createElement('canvas'); c.width = c.height = size;
    const x = c.getContext('2d'), s = Math.min(bmp.width, bmp.height);
    x.fillStyle = '#fff'; x.fillRect(0, 0, size, size);
    x.drawImage(bmp, (bmp.width - s) / 2, (bmp.height - s) / 2, s, s, 0, 0, size, size);
    return c.toDataURL('image/jpeg', 0.88);
  });
}
VIEWS.settings = async function () {
  const [s, al] = await Promise.all([api('/api/settings'), api('/api/alerts')]);
  await loadProfile();
  let p = S.profile;
  const save = async (patch, msg = 'Setting saved') => {
    try { Object.assign(S.status, await api('/api/settings', 'PUT', patch)); document.documentElement.dataset.motion = S.status.motion; toast(msg); S.lockAt = Date.now() + S.status.autoLockMinutes * 60000; } catch (e) { fail(e); }
  };
  const sel = (id, options, value, key, fmt) => h('select', { class: 'input sel', id, 'aria-label': id, onchange: (e) => save({ [key]: isNaN(Number(e.target.value)) ? e.target.value : Number(e.target.value) }) },
    options.map((o) => h('option', { value: o, selected: o === value || null }, fmt(o))));
  const row = (title, desc, control) => h('div', { class: 'set-row' }, h('div', { class: 'grow' }, h('div', {}, title), desc ? h('p', {}, desc) : null), control);

  /* profile + avatar */
  const heroAv = h('div', {}, profileAvatar(88)), heroName = h('h3', {}), heroMail = h('p', {});
  const nameIn = h('input', { class: 'input', id: 'pf-name', maxlength: 60, value: p.name || '', placeholder: 'Your name', autocomplete: 'off' });
  const mailIn = h('input', { class: 'input', id: 'pf-email', type: 'email', maxlength: 254, value: p.email || '', placeholder: 'you@example.com', autocomplete: 'off', spellcheck: 'false' });
  const grid = h('div', { class: 'av-grid', role: 'group', 'aria-label': 'Built-in avatars' });
  const paintProfile = () => {
    p = S.profile;
    heroAv.replaceChildren(profileAvatar(88)); heroName.textContent = p.name || 'Welcome'; heroMail.textContent = p.email || 'Add your name and e-mail below';
    grid.replaceChildren(...window.NVAvatars.ids.map((id) => {
      const b = h('button', { class: 'av-opt' + (p.avatarKind === 'builtin' && p.avatarId === id ? ' on' : ''), type: 'button', 'data-id': id, 'aria-label': `${window.NVAvatars.names[id]} avatar`, title: window.NVAvatars.names[id], onclick: () => setAvatar({ kind: 'builtin', id }) });
      const av = h('span', { class: 'av' }); av.append(window.NVAvatars.svg(id)); b.append(av); return b;
    }));
    repaintProfile();
  };
  async function setAvatar(body) { try { S.profile = await api('/api/profile/avatar', 'POST', body); paintProfile(); toast('Profile picture updated'); } catch (e) { fail(e); } }
  const file = h('input', { type: 'file', accept: 'image/png,image/jpeg,image/webp', class: 'hidden', id: 'pf-file', 'aria-label': 'Upload profile picture' });
  file.addEventListener('change', async () => {
    const f = file.files && file.files[0]; if (!f) return;
    try { if (f.size > 12e6) throw new Error('Please choose an image under 12 MB.'); await setAvatar({ kind: 'custom', dataUrl: await resizeImage(f, 256) }); }
    catch (e) { toast(e.message || 'That image could not be read.', 'err'); }
    file.value = '';
  });
  const saveProfile = h('button', { class: 'btn primary', type: 'button', id: 'pf-save', onclick: async () => {
    try { S.profile = await api('/api/profile', 'PUT', { name: nameIn.value, email: mailIn.value }); await refreshStatus(); paintProfile(); toast('Profile saved'); } catch (e) { fail(e); }
  } }, icon('check', 18), 'Save profile');
  const hero = h('div', { class: 'settings-top card glass' }, heroAv, h('div', { class: 'who' }, heroName, heroMail),
    h('div', { class: 'btn-row' }, h('button', { class: 'btn', type: 'button', id: 'pf-upload', onclick: () => file.click() }, icon('plus', 18), 'Upload photo'),
      h('button', { class: 'btn ghost', type: 'button', id: 'pf-remove', onclick: () => setAvatar({ kind: 'none' }) }, 'Remove'), file));
  paintProfile();

  /* security + hint */
  const hintIn = h('input', { class: 'input', id: 'hint-input', maxlength: 100, placeholder: 'A reminder only you would understand', autocomplete: 'off', spellcheck: 'false' });
  api('/api/hint').then((r) => { hintIn.value = r.hint; }).catch(() => {});
  const saveHint = h('button', { class: 'btn', type: 'button', id: 'hint-save', onclick: async () => {
    try { await api('/api/settings', 'PUT', { masterHint: hintIn.value }); await refreshStatus(); toast(hintIn.value.trim() ? 'Hint saved' : 'Hint removed'); } catch (e) { fail(e); }
  } }, icon('check', 18), 'Save hint');

  /* master password */
  const cur = passwordField({ label: 'Current master password', id: 'cm-cur' });
  const nxt = passwordField({ label: 'New master password', id: 'cm-new', onInput: (v) => meter.set(v) });
  const cnf = passwordField({ label: 'Confirm new master password', id: 'cm-cnf' });
  const meter = strengthMeter();
  const err = h('p', { class: 'form-error', role: 'alert' });
  const change = h('button', { class: 'btn primary', type: 'button', onclick: async () => {
    err.textContent = '';
    if (!cur.input.value || !nxt.input.value) { err.textContent = 'Please fill in all fields.'; return; }
    if (nxt.input.value !== cnf.input.value) { err.textContent = 'The new passwords do not match.'; return; }
    change.disabled = true;
    try {
      await api('/api/master', 'POST', { current: cur.input.value, next: nxt.input.value });
      [cur, nxt, cnf].forEach((f) => { f.input.value = ''; }); meter.set('');
      toast('Master password changed. Your recovery key still works.');
    } catch (e) { err.textContent = e.message; } finally { change.disabled = false; }
  } }, icon('key', 18), 'Change master password');

  /* alert e-mail */
  const f = (id, label, val, extra = {}) => h('div', { class: 'field' }, h('label', { for: id }, label), h('input', { class: 'input', id, value: val ?? '', autocomplete: 'off', spellcheck: 'false', ...extra }));
  const host = f('al-host', 'SMTP server', al.host, { placeholder: 'smtp.gmail.com' });
  const port = f('al-port', 'Port', String(al.port), { inputmode: 'numeric' });
  const secSel = h('select', { class: 'input', id: 'al-sec' }, [['STARTTLS', 'STARTTLS (port 587)'], ['SSL', 'SSL/TLS (port 465)'], ['NONE', 'None (localhost only)']].map(([v, l]) => h('option', { value: v, selected: v === al.security || null }, l)));
  const user = f('al-user', 'SMTP user name', al.user, { placeholder: 'you@example.com' });
  const apw = passwordField({ label: 'SMTP password', id: 'al-pass', placeholder: al.passSet ? 'Saved. Leave blank to keep it' : 'App password' });
  const from = f('al-from', 'From address (optional)', al.from, { placeholder: 'defaults to your user name' });
  const alStatus = h('div', { class: 'status-line' });
  const paintAlert = (c) => {
    const la = c.lastAlert;
    alStatus.className = 'status-line ' + (la ? (la.status === 'sent' ? 'ok' : la.status === 'failed' ? 'bad' : '') : '');
    alStatus.replaceChildren(icon(la && la.status === 'sent' ? 'check' : 'warn', 16), la ? [`Last alert: ${la.status} `, h('span', { class: 'ts', title: fmtDate(la.ts) }, timeAgo(la.ts)), `. ${la.detail}`] : 'No alert e-mail has been sent yet.');
  };
  paintAlert(al);
  const alErr = h('p', { class: 'form-error', role: 'alert' });
  const alertBody = () => { const b = { host: host.querySelector('input').value, port: port.querySelector('input').value, security: secSel.value, user: user.querySelector('input').value, from: from.querySelector('input').value }; if (apw.input.value) b.password = apw.input.value; return b; };
  const saveAlerts = h('button', { class: 'btn primary', type: 'button', id: 'al-save', onclick: async () => {
    alErr.textContent = '';
    try { const c = await api('/api/alerts', 'PUT', alertBody()); apw.input.value = ''; apw.input.placeholder = c.passSet ? 'Saved. Leave blank to keep it' : 'App password'; paintAlert(c); await refreshStatus(); toast('E-mail alert settings saved'); } catch (e) { alErr.textContent = e.message; }
  } }, icon('check', 18), 'Save');
  const testAlerts = h('button', { class: 'btn', type: 'button', id: 'al-test', onclick: async () => {
    alErr.textContent = ''; testAlerts.disabled = true;
    try { await api('/api/alerts', 'PUT', alertBody()); apw.input.value = ''; const c = await api('/api/alerts/test', 'POST', {}); paintAlert(c); toast('Test e-mail sent to ' + c.recipient); }
    catch (e) { alErr.textContent = e.message; api('/api/alerts').then(paintAlert).catch(() => {}); } finally { testAlerts.disabled = false; }
  } }, icon('link', 18), 'Send test e-mail');

  return h('div', {}, pageHead('Settings', 'Your profile, appearance, security and privacy'), hero,
    h('div', { class: 'settings-cols' },
      h('div', { class: 'card glass' }, h('h3', {}, 'Profile details'), h('div', { class: 'field' }, h('label', { for: 'pf-name' }, 'Name'), nameIn), h('div', { class: 'field' }, h('label', { for: 'pf-email' }, 'E-mail (receives security alerts)'), mailIn),
        h('div', { class: 'label' }, 'Choose an avatar'), grid, saveProfile),
      h('div', { class: 'card glass' }, h('h3', {}, 'Appearance & motion'),
        row('Theme', 'Black and purple, or charcoal and grey', h('div', { class: 'seg', id: 'theme-seg', role: 'group', 'aria-label': 'Theme' }, [['dark', 'moon', 'Dark'], ['light', 'sun', 'Light']].map(([v, ic, l]) =>
          h('button', { type: 'button', 'data-v': v, class: S.status.theme === v ? 'on' : '', onclick: () => { if (S.status.theme !== v) toggleTheme(); } }, icon(ic, 16), l)))),
        row('Animations', 'Cinematic vault door and intro', sel('set-motion', ['full', 'reduced', 'off'], s.motion, 'motion', (m) => ({ full: 'Cinematic', reduced: 'Reduced', off: 'Off' }[m]))),
        row('Sound effects', 'Subtle sounds for locking and unlocking', switchEl('Sound effects', s.sound, (on) => save({ sound: on }, on ? 'Sound on' : 'Sound off')))),
      h('div', { class: 'card glass' }, h('h3', {}, 'Vault security'),
        row('Auto-lock after inactivity', 'Wipes the key from memory', sel('set-lock', [1, 2, 5, 10, 15, 30, 60], s.autoLockMinutes, 'autoLockMinutes', (n) => n + (n === 1 ? ' minute' : ' minutes'))),
        row('Clear clipboard after', 'Copied passwords are removed', sel('set-clip', [10, 20, 30, 60, 120], s.clipboardSeconds, 'clipboardSeconds', (n) => n + ' seconds')),
        h('div', { class: 'info-box neu-in' }, h('b', {}, 'Lockout policy: '), 'after 4 wrong master-password attempts an alert e-mail is sent; after 5 in a row the vault locks for 1 hour (with a live countdown), even if you restart the app.'),
        h('div', { class: 'field' }, h('label', { for: 'hint-input' }, 'Master password hint'), hintIn), saveHint),
      h('div', { class: 'card glass' }, h('h3', {}, 'Change master password'), cur.el, nxt.el, meter, h('div', { class: 'field' }, cnf.el), err, change),
      h('div', { class: 'card glass' }, h('h3', {}, 'Security alert e-mail'),
        h('p', { class: 'hint' }, 'Sent to your profile e-mail. Never contains passwords or vault data. Use an app password (Gmail, Outlook) rather than your real one.'),
        host, h('div', { class: 'two' }, port, h('div', { class: 'field' }, h('label', { for: 'al-sec' }, 'Security'), secSel)), user, apw.el, from, alStatus, alErr, h('div', { class: 'btn-row' }, saveAlerts, testAlerts),
        h('p', { class: 'privacy-note' }, 'The SMTP password is protected by your Windows account (DPAPI) and only ever sent to your mail server over TLS.')),
      h('div', { class: 'card glass' }, h('h3', {}, 'Privacy & monitoring'),
        row('Monitor for breaches automatically', 'Weekly check while unlocked. Only a 5-character hash prefix leaves this device.', switchEl('Monitor for breaches automatically', s.breachAuto, (on) => save({ breachAuto: on }, on ? 'Automatic breach monitoring on' : 'Automatic breach monitoring off'))),
        row('Show website logos', 'Fetched privately by NexVault, never by your browser, and cached locally.', switchEl('Show website logos', s.logosEnabled, (on) => { logoCache.clear(); save({ logosEnabled: on }, on ? 'Website logos on' : 'Website logos off'); })),
        row('Logo cache', 'Remove all stored website icons', h('button', { class: 'btn sm', type: 'button', id: 'logo-clear', onclick: async () => { try { const r = await api('/api/logos/clear', 'POST', {}); logoCache.clear(); toast(`Logo cache cleared (${r.cleared} files)`); } catch (e) { fail(e); } } }, icon('trash', 16), 'Clear')))));
};

/* ---------------------------------------------------------------- FAQ & info centre */
const FAQ = [
  { cat: 'Basics', q: 'How do I add a login?', a: [['p', 'Press Alt+N or click Add entry in the top bar. Enter the service name and a password (or use the refresh button to generate one), then save. Only the service name and password are required.']] },
  { cat: 'Basics', q: 'Which keyboard shortcuts are available?', a: [['ul', ['Alt+N: add an entry (when no dialog is open).', 'Alt+L: lock the vault immediately.', 'Esc: close the open dialog.', 'Enter: submit the form you are typing in.', 'Click or press any key: skip the lock and unlock animations.']]] },
  { cat: 'Basics', q: 'How do I copy a password without showing it?', a: [['p', 'Hover a card in the Vault and use the Password button, or open the entry and use the copy icon. NexVault clears the clipboard automatically (20 seconds by default; change it in Settings > Vault security).']] },
  { cat: 'Basics', q: 'Why does the Unlock button run away from my mouse?', a: [['p', 'It is a small joke on the master-password screen: while the password box is empty, the button dodges your pointer. As soon as you type your master password it settles down, glows briefly and can be clicked. Pressing Enter always works.'], ['p', 'Touch screens, keyboard navigation and the Reduced or Off animation settings (Settings > Appearance & motion) get an ordinary, fixed button.']] },
  { cat: 'Security', q: 'How is my data protected?', a: [['p', 'Every entry is encrypted with AES-256-GCM before it is written to disk. A random data key is wrapped by your master password (PBKDF2WithHmacSHA256, 200,000 iterations, unique salt) and by your recovery key. Your master password is never stored.']] },
  { cat: 'Security', q: 'What happens if someone guesses my master password?', a: [['ul', ['After more than 3 wrong attempts in a row NexVault e-mails you a security alert (if you set up alerts).', 'After 5 in a row the vault locks for 1 hour with a live countdown.', 'The counter and the lock are stored in the database, so restarting the app does not bypass them.']]] },
  { cat: 'Security', q: 'When does the vault lock itself?', a: [['p', 'After the inactivity time you choose (5 minutes by default), when you press Alt+L or use Lock vault, and if the app window stops talking to NexVault for 2 minutes. Locking wipes the key and decrypted metadata from memory and clears any password NexVault put on the clipboard.']] },
  { cat: 'Security', q: 'How does the breach check keep my passwords private?', a: [['p', 'NexVault uses k-anonymity with the Pwned Passwords service: only the first 5 characters of a password’s SHA-1 hash are sent. Your passwords and full hashes never leave this device.']] },
  { cat: 'Recovery', q: 'I forgot my master password. What now?', a: [['ol', ['On the unlock screen choose “Forgot master password?”.', 'Use your recovery key (shown once when you created the vault) to set a new master password. Your data stays.', 'No recovery key? Show your hint, or as a last resort erase the vault and start over.']], ['p', 'If you lose both the master password and the recovery key the data cannot be recovered. There is no backdoor.']] },
  { cat: 'Recovery', q: 'How do I back up my vault?', a: [['p', 'Close NexVault, then copy data\\nexvault.db to a safe place. The file only contains ciphertext. Keep your recovery key somewhere different from the backup.']] },
  { cat: 'Recovery', q: 'How do I replace my recovery key?', a: [['p', 'Open Security Center > Account recovery > Replace recovery key and confirm with your master password. The old key stops working immediately.']] },
  { cat: 'Privacy', q: 'What leaves my computer?', a: [['ul', ['The 5-character hash prefix used by the breach check.', 'Website logo requests, fetched by NexVault (turn off in Settings > Privacy & monitoring).', 'Security alert e-mails, sent through the SMTP account you configure.']], ['p', 'Everything else stays on this machine. The app listens on 127.0.0.1 only.']] },
  { cat: 'Privacy', q: 'Do alert e-mails contain my passwords?', a: [['p', 'Never. They contain counters, the time, the computer name and recommended steps, and nothing from your vault.']] },
  { cat: 'Setup', q: 'How do I set up security alert e-mails?', a: [['ol', ['Add your name and e-mail in Settings > Profile details and save.', 'Under Security alert e-mail enter your SMTP server, port, security mode and an app password (not your normal one).', 'Click Save, then Send test e-mail to confirm delivery.']]] },
  { cat: 'Setup', q: 'How do I switch between dark and light?', a: [['p', 'Use the theme button in the sidebar or Settings > Appearance & motion. NexVault always starts in the light theme; the dark choice lasts for the session.']] },
  { cat: 'Setup', q: 'The fonts look different from the screenshots.', a: [['p', 'Urbanist and Space Grotesk are downloaded the first time you run run-vault.bat, so an offline first run uses a system font. The logo font Godber is not bundled: place Godber.woff2 (or .otf / .ttf) in src\\main\\resources\\web\\fonts and restart. Until then the logo uses a serif fallback.']] },
  { cat: 'Setup', q: 'The app says it is already running.', a: [['p', 'Only one NexVault can use a data folder at a time. Switch to the open window, or close it first.']] },
];
const FAQ_CATS = ['All', ...new Set(FAQ.map((f) => f.cat))];
function faqBody(parts) {
  return h('div', { class: 'ans' }, parts.map(([t, v]) => t === 'p' ? h('p', {}, v) : h(t, {}, v.map((x) => h('li', {}, x)))));
}
const combo = (...keys) => h('span', { class: 'kbd-combo' }, keys.flatMap((k, i) => (i ? [h('i', {}, '+')] : []).concat([h('kbd', {}, k)])));
VIEWS.info = async function () {
  const tab = S.infoTab || 'faq';
  const root = h('div', {}, pageHead('FAQ & Info', 'Answers, shortcuts and how NexVault keeps your data safe'));
  const seg = h('div', { class: 'seg', role: 'group', 'aria-label': 'Info sections', id: 'info-seg' }, [['faq', 'help', 'FAQ'], ['info', 'layers', 'Info centre']].map(([v, ic, l]) =>
    h('button', { type: 'button', 'data-v': v, class: tab === v ? 'on' : '', 'aria-pressed': tab === v ? 'true' : 'false', onclick: () => { S.infoTab = v; go('info', { soft: true }); } }, icon(ic, 16), l)));
  const body = h('div', { id: 'info-body' });
  root.append(h('div', { class: 'info-bar' }, seg), body);
  if (tab === 'faq') {
    let cat = S.faqCat || 'All', q = '';
    const list = h('div', { class: 'faq-list', id: 'faq-list' });
    const chips = h('div', { class: 'chips' });
    const search = h('input', { class: 'input', id: 'faq-search', type: 'search', placeholder: 'Search the FAQ', autocomplete: 'off', spellcheck: 'false', 'aria-label': 'Search the FAQ' });
    const paint = () => {
      chips.replaceChildren(...FAQ_CATS.map((c) => h('button', { class: 'chip' + (cat === c ? ' on' : ''), type: 'button', 'aria-pressed': cat === c ? 'true' : 'false', onclick: () => { cat = S.faqCat = c; paint(); } }, c)));
      const needle = q.trim().toLowerCase();
      const hits = FAQ.filter((f) => (cat === 'All' || f.cat === cat) && (!needle || (f.q + ' ' + f.a.map(([, v]) => (Array.isArray(v) ? v.join(' ') : v)).join(' ')).toLowerCase().includes(needle)));
      list.replaceChildren(...(hits.length ? hits.map((f) => h('details', { class: 'faq-item neu' }, h('summary', {}, f.q), faqBody(f.a)))
        : [h('div', { class: 'faq-none' }, needle ? `Nothing matches “${q.trim()}”.` : 'No questions in this category.')]));
    };
    search.addEventListener('input', debounce(() => { q = search.value; paint(); }, 90));
    body.append(h('div', { class: 'info-bar' }, h('div', { class: 'search' }, icon('search', 18), search)), h('div', { class: 'toolbar' }, chips), list);
    paint();
  } else {
    body.append(h('div', { class: 'info-grid' },
      h('div', { class: 'card glass' }, h('h3', {}, 'Quick start'),
        h('ol', { class: 'step-list' }, [
          h('li', {}, h('span', {}, h('b', {}, 'Create your vault. '), 'Choose a long master password and save the recovery key you are shown once.')),
          h('li', {}, h('span', {}, h('b', {}, 'Add your logins. '), 'Press Alt+N, or generate a strong password while you add an entry.')),
          h('li', {}, h('span', {}, h('b', {}, 'Check your health. '), 'Open Security Center to run a breach scan and fix weak or reused passwords.')),
          h('li', {}, h('span', {}, h('b', {}, 'Set up alerts. '), 'Add your e-mail and SMTP details in Settings so repeated wrong attempts reach you.')),
          h('li', {}, h('span', {}, h('b', {}, 'Lock when you leave. '), 'Press Alt+L. NexVault also locks itself after inactivity.'))])),
      h('div', { class: 'card glass' }, h('h3', {}, 'Keyboard shortcuts'),
        h('div', { class: 'kbd-row' }, h('span', {}, 'Add entry'), combo('Alt', 'N')),
        h('div', { class: 'kbd-row' }, h('span', {}, 'Lock vault'), combo('Alt', 'L')),
        h('div', { class: 'kbd-row' }, h('span', {}, 'Close dialog'), combo('Esc')),
        h('div', { class: 'kbd-row' }, h('span', {}, 'Skip lock / unlock animation'), h('span', { class: 'kbd-combo' }, h('kbd', {}, 'Any key'))),
        h('p', { class: 'privacy-note' }, 'Shortcuts work while the vault is unlocked and no animation is playing.')),
      h('div', { class: 'card glass' }, h('h3', {}, 'How your data is protected'),
        h('ul', { class: 'facts' },
          h('li', {}, 'AES-256-GCM encrypts every entry; each item has its own random IV.'),
          h('li', {}, 'Your master password never touches the disk. It unlocks a data key through PBKDF2 (200,000 iterations, unique salt).'),
          h('li', {}, 'The recovery key wraps the same data key, so changing your master password keeps it valid.'),
          h('li', {}, 'The vault is one local SQLite file. Locking wipes the key and decrypted metadata from memory.'),
          h('li', {}, '5 wrong attempts lock the vault for 1 hour, even across restarts.'))),
      h('div', { class: 'card glass' }, h('h3', {}, 'Where your data lives'),
        h('p', {}, 'Your vault is stored in data\\nexvault.db next to the launcher. Copy that file (while NexVault is closed) to back it up, and keep your recovery key somewhere else.'),
        h('p', { class: 'privacy-note' }, 'Use run-vault.bat --data D:\\Vaults\\mine to keep the vault in another folder.')),
      h('div', { class: 'card glass' }, h('h3', {}, 'Good to know'),
        h('ul', { class: 'facts' },
          h('li', {}, 'There is no backdoor: lose both the master password and the recovery key and the data is unrecoverable.'),
          h('li', {}, 'Windows Clipboard History (Win+V) can keep a copy of anything you copy; turn it off if that matters to you.'),
          h('li', {}, 'The browser-based window cannot zero what you type; lock the vault when you step away.'),
          h('li', {}, 'The password hint is stored unencrypted so it can be shown before unlocking. Never put the password in it.')))));
  }
  return root;
};

/* ------------------------------------------------------------ keyboard shortcuts
   Alt+N adds an entry, Alt+L locks the vault. Matched on the physical key (e.code) so they work on every keyboard layout
   (on macOS, Option+letter types a symbol, which is why e.key is not used). Only active inside the unlocked app. */
function onShortcut(e) {
  if (e.repeat || !e.altKey || e.ctrlKey || e.metaKey || e.shiftKey) return;
  if (!S.status || !S.status.unlocked || S.fx || !$('#shell')) return;
  if (e.code === 'KeyN') { e.preventDefault(); if (!modals.length) openForm(); }
  else if (e.code === 'KeyL') { e.preventDefault(); lockNow(); }
}

/* ---------------------------------------------------- lifecycle: timers & input */
let hb = null, tick = null;
function stopTimers() { clearInterval(hb); clearInterval(tick); hb = tick = null; }
function startTimers() {
  hb = setInterval(async () => {
    const was = S.status && S.status.unlocked;
    try {
      await refreshStatus();
      if (S.offline) { S.offline = false; const b = $('.banner'); if (b) b.remove(); }
      if (was && !S.status.unlocked && !S.fx) { await vaultFx('close', render); toast('Vault locked after inactivity', 'err'); }
    } catch (e) {
      if (!S.offline) { S.offline = true; document.body.append(h('div', { class: 'banner' }, 'Lost connection to NexVault. If you quit the app, close this window.')); }
    }
  }, 5000);
  tick = setInterval(() => {
    const chip = $('#lockchip');
    if (!chip || !S.status || !S.status.unlocked) return;
    const left = Math.max(0, Math.ceil((S.lockAt - Date.now()) / 1000));
    if (left <= 60 && left > 0) { chip.classList.remove('hidden'); chip.lastChild.textContent = `Locking in ${Math.floor(left / 60)}:${String(left % 60).padStart(2, '0')}`; }
    else chip.classList.add('hidden');
  }, 1000);
}
function onActivity() {
  const now = Date.now();
  if (!S.status || !S.status.unlocked || now - lastPing < 8000) return;
  lastPing = now;
  api('/api/ping', 'POST', {}).then((r) => { S.lockAt = Date.now() + r.secondsUntilLock * 1000; }).catch(() => {});
}

/* ------------------------------------------------------------------------ boot */
async function boot() {
  const m = location.hash.match(/t=([\w-]+)/);
  try {
    if (m) { TOKEN = m[1]; sessionStorage.setItem('nv.t', TOKEN); history.replaceState(null, '', location.pathname); }
    else TOKEN = sessionStorage.getItem('nv.t');
  } catch { if (m) TOKEN = m[1]; }
  const app = $('#app');
  const msg = (title, text) => app.replaceChildren(h('div', { class: 'auth' }, h('div', { class: 'auth-card glass' }, brand(), h('h2', {}, title), h('p', { class: 'lead' }, text))));
  if (!TOKEN) { msg('Launch NexVault from its launcher', 'This page needs a one-time session key. Start the app with run-vault.bat and it will open the right window for you.'); return; }
  try { await refreshStatus(); } catch (e) { msg('Cannot connect', e.message + ' Restart NexVault with run-vault.bat.'); return; }
  await loadProfile();
  app.replaceChildren();
  await FX.fonts();
  await splash();
  render();
  startTimers();
  document.addEventListener('keydown', onShortcut);
  ['mousemove', 'mousedown', 'keydown', 'scroll', 'touchstart', 'wheel'].forEach((ev) => window.addEventListener(ev, onActivity, { passive: true, capture: true }));
  document.addEventListener('visibilitychange', () => { if (!document.hidden && !S.fx) refreshStatus().then(() => { if (!S.status.unlocked && $('.shell')) vaultFx('close', render); }).catch(() => {}); });
}
function startParallax() {
  let raf = 0;
  window.addEventListener('pointermove', (ev) => {
    if (raf) return;
    raf = requestAnimationFrame(() => {
      raf = 0;
      if (reduced()) return;
      const x = ev.clientX / innerWidth - 0.5, y = ev.clientY / innerHeight - 0.5;
      const r = document.documentElement.style;
      r.setProperty('--px', x.toFixed(3)); r.setProperty('--py', y.toFixed(3));
      const card = $('.auth-card');
      if (card) { card.style.setProperty('--ry', (x * 7).toFixed(2) + 'deg'); card.style.setProperty('--rx', (-y * 6).toFixed(2) + 'deg'); }
    });
  }, { passive: true });
}
boot();
startParallax();
})();
