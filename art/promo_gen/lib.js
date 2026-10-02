// 위젯 코드(WeatherScene / WideScene / CharacterLayouts / MusicWidget)의 치수·색을 그대로 옮긴 캔버스 렌더러
const SERIF = (s) => `700 ${s}px 'Noto Serif KR'`;
const SANS = (s) => `700 ${s}px 'Noto Sans KR'`;
const MED = (s) => `500 ${s}px 'Noto Sans KR'`;
const REG = (s) => `400 ${s}px 'Noto Sans KR'`;
const BLACK = (s) => `900 ${s}px 'Noto Sans KR'`;
const IMG = {};
const load = (n) => IMG[n] ? Promise.resolve(IMG[n]) : new Promise((r) => { const i = new Image(); i.onload = () => { IMG[n] = i; r(i); }; i.onerror = () => { console.log('missing ' + n); r(null); }; i.src = n + '.webp'; });

const WX = {
  clear_day: { kind: 'clear', night: false, temp: '22°', cond: '맑음', range: '14°/24°', fun: 'w_fun_clear_day', quip: '광합성 하기 딱 좋은 날', ic: 'w_ic3_clear_day' },
  cloudy_day: { kind: 'cloudy', night: false, temp: '19°', cond: '흐림', range: '15°/21°', fun: 'w_fun_cloudy', quip: '하늘도 오늘은 귀찮대', ic: 'w_ic3_cloudy' },
  rain_day: { kind: 'rain', night: false, temp: '17°', cond: '비', range: '15°/18°', fun: 'w_fun_rain', quip: '하늘이 운다 ㅠㅠ 우산!', ic: 'w_ic3_rain' },
  snow_day: { kind: 'snow', night: false, temp: '-2°', cond: '눈', range: '-5°/1°', fun: 'w_fun_snow', quip: '덜덜.. 패딩 필수', ic: 'w_ic3_snow' },
  clear_night: { kind: 'clear', night: true, temp: '16°', cond: '맑음', range: '14°/24°', fun: 'w_fun_clear_night', quip: '꿀잠 예약 완료', ic: 'w_ic3_clear_night' },
  cloudy_night: { kind: 'cloudy', night: true, temp: '15°', cond: '구름 많음', range: '13°/20°', fun: 'w_fun_partly_night', quip: '구름이 달 가리는 중', ic: 'w_ic3_partly_night' },
  rain_night: { kind: 'rain', night: true, temp: '14°', cond: '비', range: '13°/17°', fun: 'w_fun_rain', quip: '하늘이 운다 ㅠㅠ 우산!', ic: 'w_ic3_rain' },
  snow_night: { kind: 'snow', night: true, temp: '-4°', cond: '눈', range: '-6°/0°', fun: 'w_fun_snow', quip: '덜덜.. 패딩 필수', ic: 'w_ic3_snow' },
};
const MD = '10.2', WK = '금요일', WKS = '금';

function canvas(el, w, h) {
  const c = document.createElement('canvas'); const k = 3;
  c.width = Math.round(w * k); c.height = Math.round(h * k); c.style.width = w + 'px'; c.style.height = h + 'px'; c.style.display = 'block';
  el.append(c); const x = c.getContext('2d'); x.scale(k, k); return x;
}
function txt(x, s, X, Y, font, col, align = 'left') { x.font = font; x.textAlign = align; x.fillStyle = col; x.fillText(s, X, Y); }
const sizeOf = (font) => parseFloat(font.split(' ')[1]);
// 안드로이드 Paint.fontMetrics와 같은 값 (세리프 숫자 = Noto Serif, 한글 = Noto Sans CJK)
const ASC = (font) => font.includes('Serif') ? 1.07 : 1.16, DESC = 0.29;
const topT = (x, s, X, Y, font, col, al) => txt(x, s, X, Y + sizeOf(font) * ASC(font), font, col, al);
const midT = (x, s, X, Y, font, col, al) => txt(x, s, X, Y + sizeOf(font) * (ASC(font) - DESC) / 2, font, col, al);
function rr(x, l, t, w, h, r) { x.beginPath(); x.roundRect(l, t, w, h, r); }
const lum = (hex) => { const n = parseInt(hex.slice(1, 7), 16); const ch = [n >> 16 & 255, n >> 8 & 255, n & 255].map(v => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }); return .2126 * ch[0] + .7152 * ch[1] + .0722 * ch[2]; };

