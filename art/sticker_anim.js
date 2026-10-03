// 움직이는 스티커 프레임 생성기.
//  - 프레임 그림: res/drawable-nodpi/stf_<키>_<n>.webp (256px, 기존 스티커와 같은 흰 칼선 + 그림자)
//  - 프레임 묶음: res/drawable/sta_<키>.xml (level-list) → 위젯의 ProgressBar가 level을 돌려 프레임을 넘김 (레코드판 회전과 같은 원리)
//  - 흔들기: res/drawable/stw_<이름>.xml (rotate) → 이미지 추가 없이 살랑살랑
//  - 레이아웃: res/layout/st_anim_<키>.xml
// 실행: node sticker_anim.js
const { chromium } = require('/opt/node-tools/node_modules/playwright');
const fs = require('fs');
const { STICKERS } = require('./stickers.js');
const RES = __dirname + '/../VinylWidget/app/src/main/res';
const FONTS = '/tmp/claude-0/-home-user-vinyl-widget/c3c818f4-cc19-50f4-bfcd-28b0c024adc6/scratchpad/p3/f/fonts.css';
const art = Object.fromEntries(STICKERS.map(([n, , , f]) => [n.slice(3), f]));
const noSteam = s => s.replace(/<path d="M\d+ \d+ q-15 -25[^"]*"[^>]*\/>/g, '');
const noZ = s => s.replace(/<text[^>]*>z<\/text>/g, '');

