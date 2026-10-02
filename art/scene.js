// 캠핑장 일러스트 (SVG). 플랫 + 부드러운 그라데이션 + 잔잔한 그레인
// kind: clear|cloudy|rain|snow, night: bool, fmt: sq|ci|pa|tent|icon
function rng(seed) { let a = seed >>> 0; return () => { a = (a + 0x6D2B79F5) >>> 0; let t = a; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }; }

const PAL = {
  clear_d: { sky: ['#8ec9ea', '#cfe8f1', '#fbe7cc'], far: '#a9c3cf', mid: '#7fa596', pine: '#3f6b56', pine2: '#517f68', ground: ['#a7c788', '#88ad6f'], canvas: '#f4e8d0', canvasShade: '#e2cfab', door: '#c9b28a', haze: '#ffffff' },
  cloudy_d: { sky: ['#b7c4cf', '#d8dfe3', '#ece9e1'], far: '#b2bcc0', mid: '#8d9f98', pine: '#4a6457', pine2: '#5b7568', ground: ['#a3b495', '#8a9d7d'], canvas: '#eee4d0', canvasShade: '#d9cbb0', door: '#bfae8f', haze: '#ffffff' },
  rain_d: { sky: ['#6e7d8c', '#8e9ba4', '#a9b1b2'], far: '#7d8b93', mid: '#647a74', pine: '#33493f', pine2: '#405a4e', ground: ['#73866d', '#5f725b'], canvas: '#e9dcc2', canvasShade: '#cdbb98', door: '#ffcf7a', haze: '#c9d2d6' },
  snow_d: { sky: ['#c9d8e4', '#e3ebf1', '#f4f2ee'], far: '#c3cfd9', mid: '#a6b8c4', pine: '#3e5a5a', pine2: '#4f6b6a', ground: ['#f4f7fa', '#e3eaf1'], canvas: '#f1e6d0', canvasShade: '#dccaa9', door: '#c7b08a', haze: '#ffffff' },
  clear_n: { sky: ['#0d1630', '#1f2d52', '#4b4566'], far: '#26365a', mid: '#1c2a47', pine: '#0f1a2e', pine2: '#14223a', ground: ['#18263d', '#111c2f'], canvas: '#ffd79a', canvasShade: '#f0b467', door: '#fff1c9', haze: '#6b5f80' },
  cloudy_n: { sky: ['#1a2233', '#2a3346', '#3d4252'], far: '#2b3446', mid: '#222b3b', pine: '#141b28', pine2: '#19212f', ground: ['#1b2230', '#141a25'], canvas: '#ffd79a', canvasShade: '#f0b467', door: '#fff1c9', haze: '#4b4f60' },
  rain_n: { sky: ['#141b29', '#222b3b', '#323a4a'], far: '#252e3e', mid: '#1d2533', pine: '#111823', pine2: '#161e2b', ground: ['#161d29', '#10151f'], canvas: '#ffd38f', canvasShade: '#eda95c', door: '#fff0c4', haze: '#3d4556' },
  snow_n: { sky: ['#101a33', '#22305a', '#3d4a72'], far: '#2c3b62', mid: '#24345a', pine: '#13203a', pine2: '#182846', ground: ['#c9d5ea', '#a9b8d4'], canvas: '#ffd79a', canvasShade: '#f0b467', door: '#fff1c9', haze: '#6d78a0' },
};

// ---------- 부품 ----------
function ridge(W, y0, amp, seed, step, H) {
  const r = rng(seed); let d = `M0 ${H} L0 ${y0}`; let prev = y0;
  for (let x = 0; x <= W + step; x += step) {
    const y = y0 - amp * (0.35 + 0.65 * r()) * (Math.sin(x / (W * 0.23) + seed) * 0.5 + 0.6);
    const cx = x - step / 2; d += ` Q${cx} ${prev} ${x} ${y}`; prev = y;
  }
  return d + ` L${W} ${H} Z`;
}
function hill(W, y, bulge, H) { return `M0 ${H} L0 ${y} C${W * 0.3} ${y - bulge} ${W * 0.7} ${y - bulge * 0.6} ${W} ${y + bulge * 0.2} L${W} ${H} Z`; }

