const { chromium } = require('/opt/node-tools/node_modules/playwright');
const { ICONS, iconSvg } = require('./icons.js');
const fs = require('fs');
let html = '<body style="margin:0;display:flex;flex-wrap:wrap;gap:10px;padding:10px;background:linear-gradient(90deg,#f3efe6 50%,#1b2340 50%)">';
for (const n of Object.keys(ICONS)) html += `<div style="width:120px;text-align:center;font:12px sans-serif;color:#888">${iconSvg(n, 120).svg}<br>${n}</div>`;
fs.writeFileSync(__dirname + '/ip.html', html);
(async () => { const b = await chromium.launch(); const p = await b.newPage({ viewport: { width: 700, height: 300 } }); await p.goto('file://' + __dirname + '/ip.html'); await p.screenshot({ path: __dirname + '/prev_icons.png', fullPage: true }); await b.close(); })();
