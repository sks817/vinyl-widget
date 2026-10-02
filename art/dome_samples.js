// 돔글라스 샘플 (앱에는 아직 안 씀): 캠핑장 그림을 유리 안에 넣은 느낌
// node dome_samples.js → samples/*.png
const { chromium } = require('/opt/node-tools/node_modules/playwright');
const { scene } = require('./scene.js');
const fs = require('fs');

const inner = (k, n, fmt) => scene(k, n, fmt).svg.replace(/^<svg[^>]*>/, '').replace(/<\/svg>$/, '');
const skyTop = (k, n) => (scene(k, n, 'sq').svg.match(/stop-color="(#[0-9a-fA-F]{6})"/) || [, '#88aacc'])[1];
// 풍경을 아래로 내리고 위쪽은 하늘색으로: 유리 안 날짜 글자 자리 확보
const lowered = (k, n, extra) => {
  const sc = inner(k, n, 'sq');
  // 좌우로 넓게 볼 때 비는 곳: 하늘색, 땅색으로 채움
  const ground = (sc.match(/id="gr[^"]*"[^>]*><stop offset="0" stop-color="(#[0-9a-fA-F]{6})"/) || [, '#88aa77'])[1];
  return `<rect x="-200" y="${-extra}" width="960" height="${560 + extra}" fill="${skyTop(k, n)}"/><rect x="-200" y="380" width="960" height="300" fill="${ground}"/>${sc}`;
};

// 유리 질감: 가장자리 두께(프레넬), 큰 곡면 반사, 작은 반짝임, 테두리 빛
function glassFx(id, shape, box) {
  const { x, y, w, h } = box;
  return `<defs>
    <radialGradient id="fr${id}" cx=".5" cy=".45" r=".62"><stop offset=".62" stop-color="#fff" stop-opacity="0"/><stop offset=".9" stop-color="#fff" stop-opacity=".18"/><stop offset="1" stop-color="#fff" stop-opacity=".42"/></radialGradient>
    <radialGradient id="sh${id}" cx=".5" cy="1" r=".75"><stop offset="0" stop-color="#000" stop-opacity=".22"/><stop offset=".6" stop-color="#000" stop-opacity="0"/></radialGradient>
    <linearGradient id="hl${id}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#fff" stop-opacity=".85"/><stop offset=".55" stop-color="#fff" stop-opacity=".12"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></linearGradient>
    <linearGradient id="hl2${id}" x1="1" y1="1" x2="0" y2="0"><stop offset="0" stop-color="#fff" stop-opacity=".5"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></linearGradient>
  </defs>
  <g clip-path="url(#cl${id})">
    <rect x="${x}" y="${y}" width="${w}" height="${h}" fill="url(#sh${id})"/>
    <rect x="${x}" y="${y}" width="${w}" height="${h}" fill="url(#fr${id})"/>
    <path d="M${x + w * 0.16} ${y + h * 0.5} C${x + w * 0.14} ${y + h * 0.26} ${x + w * 0.3} ${y + h * 0.1} ${x + w * 0.5} ${y + h * 0.075}
             C${x + w * 0.36} ${y + h * 0.14} ${x + w * 0.24} ${y + h * 0.28} ${x + w * 0.22} ${y + h * 0.5} Z" fill="url(#hl${id})"/>
    <path d="M${x + w * 0.86} ${y + h * 0.62} C${x + w * 0.88} ${y + h * 0.74} ${x + w * 0.8} ${y + h * 0.84} ${x + w * 0.7} ${y + h * 0.88}
             C${x + w * 0.78} ${y + h * 0.8} ${x + w * 0.83} ${y + h * 0.72} ${x + w * 0.86} ${y + h * 0.62} Z" fill="url(#hl2${id})"/>
    <ellipse cx="${x + w * 0.74}" cy="${y + h * 0.2}" rx="${w * 0.035}" ry="${h * 0.02}" fill="#fff" opacity=".7" transform="rotate(35 ${x + w * 0.74} ${y + h * 0.2})"/>
  </g>
  <path d="${shape}" fill="none" stroke="#fff" stroke-opacity=".55" stroke-width="${w * 0.006}"/>
  <path d="${shape}" fill="none" stroke="#000" stroke-opacity=".12" stroke-width="${w * 0.016}" transform="translate(0 ${w * 0.004})"/>`;
}

// ① 스노우볼: 유리구 + 원목 받침
function snowGlobe(k, n, size = 560) {
  const W = size, H = size, id = `sg${k}${n}`;
  const cx = W / 2, cy = H * 0.44, r = W * 0.4;
  const shape = `M${cx - r} ${cy} a${r} ${r} 0 1 1 ${2 * r} 0 a${r} ${r} 0 1 1 ${-2 * r} 0`;
  const bx = cx - W * 0.33, by = H * 0.76, bw = W * 0.66, bh = H * 0.19;
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W} ${H}" width="100%" height="100%">
  <defs><clipPath id="cl${id}"><path d="${shape}"/></clipPath>
    <linearGradient id="wd${id}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#9a6a45"/><stop offset=".5" stop-color="#7a4f31"/><stop offset="1" stop-color="#5c3a23"/></linearGradient>
    <linearGradient id="br${id}" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#b8935a"/><stop offset=".5" stop-color="#f1d79c"/><stop offset="1" stop-color="#a88349"/></linearGradient></defs>
  <ellipse cx="${cx}" cy="${H * 0.965}" rx="${W * 0.36}" ry="${H * 0.025}" fill="#000" opacity=".22"/>
  <g clip-path="url(#cl${id})"><svg x="${cx - r}" y="${cy - r}" width="${2 * r}" height="${2 * r}" viewBox="-70 -130 700 700">${lowered(k, n, 130)}</svg></g>
  ${glassFx(id, shape, { x: cx - r, y: cy - r, w: 2 * r, h: 2 * r })}
  <path d="M${bx + bw * 0.06} ${by} L${bx + bw * 0.94} ${by} L${bx + bw} ${by + bh} Q${cx} ${by + bh * 1.12} ${bx} ${by + bh} Z" fill="url(#wd${id})"/>
  <rect x="${bx + bw * 0.04}" y="${by - H * 0.012}" width="${bw * 0.92}" height="${H * 0.03}" rx="${H * 0.012}" fill="url(#br${id})"/>
  <path d="M${bx + bw * 0.12} ${by + bh * 0.35} L${bx + bw * 0.88} ${by + bh * 0.35}" stroke="#fff" stroke-opacity=".12" stroke-width="${H * 0.006}"/>
</svg>`;
}

// ② 유리 클로슈 돔: 아치형 유리 덮개 + 손잡이 + 원목 접시
function cloche(k, n, size = 560) {
  const W = size, H = size, id = `cl${k}${n}`;
  const L = W * 0.12, R = W * 0.88, top = H * 0.12, bot = H * 0.84, rr = (R - L) / 2;
  const shape = `M${L} ${bot} L${L} ${top + rr} A${rr} ${rr} 0 0 1 ${R} ${top + rr} L${R} ${bot} Z`;
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W} ${H}" width="100%" height="100%">
  <defs><clipPath id="cl${id}"><path d="${shape}"/></clipPath>
    <linearGradient id="wd${id}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#a87a52"/><stop offset="1" stop-color="#6b4429"/></linearGradient>
    <radialGradient id="kn${id}" cx=".35" cy=".3" r=".7"><stop offset="0" stop-color="#fff" stop-opacity=".95"/><stop offset=".5" stop-color="#dfe6ee" stop-opacity=".7"/><stop offset="1" stop-color="#9fb0c2" stop-opacity=".6"/></radialGradient></defs>
  <ellipse cx="${W / 2}" cy="${H * 0.95}" rx="${W * 0.44}" ry="${H * 0.03}" fill="#000" opacity=".2"/>
  <g clip-path="url(#cl${id})"><svg x="${L}" y="${top}" width="${R - L}" height="${bot - top}" viewBox="0 -110 560 ${560 * (bot - top) / (R - L) + 110}" preserveAspectRatio="xMidYMax slice">${lowered(k, n, 110)}</svg></g>
  ${glassFx(id, shape, { x: L, y: top, w: R - L, h: bot - top })}
  <ellipse cx="${W / 2}" cy="${top - H * 0.015}" rx="${W * 0.045}" ry="${H * 0.04}" fill="url(#kn${id})" stroke="#fff" stroke-opacity=".5"/>
  <rect x="${W * 0.06}" y="${bot - H * 0.005}" width="${W * 0.88}" height="${H * 0.075}" rx="${H * 0.035}" fill="url(#wd${id})"/>
  <rect x="${W * 0.08}" y="${bot}" width="${W * 0.84}" height="${H * 0.012}" rx="${H * 0.006}" fill="#fff" opacity=".18"/>
</svg>`;
}

// ③ 1×4 유리 캡슐: 알약 모양 유리관 안에 파노라마
function capsule(k, n, W = 1400, H = 300, cornerPct = 30, fmt = 'pa') {
  const id = `cp${k}${n}${fmt}${cornerPct}`, r = Math.min(W, H) * cornerPct / 100;
  const shape = `M${r} 0 L${W - r} 0 A${r} ${r} 0 0 1 ${W} ${r} L${W} ${H - r} A${r} ${r} 0 0 1 ${W - r} ${H} L${r} ${H} A${r} ${r} 0 0 1 0 ${H - r} L0 ${r} A${r} ${r} 0 0 1 ${r} 0 Z`;
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W} ${H}" width="100%" height="100%" preserveAspectRatio="none">
  <defs><clipPath id="cl${id}"><path d="${shape}"/></clipPath>
    <linearGradient id="tb${id}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff" stop-opacity=".55"/><stop offset=".18" stop-color="#fff" stop-opacity=".08"/><stop offset=".5" stop-color="#fff" stop-opacity="0"/><stop offset=".86" stop-color="#000" stop-opacity=".0"/><stop offset="1" stop-color="#000" stop-opacity=".22"/></linearGradient>
    <linearGradient id="sd${id}" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#fff" stop-opacity=".35"/><stop offset="${fmt === 'pa' ? .06 : .08}" stop-color="#fff" stop-opacity="0"/><stop offset="${fmt === 'pa' ? .94 : .92}" stop-color="#fff" stop-opacity="0"/><stop offset="1" stop-color="#fff" stop-opacity=".3"/></linearGradient></defs>
  <g clip-path="url(#cl${id})">
    ${fmt === 'pa' ? `<svg x="0" y="${-(350 * W / 1400 - H) / 2}" width="${W}" height="${350 * W / 1400}" viewBox="0 0 1400 350">${inner(k, n, 'pa')}</svg>`
                   : `<svg x="0" y="0" width="${W}" height="${H}" viewBox="0 0 560 560">${inner(k, n, 'sq')}</svg>`}
    <rect width="${W}" height="${H}" fill="url(#tb${id})"/><rect width="${W}" height="${H}" fill="url(#sd${id})"/>
    <defs><filter id="bl${id}" x="-5%" y="-50%" width="110%" height="200%"><feGaussianBlur stdDeviation="${Math.min(W, H) * 0.015}"/></filter></defs>
    <path d="M${Math.max(r * 0.75, W * 0.06)} ${Math.min(W, H) * 0.14} Q${W * 0.32} ${Math.min(W, H) * 0.06} ${W * 0.6} ${Math.min(W, H) * 0.09}" stroke="#fff" stroke-opacity=".55" stroke-width="${Math.min(W, H) * 0.05}" stroke-linecap="round" fill="none" filter="url(#bl${id})"/>
    <path d="M${W * 0.64} ${Math.min(W, H) * 0.09} L${W * 0.66} ${Math.min(W, H) * 0.09}" stroke="#fff" stroke-opacity=".5" stroke-width="${Math.min(W, H) * 0.045}" stroke-linecap="round" filter="url(#bl${id})"/>
  </g>
  <path d="${shape}" fill="none" stroke="#fff" stroke-opacity=".6" stroke-width="3"/>
</svg>`;
}

// ---------- 샘플 보드 ----------
const FONTS = `<link href="file://${process.env.FONTS_CSS || ''}" rel="stylesheet">`;  // FONTS_CSS=Noto Sans/Serif KR @font-face 파일
const WALL_L = 'linear-gradient(165deg,#3a4a7a 0%,#7a6aa8 38%,#e59a8a 72%,#f4c78e 100%)';
const WALL_D = 'radial-gradient(90% 50% at 80% 15%,#3d5a8f 0%,transparent 60%),linear-gradient(180deg,#141c2e 0%,#22365a 60%,#2d4a6e 100%)';
const WALL_G = 'radial-gradient(120% 70% at 20% 10%,#f6f3d8 0%,transparent 60%),linear-gradient(170deg,#cfe3c4 0%,#9fc59a 45%,#6f9f78 100%)';
const W = { clear_d: ['22°', '맑음', '14°/24°'], clear_n: ['16°', '맑음', '14°/24°'], snow_n: ['-4°', '눈', '-6°/0°'], rain_d: ['17°', '비', '15°/18°'], cloudy_d: ['19°', '흐림', '15°/21°'], snow_d: ['-2°', '눈', '-5°/1°'], rain_n: ['14°', '비', '13°/17°'] };
const dark = (wk, wall) => wall !== WALL_G;

function w2x2(kind, wk, wall) {
  const [k, n] = [wk.split('_')[0], wk.endsWith('_n')];
  const art = kind === 'globe' ? snowGlobe(k, n) : cloche(k, n);
  const tc = dark(wk, wall) ? '#fffbff' : '#1a1c19', sc = dark(wk, wall) ? 'rgba(255,255,255,.75)' : 'rgba(26,28,25,.7)';
  const sh = dark(wk, wall) ? 'text-shadow:0 1px 6px rgba(0,0,0,.35)' : 'text-shadow:0 1px 6px rgba(255,255,255,.4)';
  const dateTop = kind === 'globe' ? 26 : 30;
  const onArt = n || k === 'rain' ? '#FBF1DC' : '#23262E';
  return `<div style="position:relative;width:170px;height:170px;flex:none;${sh}">
    <div style="position:absolute;left:14px;top:4px;width:126px;height:126px;left:22px">${art}</div>
    <div style="position:absolute;left:0;right:0;top:${dateTop}px;text-align:center;font:900 30px/1 'Noto Serif KR';color:${onArt};text-shadow:none">10.2</div>
    <div style="position:absolute;left:0;right:0;top:${dateTop + 33}px;text-align:center;font:500 9px/1 'Noto Sans KR';color:${onArt};opacity:.85;text-shadow:none">금요일</div>
    <div style="position:absolute;left:8px;right:8px;bottom:6px;height:32px;display:flex;font:700 13px 'Noto Sans KR';color:${tc}">
      <div style="flex:1;text-align:center;line-height:32px">${W[wk][0]}</div><div style="flex:1;text-align:center;line-height:32px;font-weight:500">${W[wk][1]}</div><div style="flex:1;text-align:center;line-height:32px;font-weight:400;color:${sc}">${W[wk][2]}</div></div></div>`;
}

function w1x4(wk, shadow = false) {
  const [k, n] = [wk.split('_')[0], wk.endsWith('_n')];
  const c = n || k === 'rain' ? '#FBF1DC' : '#23262E', c2 = n || k === 'rain' ? '#E2D7C6' : '#3E434D';
  return `<div style="position:relative;width:340px;height:74px;flex:none">
    <div style="position:absolute;inset:0;${shadow ? 'filter:drop-shadow(0 6px 10px rgba(0,0,0,.28)) drop-shadow(0 1px 2px rgba(0,0,0,.18));' : ''}">${capsule(k, n, 1400, 300, 30)}</div>
    <div style="position:absolute;left:26px;top:11px;font:900 32px/1 'Noto Serif KR';color:${c}">10.2</div>
    <div style="position:absolute;left:28px;top:47px;font:500 10px/1 'Noto Sans KR';color:${c2}">금요일</div>
    <div style="position:absolute;right:26px;top:12px;font:900 28px/1 'Noto Serif KR';color:${c}">${W[wk][0]}</div>
    <div style="position:absolute;right:26px;top:47px;font:500 10px/1 'Noto Sans KR';color:${c2}">${W[wk][1]}&nbsp;&nbsp;${W[wk][2]}</div></div>`;
}

function sq2x2(wk, wall, shadow = false) {
  const [k, n] = [wk.split('_')[0], wk.endsWith('_n')];
  const tc = wall === WALL_G ? '#1a1c19' : '#fffbff', sc = wall === WALL_G ? 'rgba(26,28,25,.7)' : 'rgba(255,255,255,.75)';
  const onArt = n || k === 'rain' ? '#FBF1DC' : '#23262E', onSub = n || k === 'rain' ? '#D9CFC0' : '#3E434D';
  const D = 118, L = (170 - D) / 2;
  return `<div style="position:relative;width:170px;height:170px;flex:none">
    <div style="position:absolute;left:${L}px;top:8px;width:${D}px;height:${D}px;${shadow ? 'filter:drop-shadow(0 6px 10px rgba(0,0,0,.28)) drop-shadow(0 1px 2px rgba(0,0,0,.18));' : ''}">${capsule(k, n, 560, 560, 11, 'sq')}</div>
    <div style="position:absolute;left:${L + D * 0.08}px;top:${8 + D * 0.07}px;font:900 ${D * 0.3}px/1 'Noto Serif KR';color:${onArt}">10.2</div>
    <div style="position:absolute;left:${L + D * 0.09}px;top:${8 + D * 0.41}px;font:500 ${D * 0.085}px/1 'Noto Sans KR';color:${onSub}">금요일</div>
    <div style="position:absolute;left:8px;right:8px;bottom:6px;height:32px;display:flex;font:700 13px 'Noto Sans KR';color:${tc};text-shadow:0 1px 6px ${wall === WALL_G ? 'rgba(255,255,255,.4)' : 'rgba(0,0,0,.35)'}">
      <div style="flex:1;text-align:center;line-height:32px">${W[wk][0]}</div><div style="flex:1;text-align:center;line-height:32px;font-weight:500">${W[wk][1]}</div><div style="flex:1;text-align:center;line-height:32px;font-weight:400;color:${sc}">${W[wk][2]}</div></div></div>`;
}

const band = (wall, inner, label, lc) => `<div style="background:${wall};border-radius:26px;padding:16px 4px 12px;margin:0 12px 12px">
  <div style="display:flex;flex-wrap:wrap;gap:10px 6px;justify-content:center">${inner}</div>
  <div style="text-align:center;font:700 12px 'Noto Sans KR';color:${lc};margin-top:8px;opacity:.85">${label}</div></div>`;
const page = (title, sub, body) => `<!doctype html><meta charset=utf-8>${FONTS}<style>*{margin:0;box-sizing:border-box}body{width:390px;background:#f3f1f6;font-family:'Noto Sans KR'}</style>
  <div style="padding:30px 22px 16px"><div style="font:900 24px/1.3 'Noto Sans KR';color:#1b1b1f">${title}</div><div style="font:500 13px/1.5 'Noto Sans KR';color:#555;margin-top:6px">${sub}</div></div>${body}<div style="height:10px"></div>`;

const cap = (t) => `<div style="width:100%;text-align:center;font:700 11px 'Noto Sans KR';color:rgba(255,255,255,.85);margin:2px 0 -4px">${t}</div>`;
const capD = (t) => `<div style="width:100%;text-align:center;font:700 11px 'Noto Sans KR';color:rgba(26,28,25,.75);margin:2px 0 -4px">${t}</div>`;
const boards = {
  sample_capsule: page('유리 캡슐 · 그림자 비교', '모서리 곡률은 기존 그대로 (2×2 11%, 1×4 30%). 왼쪽/위 = 그림자 없음, 오른쪽/아래 = 그림자 있음',
    band(WALL_L, cap('2×2 · 그림자 없음 / 있음') + sq2x2('clear_d', WALL_L) + sq2x2('clear_d', WALL_L, true) + cap('1×4 · 그림자 없음') + w1x4('clear_d') + cap('1×4 · 그림자 있음') + w1x4('clear_d', true), '노을 배경화면', '#fff') +
    band(WALL_D, cap('2×2 · 그림자 없음 / 있음') + sq2x2('snow_n', WALL_D) + sq2x2('snow_n', WALL_D, true) + cap('1×4 · 그림자 없음') + w1x4('clear_n') + cap('1×4 · 그림자 있음') + w1x4('clear_n', true), '밤하늘 배경화면', '#fff') +
    band(WALL_G, capD('2×2 · 그림자 없음 / 있음') + sq2x2('cloudy_d', WALL_G) + sq2x2('cloudy_d', WALL_G, true) + capD('1×4 · 그림자 없음') + w1x4('snow_d') + capD('1×4 · 그림자 있음') + w1x4('snow_d', true), '숲 배경화면', '#1a1c19')),
};

(async () => {
  fs.mkdirSync(__dirname + '/samples', { recursive: true });
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 390, height: 800 }, deviceScaleFactor: 2.77 });
  for (const [name, html] of Object.entries(boards)) {
    const f = `${__dirname}/samples/${name}.html`;
    fs.writeFileSync(f, html);
    await p.goto('file://' + f);
    await p.evaluate(() => document.fonts.ready); await p.waitForTimeout(800);
    await p.screenshot({ path: `${__dirname}/samples/${name}.png`, fullPage: true });
    fs.unlinkSync(f);
  }
  await b.close(); console.log('ok');
})();