function cover(x, img, l, t, w, h) {
  const ar = w / h, br = img.width / img.height;
  if (br > ar) { const sw = img.height * ar; x.drawImage(img, (img.width - sw) / 2, 0, sw, img.height, l, t, w, h); }
  else { const sh = img.width / ar; x.drawImage(img, 0, (img.height - sh) / 2, img.width, sh, l, t, w, h); }
}
function fitBottom(img, l, t, w, h) { const r = img.width / img.height; let ww = w, hh = ww / r; if (hh > h) { hh = h; ww = hh * r; } return [l + (w - ww) / 2, t + h - hh, ww, hh]; }

// 유리 캡슐 + 그림자 (GlassArt)
function artShadow(x, l, t, w, h, rad) {
  const s = Math.min(w, h); x.save(); x.filter = `blur(${s * 0.05}px)`; x.fillStyle = 'rgba(0,0,0,.26)'; rr(x, l + s * .03, t + s * .06, w - s * .06, h - s * .025, rad); x.fill(); x.restore();
}
function glassArt(x, l, t, w, h, rad) {
  const s = Math.min(w, h); x.save(); rr(x, l, t, w, h, rad); x.clip();
  let g = x.createLinearGradient(0, t, 0, t + h); g.addColorStop(0, 'rgba(255,255,255,.55)'); g.addColorStop(.18, 'rgba(255,255,255,.08)'); g.addColorStop(.5, 'rgba(255,255,255,0)'); g.addColorStop(.86, 'rgba(0,0,0,0)'); g.addColorStop(1, 'rgba(0,0,0,.22)');
  x.fillStyle = g; x.fillRect(l, t, w, h);
  const e = Math.max(s * .06, Math.min(w, h * 4) * .06);
  g = x.createLinearGradient(l, 0, l + e, 0); g.addColorStop(0, 'rgba(255,255,255,.35)'); g.addColorStop(1, 'rgba(255,255,255,0)'); x.fillStyle = g; x.fillRect(l, t, e, h);
  g = x.createLinearGradient(l + w, 0, l + w - e, 0); g.addColorStop(0, 'rgba(255,255,255,.3)'); g.addColorStop(1, 'rgba(255,255,255,0)'); x.fillStyle = g; x.fillRect(l + w - e, t, e, h);
  const span = w > h * 2 ? Math.min(w * .6, h * 1.1) / w : .6, sw = s * .05, y = t + s * .12, x0 = l + Math.max(rad * .75, w * .06);
  x.filter = `blur(${sw * .5}px)`; x.strokeStyle = 'rgba(255,255,255,.55)'; x.lineWidth = sw; x.lineCap = 'round';
  x.beginPath(); x.moveTo(x0, y + s * .02); x.quadraticCurveTo(l + w * span * .53, y - s * .06, l + w * span, y - s * .03); x.stroke();
  x.restore();
  x.strokeStyle = 'rgba(255,255,255,.6)'; x.lineWidth = Math.max(1, s * .008); rr(x, l + .5, t + .5, w - 1, h - 1, rad); x.stroke();
}

