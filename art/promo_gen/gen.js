// 홍보용 캡쳐 (3.2.2 기준). lib.js가 실제 위젯 코드의 치수·색을 그대로 따라 그림
const { chromium } = require('/opt/node-tools/node_modules/playwright');
const fs = require('fs');
const dir = __dirname;
const OUT = process.argv[2] || dir + '/out';
fs.mkdirSync(OUT, { recursive: true });

const WALL = {
  dusk: 'linear-gradient(180deg,#3d4a7a 0%,#6c5a9c 35%,#c98a9a 70%,#efb68c 100%)',
  forest: 'linear-gradient(180deg,#1f3b36 0%,#2f5a4e 45%,#7a9a78 100%)',
  night: 'linear-gradient(180deg,#0f1630 0%,#222d55 55%,#46507e 100%)',
  sand: 'linear-gradient(180deg,#f3e6d3 0%,#e6cfb4 55%,#c9a988 100%)',
  ocean: 'linear-gradient(180deg,#0e3a5a 0%,#1d6b8a 50%,#7fc0c8 100%)',
};
const status = (c = '#fff') => `<div style="display:flex;justify-content:space-between;padding:14px 26px 0;color:${c};font:600 15px 'Noto Sans KR'"><span>9:41</span><svg width="46" height="14" viewBox="0 0 46 14"><g fill="${c}"><rect x="0" y="9" width="3" height="5" rx="1"/><rect x="5" y="6" width="3" height="8" rx="1"/><rect x="10" y="3" width="3" height="11" rx="1"/><rect x="15" y="0" width="3" height="14" rx="1"/><rect x="23" y="1" width="20" height="12" rx="3" fill="none" stroke="${c}" stroke-width="1.4"/><rect x="25" y="3" width="15" height="8" rx="1.5"/><rect x="44" y="5" width="2" height="4" rx="1"/></g></svg></div>`;
const header = (t, s, c = '#fff') => `<div style="padding:22px 26px 6px;color:${c}"><div style="font:800 27px/1.32 'Noto Sans KR';letter-spacing:-.5px">${t}</div><div style="margin-top:10px;font:500 14px/1.55 'Noto Sans KR';opacity:.85">${s}</div></div>`;
const cap = (t, c = '#fff') => `<div style="margin-top:7px;text-align:center;color:${c};font:600 12.5px 'Noto Sans KR';opacity:.9">${t}</div>`;
const page = (wall, body, js) => `<!doctype html><html><head><meta charset="utf-8"><link rel="stylesheet" href="f/fonts.css">
<style>body{margin:0;width:390px;height:844px;overflow:hidden;background:${wall};font-family:'Noto Sans KR'}.w{display:inline-block}
.grid{display:grid;justify-content:center}</style></head><body>${body}
<script src="lib.js"></script><script>
(async()=>{await Promise.all(["700 20px 'Noto Serif KR'","900 20px 'Noto Sans KR'","700 20px 'Noto Sans KR'","500 20px 'Noto Sans KR'","400 20px 'Noto Sans KR'"].map(f=>document.fonts.load(f)));
const $=(id)=>document.getElementById(id);
${js}
document.title='done';})().catch(e=>{console.log('ERR '+e.stack);document.title='done'});
</script></body></html>`;

const S = {};

// 1. 홈 화면
S['01_home'] = page(WALL.dusk, `${status()}
 <div style="display:flex;justify-content:space-between;padding:26px 22px 0"><div id=a></div><div id=b></div></div>
 <div style="padding:18px 20px 0" id=c></div>
 <div style="padding:16px 20px 0" id=d></div>
 <div style="position:absolute;left:0;right:0;bottom:120px;display:flex;justify-content:space-around;padding:0 20px">${['#FFCB47', '#4CD69B', '#5AA9FF', '#FF6B8B'].map((c, i) => `<div style="text-align:center;color:#fff;font:500 12px 'Noto Sans KR'"><div style="width:58px;height:58px;border-radius:18px;background:${c};margin-bottom:6px"></div>${['갤러리', '메시지', '캘린더', '음악'][i]}</div>`).join('')}</div>
 <div style="position:absolute;left:20px;right:20px;bottom:24px;height:82px;border-radius:30px;background:rgba(255,255,255,.22);display:flex;justify-content:space-around;align-items:center">${['#34C759', '#0A84FF', '#FF9F0A', '#BF5AF2'].map(c => `<div style="width:56px;height:56px;border-radius:18px;background:${c}"></div>`).join('')}</div>`,
`record2x2($('a'),{art:'sunset',rot:.6}); await weather2x2($('b'),'jacket','clear_day');
 await weatherWide($('c'),'pano','clear_day'); recordWide($('d'),{rot:1.2});`);