// 소나무: 3단 삼각형, 끝이 살짝 둥근 플랫 스타일
function pine(x, base, h, col, snow) {
  const w = h * 0.42; let s = `<g>`;
  s += `<rect x="${x - h * 0.025}" y="${base - h * 0.12}" width="${h * 0.05}" height="${h * 0.12}" fill="${col}" />`;
  for (let i = 0; i < 3; i++) {
    const top = base - h + i * h * 0.24, bot = base - h * 0.08 - (2 - i) * h * 0.2;
    const ww = w * (0.55 + i * 0.25);
    s += `<path d="M${x} ${top} L${x + ww / 2} ${bot} Q${x} ${bot + h * 0.05} ${x - ww / 2} ${bot} Z" fill="${col}" stroke="${col}" stroke-width="${h * 0.02}" stroke-linejoin="round"/>`;
    if (snow) s += `<path d="M${x} ${top} L${x + ww * 0.2} ${top + (bot - top) * 0.4} Q${x} ${top + (bot - top) * 0.5} ${x - ww * 0.2} ${top + (bot - top) * 0.4} Z" fill="#f4f7fb" opacity=".92"/>`;
  }
  return s + `</g>`;
}

// 벨텐트: 원뿔 지붕 + 낮은 벽 + 꼭대기 폴. lit=안에서 불이 켜짐
function bellTent(cx, base, w, p, lit, snow, uid, glow = true) {
  const h = w * 0.78, wallH = h * 0.26, roofBase = base - wallH, apex = base - h;
  const L = cx - w / 2, R = cx + w / 2, rl = cx - w * 0.52, rr = cx + w * 0.52;
  const g = `tg${uid}`;
  let s = `<defs>
    <linearGradient id="${g}" x1="0" y1="0" x2="1" y2="0">
      <stop offset="0" stop-color="${p.canvasShade}"/><stop offset=".45" stop-color="${p.canvas}"/><stop offset="1" stop-color="${p.canvasShade}"/></linearGradient>
    <radialGradient id="${g}g" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="#ffb347" stop-opacity=".32"/><stop offset=".55" stop-color="#ffb347" stop-opacity=".1"/><stop offset="1" stop-color="#ffb347" stop-opacity="0"/></radialGradient>
  </defs>`;
  if (lit && glow) s += `<ellipse cx="${cx}" cy="${base - h * 0.05}" rx="${w * 0.95}" ry="${h * 0.32}" fill="url(#${g}g)"/>`;
  // 그림자
  s += `<ellipse cx="${cx}" cy="${base + h * 0.02}" rx="${w * 0.58}" ry="${h * 0.045}" fill="#000" opacity="${lit ? 0.18 : 0.12}"/>`;
  // 벽
  s += `<path d="M${L} ${roofBase} L${R} ${roofBase} L${R - w * 0.01} ${base} L${L + w * 0.01} ${base} Z" fill="url(#${g})"/>`;
  // 지붕 (살짝 오목한 원뿔)
  s += `<path d="M${cx} ${apex} Q${cx + w * 0.2} ${roofBase - h * 0.28} ${rr} ${roofBase} Q${cx} ${roofBase + h * 0.035} ${rl} ${roofBase} Q${cx - w * 0.2} ${roofBase - h * 0.28} ${cx} ${apex} Z" fill="url(#${g})"/>`;
  // 지붕 솔기 (패널 라인)
  for (const t of [-0.3, 0.3]) s += `<path d="M${cx} ${apex} Q${cx + w * t * 0.5} ${roofBase - h * 0.2} ${cx + w * t} ${roofBase + h * 0.01}" stroke="${p.canvasShade}" stroke-width="${w * 0.006}" fill="none" opacity=".9"/>`;
  // 처마 그림자 띠
  s += `<path d="M${rl} ${roofBase} Q${cx} ${roofBase + h * 0.035} ${rr} ${roofBase} L${R} ${roofBase + h * 0.03} Q${cx} ${roofBase + h * 0.06} ${L} ${roofBase + h * 0.03} Z" fill="#000" opacity=".08"/>`;
  // 문: 삼각형으로 걷어 올린 입구
  const dw = w * 0.2, dTop = roofBase - h * 0.16;
  s += `<path d="M${cx} ${dTop} L${cx + dw / 2} ${base} L${cx - dw / 2} ${base} Z" fill="${lit ? p.door : '#6b5638'}" opacity="${lit ? 1 : 0.55}"/>`;
  if (lit) s += `<path d="M${cx} ${dTop} L${cx + dw / 2} ${base} L${cx - dw / 2} ${base} Z" fill="#fff8e0" opacity=".35"/>`;
  // 걷어 묶은 문 자락
  s += `<path d="M${cx} ${dTop} L${cx - dw / 2} ${base} L${cx - dw * 0.78} ${base} Q${cx - dw * 0.45} ${roofBase} ${cx} ${dTop} Z" fill="${p.canvasShade}"/>`;
  s += `<path d="M${cx} ${dTop} L${cx + dw / 2} ${base} L${cx + dw * 0.78} ${base} Q${cx + dw * 0.45} ${roofBase} ${cx} ${dTop} Z" fill="${p.canvasShade}" opacity=".85"/>`;
  // 꼭대기 폴 + 깃발
  s += `<line x1="${cx}" y1="${apex}" x2="${cx}" y2="${apex - h * 0.1}" stroke="#7a5233" stroke-width="${w * 0.012}" stroke-linecap="round"/>`;
  s += `<path d="M${cx} ${apex - h * 0.1} L${cx + w * 0.07} ${apex - h * 0.075} L${cx} ${apex - h * 0.05} Z" fill="#e0794f"/>`;
  if (snow) s += `<path d="M${cx} ${apex} Q${cx + w * 0.12} ${apex + h * 0.18} ${cx + w * 0.2} ${apex + h * 0.3} Q${cx} ${apex + h * 0.24} ${cx - w * 0.2} ${apex + h * 0.3} Q${cx - w * 0.12} ${apex + h * 0.18} ${cx} ${apex} Z" fill="#f7f9fc"/>`;
  // 줄(가이라인)
  s += `<line x1="${rl + w * 0.02}" y1="${roofBase}" x2="${L - w * 0.18}" y2="${base}" stroke="${p.canvasShade}" stroke-width="${w * 0.004}" opacity=".8"/>`;
  s += `<line x1="${rr - w * 0.02}" y1="${roofBase}" x2="${R + w * 0.18}" y2="${base}" stroke="${p.canvasShade}" stroke-width="${w * 0.004}" opacity=".8"/>`;
  return { svg: s, apex, roofBase, h };
}

