const { chromium } = require('/opt/node-tools/node_modules/playwright');
const { scene, tentOnly } = require('./scene.js');
const fs = require('fs');
const OUT = __dirname + '/../VinylWidget/app/src/main/res/drawable-nodpi';
const jobs = [];
for (const k of ['clear', 'cloudy', 'rain', 'snow']) for (const n of [false, true]) {
  const tag = `${k}_${n ? 'night' : 'day'}`;
  jobs.push([`w_sc_${tag}`, scene(k, n, 'sq')], [`w_ci_${tag}`, scene(k, n, 'ci')], [`w_pa_${tag}`, scene(k, n, 'pa')]);
}
jobs.push(['w_tent_day', tentOnly(false, 'tent')], ['w_tent_glow', tentOnly(true, 'tent')], ['w_tent_icon_day', tentOnly(false, 'icon')], ['w_tent_icon', tentOnly(true, 'icon')]);
(async () => {
  const b = await chromium.launch(); const p = await b.newPage();
  await p.setContent('<canvas id=c></canvas>');
  for (const [name, { W, H, svg }] of jobs) {
    const data = await p.evaluate(async ({ svg, W, H }) => {
      const img = new Image(); img.src = 'data:image/svg+xml;base64,' + btoa(unescape(encodeURIComponent(svg)));
      await img.decode();
      const c = document.getElementById('c'); c.width = W; c.height = H;
      const g = c.getContext('2d'); g.clearRect(0, 0, W, H); g.drawImage(img, 0, 0, W, H);
      return c.toDataURL('image/webp', 0.88);
    }, { svg, W, H });
    if (!data.startsWith('data:image/webp')) throw new Error('no webp ' + name);
    fs.writeFileSync(`${OUT}/${name}.webp`, Buffer.from(data.split(',')[1], 'base64'));
  }
  await b.close(); console.log('exported', jobs.length);
})();
