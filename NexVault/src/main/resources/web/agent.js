/* Agent Tiny: a shaded, softly lit chibi agent with moods, blinking, breathing and eyes that follow the pointer. */
window.NVAgent = (function () {
  const SVG = '<svg class="agent-svg" viewBox="0 0 160 190" aria-hidden="true"><defs>'
    + '<radialGradient id="agSkin" cx=".36" cy=".3" r=".85"><stop offset="0" stop-color="#ffeddc"/><stop offset=".55" stop-color="#f7cfae"/><stop offset="1" stop-color="#d9976f"/></radialGradient>'
    + '<radialGradient id="agEar" cx=".4" cy=".4" r=".7"><stop offset="0" stop-color="#f4c4a0"/><stop offset="1" stop-color="#c98a64"/></radialGradient>'
    + '<linearGradient id="agHair" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#5a4570"/><stop offset="1" stop-color="#1b1228"/></linearGradient>'
    + '<linearGradient id="agCap" x1=".1" y1="0" x2=".9" y2="1"><stop offset="0" stop-color="#4a4478"/><stop offset=".5" stop-color="#1e1a38"/><stop offset="1" stop-color="#0b0918"/></linearGradient>'
    + '<linearGradient id="agBrim" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2d2852"/><stop offset="1" stop-color="#07050f"/></linearGradient>'
    + '<linearGradient id="agSuit" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#3a3460"/><stop offset=".6" stop-color="#14112a"/><stop offset="1" stop-color="#08060f"/></linearGradient>'
    + '<linearGradient id="agTie" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#a672ff"/><stop offset="1" stop-color="#4c1d95"/></linearGradient>'
    + '<linearGradient id="agGold" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#fff0b8"/><stop offset="1" stop-color="#c9951f"/></linearGradient>'
    + '<radialGradient id="agIris" cx=".38" cy=".32" r=".75"><stop offset="0" stop-color="#a58aff"/><stop offset=".45" stop-color="#4b3aa8"/><stop offset="1" stop-color="#150e3a"/></radialGradient>'
    + '<linearGradient id="agLens" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#3b3b4e"/><stop offset=".5" stop-color="#0c0c14"/><stop offset="1" stop-color="#050508"/></linearGradient>'
    + '<filter id="agBlur" x="-30%" y="-80%" width="160%" height="260%"><feGaussianBlur stdDeviation="3.5"/></filter><filter id="agSoft"><feGaussianBlur stdDeviation="1.7"/></filter></defs>'
    + '<ellipse cx="80" cy="184" rx="48" ry="6" fill="#000" opacity=".5" filter="url(#agBlur)"/>'
    + '<g class="a-body"><path d="M22 190c0-30 20-46 58-46s58 16 58 46z" fill="url(#agSuit)"/><path d="M22 190c0-30 20-46 58-46" fill="none" stroke="rgba(255,255,255,.2)" stroke-width="1.3"/>'
    + '<path d="M66 126h28v20c-6 9-22 9-28 0z" fill="#d79a74"/><path d="M66 134c8 8 20 8 28 0v-8H66z" fill="#b97752" opacity=".55"/>'
    + '<path d="M60 146l20 28 20-28-8-8H68z" fill="#f6f3fb"/><path d="M60 146l20 28V138H68z" fill="#dcd7e8" opacity=".6"/>'
    + '<path d="M44 158l24-14-6 36zM116 158l-24-14 6 36z" fill="#0d0a1a" stroke="rgba(255,255,255,.14)"/>'
    + '<path d="M74 146h12l-2 8h-8z" fill="url(#agTie)"/><path d="M75 154h10l4 26-9 7-9-7z" fill="url(#agTie)"/><path d="M78 156l-2 24" stroke="rgba(255,255,255,.35)" stroke-width="1.2"/>'
    + '<rect x="100" y="164" width="15" height="11" rx="3" fill="url(#agGold)"/><path d="M104 168h7" stroke="#8a6410" stroke-width="1.2"/>'
    + '<g class="arms"><path d="M30 162c18-13 82-13 100 0l-6 18c-24-10-64-10-88 0z" fill="url(#agSuit)" stroke="rgba(255,255,255,.2)"/><path d="M34 164c26-8 66-8 92 0" fill="none" stroke="rgba(255,255,255,.14)"/><circle cx="48" cy="168" r="9" fill="url(#agSkin)"/><circle cx="112" cy="168" r="9" fill="url(#agSkin)"/></g></g>'
    + '<g class="a-head"><ellipse cx="38" cy="88" rx="8" ry="10" fill="url(#agEar)"/><ellipse cx="38" cy="89" rx="3.4" ry="5.2" fill="#b9775a" opacity=".5"/><ellipse cx="122" cy="88" rx="8" ry="10" fill="url(#agEar)"/><ellipse cx="122" cy="89" rx="3.4" ry="5.2" fill="#b9775a" opacity=".5"/>'
    + '<ellipse cx="80" cy="84" rx="44" ry="42" fill="url(#agSkin)"/><path d="M118 70c6 24-6 46-38 54 30 0 50-18 44-48z" fill="#c4805a" opacity=".22" filter="url(#agSoft)"/>'
    + '<circle cx="54" cy="104" r="8.5" fill="#ff8fa3" opacity=".42" filter="url(#agSoft)"/><circle cx="106" cy="104" r="8.5" fill="#ff8fa3" opacity=".42" filter="url(#agSoft)"/>'
    + '<path d="M38 76c4-12 14-20 24-22l-4 18zM122 76c-4-12-14-20-24-22l4 18z" fill="url(#agHair)"/><path d="M64 62c8 5 24 5 32 0l-2 8c-8 3-20 3-28 0z" fill="url(#agHair)"/>'
    + '<g class="eye"><ellipse cx="62" cy="98" rx="7" ry="8.2" fill="#f5f2fb"/><g class="pupil"><circle cx="62" cy="98.5" r="5.4" fill="url(#agIris)"/><circle cx="62" cy="98.5" r="2.5" fill="#0a0618"/><circle cx="60" cy="96" r="1.9" fill="#fff"/><circle cx="64.2" cy="101" r=".9" fill="#fff" opacity=".8"/></g><path d="M54.6 94c2-3.4 12-3.4 14.8 0" fill="none" stroke="#2a1d3a" stroke-width="1.6" opacity=".55"/><rect class="lid" x="53" y="88" width="18" height="10"/></g>'
    + '<g class="eye"><ellipse cx="98" cy="98" rx="7" ry="8.2" fill="#f5f2fb"/><g class="pupil"><circle cx="98" cy="98.5" r="5.4" fill="url(#agIris)"/><circle cx="98" cy="98.5" r="2.5" fill="#0a0618"/><circle cx="96" cy="96" r="1.9" fill="#fff"/><circle cx="100.2" cy="101" r=".9" fill="#fff" opacity=".8"/></g><path d="M90.6 94c2-3.4 12-3.4 14.8 0" fill="none" stroke="#2a1d3a" stroke-width="1.6" opacity=".55"/><rect class="lid" x="89" y="88" width="18" height="10"/></g>'
    + '<path class="brow bl" d="M51 85 71 82"/><path class="brow br" d="M89 82 109 85"/>'
    + '<path d="M80 102q-3.4 6 0 8.4q3 1.4 6.4-1" fill="none" stroke="#c98a64" stroke-width="1.6" stroke-linecap="round" opacity=".75"/><circle cx="79.4" cy="103" r="1.1" fill="#fff" opacity=".55"/>'
    + '<path class="mouth m-smirk" d="M68 119c8 8 18 7 26-3"/><path class="mouth m-flat" d="M68 120h24"/><path class="mouth m-pout" d="M71 122c5-5 13-5 18 0"/>'
    + '<g class="shades"><rect x="45" y="87" width="31" height="20" rx="7.5" fill="url(#agLens)" stroke="rgba(255,255,255,.25)"/><rect x="84" y="87" width="31" height="20" rx="7.5" fill="url(#agLens)" stroke="rgba(255,255,255,.25)"/><path d="M76 93h8" stroke="#0c0a14" stroke-width="2.6"/><path d="M51 91l11-2M90 91l11-2" stroke="rgba(255,255,255,.7)" stroke-width="1.8" stroke-linecap="round"/><ellipse cx="60" cy="112" rx="14" ry="3" fill="#000" opacity=".18" filter="url(#agSoft)"/></g>'
    + '<ellipse cx="80" cy="80" rx="42" ry="7" fill="#000" opacity=".26" filter="url(#agSoft)"/>'
    + '<path d="M34 70C34 38 54 20 80 20s46 18 46 50z" fill="url(#agCap)"/><path d="M80 20v50M60 24c-6 12-8 30-6 46M100 24c6 12 8 30 6 46" fill="none" stroke="rgba(255,255,255,.07)"/><path d="M50 46c5-12 16-19 30-20" fill="none" stroke="#fff" stroke-width="3.2" stroke-linecap="round" opacity=".22" filter="url(#agSoft)"/><circle cx="80" cy="20" r="3.6" fill="#2d2850" stroke="rgba(255,255,255,.2)"/>'
    + '<path d="M22 72c20-9 38-12 58-12s38 3 58 12c-6 9-20 8-58 8s-52 1-58-8z" fill="url(#agBrim)"/><path d="M26 71c18-8 36-10 54-10" fill="none" stroke="rgba(255,255,255,.22)" stroke-width="1.4" stroke-linecap="round"/>'
    + '<rect x="62" y="34" width="36" height="17" rx="4" fill="#3a3166" stroke="rgba(255,255,255,.22)"/><text x="80" y="47" text-anchor="middle" font-family="Arial,sans-serif" font-weight="800" font-size="11" fill="#f1e7ff" letter-spacing=".6">FBI</text></g>'
    + '<g class="lens"><circle cx="128" cy="150" r="15" fill="rgba(200,230,255,.24)" stroke="#d6d1ea" stroke-width="4"/><path d="M119 143a11 11 0 0 1 8-5" fill="none" stroke="#fff" stroke-width="2.2" stroke-linecap="round" opacity=".7"/><path d="M139 161l12 12" stroke="#d6d1ea" stroke-width="5" stroke-linecap="round"/></g></svg>';

  const LINES = [
    ['smug', 'Wrong password. Bold of you to guess with such confidence.'],
    ['suspicious', 'Hmm. Are you sure you own this vault, rookie?'],
    ['deadpan', 'Three strikes of pure confidence, zero strikes of correctness.'],
    ['deadpan', 'Careful now. One more and I am locking this place down for an hour.'],
  ];
  function lineFor(n) { return LINES[Math.min(Math.max(n, 1), LINES.length) - 1]; }

  function view(h, mood, text) {
    const fig = h('div', { class: 'agent-fig' });
    fig.innerHTML = SVG;   // static, trusted markup
    return h('div', { class: 'agent', 'data-mood': mood, role: 'alert' }, fig, h('div', { class: 'bubble' }, h('b', { class: 'who' }, 'Agent Tiny'), h('span', {}, text)));
  }

  let raf = 0, started = false;
  function trackPointer(enabled) {
    if (started) return;
    started = true;
    window.addEventListener('pointermove', (ev) => {
      if (raf || !enabled()) return;
      raf = requestAnimationFrame(() => {
        raf = 0;
        document.querySelectorAll('.agent').forEach((a) => {
          const r = a.getBoundingClientRect();
          const dx = (ev.clientX - (r.left + 60)) / 320, dy = (ev.clientY - (r.top + r.height * 0.5)) / 240;
          a.style.setProperty('--ex', Math.max(-1, Math.min(1, dx)).toFixed(2));
          a.style.setProperty('--ey', Math.max(-1, Math.min(1, dy)).toFixed(2));
        });
      });
    }, { passive: true });
  }
  return { view, lineFor, trackPointer };
})();