// 전구 가랜드
function garland(x1, y1, x2, y2, sag, n, on, size) {
  const mx = (x1 + x2) / 2, my = (y1 + y2) / 2 + sag;
  let s = `<path d="M${x1} ${y1} Q${mx} ${my} ${x2} ${y2}" stroke="${on ? '#3a2f28' : '#5b5048'}" stroke-width="${size * 0.25}" fill="none" opacity=".8"/>`;
  for (let i = 1; i < n; i++) {
    const t = i / n, x = (1 - t) ** 2 * x1 + 2 * (1 - t) * t * mx + t * t * x2, y = (1 - t) ** 2 * y1 + 2 * (1 - t) * t * my + t * t * y2;
    if (on) s += `<circle cx="${x}" cy="${y + size}" r="${size * 2.6}" fill="#ffd27a" opacity=".22"/>`;
    s += `<circle cx="${x}" cy="${y + size}" r="${size}" fill="${on ? '#ffe3a1' : '#f2e2b8'}"/>`;
  }
  return s;
}

// 모닥불
function campfire(x, base, s, on, uid) {
  let g = `<defs><radialGradient id="fg${uid}" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="#ffb24a" stop-opacity=".45"/><stop offset=".5" stop-color="#ff9a40" stop-opacity=".12"/><stop offset="1" stop-color="#ff8a3a" stop-opacity="0"/></radialGradient></defs>`;
  if (on) g += `<ellipse cx="${x}" cy="${base - s * 0.3}" rx="${s * 3.2}" ry="${s * 2.2}" fill="url(#fg${uid})"/>`;
  // 돌
  for (const [dx, r] of [[-0.75, 0.22], [-0.38, 0.2], [0, 0.22], [0.38, 0.2], [0.75, 0.22]]) g += `<ellipse cx="${x + dx * s}" cy="${base - r * s * 0.4}" rx="${r * s}" ry="${r * s * 0.6}" fill="${on ? '#5b5552' : '#8b8682'}"/>`;
  // 장작
  g += `<rect x="${x - s * 0.7}" y="${base - s * 0.42}" width="${s * 1.4}" height="${s * 0.16}" rx="${s * 0.08}" fill="#7a4e2d" transform="rotate(-14 ${x} ${base - s * 0.34})"/>`;
  g += `<rect x="${x - s * 0.7}" y="${base - s * 0.42}" width="${s * 1.4}" height="${s * 0.16}" rx="${s * 0.08}" fill="#8d5c37" transform="rotate(14 ${x} ${base - s * 0.34})"/>`;
  if (on) {
    g += `<path d="M${x} ${base - s * 1.55} C${x + s * 0.55} ${base - s * 0.95} ${x + s * 0.45} ${base - s * 0.4} ${x} ${base - s * 0.38} C${x - s * 0.45} ${base - s * 0.4} ${x - s * 0.55} ${base - s * 0.9} ${x} ${base - s * 1.55} Z" fill="#ff8a3d"/>`;
    g += `<path d="M${x + s * 0.05} ${base - s * 1.1} C${x + s * 0.32} ${base - s * 0.75} ${x + s * 0.25} ${base - s * 0.45} ${x} ${base - s * 0.42} C${x - s * 0.25} ${base - s * 0.45} ${x - s * 0.28} ${base - s * 0.7} ${x + s * 0.05} ${base - s * 1.1} Z" fill="#ffd36a"/>`;
  } else {
    g += `<path d="M${x} ${base - s * 0.5} q${s * 0.2} ${-s * 0.4} 0 ${-s * 0.8} q${-s * 0.2} ${-s * 0.4} ${s * 0.05} ${-s * 0.7}" stroke="#ffffff" stroke-width="${s * 0.08}" fill="none" opacity=".35" stroke-linecap="round"/>`;
  }
  return g;
}

