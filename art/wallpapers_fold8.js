// 폴드8 해상도 배경화면: 땅(아래 700 단위)은 아래에 붙이고, 하늘은 남는 높이만큼 늘림. 가로가 넓으면 숲·산을 옆으로 이어 그림
const { chromium } = require('/opt/node-tools/node_modules/playwright');
const fs=require('fs');
const SIZES={fold8_cover:[1248,1972],fold8_main:[2448,1848],ultra_cover:[1080,2520],ultra_main:[2504,2256]};
const NAMES=['forest_night','canyon_dusk','topo_map','vinyl_noir','milkyway','mono_grain'];
const page=`<html><head><link rel="stylesheet" href="../p3/f/fonts.css"></head><body style="margin:0"><canvas id=c></canvas><script>
let x,W,H,u,VW,VH,seed=7;const rnd=()=>{seed=(seed*9301+49297)%233280;return seed/233280};
const gy=v=>VH-(2400-v);            // 땅: 아래 기준
const cx=v=>v+(VW-1080)/2;          // 가운데 물체: 가로 가운데 기준
const sy=v=>v*(VH-700)/1700;        // 하늘: 남는 높이에 맞춰 늘림
const lg=(y0,y1,stops)=>{const g=x.createLinearGradient(0,y0,0,y1);stops.forEach(([p,col])=>g.addColorStop(p,col));return g};
const rg=(a,b,r,stops)=>{const g=x.createRadialGradient(a,b,0,a,b,r);stops.forEach(([p,col])=>g.addColorStop(p,col));return g};
function ridge(y,amp,col,step=60,s=1){x.fillStyle=col;x.beginPath();x.moveTo(0,VH);let px=0;x.lineTo(0,y);while(px<=VW+step){const yy=y-Math.abs(Math.sin(px*0.004*s+s))*amp-rnd()*amp*0.25;x.lineTo(px,yy);px+=step}x.lineTo(VW,VH);x.fill()}
function pine(px,base,h,col){x.fillStyle=col;x.beginPath();x.moveTo(px,base-h);for(let i=0;i<4;i++){const t=(i+1)/4;x.lineTo(px+h*0.22*t+8,base-h+h*t*0.85);x.lineTo(px+h*0.1*t,base-h+h*t*0.85)}x.lineTo(px+6,base);x.lineTo(px-6,base);for(let i=3;i>=0;i--){const t=(i+1)/4;x.lineTo(px-h*0.1*t,base-h+h*t*0.85);x.lineTo(px-h*0.22*t-8,base-h+h*t*0.85)}x.closePath();x.fill()}
function tent(px,base,w,col,glow){if(glow){x.fillStyle=rg(px,base-w*.3,w*1.6,[[0,'rgba(255,190,110,.55)'],[1,'rgba(255,190,110,0)']]);x.fillRect(px-w*2,base-w*2,w*4,w*3)}
 x.fillStyle=col;x.beginPath();x.moveTo(px-w/2,base);x.lineTo(px,base-w*.62);x.lineTo(px+w/2,base);x.fill();x.fillStyle=glow?'#FFD89A':'rgba(0,0,0,.25)';x.beginPath();x.moveTo(px-w*.12,base);x.lineTo(px,base-w*.4);x.lineTo(px+w*.12,base);x.fill()}
function stars(n,top,bot,a=.5){const k=VW*(bot-top)/(1080*1700);for(let i=0;i<n*k;i++){x.fillStyle='rgba(235,240,235,'+(.2+rnd()*a)+')';x.beginPath();x.arc(rnd()*VW,top+rnd()*(bot-top),rnd()<.04?2.2:rnd()*1.3+.3,0,7);x.fill()}}
function grain(a){x.setTransform(1,0,0,1,0,0);const img=x.getImageData(0,0,W,H),d=img.data;for(let i=0;i<d.length;i+=4){const n=(rnd()-.5)*255*a;d[i]+=n;d[i+1]+=n;d[i+2]+=n}x.putImageData(img,0,0)}
const per=k=>Math.round(k*VW/1080);   // 넓으면 나무 수도 늘림
const D={};
D.forest_night=()=>{x.fillStyle=lg(0,VH,[[0,'#05090A'],[.5,'#0E1A17'],[.8,'#16261F'],[1,'#070B0A']]);x.fillRect(0,0,VW,VH);
 stars(260,0,sy(1080));ridge(gy(1700),220,'#122019',80,1.1);
 x.fillStyle=lg(gy(1500),gy(1900),[[0,'rgba(180,200,190,0)'],[.5,'rgba(180,200,190,.10)'],[1,'rgba(180,200,190,0)']]);x.fillRect(0,gy(1500),VW,400);
 for(let i=0;i<per(16);i++)pine(rnd()*VW,gy(1880+rnd()*60),260+rnd()*160,'#0C1611');
 ridge(gy(1990),50,'#08100C',60,2.4);for(let i=0;i<per(9);i++){const px=rnd()<.5?rnd()*260:VW-rnd()*260;pine(px,gy(2300),420+rnd()*200,'#040806')}
 x.fillStyle='#040806';x.fillRect(0,gy(2280),VW,VH);
 const fx=cx(540),fy=gy(1930);x.fillStyle=rg(fx,fy,420,[[0,'rgba(255,140,50,.55)'],[.4,'rgba(255,120,40,.18)'],[1,'rgba(255,120,40,0)']]);x.fillRect(fx-540,fy-450,1080,800);
 x.fillStyle='#FF8A3D';x.beginPath();x.moveTo(fx-30,fy);x.quadraticCurveTo(fx,fy-90,fx+30,fy);x.fill();x.fillStyle='#FFD08A';x.beginPath();x.moveTo(fx-12,fy);x.quadraticCurveTo(fx,fy-40,fx+12,fy);x.fill();
 x.strokeStyle='#2A1A10';x.lineWidth=12;x.beginPath();x.moveTo(fx-50,fy+15);x.lineTo(fx+50,fy-5);x.moveTo(fx-50,fy-5);x.lineTo(fx+50,fy+15);x.stroke();grain(.04)};
D.canyon_dusk=()=>{const hz=gy(1700);x.fillStyle=lg(0,hz,[[0,'#0F2233'],[.45,'#2B4A5C'],[.75,'#C46A3C'],[1,'#E8A15C']]);x.fillRect(0,0,VW,hz);
 stars(120,0,hz*.35);
 x.fillStyle='#7A3A22';x.beginPath();x.moveTo(0,hz);x.lineTo(0,gy(1420));x.lineTo(120,gy(1400));x.lineTo(160,gy(1460));x.lineTo(300,gy(1450));x.lineTo(330,gy(1560));x.lineTo(0,hz);x.fill();
 x.beginPath();x.moveTo(VW,hz);x.lineTo(VW,gy(1380));x.lineTo(VW-180,gy(1370));x.lineTo(VW-220,gy(1430));x.lineTo(VW-340,gy(1440));x.lineTo(VW-380,gy(1560));x.lineTo(VW,hz);x.fill();
 ridge(gy(1640),90,'#5A2A1A',70,1.7);x.fillStyle=lg(gy(1640),VH,[[0,'#3A1C14'],[1,'#120807']]);x.fillRect(0,gy(1640),VW,VH);
 const rx=cx(520);x.fillStyle='#1D1210';x.beginPath();x.moveTo(rx-50,gy(1650));x.quadraticCurveTo(rx,gy(1900),rx-220,VH);x.lineTo(rx+260,VH);x.quadraticCurveTo(rx+60,gy(1900),rx+50,gy(1650));x.fill();
 x.strokeStyle='rgba(255,210,120,.6)';x.setLineDash([26,30]);x.lineWidth=6;x.beginPath();x.moveTo(rx,gy(1660));x.quadraticCurveTo(rx+30,gy(1900),rx+20,VH);x.stroke();x.setLineDash([]);
 const vx=rx-50,vy=gy(1815);x.fillStyle=rg(vx,vy+25,160,[[0,'rgba(255,230,160,.35)'],[1,'rgba(255,230,160,0)']]);x.fillRect(vx-170,vy-140,340,320);
 x.fillStyle='#0B0605';x.fillRect(vx,vy,140,60);x.fillRect(vx+20,vy-30,90,40);x.fillStyle='rgba(255,220,150,.9)';x.fillRect(vx+30,vy-23,30,22);x.fillStyle='#000';x.beginPath();x.arc(vx+30,vy+63,16,0,7);x.arc(vx+115,vy+63,16,0,7);x.fill();grain(.035)};
D.topo_map=()=>{x.fillStyle='#2E3427';x.fillRect(0,0,VW,VH);
 x.setTransform(1,0,0,1,0,0);const f=(px,py)=>{px/=u;py/=u;return Math.sin(px*.004+1)*Math.cos(py*.003)+Math.sin((px+py)*.0025)*.8+Math.cos(px*.0015-py*.004)*.6};
 const img=x.getImageData(0,0,W,H),d=img.data,st=Math.max(2,Math.round(2*u));for(let py=0;py<H;py+=st)for(let px=0;px<W;px+=st){const v=f(px,py)*6;if(Math.abs(v-Math.round(v))<.045){for(let oy=0;oy<st;oy++)for(let ox=0;ox<st;ox++){const i=((py+oy)*W+px+ox)*4;if(i<d.length){d[i]=150;d[i+1]=160;d[i+2]=120}}}}
 x.putImageData(img,0,0);x.setTransform(u,0,0,u,0,0);
 const o=(VW-1080)/2;x.strokeStyle='rgba(230,140,60,.9)';x.lineWidth=5;x.setLineDash([18,14]);x.beginPath();x.moveTo(o+160,gy(2250));x.bezierCurveTo(o+300,gy(2000),o+700,gy(2150),o+760,gy(1850));x.bezierCurveTo(o+800,gy(1700),o+620,gy(1650),o+700,gy(1560));x.stroke();x.setLineDash([]);
 x.fillStyle='#E68C3C';x.beginPath();x.moveTo(o+700,gy(1520));x.lineTo(o+728,gy(1570));x.lineTo(o+672,gy(1570));x.fill();
 x.strokeStyle='rgba(220,225,200,.55)';x.lineWidth=3;x.beginPath();x.arc(VW-180,gy(2200),70,0,7);x.stroke();x.fillStyle='rgba(220,225,200,.8)';x.beginPath();x.moveTo(VW-180,gy(2130));x.lineTo(VW-165,gy(2200));x.lineTo(VW-195,gy(2200));x.fill();
 x.font="700 30px monospace";x.fillStyle='rgba(220,225,200,.6)';x.fillText('N',VW-190,gy(2120));x.fillText('37°33′N 127°58′E · ALT 1,208m',60,gy(2340));grain(.04)};
D.vinyl_noir=()=>{x.fillStyle=lg(0,VH,[[0,'#121212'],[1,'#1E1C1A']]);x.fillRect(0,0,VW,VH);
 x.fillStyle=rg(VW-180,sy(300),700,[[0,'rgba(255,170,80,.14)'],[1,'rgba(255,170,80,0)']]);x.fillRect(0,0,VW,VH);
 const ox=cx(560),oy=gy(1880),R=600;x.fillStyle='#0A0A0A';x.beginPath();x.arc(ox,oy,R,0,7);x.fill();
 for(let r=R-16;r>200;r-=7){x.strokeStyle='rgba(255,255,255,'+(r%21<7?.06:.025)+')';x.lineWidth=2;x.beginPath();x.arc(ox,oy,r,0,7);x.stroke()}
 x.strokeStyle='rgba(255,200,140,.22)';x.lineWidth=40;x.beginPath();x.arc(ox,oy,R*.72,-2.6,-2.0);x.stroke();
 x.fillStyle='#C4512C';x.beginPath();x.arc(ox,oy,200,0,7);x.fill();x.fillStyle='#121212';x.beginPath();x.arc(ox,oy,16,0,7);x.fill();
 x.fillStyle='#F1E4CF';x.font="900 46px 'Noto Serif KR', serif";x.textAlign='center';x.fillText('SIDE B',ox,oy-80);x.font="700 26px monospace";x.fillText('33⅓ RPM',ox,oy+100);
 const ax=ox+450,ay=oy-680;x.strokeStyle='#8F8F8F';x.lineWidth=16;x.lineCap='round';x.beginPath();x.moveTo(ax,ay);x.lineTo(ax-20,ay+360);x.lineTo(ax-150,ay+480);x.stroke();x.fillStyle='#5A5A5A';x.beginPath();x.arc(ax,ay,46,0,7);x.fill();grain(.05)};
D.milkyway=()=>{x.fillStyle=lg(0,VH,[[0,'#03050C'],[.6,'#0B1226'],[1,'#05070F']]);x.fillRect(0,0,VW,VH);
 const mx=VW/2,my=sy(1100),ang=VW>VH?-.25:-.5,len=Math.hypot(VW,VH)*.6;x.save();x.translate(mx,my);x.rotate(ang);x.fillStyle=rg(0,0,len*.75,[[0,'rgba(170,180,220,.22)'],[.5,'rgba(120,130,200,.08)'],[1,'rgba(0,0,0,0)']]);x.scale(1,.28);x.beginPath();x.arc(0,0,len*.9,0,7);x.fill();x.restore();
 const n=1400*Math.max(1,VW/1080);for(let i=0;i<n;i++){const t=rnd()*2-1,along=t*len,off=(rnd()-.5)*(rnd()<.7?300:1600);const px=mx+along*Math.cos(ang)-off*Math.sin(ang),py=my+along*Math.sin(ang)+off*Math.cos(ang);x.fillStyle='rgba(255,255,255,'+(.15+rnd()*.7)+')';x.beginPath();x.arc(px,py,rnd()<.03?2.2:rnd()*1.1+.2,0,7);x.fill()}
 ridge(gy(1820),160,'#05070C',60,1.6);x.fillStyle='#05070C';x.fillRect(0,gy(1810),VW,VH);
 tent(cx(760),gy(1930),170,'#0E1220',true);x.fillStyle='#0A0D18';x.beginPath();x.arc(cx(250),gy(1900),26,0,7);x.fill();x.fillRect(cx(238),gy(1920),24,90);grain(.03)};
D.mono_grain=()=>{x.fillStyle=lg(0,VH,[[0,'#2A2C2E'],[1,'#141516']]);x.fillRect(0,0,VW,VH);
 x.fillStyle=rg(200,gy(2200),900,[[0,'rgba(255,255,255,.05)'],[1,'rgba(255,255,255,0)']]);x.fillRect(0,0,VW,VH);
 x.strokeStyle='#E8742C';x.lineWidth=6;x.beginPath();x.moveTo(80,gy(1980));x.lineTo(VW-80,gy(1980));x.stroke();
 x.fillStyle='rgba(255,255,255,.85)';x.font="900 120px 'Noto Sans KR'";x.textAlign='left';x.fillText('OUTSIDE',76,gy(1920));
 x.font="500 34px 'Noto Sans KR'";x.fillStyle='rgba(255,255,255,.55)';x.fillText('LEAVE NO TRACE · KEEP WANDERING',80,gy(2050));
 x.fillStyle='#E8742C';x.beginPath();x.moveTo(VW-150,gy(1890));x.lineTo(VW-90,gy(1960));x.lineTo(VW-210,gy(1960));x.fill();grain(.07)};
window.draw=(n,w,h)=>{seed=7;W=w;H=h;const c=document.getElementById('c');c.width=W;c.height=H;x=c.getContext('2d');
 u=Math.min(W/1080,0.42*H/700);VW=W/u;VH=H/u;x.setTransform(u,0,0,u,0,0);D[n]();return c.toDataURL('image/png')};
</script></body></html>`;
(async()=>{fs.writeFileSync(__dirname+'/w3.html',page);fs.mkdirSync(__dirname+'/fold8',{recursive:true});const b=await chromium.launch();const p=await b.newPage();await p.goto('file://'+__dirname+'/w3.html');
 await p.evaluate(()=>document.fonts.load("900 40px 'Noto Sans KR'",'OUT').then(()=>document.fonts.load("900 40px 'Noto Serif KR'",'SIDE')).then(()=>document.fonts.load("500 30px 'Noto Sans KR'",'LEAVE')));
 for(const [sz,[w,h]] of Object.entries(SIZES))for(const n of NAMES){const d=await p.evaluate(([n,w,h])=>window.draw(n,w,h),[n,w,h]);fs.writeFileSync(`${__dirname}/fold8/${sz}_${n}.png`,Buffer.from(d.split(',')[1],'base64'))}
 await b.close();console.log('ok')})();