// 2. 레코드 위젯
S['02_record'] = page(WALL.night, `${status()}${header('음악을 틀면<br>레코드판이 돌아가요', '지금 듣는 곡의 앨범 커버가 레코드 라벨이 되고,<br>재생하면 톤암이 판 위로 내려와요.')}
 <div style="display:flex;justify-content:space-around;padding:18px 18px 0"><div><div id=a></div>${cap('기본 레코드판')}</div><div><div id=b></div>${cap('병맛 레코드판 (왕눈이 라벨)')}</div></div>
 <div style="padding:22px 20px 0"><div id=c></div>${cap('1×4 · 곡 제목 · 가수 · 조작 버튼')}</div>
 <div style="padding:16px 20px 0"><div id=d></div>${cap('병맛 테마 1×4')}</div>
 <div style="margin:22px 26px 0;padding:14px 16px;border-radius:18px;background:rgba(255,255,255,.1);color:#fff;font:500 13.5px/1.7 'Noto Sans KR'">▶ 위젯에서 바로 이전 곡 · 재생/일시정지 · 다음 곡<br>▶ 누르기 편한 큰 재생 버튼 (배경화면 색을 따라감)<br>▶ 유튜브 뮤직 재생 정보를 받아서 표시</div>`,
`record2x2($('a'),{art:'sunset',theme:'dark',rot:.5}); record2x2($('b'),{art:'mint',theme:'dark',quirky:true,rot:.3});
 recordWide($('c'),{theme:'dark',rot:1}); recordWide($('d'),{theme:'dark',quirky:true,art:'sunset',title:'월요일 출근길',artist:'City Pop Mix',rot:.1});`);

// 3. 날씨 디자인
S['03_weather_designs'] = page(WALL.forest, `${status()}${header('날씨·날짜 위젯 디자인', '2×2 10종 · 1×4 6종 중에서 골라 쓰세요 (일부)')}
 <div class=grid style="grid-template-columns:repeat(3,112px);gap:10px 12px;padding:8px 0 0">
 ${[['a', 'LP 재킷'], ['b', '불 켜진 텐트'], ['c', '하늘 원'], ['d', '큰 날짜 + 텐트'], ['e', '캐릭터 카드'], ['f', '캐릭터 헤드라인']].map(([i, t]) => `<div><div id=${i}></div>${cap(t)}</div>`).join('')}</div>
 <div style="padding:14px 20px 0"><div id=g></div>${cap('1×4 캠핑 파노라마')}</div>
 <div style="padding:10px 20px 0"><div id=h></div>${cap('1×4 캐릭터 카드')}</div>
 <div style="padding:10px 20px 0"><div id=i></div>${cap('1×4 텐트 + 날짜')}</div>`,
`const o={size:112}; await weather2x2($('a'),'jacket','clear_day',o); await weather2x2($('b'),'tent','clear_night',o); await weather2x2($('c'),'circle','cloudy_day',o);
 await weather2x2($('d'),'bigdate','rain_night',o); await weather2x2($('e'),'card','clear_day',o); await weather2x2($('f'),'headline','rain_day',o);
 await weatherWide($('g'),'pano','clear_night',{h:76}); await weatherWide($('h'),'card','snow_day',{h:72}); await weatherWide($('i'),'tent','clear_day',{h:70});`);

