const { chromium } = require('/opt/node-tools/node_modules/playwright');
const { scene, tentOnly } = require('./scene.js');
const fs = require('fs');
const kinds = ['clear', 'cloudy', 'rain', 'snow'];
const which = process.argv[2] || 'pa';
let html = '<body style="margin:0;background:#888;display:flex;flex-wrap:wrap;gap:8px;padding:8px">';
for (const n of [false, true]) for (const k of kinds) {
  if (which === 'tent') continue;
  const sc = scene(k, n, which);
  const w = which === 'pa' ? 690 : 330;
  html += `<div style="width:${w}px">${sc.svg.replace(/width="\d+" height="\d+"/, `width="${w}" height="${w * sc.H / sc.W}"`)}</div>`;
}
if (which === 'tent') for (const lit of [false, true]) for (const f of ['tent', 'icon']) { const t = tentOnly(lit, f); html += `<div style="background:#ddd">${t.svg}</div>`; }
fs.writeFileSync('p.html', html);
(async () => { const b = await chromium.launch(); const p = await b.newPage({ viewport: { width: 1420, height: 600 } }); await p.goto('file://' + __dirname + '/p.html'); await p.waitForTimeout(300); await p.screenshot({ path: `prev_${which}.png`, fullPage: true }); await b.close(); })();