// 랜턴 기둥 (텐트 옆)
function lantern(x, base, h, on) {
  const lw = h * 0.13;
  let s = `<line x1="${x}" y1="${base}" x2="${x}" y2="${base - h}" stroke="#5c3f28" stroke-width="${h * 0.03}" stroke-linecap="round"/>`;
  s += `<path d="M${x} ${base - h} q${h * 0.12} 0 ${h * 0.14} ${h * 0.08}" stroke="#5c3f28" stroke-width="${h * 0.025}" fill="none" stroke-linecap="round"/>`;
  const lx = x + h * 0.14, ly = base - h + h * 0.1;
  if (on) s += `<defs><radialGradient id="lg${Math.round(x)}" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="#ffd27a" stop-opacity=".45"/><stop offset="1" stop-color="#ffd27a" stop-opacity="0"/></radialGradient></defs><circle cx="${lx}" cy="${ly + lw * 0.7}" r="${lw * 2.6}" fill="url(#lg${Math.round(x)})"/>`;
  s += `<rect x="${lx - lw * 0.4}" y="${ly}" width="${lw * 0.8}" height="${lw * 0.18}" rx="${lw * 0.06}" fill="#3b3b3b"/>`;
  s += `<rect x="${lx - lw * 0.35}" y="${ly + lw * 0.18}" width="${lw * 0.7}" height="${lw * 1.05}" rx="${lw * 0.2}" fill="${on ? '#ffe6a3' : '#e9e2cf'}" stroke="#3b3b3b" stroke-width="${lw * 0.08}"/>`;
  s += `<rect x="${lx - lw * 0.45}" y="${ly + lw * 1.2}" width="${lw * 0.9}" height="${lw * 0.18}" rx="${lw * 0.06}" fill="#3b3b3b"/>`;
  return s;
}