// [키, 프레임 수, 길이(ms), 반복 방식, {svg: 기본 그림, tr: 프레임별 변형, ov: 프레임 위 그림(캔버스), bg: 칼선 뒤 그림, draw: 직접 그리기}]
const SPECS = [];
for (let s = 0; s < 5; s++) SPECS.push([`marsh_${s}`, 12, 3600, 'repeat', { draw: (g, ph) => marsh(g, s, ph) }]);
for (let s = 0; s < 4; s++) SPECS.push([`fire_${s}`, 10, 1800, 'repeat', { draw: (g, ph) => fire(g, s, ph) }]);
for (const n of ['mug', 'coffee', 'kettle']) {
  SPECS.push([`${n}_0`, 6, 1500, 'repeat', { svg: noSteam(art[n]()), ov: (g, ph) => steam(g, n, ph, false) }]);
  SPECS.push([`${n}_1`, 1, 1000, 'repeat', { svg: noSteam(art[n]()), ov: (g, ph) => cold(g, n) }]);
}
SPECS.push(['bbq', 6, 1500, 'repeat', { svg: noSteam(art.bbq()), ov: (g, ph) => steam(g, 'bbq', ph, true) }]);
SPECS.push(['sleep', 8, 2400, 'repeat', { svg: noZ(art.sleep()), ov: zzz }]);
SPECS.push(['lantern', 6, 1400, 'cycle', { svg: art.lantern(), bg: glow }]);
SPECS.push(['camper', 6, 600, 'repeat', { svg: art.camper(), tr: ph => `translate(0 ${-Math.abs(Math.sin(ph * Math.PI)) * 12})`, ov: speed }]);
SPECS.push(['party', 8, 1600, 'repeat', { svg: art.party(), ov: confetti }]);
SPECS.push(['music', 6, 1200, 'cycle', { svg: art.music(), tr: ph => `translate(0 ${-ph * 14}) rotate(${(ph - .5) * 8} 256 300)` }]);
SPECS.push(['love', 6, 900, 'cycle', { svg: art.love(), tr: ph => `translate(256 270) scale(${0.9 + 0.1 * ph}) translate(-256 -270)` }]);
SPECS.push(['lucky', 6, 1200, 'cycle', { svg: art.lucky(), tr: ph => `rotate(${(ph - .5) * 14} 256 250)` }]);
// 캐릭터 병맛 움직임: 그림 전체를 변형한 프레임. [이름, 동작, 프레임, 길이(ms), 반복]
const MOVES = {
  hop: ph => `translate(0 ${-Math.abs(Math.sin(ph * Math.PI)) * 34}) translate(256 470) scale(${1 + (1 - Math.abs(Math.sin(ph * Math.PI))) * 0.07} ${1 - (1 - Math.abs(Math.sin(ph * Math.PI))) * 0.07}) translate(-256 -470)`,
  run: ph => `translate(${Math.sin(ph * Math.PI * 2) * 8} ${-Math.abs(Math.sin(ph * Math.PI * 2)) * 26}) rotate(${-8 + Math.sin(ph * Math.PI * 2) * 5} 256 300)`,
  melt: ph => `translate(256 470) scale(${1 + ph * 0.14} ${1 - ph * 0.16}) translate(-256 -470)`,
  squash: ph => `translate(256 440) scale(${1 + ph * 0.1} ${1 - ph * 0.1}) translate(-256 -440)`,
  roll: ph => `rotate(${(ph - 0.5) * 34} 256 270)`,
  sway: ph => `rotate(${(ph - 0.5) * 18} 256 430)`,
  float: ph => `translate(0 ${(ph - 0.5) * 30}) rotate(${(ph - 0.5) * 6} 256 270)`,
  bow: ph => `rotate(${ph * 24} 256 460)`,
  punch: ph => `translate(${ph * 22} 0) translate(256 270) scale(${1 + ph * 0.06}) translate(-256 -270)`,
  nod: ph => `rotate(${(ph - 0.5) * 16} 256 330)`,
  pulse: ph => `translate(256 270) scale(${0.9 + 0.1 * ph}) translate(-256 -270)`,
};
const CHARS = [
  ['leave', 'run', 6, 520, 'repeat'], ['nowork', 'melt', 6, 1500, 'cycle'], ['godlife', 'hop', 6, 700, 'repeat'],
  ['letsgo', 'squash', 6, 380, 'cycle'], ['lol', 'roll', 6, 460, 'cycle'], ['spirit', 'sway', 6, 1600, 'cycle'],
  ['zone', 'float', 6, 2200, 'cycle'], ['hungry', 'squash', 6, 600, 'cycle'], ['gym', 'hop', 6, 650, 'repeat'],
  ['ok', 'nod', 6, 900, 'cycle'], ['thanks', 'bow', 6, 1500, 'cycle'], ['fighting', 'punch', 6, 380, 'cycle'],
  ['hot', 'melt', 6, 1300, 'cycle'], ['study', 'nod', 6, 1500, 'cycle'], ['photo', 'pulse', 6, 700, 'cycle'],
  ['map', 'hop', 6, 750, 'repeat'], ['bday', 'pulse', 6, 900, 'cycle'], ['tent', 'sway', 6, 2000, 'cycle'],
];
for (const [n, mv, f, dur, beh] of CHARS) SPECS.push([n, f, dur, beh, { svg: art[n](), tr: MOVES[mv] }]);
const FRAMED = new Set([...CHARS.map(c => c[0]), 'marsh', 'fire', 'mug', 'coffee', 'kettle', 'bbq', 'sleep', 'lantern', 'camper', 'party', 'music', 'love', 'lucky']);
// 흔들기: 나머지 스티커. [이름, 각도, 길이(ms)]
const SHAKE = { cold: [4, 160], angry: [6, 200], money: [4, 180], call: [10, 160], game: [6, 240] };
const WIGGLE = STICKERS.map(s => s[0].slice(3)).filter(n => !FRAMED.has(n)).map(n => [n, ...(SHAKE[n] || [6, 1400])]);

