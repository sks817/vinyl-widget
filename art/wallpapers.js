const { chromium } = require('/opt/node-tools/node_modules/playwright');
const fs=require('fs');
const W=1080,H=2400;
const page=`<html><body style="margin:0"><canvas id=c width=${W} height=${H}></canvas><script>
const W=${W},H=${H};const c=document.getElementById('c'),x=c.getContext('2d');
let seed=7;const rnd=()=>{seed=(seed*9301+49297)%233280;return seed/233280};
const lg=(y0,y1,stops)=>{const g=x.createLinearGradient(0,y0,0,y1);stops.forEach(([p,col])=>g.addColorStop(p,col));return g};
const rg=(cx,cy,r,stops)=>{const g=x.createRadialGradient(cx,cy,0,cx,cy,r);stops.forEach(([p,col])=>g.addColorStop(p,col));return g};
function ridge(y,amp,col,step=60,s=1){x.fillStyle=col;x.beginPath();x.moveTo(0,H);let px=0;x.lineTo(0,y);while(px<=W+step){const yy=y-Math.abs(Math.sin(px*0.004*s+s))*amp-rnd()*amp*0.25;x.lineTo(px,yy);px+=step}x.lineTo(W,H);x.fill()}
function pine(cx,base,h,col){x.fillStyle=col;x.beginPath();x.moveTo(cx,base-h);for(let i=0;i<4;i++){const t=(i+1)/4;x.lineTo(cx+h*0.22*t+8,base-h+h*t*0.85);x.lineTo(cx+h*0.1*t,base-h+h*t*0.85)}x.lineTo(cx+6,base);x.lineTo(cx-6,base);for(let i=3;i>=0;i--){const t=(i+1)/4;x.lineTo(cx-h*0.1*t,base-h+h*t*0.85);x.lineTo(cx-h*0.22*t-8,base-h+h*t*0.85)}x.closePath();x.fill()}
function grain(a=.05){const img=x.getImageData(0,0,W,H),d=img.data;for(let i=0;i<d.length;i+=4){const n=(rnd()-.5)*255*a;d[i]+=n;d[i+1]+=n;d[i+2]+=n}x.putImageData(img,0,0)}
function tent(cx,base,w,col,glow){if(glow){x.fillStyle=rg(cx,base-w*.3,w*1.6,[[0,'rgba(255,190,110,.55)'],[1,'rgba(255,190,110,0)']]);x.fillRect(cx-w*2,base-w*2,w*4,w*3)}
 x.fillStyle=col;x.beginPath();x.moveTo(cx-w/2,base);x.lineTo(cx,base-w*.62);x.lineTo(cx+w/2,base);x.fill();x.fillStyle=glow?'#FFD89A':'rgba(0,0,0,.25)';x.beginPath();x.moveTo(cx-w*.12,base);x.lineTo(cx,base-w*.4);x.lineTo(cx+w*.12,base);x.fill()}
const D={};
D.night_camp=()=>{x.fillStyle=lg(0,H,[[0,'#0A1230'],[.55,'#1C2A57'],[.8,'#33386A'],[1,'#141A35']]);x.fillRect(0,0,W,H);
 x.fillStyle=rg(400,700,900,[[0,'rgba(140,150,255,.18)'],[1,'rgba(140,150,255,0)']]);x.fillRect(0,0,W,H);
 for(let i=0;i<420;i++){const sx=rnd()*W,sy=rnd()*H*.72,r=rnd()<.06?2.6:rnd()*1.4+.4;x.fillStyle='rgba(255,255,255,'+(.35+rnd()*.6)+')';x.beginPath();x.arc(sx,sy,r,0,7);x.fill()}
 x.fillStyle='#FFF4D6';x.beginPath();x.arc(830,420,70,0,7);x.fill();x.fillStyle='#0F1838';x.beginPath();x.arc(800,395,66,0,7);x.fill();
 x.fillStyle=rg(830,420,220,[[0,'rgba(255,240,200,.18)'],[1,'rgba(255,240,200,0)']]);x.fillRect(600,200,460,460);
 ridge(1780,180,'#232B58',70,1.3);ridge(1900,120,'#1A2048',60,2.1);
 for(const [px,h] of [[60,420],[150,330],[230,260],[880,380],[970,460],[1040,300]])pine(px,2130,h,'#0D1230');
 ridge(2130,30,'#0D1230',50,3);
 x.strokeStyle='rgba(255,255,255,.35)';x.lineWidth=2;x.beginPath();x.moveTo(150,1830);x.quadraticCurveTo(540,1990,900,1790);x.stroke();
 for(let i=0;i<=18;i++){const t=i/18,px=(1-t)*(1-t)*150+2*(1-t)*t*540+t*t*900,py=(1-t)*(1-t)*1830+2*(1-t)*t*1990+t*t*1790;x.fillStyle=rg(px,py+8,26,[[0,'rgba(255,214,140,.9)'],[1,'rgba(255,214,140,0)']]);x.fillRect(px-26,py-18,52,52);x.fillStyle='#FFE3A8';x.beginPath();x.arc(px,py+8,5,0,7);x.fill()}
 tent(540,2120,300,'#2A2F55',true);grain(.03)};
D.dawn_lake=()=>{x.fillStyle=lg(0,1760,[[0,'#FFE1C8'],[.35,'#FFC3B4'],[.65,'#E7A9C4'],[1,'#B8A6E3']]);x.fillRect(0,0,W,1760);
 x.fillStyle=rg(540,1560,520,[[0,'rgba(255,248,230,.95)'],[.25,'rgba(255,236,210,.6)'],[1,'rgba(255,236,210,0)']]);x.fillRect(0,900,W,1000);
 x.fillStyle='#FFF6E8';x.beginPath();x.arc(540,1580,110,0,7);x.fill();
 for(let i=0;i<5;i++){x.fillStyle='rgba(255,255,255,.45)';const cy=300+i*170,cx=rnd()*W;x.beginPath();x.ellipse(cx,cy,160+rnd()*120,22,0,0,7);x.fill()}
 ridge(1640,160,'#D49CC6',70,1.1);ridge(1700,120,'#AE8CCB',60,1.7);ridge(1760,70,'#8C79BF',50,2.6);
 x.fillStyle=lg(1760,H,[[0,'#C7B6E8'],[.4,'#E8B8CC'],[1,'#7E70B5']]);x.fillRect(0,1760,W,H-1760);
 for(let i=0;i<46;i++){const y=1780+i*14+rnd()*6,w=60+rnd()*260,cx=540+(rnd()-.5)*700*(i/46+.3);x.fillStyle='rgba(255,246,232,'+(0.5-i/110)+')';x.fillRect(cx-w/2,y,w,3)}
 tent(250,1765,90,'#5E4D8F',false);x.fillStyle='#5E4D8F';x.fillRect(320,1755,3,10);grain(.025)};
D.retro_lp=()=>{x.fillStyle='#F3E6CF';x.fillRect(0,0,W,H);
 x.save();x.translate(540,2600);for(let i=0;i<36;i++){x.rotate(Math.PI/18);x.fillStyle=i%2?'rgba(242,193,78,.16)':'rgba(243,230,207,0)';x.beginPath();x.moveTo(0,0);x.lineTo(-90,-2600);x.lineTo(90,-2600);x.fill()}x.restore();
 const cols=['#C2462E','#E07A2C','#F2C14E','#6E8B3D','#2F5D62'];cols.forEach((col,i)=>{x.strokeStyle=col;x.lineWidth=70;x.beginPath();x.arc(-40,2400,900-i*70,-Math.PI/2,0);x.stroke()});
 const cx=860,cy=2080,R=420;x.fillStyle='rgba(0,0,0,.25)';x.beginPath();x.arc(cx+14,cy+20,R,0,7);x.fill();x.fillStyle='#151515';x.beginPath();x.arc(cx,cy,R,0,7);x.fill();
 for(let r=R-20;r>140;r-=9){x.strokeStyle='rgba(255,255,255,'+(r%27<9?.09:.04)+')';x.lineWidth=2;x.beginPath();x.arc(cx,cy,r,0,7);x.stroke()}
 x.strokeStyle='rgba(255,255,255,.22)';x.lineWidth=26;x.beginPath();x.arc(cx,cy,R*.7,-2.4,-1.7);x.stroke();
 x.fillStyle='#E07A2C';x.beginPath();x.arc(cx,cy,140,0,7);x.fill();x.fillStyle='#F3E6CF';x.beginPath();x.arc(cx,cy,14,0,7);x.fill();
 x.fillStyle='#F3E6CF';x.font="900 46px 'Noto Serif KR', serif";x.textAlign='center';x.fillText('SIDE A',cx,cy-50);
 grain(.06)};
D.doodle_pastel=()=>{x.fillStyle=lg(0,H,[[0,'#FFF4E8'],[1,'#FDE7EF']]);x.fillRect(0,0,W,H);
 const cols=['#FFD84D','#FF9EB5','#9DD6FF','#7ED99A','#B79CFF','#FFB86B'];const ink='rgba(43,38,34,.55)';
 for(let i=0;i<70;i++){const px=rnd()*W,py=rnd()*H,s=24+rnd()*34,col=cols[Math.floor(rnd()*cols.length)],k=Math.floor(rnd()*5),rot=rnd()*6;
  x.save();x.translate(px,py);x.rotate(rot);x.globalAlpha=.55;x.fillStyle=col;x.strokeStyle=ink;x.lineWidth=3;x.lineJoin='round';x.beginPath();
  if(k==0){for(let j=0;j<10;j++){const a=-Math.PI/2+j*Math.PI/5,q=j%2?s*.45:s;x.lineTo(q*Math.cos(a),q*Math.sin(a))}x.closePath();x.fill();x.stroke()}
  else if(k==1){x.moveTo(0,s*.35);x.bezierCurveTo(-s*.9,-s*.2,-s*.4,-s*.9,0,-s*.35);x.bezierCurveTo(s*.4,-s*.9,s*.9,-s*.2,0,s*.35);x.fill();x.stroke()}
  else if(k==2){x.arc(0,0,s*.5,0,7);x.fill();x.stroke()}
  else if(k==3){x.moveTo(-s,0);for(let j=0;j<4;j++)x.quadraticCurveTo(-s+j*s*.5+s*.25,(j%2?1:-1)*s*.4,-s+(j+1)*s*.5,0);x.lineWidth=6;x.strokeStyle=col;x.stroke()}
  else{x.moveTo(0,-s);x.quadraticCurveTo(s*.12,-s*.12,s,0);x.quadraticCurveTo(s*.12,s*.12,0,s);x.quadraticCurveTo(-s*.12,s*.12,-s,0);x.quadraticCurveTo(-s*.12,-s*.12,0,-s);x.fill();x.stroke()}
  x.restore()}
 grain(.02)};
D.forest_day=()=>{x.fillStyle=lg(0,1700,[[0,'#F1EADB'],[.6,'#E6E6D2'],[1,'#D2DEC6']]);x.fillRect(0,0,W,1700);
 x.fillStyle='#F2B880';x.beginPath();x.arc(760,1180,120,0,7);x.fill();
 x.fillStyle='rgba(255,255,255,.6)';for(const [cx,cy,w] of [[260,520,220],[760,760,180],[420,980,140]]){x.beginPath();x.ellipse(cx,cy,w,30,0,0,7);x.fill()}
 ridge(1560,170,'#B9C6A8',80,1.2);for(let i=0;i<14;i++)pine(rnd()*W,1600+rnd()*60,140+rnd()*90,'#9DB08D');
 ridge(1760,110,'#8EA27F',70,2.0);for(let i=0;i<12;i++)pine(rnd()*W,1830+rnd()*40,200+rnd()*120,'#748A68');
 ridge(1980,60,'#5E7356',60,2.9);for(let i=0;i<8;i++){const px=rnd()<.5?rnd()*300:W-rnd()*300;pine(px,2240,300+rnd()*160,'#46593F')}
 x.fillStyle='#46593F';x.fillRect(0,2230,W,H-2230);
 tent(560,2060,190,'#D9763E',false);x.fillStyle='#B85C2C';x.beginPath();x.moveTo(560,1942);x.lineTo(655,2060);x.lineTo(560,2060);x.fill();
 x.strokeStyle='rgba(255,255,255,.55)';x.lineWidth=5;x.beginPath();x.moveTo(720,2040);x.bezierCurveTo(700,1990,750,1960,725,1910);x.stroke();
 x.fillStyle='#F2A13A';x.beginPath();x.moveTo(705,2060);x.quadraticCurveTo(722,2020,735,2060);x.fill();grain(.03)};
D.y2k_checker=()=>{const s=110;for(let yy=0;yy<H/s+1;yy++)for(let xx=0;xx<W/s+1;xx++){x.fillStyle=(xx+yy)%2?'#F7E3F0':'#E6E2FF';x.fillRect(xx*s,yy*s,s,s)}
 x.fillStyle=lg(0,H,[[0,'rgba(255,255,255,.35)'],[.5,'rgba(255,255,255,0)'],[1,'rgba(255,214,236,.4)']]);x.fillRect(0,0,W,H);
 const blob=(cx,cy,r,col)=>{x.fillStyle=col;x.beginPath();for(let a=0;a<=Math.PI*2+.01;a+=.2){const q=r*(1+.12*Math.sin(a*5+cx));x.lineTo(cx+q*Math.cos(a),cy+q*Math.sin(a))}x.fill()};
 blob(140,2150,260,'rgba(183,156,255,.55)');blob(980,1900,220,'rgba(255,158,181,.5)');blob(900,2350,180,'rgba(157,214,255,.55)');
 const spark=(cx,cy,r)=>{const g=x.createLinearGradient(cx-r,cy-r,cx+r,cy+r);g.addColorStop(0,'#ffffff');g.addColorStop(.5,'#C9D3FF');g.addColorStop(1,'#FFD1EC');x.fillStyle=g;x.beginPath();x.moveTo(cx,cy-r);x.quadraticCurveTo(cx+r*.15,cy-r*.15,cx+r,cy);x.quadraticCurveTo(cx+r*.15,cy+r*.15,cx,cy+r);x.quadraticCurveTo(cx-r*.15,cy+r*.15,cx-r,cy);x.quadraticCurveTo(cx-r*.15,cy-r*.15,cx,cy-r);x.fill();x.strokeStyle='rgba(120,110,200,.5)';x.lineWidth=3;x.stroke()};
 spark(860,330,70);spark(170,700,40);spark(930,1350,46);spark(300,1880,60);spark(600,2250,34);grain(.02)};
window.draw=(n)=>{seed=7;x.clearRect(0,0,W,H);D[n]();return c.toDataURL('image/png')};
</script></body></html>`;
(async()=>{fs.writeFileSync(__dirname+'/w_tmp.html',page);const b=await chromium.launch();const p=await b.newPage();await p.goto('file://'+__dirname+'/w_tmp.html');
 const names=['night_camp','dawn_lake','retro_lp','doodle_pastel','forest_day','y2k_checker'];
 for(const n of names){const d=await p.evaluate(n=>window.draw(n),n);fs.writeFileSync(__dirname+'/../wallpapers/wall_'+n+'.png',Buffer.from(d.split(',')[1],'base64'))}
 await b.close();console.log('ok')})();