// 캠핑 의자
function chair(x, base, s, col) {
  return `<g stroke="#6b4a30" stroke-width="${s * 0.06}" stroke-linecap="round">
    <line x1="${x - s * 0.35}" y1="${base}" x2="${x + s * 0.3}" y2="${base - s * 0.55}"/><line x1="${x + s * 0.35}" y1="${base}" x2="${x - s * 0.3}" y2="${base - s * 0.55}"/></g>
    <path d="M${x - s * 0.38} ${base - s * 0.55} L${x + s * 0.38} ${base - s * 0.55} L${x + s * 0.32} ${base - s * 1.15} L${x - s * 0.32} ${base - s * 1.15} Z" fill="${col}"/>
    <path d="M${x - s * 0.38} ${base - s * 0.55} Q${x} ${base - s * 0.4} ${x + s * 0.38} ${base - s * 0.55}" fill="${col}" opacity=".85"/>`;
}

function clouds(W, H, y, n, col, op, seed, scale) {
  const r = rng(seed); let s = '';
  for (let i = 0; i < n; i++) {
    const cx = W * (0.08 + r() * 0.9), cy = y + (r() - 0.5) * H * 0.1, w = W * scale * (0.6 + r() * 0.6);
    s += `<g opacity="${op}" fill="${col}"><ellipse cx="${cx}" cy="${cy}" rx="${w * 0.5}" ry="${w * 0.12}"/><circle cx="${cx - w * 0.14}" cy="${cy - w * 0.07}" r="${w * 0.15}"/><circle cx="${cx + w * 0.1}" cy="${cy - w * 0.11}" r="${w * 0.2}"/></g>`;
  }
  return s;
}

function stars(W, H, n, seed, maxY) {
  const r = rng(seed); let s = '';
  for (let i = 0; i < n; i++) { const x = r() * W, y = r() * maxY, rr = (r() < 0.12 ? 1.6 : 0.8) * (W / 560) * (0.6 + r() * 0.6); s += `<circle cx="${x}" cy="${y}" r="${rr}" fill="#fff" opacity="${0.35 + r() * 0.6}"/>`; }
  return s;
}

function rain(W, H, n, seed, night) {
  const r = rng(seed); let s = `<g stroke="${night ? '#9fb3cf' : '#e9f0f5'}" stroke-linecap="round" opacity="${night ? 0.45 : 0.55}">`;
  for (let i = 0; i < n; i++) { const x = r() * W * 1.1, y = r() * H, l = H * (0.04 + r() * 0.04); s += `<line x1="${x}" y1="${y}" x2="${x - l * 0.28}" y2="${y + l}" stroke-width="${W / 560 * 1.4}"/>`; }
  return s + '</g>';
}

function snowfall(W, H, n, seed) {
  const r = rng(seed); let s = `<g fill="#fff">`;
  for (let i = 0; i < n; i++) s += `<circle cx="${r() * W}" cy="${r() * H}" r="${(W / 560) * (1 + r() * 2.2)}" opacity="${0.55 + r() * 0.45}"/>`;
  return s + '</g>';
}

function grain(W, H, id, op) {
  return `<defs><filter id="${id}" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves="2" seed="4" stitchTiles="stitch"/><feColorMatrix type="saturate" values="0"/></filter></defs>
  <rect width="${W}" height="${H}" filter="url(#${id})" opacity="${op}" style="mix-blend-mode:soft-light"/>`;
}