// ---------- 직접 그리는 스티커 (512 좌표, 잉크 선 11) ----------
const INK = '#2B2622', LW = 11;
function eyes(g, cx, cy, s, mood) {
  g.fillStyle = INK; g.strokeStyle = INK; g.lineCap = 'round'; g.lineWidth = LW * .8;
  for (const sx of [-1, 1]) {
    const x = cx + sx * s * .3;
    if (mood == 'x') { g.beginPath(); g.moveTo(x - 12, cy - 12); g.lineTo(x + 12, cy + 12); g.moveTo(x + 12, cy - 12); g.lineTo(x - 12, cy + 12); g.stroke(); }
    else if (mood == 'hot') { g.beginPath(); g.moveTo(x - 15, cy + 2); g.quadraticCurveTo(x, cy - 16, x + 15, cy + 2); g.stroke(); }
    else { g.beginPath(); g.arc(x, cy, 10, 0, 7); g.fill(); g.fillStyle = '#fff'; g.beginPath(); g.arc(x - 3, cy - 4, 3.5, 0, 7); g.fill(); g.fillStyle = INK; }
    if (mood != 'x') { g.fillStyle = 'rgba(255,138,128,.8)'; g.beginPath(); g.ellipse(x + sx * 10, cy + 26, 14, 8, 0, 0, 7); g.fill(); g.fillStyle = INK; }
  }
  g.beginPath();
  if (mood == 'x') { g.arc(cx, cy + s * .27, 9, 0, 7); g.fill(); }
  else if (mood == 'hot') { g.moveTo(cx - 16, cy + s * .22); g.quadraticCurveTo(cx, cy + s * .22 + 20, cx + 16, cy + s * .22); g.stroke(); }
  else { g.moveTo(cx - 14, cy + s * .2); g.quadraticCurveTo(cx, cy + s * .2 + 16, cx + 14, cy + s * .2); g.stroke(); }
}
const MCOL = ['#FFF8EE', '#F6D08A', '#D79A4E', '#8A5426', '#2A1E18'];
function marsh(g, st, ph) {   // 꼬치 축을 중심으로 굴리며 굽기: 겉모양은 그대로, 얼굴·탄 자국이 둘레를 따라 돌아감
  g.lineCap = 'round';
  g.strokeStyle = INK; g.lineWidth = 30; g.beginPath(); g.moveTo(95, 470); g.lineTo(265, 255); g.stroke();
  g.strokeStyle = '#B07A4A'; g.lineWidth = 16; g.stroke();
  const A = -0.902, R = 80, th = ph * Math.PI * 2;
  const body = () => { g.beginPath(); g.roundRect(-95, -R, 190, 2 * R, 50); };
  g.save(); g.translate(300, 205); g.rotate(A);
  body(); g.fillStyle = MCOL[st]; g.fill();
  g.save(); body(); g.clip();
  if (st >= 1) for (const [off, rx] of [[Math.PI, 72], [Math.PI * 0.55, 46], [Math.PI * 1.45, 40]].slice(0, st >= 3 ? 3 : st)) {   // 탄 자국 (뒤쪽부터 생김)
    const a = th + off, c = Math.cos(a); if (c <= 0.05) continue;
    g.fillStyle = `rgba(${st >= 3 ? '40,22,12' : '125,62,20'},${0.25 + st * 0.12})`;
    g.beginPath(); g.ellipse(-8, R * 0.92 * Math.sin(a), rx, 34 * c, 0, 0, 7); g.fill();
  }
  const sh = g.createLinearGradient(0, -R, 0, R);   // 둥근 몸통 명암 (빛은 고정)
  sh.addColorStop(0, 'rgba(255,255,255,.35)'); sh.addColorStop(0.35, 'rgba(255,255,255,0)'); sh.addColorStop(0.7, 'rgba(0,0,0,0)'); sh.addColorStop(1, 'rgba(0,0,0,.18)');
  g.fillStyle = sh; g.fillRect(-95, -R, 190, 2 * R);
  g.restore();
  body(); g.strokeStyle = INK; g.lineWidth = LW; g.stroke();
  g.restore();
  const c = Math.cos(th);
  if (c > 0.2) {   // 얼굴이 앞으로 돌아왔을 때만
    const y = R * 0.85 * Math.sin(th), fx = 300 - Math.sin(A) * y, fy = 205 + Math.cos(A) * y;
    g.save(); g.translate(300, 205); g.rotate(A); g.beginPath(); g.roundRect(-89, -R + 6, 178, 2 * R - 12, 46); g.clip(); g.setTransform(1, 0, 0, 1, 0, 0);   // 몸통 밖으로 안 나가게
    g.translate(fx, fy); g.scale(0.55 + 0.45 * c, 0.55 + 0.45 * c);
    eyes(g, 0, -10, 120, st == 4 ? 'x' : st >= 2 ? 'hot' : 'dot'); g.restore();
  }
  if (st >= 3) for (let i = 0; i < 3; i++) {
    const p = (ph + i / 3) % 1; g.strokeStyle = `rgba(110,110,110,${(1 - p) * 0.85})`; g.lineWidth = 12;
    g.beginPath(); g.moveTo(330 + i * 34, 100 - p * 70); g.quadraticCurveTo(310 + i * 34, 75 - p * 70, 335 + i * 34, 50 - p * 70); g.stroke();
  }
  if (st == 4) {
    g.save(); g.translate(150, 120); g.rotate(-0.15); g.fillStyle = '#FF4D4D'; g.strokeStyle = INK; g.lineWidth = 8;
    g.beginPath(); g.roundRect(-95, -36, 190, 72, 18); g.fill(); g.stroke();
    g.fillStyle = '#fff'; g.font = "900 48px 'Noto Sans KR'"; g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillText('앗 탔다!', 0, 2); g.restore();
  }
}
function fire(g, st, ph) {
  g.lineCap = 'round';
  for (const [a, b, c, d] of [[110, 440, 402, 370], [110, 370, 402, 440]]) {
    g.strokeStyle = INK; g.lineWidth = 52; g.beginPath(); g.moveTo(a, b); g.lineTo(c, d); g.stroke();
    g.strokeStyle = st == 3 ? '#6B4630' : '#8B5A3C'; g.lineWidth = 36; g.stroke();
  }
  if (st == 3) {   // 다 탄 뒤: 숯불 + 연기
    for (const [x, y, r] of [[220, 395, 26], [270, 400, 30], [315, 392, 22]]) {
      g.fillStyle = `rgba(255,${90 + Math.sin(ph * 6.28 + x) * 40},40,.95)`; g.strokeStyle = INK; g.lineWidth = 7; g.beginPath(); g.arc(x, y, r, 0, 7); g.fill(); g.stroke();
    }
    for (let i = 0; i < 3; i++) {
      const p = (ph + i / 3) % 1; g.strokeStyle = `rgba(120,120,120,${(1 - p) * 0.8})`; g.lineWidth = 14;
      g.beginPath(); g.moveTo(230 + i * 30, 330 - p * 120); g.quadraticCurveTo(205 + i * 30, 290 - p * 120, 235 + i * 30, 250 - p * 120); g.stroke();
    }
    return;
  }
  const s = [1, 0.78, 0.55][st], wob = Math.sin(ph * 6.28) * 0.06 + Math.sin(ph * 12.56 + 1) * 0.04;
  g.save(); g.translate(256, 395); g.scale(s * (1 + wob), s * (1 - wob));
  g.fillStyle = '#FF7A45'; g.strokeStyle = INK; g.lineWidth = LW / s; g.lineJoin = 'round';
  g.beginPath(); g.moveTo(0, -310); g.bezierCurveTo(140, -175, 150, -38, 0, 0); g.bezierCurveTo(-150, -38, -140, -175, -30, -238); g.bezierCurveTo(-20, -175, 12, -175, 0, -310); g.fill(); g.stroke();
  g.fillStyle = '#FFD84D'; g.beginPath(); g.moveTo(0, -175); g.bezierCurveTo(62, -100, 62, -25, 0, -10); g.bezierCurveTo(-62, -25, -62, -100, 0, -175); g.fill();
  g.restore();
  eyes(g, 256, 395 - 85 * s, 90 * s, 'dot');
  for (let i = 0; i < 5; i++) {   // 타닥타닥 불티
    const p = (ph + i / 5) % 1;
    g.fillStyle = `rgba(255,${180 + i * 12},70,${1 - p})`; g.strokeStyle = INK; g.lineWidth = 3;
    g.beginPath(); g.arc(256 + Math.sin(i * 7.3 + p * 5) * 80 * s, 395 - 300 * s - p * 110, 9 - p * 4, 0, 7); g.fill(); g.stroke();
  }
  if (st == 0 && (Math.floor(ph * 8) == 2 || Math.floor(ph * 8) == 6)) {
    g.save(); g.translate(Math.floor(ph * 8) == 2 ? 105 : 405, 150); g.rotate(Math.floor(ph * 8) == 2 ? -0.25 : 0.25);
    g.font = "900 50px 'Noto Sans KR'"; g.textAlign = 'center'; g.textBaseline = 'middle'; g.lineWidth = 12; g.strokeStyle = '#fff'; g.strokeText('탁!', 0, 0); g.fillStyle = '#FF5A1F'; g.fillText('탁!', 0, 0); g.restore();
  }
}
// ---------- 덧그림 ----------
const STEAM_X = { mug: [200, 256, 312], coffee: [200, 255, 310], kettle: [200, 256, 312], bbq: [200, 300] };
const STEAM_Y = { mug: 150, coffee: 180, kettle: 170, bbq: 175 };
function steam(g, n, ph, smoke) {
  STEAM_X[n].forEach((x, i) => {
    const p = (ph + i / STEAM_X[n].length) % 1, y = STEAM_Y[n] - p * 55;
    g.strokeStyle = smoke ? `rgba(120,120,120,${(1 - p) * .85})` : `rgba(43,38,34,${(1 - p) * .9})`; g.lineWidth = smoke ? 12 : LW * .7; g.lineCap = 'round';
    g.beginPath(); g.moveTo(x, y); g.quadraticCurveTo(x - 15, y - 25, x + 5, y - 50); g.stroke();
  });
}
function cold(g, n) {
  g.font = "900 46px 'Noto Sans KR'"; g.textAlign = 'center'; g.textBaseline = 'middle'; g.lineWidth = 10; g.strokeStyle = '#fff';
  g.strokeText('식었다…', 256, 110); g.fillStyle = '#5B8FD8'; g.fillText('식었다…', 256, 110);
}
function zzz(g, ph) {   // z를 선으로 그림 (글꼴 크기에 영향 안 받게)
  for (let i = 0; i < 3; i++) {
    const p = (ph + i / 3) % 1, z = 26 + p * 26, x = 360 + p * 70, y = 270 - p * 170;
    g.globalAlpha = p < .15 ? p / .15 : 1 - (p - .15) / .85;
    g.strokeStyle = INK; g.lineWidth = 9; g.lineCap = 'round'; g.lineJoin = 'round';
    g.beginPath(); g.moveTo(x - z / 2, y - z / 2); g.lineTo(x + z / 2, y - z / 2); g.lineTo(x - z / 2, y + z / 2); g.lineTo(x + z / 2, y + z / 2); g.stroke();
  }
  g.globalAlpha = 1;
}
function confetti(g, ph) {
  const cols = ['#FF6B9A', '#7ED99A', '#FFD84D', '#7FD3E8', '#B79CFF'];
  for (let i = 0; i < 10; i++) {
    const p = (ph + i / 10) % 1, x = 70 + (i * 97) % 380, y = 40 + p * 300;
    g.save(); g.translate(x + Math.sin(p * 9 + i) * 18, y); g.rotate(p * 9 + i);
    g.fillStyle = cols[i % 5]; g.strokeStyle = INK; g.lineWidth = 4; g.beginPath(); g.roundRect(-13, -7, 26, 14, 4); g.fill(); g.stroke(); g.restore();
  }
}
function speed(g, ph) {
  g.strokeStyle = INK; g.lineWidth = 8; g.lineCap = 'round';
  for (let i = 0; i < 3; i++) { const o = ((ph + i / 3) % 1) * 60; g.globalAlpha = .7; g.beginPath(); g.moveTo(60 - o, 220 + i * 50); g.lineTo(110 - o, 220 + i * 50); g.stroke(); }
  g.globalAlpha = 1;
}
function glow(g, ph) { const r = 200 + ph * 40; const gr = g.createRadialGradient(256, 270, 0, 256, 270, r); gr.addColorStop(0, `rgba(255,220,120,${0.35 + ph * 0.25})`); gr.addColorStop(1, 'rgba(255,220,120,0)'); g.fillStyle = gr; g.fillRect(0, 0, 512, 512); }