// 4. 날씨·시간에 따라 바뀜
const ks = ['clear_day', 'cloudy_day', 'rain_day', 'snow_day', 'clear_night', 'cloudy_night', 'rain_night', 'snow_night'];
const names = ['맑은 낮', '흐린 낮', '비 오는 낮', '눈 오는 낮', '맑은 밤', '구름 낀 밤', '비 오는 밤', '눈 오는 밤'];
S['04_weather_changes'] = page(WALL.night, `${status()}${header('날씨와 시간에 따라<br>캠핑장 그림이 바뀌어요', '맑음 · 흐림 · 비 · 눈 × 낮 · 밤<br>밤이나 비 오는 날엔 텐트와 전구에 불이 켜져요 🔥')}
 <div class=grid style="grid-template-columns:repeat(3,112px);gap:8px 12px;padding:6px 0 0">${ks.slice(0, 6).map((k, i) => `<div><div id=k${i}></div>${cap(names[i])}</div>`).join('')}</div>
 <div style="padding:12px 20px 0"><div id=p1></div>${cap('1×4 · 비 오는 밤')}</div>
 <div style="padding:8px 20px 0"><div id=p2></div>${cap('1×4 · 눈 오는 낮')}</div>`,
`for(let i=0;i<6;i++) await weather2x2($('k'+i),'jacket',${JSON.stringify(ks)}[i],{size:112});
 await weatherWide($('p1'),'pano','rain_night',{h:72}); await weatherWide($('p2'),'pano','snow_day',{h:72});`);

// 5. 병맛 테마
const fun = ['w_fun_clear_day', 'w_fun_partly_day', 'w_fun_cloudy', 'w_fun_rain', 'w_fun_snow', 'w_fun_thunder', 'w_fun_fog', 'w_fun_clear_night', 'w_fun_partly_night'];
S['05_quirky'] = page(WALL.sand, `${status('#2b2622')}${header('병맛 테마 ON 🤪', '표정 있는 날씨 캐릭터와 얼굴 있는 텐트,<br>왕눈이 레코드판까지. 켜고 끌 수 있어요.', '#2b2622')}
 <div style="margin:6px 22px 0;padding:12px 6px;border-radius:22px;background:rgba(255,255,255,.55);display:grid;grid-template-columns:repeat(9,1fr);gap:2px">${fun.map(f => `<img src="${f}.webp" style="width:36px;height:36px;justify-self:center">`).join('')}</div>
 <div class=grid style="grid-template-columns:repeat(2,160px);gap:12px 18px;padding:16px 0 0">
  <div><div id=a></div>${cap('캐릭터 포스터', '#2b2622')}</div><div><div id=b></div>${cap('날씨마다 색이 바뀌는 카드', '#2b2622')}</div>
  <div><div id=c></div>${cap('병맛 텐트', '#2b2622')}</div><div><div id=d></div>${cap('병맛 레코드판', '#2b2622')}</div></div>
 <div style="padding:14px 20px 0"><div id=e></div>${cap('1×4 캐릭터 카드', '#2b2622')}</div>`,
`await weather2x2($('a'),'poster','clear_day',{size:160,fg:'#2b2622',sub:'rgba(43,38,34,.7)'}); await weather2x2($('b'),'card','rain_night',{size:160});
 await weather2x2($('c'),'tent','clear_night',{size:160,fg:'#2b2622',sub:'rgba(43,38,34,.7)'}); record2x2($('d'),{size:160,quirky:true,art:'sunset',rot:.4});
 await weatherWide($('e'),'card','cloudy_day',{h:74});`);

