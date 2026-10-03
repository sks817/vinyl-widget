// 병맛 스티커 (1×1 스티커 위젯용). 전부 직접 그린 오리지널 캐릭터 + 흔한 유행어 → 저작권 걱정 없음
// 굵은 잉크 외곽선 + 납작한 색 + 표정, 바깥에 흰 칼선(다이컷 스티커)과 옅은 그림자. 글자 없이 그림만으로 뜻이 보이게
// 실행: node stickers.js  → res/drawable-nodpi/st_*.webp (+ art/prev_stickers.png 미리보기)
const { chromium } = require('/opt/node-tools/node_modules/playwright');
const fs = require('fs');
const OUT = __dirname + '/../VinylWidget/app/src/main/res/drawable-nodpi';
const FONTS = '/tmp/claude-0/-home-user-vinyl-widget/c3c818f4-cc19-50f4-bfcd-28b0c024adc6/scratchpad/p3/f/fonts.css';

const INK = '#2B2622', LW = 11;
const st = (w = LW) => `stroke="${INK}" stroke-width="${w}" stroke-linejoin="round" stroke-linecap="round"`;
const S = 512;

// 삐뚤빼뚤한 둥근 몸통
function blob(cx, cy, rx, ry, wob = 0.04, seed = 1, n = 12) {
  const pts = [];
  for (let i = 0; i < n; i++) { const a = (i / n) * Math.PI * 2, k = 1 + Math.sin(i * 2.3 + seed) * wob; pts.push([cx + Math.cos(a) * rx * k, cy + Math.sin(a) * ry * k]); }
  let d = '';
  for (let i = 0; i < n; i++) { const p1 = pts[(i + 1) % n], p2 = pts[(i + 2) % n]; const m0 = [(pts[i][0] + p1[0]) / 2, (pts[i][1] + p1[1]) / 2]; d += (i === 0 ? `M${m0[0]} ${m0[1]}` : '') + ` Q${p1[0]} ${p1[1]} ${(p1[0] + p2[0]) / 2} ${(p1[1] + p2[1]) / 2}`; }
  return d + 'Z';
}
const body = (cx, cy, rx, ry, col, seed = 1) => `<path d="${blob(cx, cy, rx, ry, 0.045, seed)}" fill="${col}" ${st()}/>`;
// 얼굴: 눈 종류 + 입 종류 + 볼터치
function face(cx, cy, s, eyes = 'dot', mouth = 'smile', blush = true) {
  const ex = s * 0.32, ey = cy - s * 0.08; let g = '';
  for (const sx of [-1, 1]) {
    const x = cx + sx * ex;
    if (eyes === 'dot') g += `<circle cx="${x}" cy="${ey}" r="${s * 0.075}" fill="${INK}"/><circle cx="${x - s * 0.025}" cy="${ey - s * 0.03}" r="${s * 0.025}" fill="#fff"/>`;
    if (eyes === 'googly') g += `<circle cx="${x}" cy="${ey}" r="${s * 0.15}" fill="#fff" ${st(LW * 0.8)}/><circle cx="${x + sx * s * 0.05}" cy="${ey + s * 0.04}" r="${s * 0.07}" fill="${INK}"/>`;
    if (eyes === 'happy') g += `<path d="M${x - s * 0.09} ${ey + s * 0.03} Q${x} ${ey - s * 0.1} ${x + s * 0.09} ${ey + s * 0.03}" fill="none" ${st(LW * 0.9)}/>`;
    if (eyes === 'sad') g += `<path d="M${x - s * 0.09} ${ey - s * 0.03} Q${x} ${ey + s * 0.08} ${x + s * 0.09} ${ey - s * 0.03}" fill="none" ${st(LW * 0.9)}/>`;
    if (eyes === 'angry') g += `<circle cx="${x}" cy="${ey + s * 0.03}" r="${s * 0.06}" fill="${INK}"/><path d="M${x - sx * s * 0.13} ${ey - s * 0.12} L${x + sx * s * 0.08} ${ey - s * 0.04}" ${st(LW * 0.9)}/>`;
    if (eyes === 'blank') g += `<circle cx="${x}" cy="${ey}" r="${s * 0.09}" fill="#fff" ${st(LW * 0.7)}/><circle cx="${x}" cy="${ey}" r="${s * 0.02}" fill="${INK}"/>`;
    if (eyes === 'line') g += `<path d="M${x - s * 0.09} ${ey} L${x + s * 0.09} ${ey}" ${st(LW * 0.9)}/>`;
    if (eyes === 'x') g += `<path d="M${x - s * 0.07} ${ey - s * 0.07} L${x + s * 0.07} ${ey + s * 0.07} M${x + s * 0.07} ${ey - s * 0.07} L${x - s * 0.07} ${ey + s * 0.07}" ${st(LW * 0.85)}/>`;
    if (blush) g += `<ellipse cx="${x + sx * s * 0.08}" cy="${ey + s * 0.2}" rx="${s * 0.09}" ry="${s * 0.05}" fill="#FF8A80" opacity=".8"/>`;
  }
  const my = cy + s * 0.2;
  if (mouth === 'smile') g += `<path d="M${cx - s * 0.12} ${my - s * 0.03} Q${cx} ${my + s * 0.12} ${cx + s * 0.12} ${my - s * 0.03}" fill="none" ${st(LW * 0.9)}/>`;
  if (mouth === 'open') g += `<path d="M${cx - s * 0.14} ${my - s * 0.04} Q${cx} ${my + s * 0.22} ${cx + s * 0.14} ${my - s * 0.04} Z" fill="#7a2a2a" ${st(LW * 0.85)}/><path d="M${cx - s * 0.06} ${my + s * 0.07} Q${cx} ${my + s * 0.02} ${cx + s * 0.07} ${my + s * 0.08}" fill="#FF8A80" stroke="none"/>`;
  if (mouth === 'wave') g += `<path d="M${cx - s * 0.14} ${my} q${s * 0.07} -${s * 0.07} ${s * 0.14} 0 t${s * 0.14} 0" fill="none" ${st(LW * 0.85)}/>`;
  if (mouth === 'o') g += `<ellipse cx="${cx}" cy="${my + s * 0.02}" rx="${s * 0.05}" ry="${s * 0.07}" fill="${INK}"/>`;
  if (mouth === 'flat') g += `<path d="M${cx - s * 0.08} ${my} L${cx + s * 0.08} ${my}" ${st(LW * 0.85)}/>`;
  if (mouth === 'tongue') g += `<path d="M${cx - s * 0.13} ${my - s * 0.03} Q${cx} ${my + s * 0.12} ${cx + s * 0.13} ${my - s * 0.03}" fill="none" ${st(LW * 0.9)}/><path d="M${cx + s * 0.01} ${my + s * 0.04} q${s * 0.05} ${s * 0.12} ${s * 0.1} 0" fill="#FF8A80" ${st(LW * 0.7)}/>`;
  return g;
}
const sparkle = (x, y, r, col = '#FFD84D') => `<path d="M${x} ${y - r} Q${x + r * 0.15} ${y - r * 0.15} ${x + r} ${y} Q${x + r * 0.15} ${y + r * 0.15} ${x} ${y + r} Q${x - r * 0.15} ${y + r * 0.15} ${x - r} ${y} Q${x - r * 0.15} ${y - r * 0.15} ${x} ${y - r} Z" fill="${col}" ${st(LW * 0.6)}/>`;
const drop = (x, y, r, col = '#7EC8F2') => `<path d="M${x} ${y - r * 1.6} Q${x + r} ${y - r * 0.2} ${x + r} ${y + r * 0.2} A${r} ${r} 0 1 1 ${x - r} ${y + r * 0.2} Q${x - r} ${y - r * 0.2} ${x} ${y - r * 1.6} Z" fill="${col}" ${st(LW * 0.6)}/>`;
const note = (x, y, s) => `<path d="M${x} ${y} L${x} ${y - s} L${x + s * 0.6} ${y - s * 1.15} L${x + s * 0.6} ${y - s * 0.15}" fill="none" ${st(LW * 0.8)}/><ellipse cx="${x - s * 0.15}" cy="${y}" rx="${s * 0.2}" ry="${s * 0.15}" fill="${INK}"/><ellipse cx="${x + s * 0.45}" cy="${y - s * 0.15}" rx="${s * 0.2}" ry="${s * 0.15}" fill="${INK}"/>`;