// ---------- 2×2 날씨 (170dp 기준) ----------
const PAD = 8, ROW = 44, ARM = 1.18;
async function weather2x2(el, design, wk, o = {}) {
  const W = o.size || 170, H = W, k = W / 170, q = o.quirky !== false;
  const fg = o.fg || '#FFFFFF', sub = o.sub || 'rgba(255,255,255,.72)', custom = !!o.fg;
  const x = canvas(el, W, H), d = WX[wk];
  const pad = PAD * k, row = ROW * k, areaW = W - pad * 2, areaH = H - pad * 2 - row, D = Math.min(areaH, areaW / ARM);
  const sq = { l: pad + areaW / 2 - D / 2, t: pad + areaH / 2 - D / 2 };
  const dark = !d.night && d.kind !== 'rain';
  const ink = q && d.kind === 'snow' && !d.night;   // 눈 오는 낮: 하얀 그림 위엔 진한 글자
  const artMain = custom ? fg : ink ? '#2B2622' : q ? '#FFFFFF' : dark ? '#23262E' : '#FBF1DC';
  const artSub = custom ? sub : ink ? '#4A433E' : q ? 'rgba(255,255,255,.9)' : dark ? '#3E434D' : '#D9CFC0';
  const sceneName = (p) => `w_${p}${q ? 'q' : ''}_${d.kind}_${d.night ? 'night' : 'day'}`;
  async function bottomRow(icon = true) {
    const y = H - pad - row / 2, xs = [0, 1, 2].map(i => pad + (W - pad * 2) * (i + .5) / 3);
    const tS = 19 * k; x.font = SERIF(tS); const tw = x.measureText(d.temp).width; const ic = icon ? row * .82 : 0, gap = icon ? tS * .15 : 0;
    const st = xs[0] - (ic + gap + tw) / 2;
    if (icon) { const im = await load(q ? d.fun : d.ic); if (im) x.drawImage(im, st, y - ic / 2, ic, ic); }
    midT(x, d.temp, st + ic + gap + tw / 2, y, SERIF(tS), fg, 'center');
    midT(x, d.cond, xs[1], y, MED(14 * k), fg, 'center');
    midT(x, d.range, xs[2], y, REG(13 * k), sub, 'center');
  }
  if (design === 'jacket') {
    const ins = 4 * k, l = ins, t = ins, w = W - ins * 2, h = H - ins * 2, S = Math.min(w, h), rad = S * (o.corner ?? 11) / 100;
    artShadow(x, l, t, w, h, rad);
    x.save(); rr(x, l, t, w, h, rad); x.clip();
    cover(x, await load(sceneName('sc')), l, t, w, h);
    const st = t + h - h * .52; let g = x.createLinearGradient(0, st, 0, t + h); g.addColorStop(0, 'rgba(0,0,0,0)'); g.addColorStop(.45, 'rgba(0,0,0,.3)'); g.addColorStop(1, 'rgba(0,0,0,.62)'); x.fillStyle = g; x.fillRect(l, st, w, t + h - st);
    if (lum(artMain.slice(0, 7)) > .45 && artMain !== '#FBF1DC') { g = x.createRadialGradient(l, t, 0, l, t, S * .8); g.addColorStop(0, 'rgba(0,0,0,.32)'); g.addColorStop(.5, 'rgba(0,0,0,.16)'); g.addColorStop(1, 'rgba(0,0,0,0)'); x.fillStyle = g; x.fillRect(l, t, w, h); }
    x.restore(); glassArt(x, l, t, w, h, rad);
    const m = S * .08;
    topT(x, MD, l + m, t + S * .065, SERIF(S * .22), artMain);
    topT(x, WK, l + m * 1.06, t + S * .30, MED(S * .075), artSub);
    const bottom = t + h - S * .07;
    txt(x, d.temp, l + m, bottom, SERIF(S * .23), custom ? fg : '#fff');
    txt(x, d.range, l + w - m, bottom, REG(S * .072), custom ? sub : 'rgba(255,255,255,.85)', 'right');
  } else if (design === 'tent') {
    const lit = d.night || d.kind === 'rain'; const im = await load(q ? (lit ? 'w_tentq_glow' : 'w_tentq_day') : (lit ? 'w_tent_glow' : 'w_tent_day'));
    const [rl, rt, rw, rh] = fitBottom(im, pad, sq.t, areaW, D); x.drawImage(im, rl, rt, rw, rh);
    topT(x, WK, rl + rw / 2, rt + rh * .28, MED(rw * .065), custom ? sub : '#5A3214', 'center');
    topT(x, MD, rl + rw / 2, rt + rh * .37, SERIF(rw * .17), custom ? fg : '#3E2210', 'center');
    await bottomRow();
  } else if (design === 'circle') {
    x.drawImage(await load(sceneName('ci')), sq.l, sq.t, D, D);
    if (q || custom) { x.save(); x.beginPath(); x.arc(sq.l + D / 2, sq.t + D / 2, D / 2, 0, 7); x.clip(); const g = x.createLinearGradient(0, sq.t, 0, sq.t + D * .55); g.addColorStop(0, 'rgba(0,0,0,.3)'); g.addColorStop(1, 'rgba(0,0,0,0)'); x.fillStyle = g; x.fillRect(sq.l, sq.t, D, D); x.restore(); }
    topT(x, MD, sq.l + D / 2, sq.t + D * .07, SERIF(D * .27), artMain, 'center');
    topT(x, WK, sq.l + D / 2, sq.t + D * .385, MED(D * .07), artSub, 'center');
    await bottomRow(false);
  } else if (design === 'bigdate') {
    topT(x, MD, pad + areaW * .02, sq.t, SERIF(D * .42), fg);
    topT(x, WK, pad + areaW * .04, sq.t + D * .5, MED(D * .1), sub);
    const lit = d.night || d.kind === 'rain'; const im = await load(q ? (lit ? 'w_tentq_icon' : 'w_tentq_icon_day') : (lit ? 'w_tent_icon' : 'w_tent_icon_day'));
    const b = fitBottom(im, pad + areaW * .4, sq.t + D * .45, areaW * .6, D * .55); x.drawImage(im, ...b);
    await bottomRow();
  } else { // 캐릭터 포스터 / 카드 / 헤드라인 (CharacterLayouts.draw2x2)
    const S = W, m = S * .082, fun = await load(d.fun);
    const ch = (cx, cy, cs) => x.drawImage(fun, cx - cs / 2, cy - cs / 2, cs, cs);
    if (design === 'poster') {
      topT(x, `${MD} ${WKS}`, m, m, MED(S * .07), sub);
      const cs = S * .56; ch(S / 2, m + S * .02 + cs / 2, cs);
      txt(x, d.temp, S / 2, S - m, BLACK(S * .235), fg, 'center');
    } else if (design === 'card') {
      const cc = cardColors(d, o.card); const g = x.createLinearGradient(0, 0, S * .4, S); g.addColorStop(0, cc[0]); g.addColorStop(1, cc[1]);
      x.fillStyle = g; rr(x, 0, 0, S, S, S * .14); x.fill();
      const ink = custom ? fg : cc[2], soft = custom ? sub : cc[3];
      topT(x, `${MD} ${WK}`, m, m, MED(S * .07), soft);
      const cs = S * .5; ch(S - S * .035 - cs / 2, S * .06 + cs / 2, cs);
      txt(x, d.temp, m, S - m - S * .12, BLACK(S * .26), ink);
      txt(x, d.quip, m, S - m, SANS(S * .07), soft);
    } else {
      if (o.bg) { x.fillStyle = o.bg; rr(x, 0, 0, S, S, 28 * k); x.fill(); }
      topT(x, `${MD} ${WK}`, m, m, MED(S * .07), sub);
      const words = d.quip.split(' '); x.font = BLACK(S * .112); const lines = []; let cur = '';
      for (const wd of words) { const tst = cur ? cur + ' ' + wd : wd; if (x.measureText(tst).width > S - m * 2 && cur) { lines.push(cur); cur = wd; } else cur = tst; }
      lines.push(cur);
      lines.slice(0, 2).forEach((ln, i) => topT(x, ln, m, m + S * .11 + i * S * .112 * 1.3, BLACK(S * .112), fg));
      const cs = S * .45; ch(S - S * .035 - cs / 2, S - S * .035 - cs / 2, cs);
      txt(x, d.temp, m, S - m, BLACK(S * .21), fg);
    }
  }
}
function cardColors(d, base) {
  if (base) {
    const n = parseInt(base.slice(1), 16), mix = v => Math.round(v + (255 - v) * .16), top = `rgb(${mix(n >> 16 & 255)},${mix(n >> 8 & 255)},${mix(n & 255)})`;
    return lum(base) > .5 ? [top, base, '#1E2128', 'rgba(30,33,40,.6)'] : [top, base, '#FFFFFF', 'rgba(255,255,255,.7)'];
  }
  const n = d.night;
  return ({
    rain: n ? ['#2C3A52', '#161E2D', '#F2F6FB', 'rgba(242,246,251,.65)'] : ['#DDE9F6', '#A9C2DE', '#1E2C3D', 'rgba(30,44,61,.6)'],
    snow: n ? ['#3D4A72', '#202A4A', '#F4F7FF', 'rgba(244,247,255,.65)'] : ['#F4F8FC', '#D3E2F1', '#22324A', 'rgba(34,50,74,.6)'],
    cloudy: n ? ['#3A4256', '#1E2330', '#F3F4F7', 'rgba(243,244,247,.65)'] : ['#EEF1F4', '#C9D2DC', '#26303A', 'rgba(38,48,58,.6)'],
    clear: n ? ['#34447A', '#1B2240', '#FFFBEF', 'rgba(255,251,239,.65)'] : ['#FFF1C2', '#FFD27A', '#3A2E12', 'rgba(58,46,18,.6)'],
  })[d.kind];
}