// 6. 꾸미기
const sw = (cs, sel) => `<div style="display:flex;justify-content:space-between;margin:8px 2px 2px">${cs.map((c, i) => `<div style="width:30px;height:30px;border-radius:50%;background:${c};box-shadow:0 0 0 ${i === sel ? '3px #D0BCFF' : '1px rgba(255,255,255,.3)'};${i === sel ? 'transform:scale(1.08)' : ''}"></div>`).join('')}</div>`;
const card = (t, inner) => `<div style="margin:8px 14px 0;padding:12px 14px;border-radius:20px;background:#2b2930;color:#E6E0E9"><div style="font:700 14px 'Noto Sans KR'">${t}</div>${inner}</div>`;
const seg = (ls, sel) => `<div style="display:flex;margin-top:8px;padding:3px;border-radius:18px;background:#49454f">${ls.map((l, i) => `<div style="flex:1;text-align:center;padding:6px 0;border-radius:15px;font:600 12.5px 'Noto Sans KR';${i === sel ? 'background:#D0BCFF;color:#381E72' : 'color:#CAC4D0'}">${l}</div>`).join('')}</div>`;
const tog = (t, d, on) => `<div style="display:flex;align-items:center;margin-top:8px"><div style="flex:1"><div style="font:700 13px 'Noto Sans KR'">${t}</div><div style="font:400 11px 'Noto Sans KR';color:#CAC4D0">${d}</div></div><div style="width:40px;height:22px;border-radius:11px;background:${on ? '#D0BCFF' : '#49454f'};position:relative"><div style="position:absolute;top:3px;${on ? 'right:3px' : 'left:3px'};width:16px;height:16px;border-radius:50%;background:${on ? '#381E72' : '#938F99'}"></div></div></div>`;
S['06_customize'] = page(WALL.ocean, `${status()}
 <div style="height:236px;display:flex;align-items:center;justify-content:center;position:relative"><div style="position:absolute;top:10px;left:50%;transform:translateX(-50%);padding:3px 10px;border-radius:12px;background:rgba(0,0,0,.32);color:#fff;font:500 11px 'Noto Sans KR'">미리보기</div><div id=a style="margin-top:14px"></div></div>
 <div style="position:absolute;left:0;right:0;top:282px;bottom:0;background:rgba(28,27,31,.97);border-radius:28px 28px 0 0;padding-top:10px">
  <div style="width:36px;height:4px;border-radius:2px;background:#938F99;margin:0 auto 6px"></div>
  <div style="padding:2px 18px 4px;color:#E6E0E9;font:800 18px 'Noto Sans KR'">날씨 위젯 꾸미기</div>
  ${card('디자인', `<div style="display:flex;gap:6px;margin-top:8px;overflow:hidden;white-space:nowrap">${['LP 재킷', '불 켜진 텐트', '하늘 원', '캐릭터 카드', '헤드라인'].map((l, i) => `<div style="padding:6px 11px;border-radius:16px;font:600 12px 'Noto Sans KR';${i === 3 ? 'background:#D0BCFF;color:#381E72' : 'border:1px solid #938F99'}">${l}</div>`).join('')}</div>`)}
  ${card('테마', tog('병맛 테마', '날씨 아이콘이 표정 있는 캐릭터로 바뀌어요', true))}
  ${card('배경', seg(['없음', '유리', '검정', '흰색', '컬러'], 4) + `<div style="font:400 11px 'Noto Sans KR';color:#CAC4D0;margin-top:9px">배경화면에 어울리는 추천 색</div>` + sw(['#E8DEF8', '#E3E0F9', '#F9DCEA', '#4F378B', '#3A3840'], -1) + sw(['#F6E7C8', '#FFD6C2', '#CFE6D4', '#2F4A3A', '#22304F'], 2)
    + `<div style="display:flex;justify-content:space-between;margin-top:8px;font:600 12px 'Noto Sans KR'"><span>배경 투명도</span><span style="color:#D0BCFF">0%</span></div><div style="height:4px;border-radius:2px;background:#49454f;margin-top:7px;position:relative"><div style="width:4%;height:4px;border-radius:2px;background:#D0BCFF"></div><div style="position:absolute;left:3%;top:-6px;width:16px;height:16px;border-radius:50%;background:#D0BCFF"></div></div>`)}
  ${card('글자·아이콘 색상', `<div style="display:inline-block;margin-top:8px;padding:6px 12px;border-radius:16px;background:#D0BCFF;color:#381E72;font:600 12px 'Noto Sans KR'">✦ 자동 · 배경에 맞춤</div>` + sw(['#FFFBFF', '#E8DEF8', '#CFBCFF', '#CCC2DC', '#EFB8C8'], -1))}
 </div>`,
`await weather2x2($('a'),'card','clear_day',{size:180,card:'#CFE6D4'});`);

(async () => {
  for (const [n, html] of Object.entries(S)) fs.writeFileSync(`${dir}/${n}.html`, html);
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 1080 / 390 });
  p.on('console', (m) => console.log(m.text()));
  for (const n of Object.keys(S)) {
    await p.goto(`file://${dir}/${n}.html`); await p.waitForFunction(() => document.title === 'done', null, { timeout: 30000 });
    await p.screenshot({ path: `${OUT}/${n}.png` }); console.log('ok', n);
  }
  await b.close();
})();