// [이름, 뜻(앱에서 이름으로만 씀), 대표색, 그림 SVG]
const STICKERS = [
  ['st_leave', '퇴근각!', '#FFD84D', () => `
    <path d="M150 360 L110 420 M200 372 L230 430" ${st()}/>
    ${body(220, 250, 120, 115, '#FFD84D', 2)}${face(230, 245, 150, 'happy', 'open')}
    <rect x="330" y="250" width="90" height="70" rx="10" fill="#8B5A3C" ${st()}/><path d="M355 250 v-18 h40 v18" fill="none" ${st()}/>
    <path d="M330 270 L300 262" ${st()}/>${sparkle(420, 140, 34)}${sparkle(90, 150, 22, '#fff')}
    <path d="M60 300 h40 M50 330 h50 M70 270 h30" ${st(LW * 0.8)} opacity=".7"/>`],
  ['st_nowork', '출근 싫어', '#9DB4FF', () => `
    <path d="M110 330 Q100 200 256 150 Q412 200 402 330 L402 360 Q385 400 370 360 Q355 420 330 370 Q300 430 280 375 Q256 440 230 375 Q205 420 185 370 Q160 410 140 360 Q120 395 110 360 Z" fill="#9DB4FF" ${st()}/>
    ${face(256, 270, 160, 'sad', 'wave')}${drop(360, 220, 16)}${drop(150, 230, 12)}`],
  ['st_godlife', '갓생 ON', '#7ED99A', () => `
    ${body(240, 260, 125, 120, '#7ED99A', 3)}<path d="M120 200 Q240 150 360 200" fill="none" stroke="#FF6B6B" stroke-width="26" stroke-linecap="round"/>
    <path d="M120 200 Q240 150 360 200" fill="none" ${st(LW * 0.6)}/>${face(240, 270, 150, 'dot', 'smile')}
    <path d="M355 280 Q420 280 420 220 Q430 180 400 185 Q380 190 385 225 Q380 250 350 250" fill="#7ED99A" ${st()}/>
    ${sparkle(110, 120, 26)}${sparkle(410, 120, 20, '#fff')}`],
  ['st_angry', '킹받네', '#FF7A6B', () => `
    ${body(256, 270, 130, 120, '#FF7A6B', 4)}${face(256, 275, 160, 'angry', 'wave', false)}
    <path d="M140 140 q-20 -30 5 -50 q25 -20 10 -45" fill="none" ${st(LW * 0.9)}/><path d="M372 140 q20 -30 -5 -50 q-25 -20 -10 -45" fill="none" ${st(LW * 0.9)}/>
    <path d="M370 210 l20 -10 M380 230 l25 0 M372 250 l20 10" ${st(LW * 0.7)}/>`],
  ['st_letsgo', '가보자고', '#FF9F43', () => `
    <path d="M256 70 Q330 150 360 210 Q400 300 340 360 Q290 400 220 390 Q130 375 125 290 Q120 220 175 180 Q170 230 205 240 Q190 160 256 70 Z" fill="#FF9F43" ${st()}/>
    <path d="M255 200 Q300 250 290 310 Q270 350 235 345 Q200 335 210 290 Q215 250 255 200 Z" fill="#FFD84D" stroke="none"/>
    ${face(250, 300, 120, 'dot', 'open', false)}
    <circle cx="380" cy="300" r="34" fill="#FFD2A6" ${st()}/><path d="M362 285 h30 M362 300 h32 M362 315 h28" ${st(LW * 0.6)}/>`],
  ['st_lol', 'ㅋㅋㅋㅋ', '#FFE14D', () => `
    ${body(256, 250, 135, 130, '#FFE14D', 5)}${face(256, 250, 170, 'happy', 'open')}
    ${drop(130, 210, 14)}${drop(382, 210, 14)}<path d="M90 120 l20 25 M420 120 l-20 25" ${st(LW * 0.8)}/>`],
  ['st_spirit', '중꺾마', '#B8E986', () => `
    <path d="M256 395 C256 330 256 280 256 230" fill="none" stroke="#5DAA4B" stroke-width="22" stroke-linecap="round"/><path d="M256 395 C256 330 256 280 256 230" fill="none" ${st(LW * 0.6)}/>
    <path d="M256 250 Q200 180 140 200 Q170 270 256 260 Z" fill="#7ED99A" ${st()}/><path d="M256 230 Q320 150 390 175 Q350 255 256 245 Z" fill="#7ED99A" ${st()}/>
    <rect x="186" y="330" width="140" height="80" rx="18" fill="#C98B5A" ${st()}/>${face(256, 375, 90, 'happy', 'smile')}
    ${sparkle(400, 110, 26)}<rect x="232" y="290" width="48" height="16" rx="4" fill="#fff" ${st(LW * 0.6)} transform="rotate(-20 256 298)"/>`],
  ['st_zone', '멍...', '#C9CED6', () => `
    ${body(256, 270, 130, 115, '#C9CED6', 6)}${face(256, 275, 160, 'blank', 'o', false)}
    <path d="M330 120 a28 28 0 0 1 50 -10 a24 24 0 0 1 40 20 a20 20 0 0 1 -5 38 h-80 a24 24 0 0 1 -5 -48 Z" fill="#fff" ${st(LW * 0.8)}/>
    <circle cx="320" cy="185" r="10" fill="#fff" ${st(LW * 0.6)}/><circle cx="300" cy="205" r="6" fill="#fff" ${st(LW * 0.5)}/>`],
  ['st_hungry', '배고파', '#FFB3C7', () => `
    ${body(240, 265, 125, 120, '#FFB3C7', 7)}${face(240, 260, 150, 'googly', 'open')}
    ${drop(215, 345, 12)}<path d="M380 140 v120 M365 140 v50 Q380 210 395 190 v-50" fill="none" ${st()}/>
    <path d="M380 260 v90" stroke="#B0B6BF" stroke-width="16" stroke-linecap="round"/><path d="M380 260 v90" ${st(LW * 0.5)}/>`],
  ['st_coffee', '커피 수혈', '#C08A5B', () => `
    <path d="M150 200 h210 l-20 190 Q330 410 300 410 h-110 Q160 410 160 390 Z" fill="#fff" ${st()}/>
    <path d="M165 250 h180 l-12 130 Q328 395 305 395 h-100 Q180 395 178 380 Z" fill="#C08A5B" stroke="none"/>
    <path d="M360 240 q60 0 55 50 q-5 45 -60 40" fill="none" ${st()}/>${face(255, 320, 120, 'line', 'smile')}
    <path d="M200 170 q-15 -25 5 -45 M255 170 q-15 -25 5 -45 M310 170 q-15 -25 5 -45" fill="none" ${st(LW * 0.7)}/>
    <path d="M110 60 L110 140 Q110 175 150 205" fill="none" stroke="#FF6B6B" stroke-width="8" stroke-dasharray="4 10"/>
    <rect x="85" y="30" width="50" height="60" rx="10" fill="#FFDCD6" ${st(LW * 0.7)}/>`],
  ['st_music', '음악 ON', '#B79CFF', () => `
    ${body(256, 270, 120, 115, '#B79CFF', 8)}${face(256, 280, 150, 'happy', 'smile')}
    <path d="M140 270 Q140 130 256 130 Q372 130 372 270" fill="none" ${st(LW * 1.3)}/>
    <rect x="115" y="240" width="45" height="75" rx="18" fill="#FF6B9A" ${st()}/><rect x="352" y="240" width="45" height="75" rx="18" fill="#FF6B9A" ${st()}/>
    ${note(410, 150, 50)}${note(90, 140, 36)}`],
  ['st_photo', '찰칵!', '#7FD3E8', () => `
    <rect x="110" y="170" width="292" height="210" rx="34" fill="#7FD3E8" ${st()}/><rect x="170" y="140" width="80" height="40" rx="10" fill="#7FD3E8" ${st()}/>
    <circle cx="256" cy="280" r="72" fill="#fff" ${st()}/><circle cx="256" cy="280" r="44" fill="#3A4A5C" ${st(LW * 0.6)}/><circle cx="240" cy="264" r="12" fill="#fff"/>
    ${face(256, 236, 260, 'dot', 'none', true)}
    ${sparkle(380, 120, 40)}${sparkle(430, 200, 18, '#fff')}`],
  ['st_map', '어디가?', '#FF7A9C', () => `
    <path d="M256 420 Q170 310 160 240 Q150 140 256 120 Q362 140 352 240 Q342 310 256 420 Z" fill="#FF7A9C" ${st()}/>
    ${face(256, 230, 140, 'googly', 'o', true)}<ellipse cx="256" cy="440" rx="70" ry="16" fill="#00000022"/>
    <path d="M90 380 q40 -40 80 0 M340 380 q40 -40 80 0" fill="none" ${st(LW * 0.7)} stroke-dasharray="2 18"/>`],
  ['st_money', '텅장', '#8FD3B5', () => `
    <path d="M120 200 h272 q20 0 20 20 v150 q0 20 -20 20 h-272 q-20 0 -20 -20 v-150 q0 -20 20 -20 Z" fill="#8FD3B5" ${st()}/>
    <path d="M330 260 h82 v60 h-82 q-20 0 -20 -30 q0 -30 20 -30 Z" fill="#6BBF9A" ${st()}/><circle cx="340" cy="290" r="9" fill="${INK}"/>
    ${face(215, 290, 140, 'sad', 'wave')}${drop(170, 330, 10)}
    <path d="M300 130 q-25 -35 -50 0 q25 15 50 0 M300 130 q25 -35 50 0 q-25 15 -50 0" fill="#fff" ${st(LW * 0.7)}/>`],
  ['st_gym', '운동 가자', '#FFB86B', () => `
    <rect x="80" y="230" width="352" height="34" rx="16" fill="#B0B6BF" ${st()}/>
    <rect x="60" y="175" width="60" height="145" rx="16" fill="#4A4F57" ${st()}/><rect x="392" y="175" width="60" height="145" rx="16" fill="#4A4F57" ${st()}/>
    ${body(256, 250, 95, 95, '#FFB86B', 9)}${face(256, 255, 120, 'x', 'wave')}${drop(340, 170, 14)}${drop(170, 160, 11)}`],
  ['st_sleep', '잠 와..', '#8E9BFF', () => `
    ${body(256, 280, 125, 110, '#8E9BFF', 10)}${face(256, 285, 150, 'line', 'o')}
    <path d="M150 220 Q190 110 300 120 Q380 140 400 90 Q420 180 360 205 Z" fill="#FF8FAB" ${st()}/><circle cx="405" cy="85" r="20" fill="#fff" ${st(LW * 0.8)}/>
    <text x="370" y="290" font-size="50" font-weight="900" fill="${INK}">z</text><text x="405" y="250" font-size="36" font-weight="900" fill="${INK}">z</text>`],
  ['st_call', '연락해', '#FF9EB5', () => `
    <rect x="160" y="110" width="190" height="300" rx="36" fill="#FF9EB5" ${st()}/><rect x="185" y="150" width="140" height="200" rx="16" fill="#fff" ${st(LW * 0.7)}/>
    ${face(255, 245, 110, 'happy', 'smile')}<circle cx="255" cy="380" r="10" fill="${INK}"/>
    <path d="M390 150 q0 -30 28 -30 q28 0 28 30 q0 30 -56 60 q-56 -30 -56 -60 q0 -30 28 -30 q28 0 28 30 Z" transform="translate(-10 0) scale(.95)" fill="#FF5C7A" ${st(LW * 0.8)}/>`],
  ['st_ok', '오히려 좋아', '#FFE066', () => `
    ${body(230, 265, 120, 115, '#FFE066', 11)}${face(230, 270, 150, 'happy', 'tongue')}
    <rect x="330" y="230" width="70" height="80" rx="20" fill="#FFE066" ${st()}/><path d="M345 230 Q345 160 375 165 Q395 170 385 230" fill="#FFE066" ${st()}/>
    ${sparkle(410, 130, 30)}${sparkle(100, 130, 20, '#fff')}`],
  // ---- 유행어 추가 ----
  ['st_lucky', '럭키비키', '#7ED99A', () => `
    ${[0, 90, 180, 270].map(a => `<path d="M256 250 C206 215 170 160 212 128 C236 110 256 132 256 150 C256 132 276 110 300 128 C342 160 306 215 256 250 Z" fill="#7ED99A" ${st()} transform="rotate(${a + 45} 256 250)"/>`).join('')}
    <path d="M256 330 Q262 390 300 430" fill="none" ${st(LW * 1.2)}/>
    <circle cx="256" cy="250" r="62" fill="#B8F0C6" ${st()}/>${face(256, 255, 110, 'happy', 'open')}
    ${sparkle(420, 110, 32)}${sparkle(95, 120, 24)}${sparkle(420, 380, 18, '#fff')}`],
  ['st_love', '사랑해', '#FF7A9C', () => `
    <path d="M256 420 C130 330 90 250 120 185 C150 120 230 120 256 180 C282 120 362 120 392 185 C422 250 382 330 256 420 Z" fill="#FF7A9C" ${st()}/>
    ${face(256, 260, 150, 'happy', 'smile')}
    <path d="M405 120 q0 -22 20 -22 q20 0 20 22 q0 22 -40 44 q-40 -22 -40 -44 q0 -22 20 -22 q20 0 20 22 Z" fill="#FFB3C7" ${st(LW * 0.7)}/>
    <path d="M100 110 q0 -15 14 -15 q14 0 14 15 q0 15 -28 30 q-28 -15 -28 -30 q0 -15 14 -15 q14 0 14 15 Z" fill="#FFB3C7" ${st(LW * 0.6)}/>`],
  ['st_thanks', '감사합니다', '#FFD2A6', () => `
    <g transform="rotate(-22 256 300)">${body(256, 290, 125, 115, '#FFD2A6', 12)}${face(256, 300, 150, 'happy', 'smile')}</g>
    <path d="M150 380 q-30 20 -10 45 M360 380 q30 20 10 45" fill="none" ${st()}/>
    ${[[100, 130], [420, 150], [400, 70]].map(([x, y]) => `<circle cx="${x}" cy="${y}" r="16" fill="#FFE066" ${st(LW * 0.6)}/>${[0, 72, 144, 216, 288].map(a => `<ellipse cx="${x}" cy="${y - 22}" rx="10" ry="14" fill="#FF9EB5" ${st(LW * 0.5)} transform="rotate(${a} ${x} ${y})"/>`).join('')}<circle cx="${x}" cy="${y}" r="10" fill="#FFE066" stroke="none"/>`).join('')}`],
  ['st_fighting', '화이팅', '#FFB86B', () => `
    ${body(230, 290, 120, 110, '#FFB86B', 13)}${face(230, 300, 145, 'angry', 'open', false)}
    <path d="M115 235 Q230 190 345 235" fill="none" stroke="#FF4D4D" stroke-width="30" stroke-linecap="round"/><path d="M115 235 Q230 190 345 235" fill="none" ${st(LW * 0.6)}/>
    <path d="M345 232 l45 -25 M345 238 l40 15" stroke="#FF4D4D" stroke-width="14" stroke-linecap="round"/>
    <path d="M340 300 Q380 250 380 170" fill="none" ${st(LW * 1.6)}/><circle cx="380" cy="150" r="42" fill="#FFB86B" ${st()}/>
    <path d="M360 140 h40 M362 155 h36" ${st(LW * 0.5)}/><path d="M430 110 l25 -20 M440 150 h30 M430 190 l25 15" ${st(LW * 0.8)}/>`],
  ['st_hot', '더워', '#FF9F43', () => `
    <path d="M140 250 Q140 140 256 140 Q372 140 372 250 L372 330 Q360 360 345 335 Q335 400 315 345 Q300 380 285 350 Q270 430 250 350 Q235 380 220 345 Q200 410 185 340 Q165 365 150 335 Z" fill="#FFB86B" ${st()}/>
    ${face(256, 245, 150, 'line', 'tongue')}${drop(160, 200, 14)}${drop(355, 190, 12)}
    <circle cx="410" cy="100" r="42" fill="#FFD84D" ${st()}/>${[0, 45, 90, 135, 180, 225, 270, 315].map(a => `<path d="M410 40 v-18" ${st(LW * 0.8)} transform="rotate(${a} 410 100)"/>`).join('')}`],
  ['st_cold', '추워', '#9DD6FF', () => `
    ${body(256, 270, 125, 120, '#9DD6FF', 14)}${face(256, 250, 145, 'x', 'wave', true)}
    <path d="M140 320 Q256 370 372 320 L372 350 Q256 400 140 350 Z" fill="#FF6B6B" ${st()}/><rect x="320" y="340" width="40" height="80" rx="10" fill="#FF6B6B" ${st()}/>
    <path d="M330 375 h20 M330 395 h20" stroke="#fff" stroke-width="8"/>
    ${[[90, 120], [420, 120], [110, 400]].map(([x, y]) => `<path d="M${x} ${y - 22} v44 M${x - 19} ${y - 11} l38 22 M${x - 19} ${y + 11} l38 -22" fill="none" stroke="#fff" stroke-width="${LW * 1.1}" stroke-linecap="round"/><path d="M${x} ${y - 22} v44 M${x - 19} ${y - 11} l38 22 M${x - 19} ${y + 11} l38 -22" fill="none" stroke="${INK}" stroke-width="3"/>`).join('')}`],
  ['st_party', '파티각', '#B79CFF', () => `
    ${body(256, 300, 120, 105, '#B79CFF', 15)}${face(256, 305, 140, 'happy', 'open')}
    <path d="M210 200 L262 70 L310 200 Z" fill="#FFE066" ${st()}/><path d="M230 150 l60 0 M220 175 l80 0" stroke="#FF6B9A" stroke-width="12"/><circle cx="262" cy="66" r="18" fill="#FF6B9A" ${st(LW * 0.7)}/>
    ${[[100, 150, '#FF6B9A'], [410, 160, '#7ED99A'], [90, 300, '#FFD84D'], [430, 300, '#7FD3E8'], [140, 90, '#7FD3E8'], [390, 90, '#FFD84D']].map(([x, y, c], i) => `<rect x="${x}" y="${y}" width="26" height="14" rx="4" fill="${c}" ${st(LW * 0.45)} transform="rotate(${i * 37} ${x} ${y})"/>`).join('')}`],
  ['st_bday', '생축', '#FFB3C7', () => `
    <rect x="130" y="250" width="252" height="150" rx="22" fill="#FFE9C9" ${st()}/><path d="M130 290 q32 30 63 0 q32 30 63 0 q32 30 63 0 q32 30 63 0" fill="none" stroke="#FF7A9C" stroke-width="22" stroke-linecap="round"/>
    <path d="M130 290 q32 30 63 0 q32 30 63 0 q32 30 63 0 q32 30 63 0" fill="none" ${st(LW * 0.5)}/>
    ${face(256, 350, 120, 'happy', 'smile')}
    <rect x="243" y="170" width="26" height="80" rx="8" fill="#9DB4FF" ${st(LW * 0.8)}/><path d="M256 120 Q280 150 256 168 Q232 150 256 120 Z" fill="#FFD84D" ${st(LW * 0.7)}/>
    ${sparkle(400, 150, 30)}${sparkle(110, 170, 22, '#fff')}`],
  ['st_study', '공부 중', '#9DB4FF', () => `
    ${body(256, 230, 120, 105, '#9DB4FF', 16)}${face(256, 225, 140, 'line', 'flat', false)}
    <circle cx="211" cy="207" r="34" fill="none" ${st(LW * 0.9)}/><circle cx="301" cy="207" r="34" fill="none" ${st(LW * 0.9)}/><path d="M245 207 h22" ${st(LW * 0.9)}/>
    <path d="M110 330 L256 300 L402 330 L402 420 L256 395 L110 420 Z" fill="#fff" ${st()}/><path d="M256 300 V395" ${st()}/>
    <path d="M140 345 l90 -15 M140 370 l90 -15 M282 330 l90 15 M282 355 l90 15" ${st(LW * 0.4)} opacity=".6"/>${drop(370, 160, 14)}`],
  ['st_game', '한 판만', '#7FD3E8', () => `
    <path d="M150 190 h212 q70 0 80 90 q10 100 -50 100 q-35 0 -55 -45 h-162 q-20 45 -55 45 q-60 0 -50 -100 q10 -90 80 -90 Z" fill="#7FD3E8" ${st()}/>
    <path d="M150 250 v50 M125 275 h50" ${st(LW * 1.3)}/><circle cx="350" cy="255" r="15" fill="#FF6B6B" ${st(LW * 0.6)}/><circle cx="385" cy="290" r="15" fill="#FFD84D" ${st(LW * 0.6)}/>
    ${face(256, 270, 110, 'googly', 'open', false)}${sparkle(420, 120, 28)}`],
  // ---- 캠핑 장비 ----
  ['st_tent', '텐트', '#FFB86B', () => `
    <path d="M70 400 L256 110 L442 400 Z" fill="#FFB86B" ${st()}/><path d="M256 110 L256 400" ${st(LW * 0.7)}/>
    <path d="M200 400 Q256 260 312 400 Z" fill="#8B5A3C" ${st()}/>${face(256, 230, 110, 'happy', 'smile')}
    <path d="M256 110 L256 70 M256 72 l40 14 l-40 14" fill="#FF6B6B" ${st(LW * 0.8)}/>
    <path d="M60 400 L40 430 M452 400 L472 430" ${st()}/><path d="M50 410 H462" ${st(LW * 0.6)}/>`],
  ['st_lantern', '랜턴', '#FFD84D', () => `
    <path d="M200 120 Q256 60 312 120" fill="none" ${st(LW * 1.2)}/><rect x="190" y="120" width="132" height="40" rx="12" fill="#5DAA4B" ${st()}/>
    <circle cx="256" cy="270" r="130" fill="#FFE066" opacity=".35"/>
    <rect x="180" y="160" width="152" height="190" rx="40" fill="#FFF3B0" ${st()}/><path d="M220 165 v180 M292 165 v180" ${st(LW * 0.5)} opacity=".5"/>
    ${face(256, 255, 120, 'happy', 'smile')}<rect x="175" y="345" width="162" height="48" rx="14" fill="#5DAA4B" ${st()}/>${sparkle(400, 140, 26)}${sparkle(110, 200, 18, '#fff')}`],
  ['st_chair', '캠핑 의자', '#7FD3E8', () => `
    <path d="M150 230 L362 430 M362 230 L150 430" ${st(LW * 1.3)}/>
    <path d="M140 110 Q256 90 372 110 L362 260 Q256 280 150 260 Z" fill="#7FD3E8" ${st()}/><path d="M130 260 Q256 300 382 260 L372 300 Q256 340 140 300 Z" fill="#5BB8D0" ${st()}/>
    ${face(256, 185, 120, 'line', 'smile')}<rect x="370" y="250" width="44" height="58" rx="10" fill="#FF7A6B" ${st(LW * 0.8)}/>`],
  ['st_fire', '불멍', '#FF7A45', () => `
    <path d="M140 400 L372 340 M372 400 L140 340" stroke="#8B5A3C" stroke-width="40" stroke-linecap="round"/><path d="M140 400 L372 340 M372 400 L140 340" fill="none" ${st(LW * 0.6)}/>
    <path d="M256 70 Q330 150 345 230 Q360 330 256 350 Q152 330 167 230 Q175 180 215 150 Q210 200 235 210 Q220 140 256 70 Z" fill="#FF7A45" ${st()}/>
    <path d="M256 180 Q300 240 295 290 Q285 330 256 330 Q227 330 217 290 Q215 240 256 180 Z" fill="#FFD84D" stroke="none"/>
    ${face(256, 285, 100, 'line', 'o', false)}${sparkle(380, 120, 18, '#FFD84D')}${sparkle(130, 150, 14, '#FFD84D')}`],
  ['st_marsh', '마시멜로', '#FFE9C9', () => `
    <path d="M90 440 L330 200" stroke="#B07A4A" stroke-width="14" stroke-linecap="round"/><path d="M90 440 L330 200" ${st(LW * 0.4)}/>
    <g transform="rotate(-45 330 200)"><rect x="250" y="120" width="170" height="150" rx="50" fill="#FFF6EA" ${st()}/><path d="M250 210 q85 40 170 0 v10 q0 50 -50 50 h-70 q-50 0 -50 -50 Z" fill="#E8B07A" ${st(LW * 0.7)}/></g>
    ${face(330, 190, 110, 'happy', 'tongue')}<path d="M420 90 q-15 -25 5 -45 M455 120 q-15 -25 5 -45" fill="none" ${st(LW * 0.7)}/>`],
  ['st_cooler', '아이스박스', '#5BB8D0', () => `
    <rect x="110" y="190" width="292" height="220" rx="30" fill="#5BB8D0" ${st()}/><rect x="100" y="160" width="312" height="60" rx="20" fill="#fff" ${st()}/>
    <path d="M190 160 Q256 110 322 160" fill="none" ${st(LW * 1.2)}/>${face(256, 310, 140, 'dot', 'smile')}
    <rect x="135" y="235" width="40" height="40" rx="8" fill="#E6F7FF" ${st(LW * 0.6)} transform="rotate(15 155 255)"/><rect x="340" y="240" width="34" height="34" rx="8" fill="#E6F7FF" ${st(LW * 0.6)} transform="rotate(-12 357 257)"/>`],
  ['st_kettle', '버너·코펠', '#C9CED6', () => `
    <rect x="130" y="380" width="252" height="40" rx="12" fill="#4A4F57" ${st()}/><path d="M200 380 l-20 -30 M312 380 l20 -30" ${st()}/>
    <path d="M160 200 h192 l-10 150 q0 20 -20 20 h-132 q-20 0 -20 -20 Z" fill="#C9CED6" ${st()}/><rect x="150" y="180" width="212" height="30" rx="12" fill="#B0B6BF" ${st()}/>
    <path d="M352 230 q60 0 60 40 q0 30 -55 40" fill="none" ${st()}/>${face(256, 285, 120, 'happy', 'o')}
    <path d="M200 160 q-15 -25 5 -50 M256 160 q-15 -25 5 -50 M312 160 q-15 -25 5 -50" fill="none" ${st(LW * 0.7)}/>
    <path d="M230 380 Q256 340 282 380 Z" fill="#7FD3E8" ${st(LW * 0.6)}/>`],
  ['st_mug', '캠핑 머그', '#FFFFFF', () => `
    <path d="M140 170 h232 v190 q0 50 -50 50 h-132 q-50 0 -50 -50 Z" fill="#F6F7F9" ${st()}/><path d="M140 170 h232" stroke="#3B6FD8" stroke-width="22"/><path d="M140 170 h232" ${st()}/>
    <path d="M372 210 q70 0 70 60 q0 60 -70 60" fill="none" stroke="#F6F7F9" stroke-width="26"/><path d="M372 210 q70 0 70 60 q0 60 -70 60" fill="none" ${st()}/>
    ${face(256, 290, 130, 'happy', 'smile')}<circle cx="330" cy="370" r="10" fill="#3B6FD8" opacity=".5"/>
    <path d="M200 140 q-15 -25 5 -50 M256 140 q-15 -25 5 -50 M312 140 q-15 -25 5 -50" fill="none" ${st(LW * 0.7)}/>`],
  ['st_bbq', '캠핑 고기', '#FF7A6B', () => `
    <path d="M110 260 h292 q0 120 -146 120 q-146 0 -146 -120 Z" fill="#4A4F57" ${st()}/><path d="M190 380 l-30 60 M322 380 l30 60" ${st()}/>
    <path d="M100 260 h312" ${st(LW * 1.2)}/>
    <rect x="140" y="200" width="150" height="56" rx="28" fill="#C0583F" ${st()}/><path d="M165 215 l20 25 M200 215 l20 25 M235 215 l20 25" ${st(LW * 0.5)}/>
    <ellipse cx="350" cy="225" rx="62" ry="38" fill="#FF8A80" ${st()}/><path d="M320 215 q30 -15 60 5" fill="none" stroke="#fff" stroke-width="10"/>
    ${face(350, 225, 80, 'happy', 'smile', false)}<path d="M200 170 q-15 -25 5 -45 M300 165 q-15 -25 5 -45" fill="none" ${st(LW * 0.7)}/>`],
  ['st_sleepbag', '침낭', '#7ED99A', () => `
    <path d="M150 120 Q256 70 362 120 L380 400 Q256 450 132 400 Z" fill="#7ED99A" ${st()}/><path d="M256 95 V430" ${st(LW * 0.5)} stroke-dasharray="14 12"/>
    <ellipse cx="256" cy="185" rx="85" ry="70" fill="#FFD2A6" ${st()}/>${face(256, 190, 120, 'line', 'o')}
    <path d="M150 230 Q256 270 362 230" fill="none" ${st()}/>
    <text x="360" y="120" font-size="50" font-weight="900" fill="${INK}">z</text><text x="400" y="80" font-size="36" font-weight="900" fill="${INK}">z</text>`],
  ['st_backpack', '배낭', '#FF9F43', () => `
    <path d="M170 140 Q256 90 342 140 L362 400 Q256 430 150 400 Z" fill="#FF9F43" ${st()}/><path d="M215 140 Q256 60 297 140" fill="none" ${st(LW * 1.2)}/>
    <rect x="190" y="300" width="132" height="90" rx="20" fill="#E07A2C" ${st()}/><path d="M190 330 h132" ${st(LW * 0.6)}/>
    ${face(256, 220, 120, 'dot', 'smile')}<rect x="345" y="230" width="34" height="110" rx="14" fill="#7FD3E8" ${st(LW * 0.8)}/>
    <path d="M120 300 a35 35 0 1 0 0.1 0" fill="none" ${st(LW * 0.6)}/>`],
  ['st_camper', '캠핑카', '#FFE066', () => `
    <path d="M70 200 Q70 140 130 140 h220 q40 0 60 40 l40 80 v100 h-370 Z" fill="#FFE066" ${st()}/><path d="M70 280 h400" stroke="#FF7A6B" stroke-width="22"/><path d="M70 280 h400" ${st(LW * 0.5)}/>
    <path d="M360 160 h40 l35 70 h-75 Z" fill="#B8E2F2" ${st(LW * 0.8)}/><rect x="110" y="170" width="90" height="70" rx="14" fill="#B8E2F2" ${st(LW * 0.8)}/>
    ${face(270, 205, 110, 'happy', 'smile')}<circle cx="150" cy="370" r="40" fill="#4A4F57" ${st()}/><circle cx="380" cy="370" r="40" fill="#4A4F57" ${st()}/>
    <circle cx="150" cy="370" r="14" fill="#C9CED6"/><circle cx="380" cy="370" r="14" fill="#C9CED6"/>`],
];