// ---------- 1×4 날씨 ----------
async function weatherWide(el, design, wk, o = {}) {
  const W = o.w || 350, H = o.h || 80, q = o.quirky !== false, x = canvas(el, W, H), d = WX[wk];
  const fg = o.fg || '#FFFFFF', sub = o.sub || 'rgba(255,255,255,.72)', custom = !!o.fg, small = (v) => Math.max(v, 12);
  if (design === 'pano') {
    const p = 6, l = p, t = p, w = W - p * 2, h = H - p * 2, rad = h * .3;
    artShadow(x, l, t, w, h, rad);
    x.save(); rr(x, l, t, w, h, rad); x.clip(); cover(x, await load(`w_pa${q ? 'q' : ''}_${d.kind}_${d.night ? 'night' : 'day'}`), l, t, w, h);
    const dark = !d.night && d.kind !== 'rain', ink = q && d.kind === 'snow' && !d.night, main = custom ? fg : ink ? '#2B2622' : q ? '#fff' : dark ? '#23262E' : '#FBF1DC', soft = custom ? sub : ink ? '#4A433E' : q ? 'rgba(255,255,255,.9)' : dark ? '#3E434D' : '#E2D7C6';
    if (lum(main.slice(0, 7)) > .45 && main !== '#FBF1DC') { const e = h * 1.6; let g = x.createLinearGradient(l, 0, l + e, 0); g.addColorStop(0, 'rgba(0,0,0,.32)'); g.addColorStop(1, 'rgba(0,0,0,0)'); x.fillStyle = g; x.fillRect(l, t, e, h); g = x.createLinearGradient(l + w, 0, l + w - e, 0); g.addColorStop(0, 'rgba(0,0,0,.32)'); g.addColorStop(1, 'rgba(0,0,0,0)'); x.fillStyle = g; x.fillRect(l + w - e, t, e, h); }
    x.restore(); glassArt(x, l, t, w, h, rad);
    const big = h * .46, sm = small(h * .17), top = t + (h - (big + h * .05 + sm)) / 2, y1 = top + big / 2, y2 = top + big + h * .05 + sm / 2;
    midT(x, MD, l + h * .3, y1, SERIF(big), main); midT(x, WK, l + h * .32, y2, MED(sm), soft);
    const right = l + w - h * .3;
    midT(x, d.temp, right, y1, SERIF(big), main, 'right'); midT(x, d.range, right, y2, MED(sm), soft, 'right');
    if (!q) { x.font = SERIF(big); const tw = x.measureText(d.temp).width; const ic = big * 1.05; const im = await load(d.ic); x.drawImage(im, right - tw - big * .08 - ic, y1 - ic / 2, ic, ic); }
  } else if (design === 'tent') {
    const h = H; let px = h * .1; const lit = d.night || d.kind === 'rain'; const im = await load(q ? (lit ? 'w_tentq_glow' : 'w_tentq_day') : (lit ? 'w_tent_glow' : 'w_tent_day'));
    const th = h * .62, tw = th * im.width / im.height; x.drawImage(im, px, (h - th) / 2, tw, th); px += tw + h * .12;
    midT(x, MD, px, h * .41, SERIF(h * .38), fg); midT(x, WK, px + h * .01, h * .705, MED(small(h * .15)), sub);
    const right = W - h * .24; midT(x, d.temp, right, h * .41, SERIF(h * .38), fg, 'right'); midT(x, d.range, right, h * .705, MED(small(h * .15)), sub, 'right');
    x.font = SERIF(h * .38); const bw = Math.max(x.measureText(d.temp).width, 40); const ic = h * .7; const fi = await load(q ? d.fun : d.ic); x.drawImage(fi, right - bw - h * .08 - ic, h / 2 - ic / 2, ic, ic);
  } else { // 캐릭터 카드 / 포스터
    const h = H, fun = await load(d.fun);
    if (design === 'card') { const cc = cardColors(d, o.card); const g = x.createLinearGradient(0, 0, W * .4, h); g.addColorStop(0, cc[0]); g.addColorStop(1, cc[1]); x.fillStyle = g; rr(x, 0, 0, W, h, Math.min(h * .36, 28)); x.fill();
      const ink = custom ? fg : cc[2], soft = custom ? sub : cc[3]; const cs = h * .84; x.drawImage(fun, h * .1, h / 2 - cs / 2, cs, cs); const tx = h * .1 + cs + h * .08, right = W - h * .3;
      midT(x, MD, right, h * .42, BLACK(h * .28), ink, 'right'); midT(x, WK, right, h * .74, MED(small(h * .14)), soft, 'right');
      midT(x, d.temp, tx, h * .4, BLACK(h * .44), ink); midT(x, d.quip, tx + h * .02, h * .78, SANS(small(h * .14)), soft);
    } else { const cs = h * .88; x.drawImage(fun, h * .08, h / 2 - cs / 2, cs, cs); const tx = h * .08 + cs + h * .08; midT(x, d.temp, tx, h / 2, BLACK(h * .5), fg);
      const right = W - h * .26; midT(x, MD, right, h * .42, BLACK(h * .3), fg, 'right'); midT(x, WK, right, h * .74, MED(small(h * .15)), sub, 'right'); }
  }
}

