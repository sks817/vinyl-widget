// 병맛 캐릭터 날씨 아이콘 (SVG). 굵은 손그림 외곽선 + 납작한 색 + 표정
// 앱에서는 색을 입히지 않고 그대로 씀 (어떤 배경에서도 보이도록 외곽선이 있음)
const INK = '#2B2622', LW = 4.2;
const st = `stroke="${INK}" stroke-width="${LW}" stroke-linejoin="round" stroke-linecap="round"`;

// 살짝 삐뚤빼뚤한 원 (손그림 느낌)
function blob(cx, cy, r, wob = 0.05, seed = 1, n = 10) {
  let d = '';
  const pts = [];
  for (let i = 0; i < n; i++) {
    const a = (i / n) * Math.PI * 2;
    const rr = r * (1 + Math.sin(i * 2.3 + seed) * wob);
    pts.push([cx + Math.cos(a) * rr, cy + Math.sin(a) * rr]);
  }
  for (let i = 0; i < n; i++) {
    const p0 = pts[i], p1 = pts[(i + 1) % n];
    const m = [(p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2];
    d += (i === 0 ? `M${m[0]} ${m[1]}` : '') + ` Q${p1[0]} ${p1[1]} ${(p1[0] + pts[(i + 2) % n][0]) / 2} ${(p1[1] + pts[(i + 2) % n][1]) / 2}`;
  }
  return d + 'Z';
}

// 뭉게구름 (아래 평평)
function cloudPath(x, y, w, h) {
  return `M${x + w * 0.12} ${y + h} C${x - w * 0.04} ${y + h} ${x - w * 0.02} ${y + h * 0.52} ${x + w * 0.18} ${y + h * 0.52}
    C${x + w * 0.18} ${y + h * 0.12} ${x + w * 0.52} ${y - h * 0.04} ${x + w * 0.62} ${y + h * 0.28}
    C${x + w * 0.74} ${y + h * 0.12} ${x + w * 1.02} ${y + h * 0.26} ${x + w * 0.92} ${y + h * 0.56}
    C${x + w * 1.08} ${y + h * 0.62} ${x + w * 1.04} ${y + h} ${x + w * 0.86} ${y + h} Z`;
}

const eyeDot = (x, y, r = 3.4) => `<circle cx="${x}" cy="${y}" r="${r}" fill="${INK}"/>`;
const cheek = (x, y) => `<ellipse cx="${x}" cy="${y}" rx="5" ry="3" fill="#FF8A80" opacity=".7"/>`;

const SUN = (cx, cy, r) => {
  let rays = '';
  for (let i = 0; i < 8; i++) {
    const a = i / 8 * Math.PI * 2 + 0.2;
    const x1 = cx + Math.cos(a) * (r + 5), y1 = cy + Math.sin(a) * (r + 5);
    const x2 = cx + Math.cos(a) * (r + 15), y2 = cy + Math.sin(a) * (r + 15);
    rays += `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" ${st}/>`;
  }
  return rays + `<path d="${blob(cx, cy, r, 0.04, 2)}" fill="#FFD23F" ${st}/>`;
};

const ICONS = {
  // 맑은 낮: 선글라스 낀 거만한 해
  clear_day: () => SUN(50, 50, 26) +
    `<path d="M30 45 Q40 41 49 45 Q51 43 53 45 Q62 41 71 45 L69 54 Q62 59 55 53 L51 48 L47 53 Q40 59 32 54 Z" fill="${INK}"/>
     <path d="M35 47 L41 46" stroke="#fff" stroke-width="2.2" stroke-linecap="round"/>
     <path d="M41 63 Q50 69 60 61" fill="none" ${st}/>`,
  // 맑은 밤: 콧물방울 불며 자는 달
  clear_night: () =>
    `<path d="M62 18 C38 16 22 36 26 58 C30 78 54 88 74 78 C56 78 42 64 44 46 C45 32 52 22 62 18 Z" fill="#FFE9A8" ${st}/>
     <path d="M34 50 Q38 53 42 50" fill="none" ${st}/><path d="M36 64 Q40 66 43 63" fill="none" ${st}/>
     <circle cx="48" cy="58" r="6" fill="#BFE6FF" stroke="${INK}" stroke-width="2.6"/>
     <text x="64" y="36" font-family="sans-serif" font-weight="900" font-size="18" fill="#fff" stroke="${INK}" stroke-width="2.2" paint-order="stroke">z</text>
     <text x="76" y="22" font-family="sans-serif" font-weight="900" font-size="13" fill="#fff" stroke="${INK}" stroke-width="2" paint-order="stroke">z</text>`,
  // 구름조금 낮: 구름 뒤에서 한쪽 눈만 빼꼼 내민 해
  partly_day: () => SUN(62, 38, 18) + eyeDot(68, 34, 3) +
    `<path d="${cloudPath(10, 44, 68, 38)}" fill="#fff" ${st}/>` + eyeDot(34, 66) + eyeDot(50, 66) + `<path d="M39 73 Q42 75 45 73" fill="none" ${st}/>`,
  partly_night: () =>
    `<path d="M74 14 C58 13 48 26 50 40 C52 52 64 58 76 52 C66 51 58 43 59 33 C60 24 66 18 74 14 Z" fill="#FFE9A8" ${st}/>
     <path d="${cloudPath(10, 44, 68, 38)}" fill="#fff" ${st}/>` + `<path d="M31 66 Q34 69 37 66" fill="none" ${st}/><path d="M47 66 Q50 69 53 66" fill="none" ${st}/>`,
  // 흐림: 만사 귀찮은 구름 (반쯤 감은 눈, 일자 입, 땀 한 방울)
  cloudy: () =>
    `<path d="${cloudPath(8, 28, 84, 52)}" fill="#E8ECF1" ${st}/>
     <path d="M33 56 L43 56" ${st}/><path d="M57 56 L67 56" ${st}/>` + eyeDot(38, 59, 2.6) + eyeDot(62, 59, 2.6) +
    `<path d="M44 70 L56 70" ${st}/>
     <path d="M76 40 Q80 48 76 51 Q72 48 76 40 Z" fill="#9FD3FF" stroke="${INK}" stroke-width="2.4"/>`,
  // 안개: 어지러워서 눈이 빙글빙글
  fog: () =>
    `<path d="${cloudPath(10, 18, 80, 48)}" fill="#E8ECF1" ${st}/>
     <path d="M34 44 a5 5 0 1 1 5 5 a2.5 2.5 0 1 1 -2.5 -2.5" fill="none" stroke="${INK}" stroke-width="2.6" stroke-linecap="round"/>
     <path d="M58 44 a5 5 0 1 1 5 5 a2.5 2.5 0 1 1 -2.5 -2.5" fill="none" stroke="${INK}" stroke-width="2.6" stroke-linecap="round"/>
     <path d="M44 58 Q50 54 56 58" fill="none" ${st}/>
     <rect x="12" y="72" width="50" height="8" rx="4" fill="#E8ECF1" stroke="${INK}" stroke-width="2.8"/><rect x="30" y="84" width="56" height="8" rx="4" fill="#E8ECF1" stroke="${INK}" stroke-width="2.8"/>`,
  // 비: 엉엉 우는 구름 (눈물 = 빗방울)
  rain: () =>
    `<path d="${cloudPath(8, 14, 84, 52)}" fill="#C9D6E3" ${st}/>
     <path d="M32 40 Q37 36 42 40" fill="none" ${st}/><path d="M58 40 Q63 36 68 40" fill="none" ${st}/>
     <path d="M43 54 Q50 48 57 54" fill="none" ${st}/>
     <path d="M37 44 Q33 58 36 64" fill="none" stroke="#5DB7FF" stroke-width="4" stroke-linecap="round"/>
     <path d="M63 44 Q67 58 64 64" fill="none" stroke="#5DB7FF" stroke-width="4" stroke-linecap="round"/>
     <path d="M28 76 Q24 84 28 88 Q32 84 28 76 Z" fill="#5DB7FF" stroke="${INK}" stroke-width="2.6"/>
     <path d="M50 78 Q46 86 50 90 Q54 86 50 78 Z" fill="#5DB7FF" stroke="${INK}" stroke-width="2.6"/>
     <path d="M72 76 Q68 84 72 88 Q76 84 72 76 Z" fill="#5DB7FF" stroke="${INK}" stroke-width="2.6"/>`,
  // 눈: 덜덜 떠는 눈사람 (볼 빨갛고 콧물)
  snow: () =>
    `<path d="${blob(50, 70, 22, 0.04, 3)}" fill="#fff" ${st}/>
     <path d="${blob(50, 36, 17, 0.05, 5)}" fill="#fff" ${st}/>
     <path d="M36 24 Q50 14 64 24 L62 28 Q50 22 38 28 Z" fill="#FF6B5E" ${st}/>` +
    eyeDot(44, 35, 2.8) + eyeDot(56, 35, 2.8) + cheek(40, 42) + cheek(60, 42) +
    `<path d="M50 39 L61 42 L50 43 Z" fill="#FF9F43" stroke="${INK}" stroke-width="2.2" stroke-linejoin="round"/>
     <path d="M30 62 L14 52 M19 55 L14 60" stroke="#8B5A3C" stroke-width="4" stroke-linecap="round"/><path d="M70 62 L86 52 M81 55 L86 60" stroke="#8B5A3C" stroke-width="4" stroke-linecap="round"/>
     <path d="M24 26 L18 24 M25 34 L18 36" stroke="#9FD3FF" stroke-width="3" stroke-linecap="round"/><path d="M76 26 L82 24 M75 34 L82 36" stroke="#9FD3FF" stroke-width="3" stroke-linecap="round"/>
     <circle cx="50" cy="64" r="2.6" fill="${INK}"/><circle cx="50" cy="74" r="2.6" fill="${INK}"/>`,
  // 뇌우: 화난 먹구름 + 번개
  thunder: () =>
    `<path d="${cloudPath(8, 12, 84, 50)}" fill="#8E9AAF" ${st}/>
     <path d="M30 34 L42 39" ${st}/><path d="M70 34 L58 39" ${st}/>` + eyeDot(37, 43, 3) + eyeDot(63, 43, 3) +
    `<path d="M42 54 Q50 49 58 54" fill="none" ${st}/>
     <path d="M54 58 L42 76 L52 76 L44 94 L66 68 L55 68 L62 58 Z" fill="#FFD23F" ${st}/>`,
};

function iconSvg(name, size = 256) {
  return { W: size, H: size, svg: `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 100 100">${ICONS[name]()}</svg>` };
}

module.exports = { ICONS, iconSvg };