// 글자 없이 캐릭터만: 그림을 가운데로 모아 크게
const svg = (inner) => `<svg xmlns="http://www.w3.org/2000/svg" width="${S}" height="${S}" viewBox="0 0 ${S} ${S}"><g transform="translate(256 262) scale(1.08) translate(-256 -262)">${inner}</g></svg>`;

(async () => {
  const b = await chromium.launch(); const p = await b.newPage();
  await p.setContent(`<html><head><link rel="stylesheet" href="file://${FONTS}"></head><body><canvas id=c></canvas></body></html>`);
  await p.evaluate(() => Promise.all(["900 40px 'Noto Sans KR'"].map(f => document.fonts.load(f))));
  const out = [];
  for (const [name, text, col, art] of STICKERS) {
    const data = await p.evaluate(async ({ svg, text, col, S }) => {
      const img = new Image(); img.src = 'data:image/svg+xml;base64,' + btoa(unescape(encodeURIComponent(svg))); await img.decode();
      // 1) 그림
      const a = document.createElement('canvas'); a.width = S; a.height = S; const g = a.getContext('2d');
      g.drawImage(img, 0, 0);
      // 2) 흰 칼선: 모양을 사방으로 밀어 찍어 흰 테두리 + 옅은 그림자
      const c = document.getElementById('c'); c.width = S; c.height = S; const h = c.getContext('2d'); h.clearRect(0, 0, S, S);
      const w = document.createElement('canvas'); w.width = S; w.height = S; const wg = w.getContext('2d');
      const R = 14;
      for (let k = 0; k < 24; k++) { const t = k / 24 * Math.PI * 2; wg.drawImage(a, Math.cos(t) * R, Math.sin(t) * R); }
      wg.drawImage(a, 0, 0);
      wg.globalCompositeOperation = 'source-in'; wg.fillStyle = '#fff'; wg.fillRect(0, 0, S, S);
      h.save(); h.shadowColor = 'rgba(0,0,0,.28)'; h.shadowBlur = 14; h.shadowOffsetY = 6; h.drawImage(w, 0, 0); h.restore();
      h.drawImage(a, 0, 0);
      // 3) 384px로 줄여 저장
      const o = document.createElement('canvas'); o.width = 384; o.height = 384; o.getContext('2d').drawImage(c, 0, 0, 384, 384);
      return o.toDataURL('image/webp', 0.9);
    }, { svg: svg(art()), text, col, S });
    fs.writeFileSync(`${OUT}/${name}.webp`, Buffer.from(data.split(',')[1], 'base64'));
    out.push(data);
  }
  // 미리보기 한 장
  await p.setContent(`<body style="margin:0;background:#6d7f95;display:grid;grid-template-columns:repeat(8,160px);gap:8px;padding:12px">${out.map(d => `<img src="${d}" width=160>`).join('')}</body>`);
  await p.setViewportSize({ width: 8 * 168 + 24, height: Math.ceil(STICKERS.length / 8) * 168 + 24 });
  await p.screenshot({ path: __dirname + '/prev_stickers.png' });
  await b.close(); console.log('stickers', STICKERS.length);
})();
module.exports = { STICKERS };