// ---------- 장면 ----------
function scene(kind, night, fmt) {
  const key = `${kind}_${night ? 'n' : 'd'}`, p = PAL[key];
  const lit = night || kind === 'rain';
  const snowy = kind === 'snow';
  const dims = { sq: [560, 560], ci: [560, 560], pa: [1400, 350] }[fmt];
  const [W, H] = dims, U = Math.min(W, H) / 560;   // 기준 단위
  const pa = fmt === 'pa';
  const horizon = pa ? H * 0.76 : H * 0.70;
  const id = `${key}${fmt}`;
  let s = `<defs><linearGradient id="sky${id}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${p.sky[0]}"/><stop offset=".62" stop-color="${p.sky[1]}"/><stop offset="1" stop-color="${p.sky[2]}"/></linearGradient>
    <linearGradient id="gr${id}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${p.ground[0]}"/><stop offset="1" stop-color="${p.ground[1]}"/></linearGradient>
    <radialGradient id="sun${id}" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="#fff6d8" stop-opacity=".9"/><stop offset=".35" stop-color="#ffe7a8" stop-opacity=".45"/><stop offset="1" stop-color="#ffe7a8" stop-opacity="0"/></radialGradient></defs>`;
  s += `<rect width="${W}" height="${H}" fill="url(#sky${id})"/>`;

  // 해·달 위치: 글자가 없는 쪽 (재킷=오른쪽 위, 원=오른쪽, 파노라마=가운데 오른쪽)
  const sx = pa ? W * 0.37 : fmt === 'ci' ? W * 0.8 : W * 0.85, sy = pa ? H * 0.3 : fmt === 'ci' ? H * 0.4 : H * 0.15;
  if (night && kind !== 'rain') {
    if (kind !== 'cloudy') s += stars(W, H, pa ? 120 : 70, 7, horizon * 0.85);
    s += `<defs><radialGradient id="mh${id}" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="#fff4cc" stop-opacity=".28"/><stop offset="1" stop-color="#fff4cc" stop-opacity="0"/></radialGradient></defs><circle cx="${sx}" cy="${sy}" r="${60 * U}" fill="url(#mh${id})"/>`;
    s += `<mask id="mm${id}"><rect width="${W}" height="${H}" fill="#fff"/><circle cx="${sx + 9 * U}" cy="${sy - 6 * U}" r="${15 * U}" fill="#000"/></mask>`;
    s += `<circle cx="${sx}" cy="${sy}" r="${16 * U}" fill="#fff1c2" mask="url(#mm${id})" opacity="${kind === 'cloudy' ? 0.6 : 1}"/>`;
  } else if (kind === 'clear') {
    s += `<circle cx="${sx}" cy="${sy}" r="${90 * U}" fill="url(#sun${id})"/><circle cx="${sx}" cy="${sy}" r="${24 * U}" fill="#fff3cf"/>`;
  }
  if (kind === 'clear' && !night) s += clouds(W, H, H * 0.33, pa ? 4 : 2, '#ffffff', 0.85, 3, pa ? 0.09 : 0.3);
  if (kind === 'cloudy') s += clouds(W, H, H * 0.18, pa ? 9 : 4, night ? '#4a5268' : '#ffffff', night ? 0.75 : 0.9, 11, pa ? 0.12 : 0.42) + clouds(W, H, H * 0.36, pa ? 7 : 3, night ? '#3b4256' : '#f3f5f6', night ? 0.8 : 0.85, 21, pa ? 0.1 : 0.36);
  if (kind === 'rain') s += clouds(W, H, H * 0.12, pa ? 10 : 5, night ? '#2a3141' : '#5e6c79', 0.85, 31, pa ? 0.14 : 0.5);
  if (kind === 'snow') s += clouds(W, H, H * 0.14, pa ? 6 : 3, night ? '#3a4670' : '#ffffff', 0.55, 41, pa ? 0.12 : 0.45);

  // 산 (먼 → 가까운, 대기원근)
  s += `<path d="${ridge(W, horizon - (pa ? H * 0.12 : H * 0.17), pa ? H * 0.1 : H * 0.12, 5, W / (pa ? 9 : 5), H)}" fill="${p.far}"/>`;
  if (snowy) s += `<path d="${ridge(W, horizon - (pa ? H * 0.12 : H * 0.17), pa ? H * 0.1 : H * 0.12, 5, W / (pa ? 9 : 5), H)}" fill="#fff" opacity="${night ? 0.12 : 0.35}"/>`;
  s += `<rect y="${horizon - H * 0.12}" width="${W}" height="${H * 0.14}" fill="${p.haze}" opacity="${night ? 0.08 : 0.18}"/>`;
  s += `<path d="${ridge(W, horizon - (pa ? H * 0.04 : H * 0.07), pa ? H * 0.05 : H * 0.07, 9, W / (pa ? 12 : 6), H)}" fill="${p.mid}"/>`;

  // 숲 (뒤쪽 소나무 줄)
  const r = rng(17);
  const treeH = pa ? H * 0.26 : H * 0.2;
  const campX = pa ? W * 0.5 : fmt === 'ci' ? W * 0.5 : W * 0.6;
  for (let i = 0; i < (pa ? 34 : 14); i++) {
    const x = r() * W, hh = treeH * (0.55 + r() * 0.5);
    if (Math.abs(x - campX) < (pa ? W * 0.08 : W * 0.16)) continue;   // 텐트 뒤는 비워 둠
    if (pa && Math.abs(x - campX) > W * 0.22) continue;               // 좌우 글자 자리는 비워 둠
    s += pine(x, horizon + H * 0.01, hh, p.pine2, snowy);
  }
  // 땅
  s += `<path d="${hill(W, horizon, pa ? H * 0.04 : H * 0.05, H)}" fill="url(#gr${id})"/>`;

  // 캠프: 텐트 + 가랜드 + 모닥불 + 의자 + 앞쪽 소나무
  const tw = pa ? H * 0.46 : W * 0.34;
  const base = pa ? horizon + H * 0.03 : horizon + H * 0.12;
  const t = bellTent(campX, base, tw, p, lit, snowy, id);
  const ci = fmt === 'ci';
  const fx = campX - tw * (pa ? 0.9 : ci ? 0.72 : 0.85), fs = tw * (pa ? 0.15 : 0.14);
  // 오른쪽 큰 소나무 + 가랜드
  const px = campX + tw * (pa ? 1.25 : 0.95), ph = tw * (pa ? 1.3 : 1.15);
  s += pine(px, base + tw * 0.02, ph, p.pine, snowy);
  s += t.svg;
  s += garland(campX, t.apex - t.h * 0.02, px - ph * 0.04, base - ph * 0.62, tw * 0.12, pa ? 9 : 8, lit, 3 * (pa ? H / 350 : U));
  if (pa) {
    const gx = campX - tw * 1.45, gy = base - tw * 0.55;
    s += `<line x1="${gx}" y1="${gy - tw * 0.03}" x2="${gx}" y2="${base + tw * 0.05}" stroke="#5c3f28" stroke-width="${tw * 0.014}" stroke-linecap="round"/>`;
    s += garland(campX, t.apex - t.h * 0.02, gx, gy, tw * 0.1, 8, lit, 3 * H / 350);
  }
  s += campfire(fx, base + tw * 0.04, fs, lit && kind !== 'rain' || night, id);
  if (!ci) s += chair(fx - fs * 2.4, base + tw * 0.06, fs * 1.5, night ? '#b8563a' : '#d9734f');
  s += lantern(campX + tw * 0.62, base + tw * 0.04, tw * 0.42, lit);
  // 앞쪽 왼쪽 작은 소나무 (파노라마는 양 끝)

  if (kind === 'rain') s += rain(W, H, pa ? 140 : 70, 51, night);
  if (kind === 'snow') s += snowfall(W, H, pa ? 110 : 55, 61);
  s += grain(W, H, `n${id}`, night ? 0.22 : 0.18);
  if (fmt === 'ci') s = `<defs><clipPath id="cc${id}"><circle cx="${W / 2}" cy="${H / 2}" r="${W / 2}"/></clipPath></defs><g clip-path="url(#cc${id})">${s}</g>`;
  return { W, H, svg: `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">${s}</svg>` };
}

// 텐트 단독 (배경 투명). 2×2 '불 켜진 텐트'는 지붕·벽 위에 날짜를 씀 → 가랜드·모닥불 없이 깔끔하게
function tentOnly(lit, fmt) {
  const p = lit ? PAL.clear_n : PAL.clear_d;
  const big = fmt === 'tent';
  const W = big ? 560 : 320, H = big ? 350 : 200;
  const w = W * 0.86, base = H * 0.97;
  const t = bellTent(W / 2, base, w, p, lit, false, `t${lit}${fmt}`, false);
  // 높이에 맞게 viewBox 조정 (꼭대기 폴까지)
  const top = t.apex - t.h * 0.12;
  const s = t.svg + `<rect x="${W * 0.04}" y="${base - 1}" width="${W * 0.92}" height="${H * 0.025}" rx="${H * 0.0125}" fill="${lit ? '#7a5a34' : '#8aa86f'}" opacity=".7"/>`;
  return { W, H, svg: `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 ${top} ${W} ${H - top + H * 0.03}" preserveAspectRatio="xMidYMax meet">${s}</svg>` };
}

module.exports = { scene, tentOnly };