// ---------- 레코드 위젯 ----------
const GLASS = { light: ['rgba(250,247,255,.80)', '#1D1B20', '#605D66', '#6750A4', '#FFFFFF'], dark: ['rgba(32,30,38,.66)', '#F4EFF4', '#CAC4D0', '#D0BCFF', '#381E72'] };
function glassCard(x, w, h, r, th) { const g = GLASS[th]; x.save(); x.shadowColor = 'rgba(0,0,0,.18)'; x.shadowBlur = 14; x.shadowOffsetY = 4; x.fillStyle = g[0]; rr(x, 0, 0, w, h, r); x.fill(); x.restore();
  const sh = x.createLinearGradient(0, 0, 0, h); sh.addColorStop(0, th === 'light' ? 'rgba(255,255,255,.35)' : 'rgba(255,255,255,.08)'); sh.addColorStop(.5, 'rgba(255,255,255,0)'); x.fillStyle = sh; rr(x, 0, 0, w, h, r); x.fill();
  x.strokeStyle = th === 'light' ? 'rgba(255,255,255,.7)' : 'rgba(255,255,255,.16)'; x.lineWidth = 1; rr(x, .5, .5, w - 1, h - 1, r); x.stroke(); }
const ARTS = { sunset: ['#ff9a5a', '#e2506a', '#3b2a6b', '#ffd27a'], ocean: ['#7fd6ff', '#3b78d8', '#1b3a8a', '#ffffff'], mint: ['#c9f2d9', '#5cc4a0', '#1f6a5a', '#fff6c8'] };
function disc(x, cx, cy, D, quirky, art, rot = 0) {
  const r = D / 2; x.save(); x.translate(cx, cy); x.rotate(rot);
  if (quirky) { x.fillStyle = '#2B2622'; x.beginPath(); x.arc(0, 0, r * .96, 0, 7); x.fill(); x.strokeStyle = '#0E0C0B'; x.lineWidth = r * .06; x.stroke();
    x.strokeStyle = '#4A433E'; x.lineWidth = r * .026; x.lineCap = 'round'; for (const [rr2, a0] of [[.84, -1.6], [.78, 0], [.82, 1.6], [.76, 3], [.7, -1.2], [.68, .4], [.7, 2], [.7, 3.6]]) { x.beginPath(); x.arc(0, 0, r * rr2, a0, a0 + 1.1); x.stroke(); }
    x.strokeStyle = 'rgba(255,255,255,.9)'; x.lineWidth = r * .07; x.beginPath(); x.arc(0, 0, r * .8, -1.25, -.55); x.stroke();
  } else { x.fillStyle = '#111'; x.beginPath(); x.arc(0, 0, r, 0, 7); x.fill(); x.strokeStyle = '#262626'; x.lineWidth = r * .008; for (let i = 0; i < 8; i++) { x.beginPath(); x.arc(0, 0, r * (.95 - i * .04), 0, 7); x.stroke(); }
    x.strokeStyle = 'rgba(255,255,255,.13)'; x.lineWidth = r * .14; x.lineCap = 'round'; x.beginPath(); x.arc(0, 0, r * .82, -1.2, -.4); x.stroke(); x.beginPath(); x.arc(0, 0, r * .82, 1.95, 2.75); x.stroke(); }
  const lr = r * .62, a = ARTS[art]; x.save(); x.beginPath(); x.arc(0, 0, lr, 0, 7); x.clip();
  const g = x.createLinearGradient(0, -lr, 0, lr); g.addColorStop(0, a[0]); g.addColorStop(.55, a[1]); g.addColorStop(1, a[2]); x.fillStyle = g; x.fillRect(-lr, -lr, lr * 2, lr * 2);
  x.fillStyle = a[3]; x.beginPath(); x.arc(0, lr * .15, lr * .34, 0, 7); x.fill(); x.fillStyle = a[2]; x.globalAlpha = .85; x.fillRect(-lr, lr * .25, lr * 2, lr); x.globalAlpha = 1; x.restore();
  if (quirky) { const INK = '#2B2622'; x.strokeStyle = INK; x.lineWidth = lr * .07; x.beginPath(); x.arc(0, 0, lr - lr * .035, 0, 7); x.stroke();
    const er = lr * .24, ey = -lr * .3; for (const [ex, dx, dy] of [[-lr * .32, -.35, .25], [lr * .3, .3, -.2]]) { x.fillStyle = '#fff'; x.beginPath(); x.arc(ex, ey, er, 0, 7); x.fill(); x.lineWidth = lr * .045; x.stroke(); x.fillStyle = INK; x.beginPath(); x.arc(ex + er * dx, ey + er * dy, er * .48, 0, 7); x.fill(); x.fillStyle = '#fff'; x.beginPath(); x.arc(ex + er * dx - er * .15, ey + er * dy - er * .18, er * .13, 0, 7); x.fill(); } }
  x.fillStyle = '#0d0d0d'; x.beginPath(); x.arc(0, 0, r * .04, 0, 7); x.fill(); x.restore();
}
function tonearm(x, l, t, D, on) { // 118×100 기준 SVG를 그대로
  const s = D / 100; x.save(); x.translate(l, t); x.scale(s, s);
  x.save(); x.translate(108, 12); x.rotate(on ? 0 : -30 * Math.PI / 180); x.translate(-108, -12);
  x.lineCap = 'round'; x.lineJoin = 'round';
  x.strokeStyle = 'rgba(0,0,0,.33)'; x.lineWidth = 3.4; x.beginPath(); x.moveTo(108.6, 12.8); x.lineTo(102.6, 38.8); x.lineTo(90.6, 56.8); x.stroke();
  x.strokeStyle = '#3a3a3a'; x.lineWidth = 4.6; x.beginPath(); x.moveTo(108, 12); x.lineTo(113, 3); x.stroke();
  x.strokeStyle = '#d2d2d2'; x.lineWidth = 2.4; x.beginPath(); x.moveTo(108, 12); x.lineTo(102, 38); x.lineTo(90, 56); x.stroke();
  x.fillStyle = '#2e2e2e'; x.beginPath(); x.moveTo(86, 53.5); x.lineTo(93.5, 56.5); x.lineTo(91, 63.5); x.lineTo(84, 61); x.fill(); x.restore();
  x.fillStyle = '#262626'; x.beginPath(); x.arc(108, 12, 7.5, 0, 7); x.fill(); x.fillStyle = '#8e8e8e'; x.beginPath(); x.arc(108, 12, 5, 0, 7); x.fill(); x.fillStyle = '#dadada'; x.beginPath(); x.arc(108, 12, 2, 0, 7); x.fill(); x.restore();
}
const IC = { prev: 'M6,6h2v12H6zM9.5,12l8.5,6V6z', next: 'M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z', play: 'M8,5v14l11,-7z', pause: 'M6,19h4V5H6v14zM14,5v14h4V5h-4z' };
function icon(x, n, cx, cy, s, col) { x.save(); x.translate(cx - s / 2, cy - s / 2); x.scale(s / 24, s / 24); x.fillStyle = col; x.fill(new Path2D(IC[n])); x.restore(); }
function buttons(x, xs, y, playing, th, playR, side) {
  const g = GLASS[th]; icon(x, 'prev', xs[0], y, side, g[3]); icon(x, 'next', xs[2], y, side, g[3]);
  x.fillStyle = g[3]; x.beginPath(); x.arc(xs[1], y, playR, 0, 7); x.fill(); icon(x, playing ? 'pause' : 'play', xs[1], y, playR * 1.1, g[4]);
}
function record2x2(el, o = {}) {
  const W = o.size || 170, H = W, k = W / 170, th = o.theme || 'light', x = canvas(el, W, H);
  glassCard(x, W, H, 28 * k, th);
  const pad = PAD * k, row = ROW * k, D = Math.min(H - pad * 2 - row, (W - pad * 2) / ARM), dl = pad + (W - pad * 2 - D * ARM) / 2, dt = pad + (H - pad * 2 - row - D) / 2;
  disc(x, dl + D / 2, dt + D / 2, D, o.quirky, o.art || 'sunset', o.rot || 0); tonearm(x, dl, dt, D, o.playing !== false);
  const y = H - pad - row / 2, xs = [0, 1, 2].map(i => pad + (W - pad * 2) * (i + .5) / 3);
  buttons(x, xs, y, o.playing !== false, th, 20 * k, 24 * k);
}
function recordWide(el, o = {}) {
  const W = o.w || 350, H = o.h || 84, th = o.theme || 'light', x = canvas(el, W, H), g = GLASS[th];
  glassCard(x, W, H, Math.min(H * .36, 28), th);
  const D = H * .8, dl = H * .1, dt = (H - D) / 2; disc(x, dl + D / 2, dt + D / 2, D, o.quirky, o.art || 'ocean', o.rot || 0); tonearm(x, dl, dt, D, true);
  const tx = dl + D * ARM + H * .1;
  midT(x, o.title || '여름밤 산책', tx, H * .38, SANS(H * .2), g[1]); midT(x, o.artist || 'Lo-Fi Radio', tx, H * .66, REG(Math.max(H * .16, 12)), g[2]);
  const pr = H * .3, right = W - H * .2; const xs = [right - pr * 2 - H * .62, right - pr, right - pr * 2 - H * .62 + 0];
  // 이전 · 재생 · 다음
  const cx = W - H * .26 - H * .42 - H * .08 - pr, px = cx - pr - H * .1 - H * .21, nx = cx + pr + H * .1 + H * .21;
  buttons(x, [px, cx, nx], H / 2, true, th, pr, H * .42);
}