const svgOf = (inner, tr) => `<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512"><g transform="${tr || ''}"><g transform="translate(256 262) scale(1.08) translate(-256 -262)">${inner}</g></g></svg>`;

(async () => {
  const b = await chromium.launch(); const p = await b.newPage();
  await p.setContent(`<html><head><link rel="stylesheet" href="file://${FONTS}"></head><body></body></html>`);
  await p.addScriptTag({ content: `const INK='${INK}',LW=${LW};${eyes};const MCOL=${JSON.stringify(MCOL)};${marsh};${fire};const STEAM_X=${JSON.stringify(STEAM_X)},STEAM_Y=${JSON.stringify(STEAM_Y)};${steam};${cold};${zzz};${confetti};${speed};${glow};
  window.frame=async(svg,ovName,ovArg,bgName,drawName,drawArg,ph)=>{
    const S=512,a=document.createElement('canvas');a.width=S;a.height=S;const g=a.getContext('2d');
    if(svg){const img=new Image();img.src='data:image/svg+xml;base64,'+btoa(unescape(encodeURIComponent(svg)));await img.decode();g.drawImage(img,0,0);}
    if(drawName) window[drawName](g,drawArg,ph);
    if(ovName) window[ovName](g,...(ovArg!==null?[ovArg]:[]),ph,...(ovName==='steam'?[ovArg==='bbq']:[]));
    const w=document.createElement('canvas');w.width=S;w.height=S;const wg=w.getContext('2d');
    for(let k=0;k<24;k++){const t=k/24*Math.PI*2;wg.drawImage(a,Math.cos(t)*14,Math.sin(t)*14);}
    wg.drawImage(a,0,0);wg.globalCompositeOperation='source-in';wg.fillStyle='#fff';wg.fillRect(0,0,S,S);
    const c=document.createElement('canvas');c.width=S;c.height=S;const h=c.getContext('2d');
    if(bgName) window[bgName](h,ph);
    h.save();h.shadowColor='rgba(0,0,0,.28)';h.shadowBlur=14;h.shadowOffsetY=6;h.drawImage(w,0,0);h.restore();h.drawImage(a,0,0);
    const o=document.createElement('canvas');o.width=256;o.height=256;o.getContext('2d').drawImage(c,0,0,256,256);
    return o.toDataURL('image/webp',0.85);
  };` });
  await p.evaluate(() => Promise.all(["900 40px 'Noto Sans KR'"].map(f => document.fonts.load(f, '앗 탔다 식었 탁z'))));
  const NODPI = RES + '/drawable-nodpi', DR = RES + '/drawable', LY = RES + '/layout';
  const prev = [];
  for (const [key, n, dur, beh, o] of SPECS) {
    const items = [];
    for (let i = 0; i < n; i++) {
      const ph = n == 1 ? 0 : (beh == 'cycle' ? i / (n - 1) : i / n);
      const svg = o.svg ? svgOf(o.svg, o.tr ? o.tr(ph) : '') : null;
      let ovName = null, ovArg = null;
      if (o.ov) { const src = o.ov.toString(); const m = src.match(/(steam|cold|zzz|confetti|speed)\(g(?:, '?(\w+)'?)?/); if (m) { ovName = m[1]; ovArg = (ovName === 'steam' || ovName === 'cold') ? key.split('_')[0] : null; } }
      let drawName = null, drawArg = null;
      if (o.draw) { drawName = key.startsWith('marsh') ? 'marsh' : 'fire'; drawArg = +key.split('_')[1]; }
      const data = await p.evaluate(([svg, ovName, ovArg, bgName, drawName, drawArg, ph]) => window.frame(svg, ovName, ovArg, bgName, drawName, drawArg, ph),
        [svg, ovName, ovArg, o.bg ? 'glow' : null, drawName, drawArg, ph]);
      const name = `stf_${key}_${i}`;
      fs.writeFileSync(`${NODPI}/${name}.webp`, Buffer.from(data.split(',')[1], 'base64'));
      items.push(name); if (i == 0 || i == Math.floor(n / 2)) prev.push(data);
    }
    if (/^(marsh|fire|mug|coffee|kettle)_\d$/.test(key)) continue;   // 단계 그림은 아래 1분짜리 묶음으로
    const step = Math.floor(10000 / n);
    fs.writeFileSync(`${DR}/sta_${key}.xml`, `<?xml version="1.0" encoding="utf-8"?>\n<!-- 움직이는 스티커 프레임 (art/sticker_anim.js가 만듦). ProgressBar가 level을 돌리면 프레임이 넘어감 -->\n<level-list xmlns:android="http://schemas.android.com/apk/res/android">\n` +
      items.map((it, i) => `    <item android:maxLevel="${i == n - 1 ? 10000 : (i + 1) * step - 1}" android:drawable="@drawable/${it}" />`).join('\n') + `\n</level-list>\n`);
    layout(`st_anim_${key}`, `@drawable/sta_${key}`, dur, beh);
  }
  // 시간이 흐르는 스티커: 굽기→타기 / 활활→숯불 / 김→식음 을 1분 안에 한 바퀴 돌고 반복
  const seq = (stages) => stages.flatMap(([k, n, times]) => Array.from({ length: n * times }, (_, i) => `stf_${k}_${i % n}`));
  const SEQS = [
    ['marsh', seq([['marsh_0', 12, 3], ['marsh_1', 12, 3], ['marsh_2', 12, 3], ['marsh_3', 12, 3], ['marsh_4', 12, 4]]), 57600],
    ['fire', seq([['fire_0', 10, 8], ['fire_1', 10, 8], ['fire_2', 10, 8], ['fire_3', 10, 8]]), 57600],
    ...['mug', 'coffee', 'kettle'].map(n => [n, seq([[`${n}_0`, 6, 30], [`${n}_1`, 1, 60]]), 60000]),
  ];
  for (const [key, items, dur] of SEQS) {
    const step = 10000 / items.length;
    fs.writeFileSync(`${DR}/sta_${key}.xml`, `<?xml version="1.0" encoding="utf-8"?>\n<!-- 움직이는 스티커 1분 묶음 (art/sticker_anim.js가 만듦) -->\n<level-list xmlns:android="http://schemas.android.com/apk/res/android">\n` +
      items.map((it, i) => `    <item android:maxLevel="${i == items.length - 1 ? 10000 : Math.round((i + 1) * step) - 1}" android:drawable="@drawable/${it}" />`).join('\n') + `\n</level-list>\n`);
    layout(`st_anim_${key}`, `@drawable/sta_${key}`, dur, 'repeat');
  }
  layout('st_anim_marsh_fast', '@drawable/sta_marsh', 38400, 'repeat');   // 모닥불 스티커가 있으면 더 빨리 탐
  for (const [n, deg, dur] of WIGGLE) {
    fs.writeFileSync(`${DR}/stw_${n}.xml`, `<?xml version="1.0" encoding="utf-8"?>\n<!-- 살랑살랑 흔들기 (art/sticker_anim.js가 만듦) -->\n<rotate xmlns:android="http://schemas.android.com/apk/res/android"\n    android:drawable="@drawable/st_${n}" android:fromDegrees="-${deg}" android:toDegrees="${deg}"\n    android:pivotX="50%" android:pivotY="62%" />\n`);
    layout(`st_anim_w_${n}`, `@drawable/stw_${n}`, dur, 'cycle');
  }
  function layout(name, drawable, dur, beh) {
    fs.writeFileSync(`${LY}/${name}.xml`, `<?xml version="1.0" encoding="utf-8"?>\n<!-- 움직이는 스티커 (art/sticker_anim.js가 만듦). 크기는 코드에서 위젯 칸에 맞춤 -->\n<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"\n    android:id="@+id/st_root"\n    android:layout_width="match_parent" android:layout_height="match_parent">\n    <ProgressBar android:id="@+id/st_anim"\n        android:layout_width="80dp" android:layout_height="80dp"\n        android:layout_gravity="center"\n        android:indeterminateOnly="true"\n        android:indeterminateDrawable="${drawable}"\n        android:indeterminateDuration="${dur}"\n        android:indeterminateBehavior="${beh}" />\n</FrameLayout>\n`);
  }
  await p.setContent(`<body style="margin:0;background:#6d7f95;display:grid;grid-template-columns:repeat(10,128px);gap:6px;padding:10px">${prev.map(d => `<img src="${d}" width=128>`).join('')}</body>`);
  await p.setViewportSize({ width: 10 * 134 + 20, height: Math.ceil(prev.length / 10) * 134 + 20 });
  await p.screenshot({ path: __dirname + '/prev_anim.png' });
  await b.close(); console.log('specs', SPECS.length, 'wiggle', WIGGLE.length);
})();
