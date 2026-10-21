/* Cinematic effects: animated startup logo and the vault door (3D, lighting, sparks, synthesized sound). */
window.NVFX = function (ctx) {
  const { h, icon, ICONS, NS, S, sleep } = ctx;
  const mode = () => (S.status && S.status.motion) || 'full';
  const soundOn = () => !S.status || S.status.sound !== false;
  const running = [];

  function anim(el, kf, opt = {}) {
    if (!el || !el.animate) return Promise.resolve();
    try { const a = el.animate(kf, { fill: 'both', ...opt }); running.push(a); return a.finished.catch(() => {}); } catch { return Promise.resolve(); }
  }
  const skipAll = () => running.forEach((a) => { try { a.finish(); } catch { /* ignore */ } });

  /* ---- synthesized sound (no assets): metallic bolts, clunk, ratchet, whoosh, chime */
  let ac = null;
  function audio() {
    if (!soundOn() || mode() === 'off') return null;
    if (ac === null) { try { ac = new (window.AudioContext || window.webkitAudioContext)(); } catch { ac = false; } }
    if (ac && ac.state === 'suspended') ac.resume().catch(() => {});
    return ac && ac.state !== 'closed' ? ac : null;
  }
  function noiseBuf(a, dur) { const b = a.createBuffer(1, Math.max(1, Math.floor(a.sampleRate * dur)), a.sampleRate), d = b.getChannelData(0); for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1; return b; }
  function env(a, g, t, peak, dur) { g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(peak, t + 0.005); g.gain.exponentialRampToValueAtTime(0.0001, t + dur); }
  const snd = {
    clunk(a, t) { const o = a.createOscillator(), g = a.createGain(); o.type = 'sine'; o.frequency.setValueAtTime(95, t); o.frequency.exponentialRampToValueAtTime(48, t + 0.25); env(a, g, t, 0.55, 0.4); o.connect(g).connect(a.destination); o.start(t); o.stop(t + 0.45);
      const n = a.createBufferSource(), f = a.createBiquadFilter(), g2 = a.createGain(); n.buffer = noiseBuf(a, 0.2); f.type = 'lowpass'; f.frequency.value = 520; env(a, g2, t, 0.35, 0.18); n.connect(f).connect(g2).connect(a.destination); n.start(t); },
    bolt(a, t) { const o = a.createOscillator(), g = a.createGain(), f = a.createBiquadFilter(); o.type = 'triangle'; o.frequency.setValueAtTime(1500, t); o.frequency.exponentialRampToValueAtTime(380, t + 0.09); f.type = 'bandpass'; f.frequency.value = 1100; env(a, g, t, 0.09, 0.12); o.connect(f).connect(g).connect(a.destination); o.start(t); o.stop(t + 0.14); },
    ratchet(a, t, n, gap) { for (let i = 0; i < n; i++) { const s = a.createBufferSource(), f = a.createBiquadFilter(), g = a.createGain(), tt = t + i * gap; s.buffer = noiseBuf(a, 0.02); f.type = 'highpass'; f.frequency.value = 2600; env(a, g, tt, 0.06, 0.02); s.connect(f).connect(g).connect(a.destination); s.start(tt); } },
    whoosh(a, t, dur) { const s = a.createBufferSource(), f = a.createBiquadFilter(), g = a.createGain(); s.buffer = noiseBuf(a, dur); f.type = 'bandpass'; f.Q.value = 0.9; f.frequency.setValueAtTime(260, t); f.frequency.exponentialRampToValueAtTime(2600, t + dur * 0.8);
      g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(0.16, t + dur * 0.4); g.gain.exponentialRampToValueAtTime(0.0001, t + dur); s.connect(f).connect(g).connect(a.destination); s.start(t); },
    chime(a, t, freqs) { freqs.forEach((fr, i) => { const o = a.createOscillator(), g = a.createGain(); o.type = 'sine'; o.frequency.value = fr; env(a, g, t + i * 0.11, 0.085, 0.9); o.connect(g).connect(a.destination); o.start(t + i * 0.11); o.stop(t + i * 0.11 + 1); }); },
  };
  function playUnlock() { const a = audio(); if (!a) return; const t = a.currentTime + 0.02; snd.ratchet(a, t, 14, 0.045); for (let i = 0; i < 8; i++) snd.bolt(a, t + 0.45 + i * 0.03); snd.clunk(a, t + 0.78); snd.whoosh(a, t + 0.82, 0.7); snd.chime(a, t + 1.3, [659, 988]); }
  function playLock() { const a = audio(); if (!a) return; const t = a.currentTime + 0.02; snd.whoosh(a, t + 0.05, 0.6); snd.clunk(a, t + 0.7); for (let i = 0; i < 8; i++) snd.bolt(a, t + 0.78 + i * 0.03); snd.ratchet(a, t + 0.95, 10, 0.05); snd.chime(a, t + 1.4, [494, 330]); }

  /* ---- sparks */
  function sparks(canvas, getColor) {
    const c = canvas.getContext('2d'); let W = 0, H = 0;
    const fit = () => { const r = devicePixelRatio || 1; W = canvas.width = innerWidth * r; H = canvas.height = innerHeight * r; c.setTransform(r, 0, 0, r, 0, 0); };
    fit();
    const ps = []; let alive = true, last = performance.now();
    function burst(x, y, n, speed, life) { for (let i = 0; i < n; i++) { const a = Math.random() * Math.PI * 2, v = speed * (0.35 + Math.random()); ps.push({ x, y, vx: Math.cos(a) * v, vy: Math.sin(a) * v - speed * 0.15, life: life * (0.6 + Math.random() * 0.8), age: 0, r: 1 + Math.random() * 2.2 }); } }
    function tick(now) {
      if (!alive) return;
      const dt = Math.min(0.04, (now - last) / 1000); last = now;
      c.clearRect(0, 0, innerWidth, innerHeight); c.globalCompositeOperation = 'lighter';
      const col = getColor();
      for (let i = ps.length - 1; i >= 0; i--) { const p = ps[i]; p.age += dt; if (p.age > p.life) { ps.splice(i, 1); continue; } p.vy += 380 * dt; p.vx *= 0.985; p.x += p.vx * dt; p.y += p.vy * dt;
        const k = 1 - p.age / p.life; c.fillStyle = col; c.globalAlpha = Math.max(0, k); c.beginPath(); c.arc(p.x, p.y, p.r * (0.5 + k), 0, 6.283); c.fill(); }
      c.globalAlpha = 1; requestAnimationFrame(tick);
    }
    requestAnimationFrame(tick);
    return { burst, stop() { alive = false; } };
  }

  /* ---- fonts: request every face up front so the UI never flashes a fallback after the splash */
  function fonts() {
    const wanted = ['400 16px "Urbanist"', '500 16px "Urbanist"', '600 16px "Urbanist"', '500 16px "Space Grotesk"', '600 16px "Space Grotesk"', '400 48px "Godber"'];
    if (!document.fonts || !document.fonts.load) return Promise.resolve();
    const all = Promise.allSettled(wanted.map((f) => document.fonts.load(f, 'NexVault 0123456789')));
    return Promise.race([all, sleep(1400)]);   // a missing file simply falls back; never block start-up
  }

  /* ---- startup particle field: floating, glowing dark-grey motes on three depth layers; pointer = parallax + gentle repulsion + glow */
  function particleField(root, canvas, { count, interactive, dark }) {
    const c = canvas.getContext('2d');
    let W = 0, H = 0, alive = true;
    const fit = () => { const r = Math.min(2, devicePixelRatio || 1); W = innerWidth; H = innerHeight; canvas.width = Math.round(W * r); canvas.height = Math.round(H * r); c.setTransform(r, 0, 0, r, 0, 0); };
    fit();
    const core = dark ? 'rgba(122,126,136,.9)' : 'rgba(40,43,50,.92)', halo = dark ? 'rgba(170,175,186,.15)' : 'rgba(62,68,80,.2)';
    const sprite = document.createElement('canvas'); sprite.width = sprite.height = 96;
    { const g = sprite.getContext('2d'), gr = g.createRadialGradient(48, 48, 0, 48, 48, 48);
      gr.addColorStop(0, core); gr.addColorStop(.2, core); gr.addColorStop(.4, halo); gr.addColorStop(1, 'rgba(0,0,0,0)'); g.fillStyle = gr; g.fillRect(0, 0, 96, 96); }
    const ps = Array.from({ length: count }, () => { const z = Math.pow(Math.random(), 1.6) * 0.85 + 0.15;   // few near, many far
      return { x: Math.random() * W, y: Math.random() * H, z, vx: (Math.random() - .5) * 14, vy: -(4 + Math.random() * 12), ox: 0, oy: 0, ovx: 0, ovy: 0, ph: Math.random() * 6.283, tw: .5 + Math.random() * 1.2, r: 1.1 + z * 3.4 }; });
    const ptr = { x: W / 2, y: H / 2, nx: 0, ny: 0, sx: 0, sy: 0, cx: W / 2, cy: H / 2, seen: false };
    const onMove = (e) => { ptr.x = e.clientX; ptr.y = e.clientY; ptr.nx = ptr.x / W - .5; ptr.ny = ptr.y / H - .5; ptr.seen = true; root.classList.add('live'); };
    root.addEventListener('pointermove', onMove, { passive: true });
    const onResize = () => fit(); window.addEventListener('resize', onResize);
    let last = performance.now(), t = 0;
    function frame(now) {
      if (!alive || !root.isConnected) { stop(); return; }
      const dt = Math.min(0.04, (now - last) / 1000); last = now; t += dt;
      if (!document.hidden) {
        // smoothed pointer for parallax, cursor light and CSS layers
        ptr.sx += (ptr.nx - ptr.sx) * Math.min(1, dt * 3.2); ptr.sy += (ptr.ny - ptr.sy) * Math.min(1, dt * 3.2);
        ptr.cx += (ptr.x - ptr.cx) * Math.min(1, dt * 5); ptr.cy += (ptr.y - ptr.cy) * Math.min(1, dt * 5);
        root.style.setProperty('--sx', ptr.sx.toFixed(3)); root.style.setProperty('--sy', ptr.sy.toFixed(3));
        root.style.setProperty('--cx', ptr.cx.toFixed(1) + 'px'); root.style.setProperty('--cy', ptr.cy.toFixed(1) + 'px');
        c.clearRect(0, 0, W, H); c.globalCompositeOperation = dark ? 'lighter' : 'source-over';
        for (const p of ps) {
          p.x += (p.vx * (.3 + p.z) + Math.sin(t * .35 + p.ph) * 7 * p.z) * dt; p.y += p.vy * (.3 + p.z) * dt;
          if (p.y < -30) { p.y = H + 30; p.x = Math.random() * W; } if (p.x < -30) p.x = W + 30; else if (p.x > W + 30) p.x = -30;
          let sx = p.x - ptr.sx * 70 * p.z + p.ox, sy = p.y - ptr.sy * 46 * p.z + p.oy, glow = 1;
          if (interactive && ptr.seen) {
            const dx = sx - ptr.x, dy = sy - ptr.y, d = Math.hypot(dx, dy) || 1, R = 150 + 90 * p.z;
            if (d < R) { const f = Math.pow(1 - d / R, 2) * 1400; p.ovx += dx / d * f * dt; p.ovy += dy / d * f * dt; }
            if (d < 260) glow = 1 + 1.1 * (1 - d / 260);
          }
          p.ovx += (-p.ox * 2.4 - p.ovx * 2.9) * dt; p.ovy += (-p.oy * 2.4 - p.ovy * 2.9) * dt; p.ox += p.ovx * dt; p.oy += p.ovy * dt;
          const tw = .55 + .45 * Math.sin(t * p.tw + p.ph), a = Math.min(1, (.22 + .78 * p.z) * tw * glow), size = p.r * 7 * (glow > 1 ? 1 + (glow - 1) * .25 : 1);
          c.globalAlpha = a; c.drawImage(sprite, sx - size / 2, sy - size / 2, size, size);
        }
        c.globalAlpha = 1;
      }
      requestAnimationFrame(frame);
    }
    function stop() { alive = false; window.removeEventListener('resize', onResize); }
    requestAnimationFrame(frame);
    return { stop };
  }

  /* ---- startup logo */
  async function splash() {
    const m = mode();
    if (m === 'off') return;
    await fonts();
    const dark = document.documentElement.dataset.theme === 'dark';
    const svg = document.createElementNS(NS, 'svg');
    svg.setAttribute('viewBox', '0 0 120 120'); svg.setAttribute('class', 'logo-anim');
    svg.innerHTML = '<defs><linearGradient id="lgrad" x1="0" y1="0" x2="1" y2="1"><stop class="g1" offset="0"/><stop class="g2" offset="1"/></linearGradient><clipPath id="lclip"><rect x="16" y="16" width="88" height="88" rx="28"/></clipPath></defs>'
      + '<circle class="ring" cx="60" cy="60" r="44"/><circle class="ring r2" cx="60" cy="60" r="44"/><rect class="lmark" x="16" y="16" width="88" height="88" rx="28"/>'
      + '<g class="lglyph" transform="translate(30 30) scale(2.5)">' + ICONS.shield.replaceAll('<path ', '<path pathLength="1" ') + '</g><g clip-path="url(#lclip)"><rect class="lshine" x="-60" y="0" width="22" height="130"/></g>';
    const word = h('div', { class: 'splash-word' }, [...'NexVault'].map((c, i) => { const s = h('span', {}, c); s.style.setProperty('--i', i); return s; }));
    const canvas = h('canvas', { class: 'splash-fx' });
    const root = h('div', { class: 'splash' + (m === 'reduced' ? ' reduced' : ''), 'aria-hidden': 'true' },
      h('div', { class: 'splash-rays' }), h('div', { class: 'splash-beams' }, h('i'), h('i'), h('i')), canvas, h('div', { class: 'splash-cursor' }),
      h('div', { class: 'splash-glow' }), svg, word, h('div', { class: 'splash-tag' }, 'Your secrets, sealed'));
    document.body.append(root);
    requestAnimationFrame(() => root.classList.add('live'));
    const count = m === 'reduced' ? 34 : Math.round(Math.min(120, Math.max(46, (innerWidth * innerHeight) / 15500)));
    const field = particleField(root, canvas, { count, interactive: m !== 'reduced', dark });
    let done = false;
    const finish = async () => { if (done) return; done = true; root.classList.add('out'); await sleep(m === 'reduced' ? 200 : 520); root.remove(); field.stop(); };
    root.addEventListener('click', finish);
    await sleep(m === 'reduced' ? 1000 : 3500);
    await finish();
  }

  /* ---- runaway button: dodges the pointer with spring motion until it is allowed to be clicked.
     Pointer-only: keyboard, touch, coarse pointers and the Reduced/Off animation settings get an ordinary button. */
  function runaway(btn, { evasive, hint }) {
    const fine = !!(window.matchMedia && window.matchMedia('(hover: hover) and (pointer: fine)').matches);
    const SCALE = 0.68, TRIGGER = 86, M = 14;
    const st = { x: 0, y: 0, vx: 0, vy: 0, s: 1, vs: 0, tx: 0, ty: 0, ts: 1, raf: 0, last: 0, evading: false, lastMove: 0, lastHop: 0, dodged: 0, idle: 0 };
    const enabled = () => fine && mode() === 'full' && !btn.disabled;
    const wants = () => enabled() && evasive();
    btn.classList.add('rw');
    const home = () => { st.tx = 0; st.ty = 0; st.ts = 1; kick(); };
    function kick() { if (!st.raf) { st.last = performance.now(); st.raf = requestAnimationFrame(frame); } }
    function frame(now) {
      st.raf = 0;
      let left = Math.min(0.12, (now - st.last) / 1000 || 0.016); st.last = now;   // real elapsed time, integrated in fixed sub-steps so slow machines do not run in slow motion
      // damped spring (zeta ~0.9): lively, with only the faintest overshoot
      const K = 190, C = 25, KS = 230, CS = 27;
      while (left > 1e-6) {
        const dt = Math.min(1 / 120, left); left -= dt;
        st.vx += (K * (st.tx - st.x) - C * st.vx) * dt; st.x += st.vx * dt;
        st.vy += (K * (st.ty - st.y) - C * st.vy) * dt; st.y += st.vy * dt;
        st.vs += (KS * (st.ts - st.s) - CS * st.vs) * dt; st.s += st.vs * dt;
      }
      const tilt = Math.max(-7, Math.min(7, st.vx * 0.014));
      btn.style.transform = `translate3d(${st.x.toFixed(2)}px, ${st.y.toFixed(2)}px, 0) scale(${st.s.toFixed(3)}) rotate(${tilt.toFixed(2)}deg)`;
      const rest = Math.abs(st.tx - st.x) < .15 && Math.abs(st.ty - st.y) < .15 && Math.abs(st.vx) < .4 && Math.abs(st.vy) < .4 && Math.abs(st.ts - st.s) < .002 && Math.abs(st.vs) < .01;
      if (rest) { st.x = st.tx; st.y = st.ty; st.s = st.ts; if (st.tx === 0 && st.ty === 0 && st.ts === 1) btn.style.transform = ''; else btn.style.transform = `translate3d(${st.x}px, ${st.y}px, 0) scale(${st.s})`; return; }
      st.raf = requestAnimationFrame(frame);
    }
    function refresh() {
      const want = wants();
      if (want === st.evading) return;
      st.evading = want;
      btn.classList.toggle('running', want);
      if (!want) { clearTimeout(st.idle); st.dodged = 0; home(); hint(false); btn.classList.remove('ready'); void btn.offsetWidth; if (enabled()) btn.classList.add('ready'); }
      else { btn.classList.remove('ready'); }
    }
    const rectDist = (r, px, py) => Math.hypot(Math.max(r.left - px, 0, px - r.right), Math.max(r.top - py, 0, py - r.bottom));
    function onMove(e) {
      if (!btn.isConnected) { cleanup(); return; }
      if (!st.evading) return;
      const px = e.clientX, py = e.clientY, now = performance.now();
      st.lastMove = now;
      clearTimeout(st.idle); st.idle = setTimeout(() => { if (st.evading) home(); }, 1200);
      const r = btn.getBoundingClientRect();
      const d = rectDist(r, px, py);
      if (d > TRIGGER || now - st.lastHop < 70) return;
      st.lastHop = now;
      const cx = r.left + r.width / 2, cy = r.top + r.height / 2;
      let ax = cx - px, ay = cy - py, len = Math.hypot(ax, ay);
      if (len < 1) { const a = Math.random() * 6.283; ax = Math.cos(a); ay = Math.sin(a); len = 1; }
      ax /= len; ay /= len;
      const j = (Math.random() - .5) * .9, nx = ax - ay * j, ny = ay + ax * j, nl = Math.hypot(nx, ny);
      const hop = 100 + (TRIGGER - d) * 1.5 + Math.random() * 70;
      const bw = btn.offsetWidth, bh = btn.offsetHeight, hw = bw * SCALE / 2, hh = bh * SCALE / 2;
      const hcx = cx - st.x, hcy = cy - st.y;     // layout (home) centre: scale is about the centre, so only the translation moves it
      const clampX = (v) => Math.min(innerWidth - M - hw - hcx, Math.max(M + hw - hcx, v));
      const clampY = (v) => Math.min(innerHeight - M - hh - hcy, Math.max(M + hh - hcy, v));
      let tx = clampX(st.x + nx / nl * hop), ty = clampY(st.y + ny / nl * hop);
      const nd = Math.hypot(Math.max(hcx + tx - hw - px, 0, px - (hcx + tx + hw)), Math.max(hcy + ty - hh - py, 0, py - (hcy + ty + hh)));
      if (nd < TRIGGER * .9) { tx = clampX(innerWidth - px - hcx); ty = clampY(innerHeight - py - hcy); }   // cornered: hop to the far side
      st.tx = tx; st.ty = ty; st.ts = SCALE; st.dodged++;
      hint(true); kick();
    }
    const onLeave = () => { if (st.evading) home(); };
    window.addEventListener('pointermove', onMove, { passive: true });
    document.documentElement.addEventListener('mouseleave', onLeave);
    function cleanup() { window.removeEventListener('pointermove', onMove); document.documentElement.removeEventListener('mouseleave', onLeave); clearTimeout(st.idle); if (st.raf) cancelAnimationFrame(st.raf); }
    return { refresh, cleanup };
  }

  /* ---- the vault door */
  async function vaultFx(dir, swap) {
    const m = mode();
    if (m === 'off') { swap(); return; }
    S.fx = true;
    running.length = 0;
    const skipHandlers = [];
    try {
      const D = Math.round(Math.max(230, Math.min(400, Math.min(innerWidth, innerHeight) * 0.5)));
      const guard = (p) => Promise.race([p, sleep(4200)]);
      if (m === 'reduced') {
        const quick = h('div', { class: 'vfx', 'aria-hidden': 'true' }, h('div', { class: 'vlogo', style: null }, h('span', {}, icon('shield', 28))));
        document.body.append(quick);
        await guard(anim(quick, [{ opacity: 0 }, { opacity: 1 }], { duration: 200 }));
        swap();
        await guard(anim(quick, [{ opacity: 1 }, { opacity: 0 }], { duration: 260 }));
        quick.remove();
        return;
      }
      const lockedR = D * 0.5 + 2, openR = D * 0.5 - D * 0.09;
      const t = (i, r) => `rotate(${i * 45}deg) translateY(${-r}px)`;
      const bolts = Array.from({ length: 8 }, () => h('i', { class: 'bolt' }));
      bolts.forEach((b, i) => { b.style.transform = t(i, dir === 'open' ? lockedR : openR); });
      const wheel = h('div', { class: 'vwheel' }, h('i', { class: 'spoke' }), h('i', { class: 'spoke' }), h('i', { class: 'spoke' }));
      const light = h('div', { class: 'vlight' });
      const door = h('div', { class: 'vdoor' }, h('div', { class: 'vedge' }), h('div', { class: 'vplate' }), bolts, wheel, h('div', { class: 'vlogo' }, h('span', {}, icon('shield', Math.round(D * 0.12)))), light);
      const portal = h('div', { class: 'vportal' });
      const rays = h('div', { class: 'vrays' });
      const stage = h('div', { class: 'vstage' }, rays, portal, door);
      const cam = h('div', { class: 'vcam' }, stage);
      cam.style.setProperty('--D', D + 'px');
      const canvas = document.createElement('canvas');
      const caption = h('div', { class: 'vcaption' }, dir === 'open' ? 'Unlocking' : 'Locking');
      const root = h('div', { class: 'vfx', 'aria-hidden': 'true', title: 'Click or press any key to skip' }, cam, canvas, caption);
      document.body.append(root);
      const accent = () => (getComputedStyle(document.documentElement).getPropertyValue('--accent') || '#a66bff').trim() || '#a66bff';
      const sp = sparks(canvas, accent);
      const skip = () => skipAll();
      root.addEventListener('pointerdown', skip); const kd = () => skip(); window.addEventListener('keydown', kd, true); skipHandlers.push(() => window.removeEventListener('keydown', kd, true));
      const cx = innerWidth / 2, cy = innerHeight / 2;
      const burstBolts = (delay, n) => { for (let i = 0; i < 8; i++) setTimeout(() => { const a = i * Math.PI / 4 - Math.PI / 2, r = D * 0.5; sp.burst(cx + Math.cos(a) * r, cy + Math.sin(a) * r, n, 150, 0.7); }, delay + i * 30); };
      const ease = 'cubic-bezier(.45,0,.2,1)', OPEN = 'rotateY(-112deg)';
      const sweep = (delay) => { if (light.animate) running.push(light.animate([{ transform: 'translateX(0)' }, { transform: 'translateX(' + (D * 1.9) + 'px)' }], { duration: 700, delay, easing: ease, fill: 'both' })); };
      if (dir === 'open') {
        swap(); playUnlock();
        sweep(120);
        burstBolts(470, 6);
        setTimeout(() => sp.burst(cx, cy, 70, 330, 1.0), 820);
        await guard(Promise.all([
          anim(cam, [{ transform: 'rotateX(7deg) rotateY(-9deg) scale(.9)' }, { transform: 'rotateX(0deg) rotateY(0deg) scale(1)' }], { duration: 620, easing: 'ease-out' }),
          anim(wheel, [{ transform: 'rotate(0deg)' }, { transform: 'rotate(-540deg)' }], { duration: 650, easing: ease }),
          ...bolts.map((b, i) => anim(b, [{ transform: t(i, lockedR) }, { transform: t(i, openR) }], { duration: 300, delay: 450 + i * 30, easing: ease })),
          anim(door, [{ transform: 'rotateY(0deg)' }, { transform: OPEN }], { duration: 780, delay: 820, easing: ease }),
          anim(portal, [{ opacity: 0.55, transform: 'scale(1)' }, { opacity: 1, transform: 'scale(1.08)' }], { duration: 780, delay: 820 }),
          anim(rays, [{ opacity: 0, transform: 'rotate(0deg) scale(.7)' }, { opacity: 1, transform: 'rotate(26deg) scale(1)' }], { duration: 900, delay: 860 }),
          anim(caption, [{ opacity: 0.9 }, { opacity: 0 }], { duration: 300, delay: 900 }),
          anim(stage, [{ transform: 'scale(1)', opacity: 1 }, { transform: 'scale(1.55)', opacity: 0 }], { duration: 440, delay: 1360, easing: 'ease-in' }),
          anim(root, [{ opacity: 1 }, { opacity: 0 }], { duration: 440, delay: 1380 }),
        ]));
      } else {
        door.style.transform = OPEN; portal.style.opacity = '1'; playLock();
        setTimeout(() => sp.burst(cx, cy, 36, 220, 0.8), 700);
        burstBolts(780, 5);
        await guard(Promise.all([
          anim(root, [{ opacity: 0 }, { opacity: 1 }], { duration: 220 }),
          anim(cam, [{ transform: 'rotateX(0deg) rotateY(0deg) scale(1.1)' }, { transform: 'rotateX(6deg) rotateY(8deg) scale(.95)' }], { duration: 1500, easing: 'ease-in-out' }),
          anim(rays, [{ opacity: 1, transform: 'rotate(0deg) scale(1)' }, { opacity: 0, transform: 'rotate(-20deg) scale(.7)' }], { duration: 650, delay: 80 }),
          anim(door, [{ transform: OPEN }, { transform: 'rotateY(0deg)' }], { duration: 640, delay: 120, easing: ease }),
          ...bolts.map((b, i) => anim(b, [{ transform: t(i, openR) }, { transform: t(i, lockedR) }], { duration: 260, delay: 780 + i * 30, easing: ease })),
          anim(wheel, [{ transform: 'rotate(0deg)' }, { transform: 'rotate(360deg)' }], { duration: 520, delay: 950, easing: ease }),
        ]));
        sweep(0);
        swap();
        await guard(anim(root, [{ opacity: 1 }, { opacity: 0 }], { duration: 400 }));
      }
      sp.stop(); root.remove();
    } finally { skipHandlers.forEach((f) => f()); S.fx = false; }
  }
  return { splash, vaultFx, fonts, runaway };
};
