# Turns block.js and photos.js into assets/home/nightdrive.js for the launcher.
# usage: python3 port.py block.js photos.js out.js   (the car pictures themselves are assets/home/cars/*.webp)
import sys, re
blk, base, out = sys.argv[1:4]
b = open(blk).read()
photo = re.search(r'^const PHOTO=\[.*$', open(base).read(), re.M).group(0)

def R(a, z, n=1):
    global b
    assert b.count(a) >= n, ('missing', a[:80])
    b = b.replace(a, z)

# drop the mockup-only parts: weather (the page has its own) and the capture hooks
b = b[:b.index('/* ---- weather (mockup only')]
# pre-drawn layers at the screen's resolution, not the mockup's 3x
R("function surface(w,h,paint,k=3){", "function surface(w,h,paint,k=RK){")
R("fadeEnds(c); },2);", "fadeEnds(c); });")
R("mc.drawImage(ROADMARK,(W.x-RX)*3,(0-RY)*3,W.w*3,W.h*3,0,0,W.w,W.h);", "mc.drawImage(ROADMARK,(W.x-RX)*RK,(0-RY)*RK,W.w*RK,W.h*RK,0,0,W.w,W.h);")
R("c.drawImage(ROADBASE,(W.x-RX)*3,(0-RY)*3,W.w*3,W.h*3,0,0,W.w,W.h);", "c.drawImage(ROADBASE,(W.x-RX)*RK,(0-RY)*RK,W.w*RK,W.h*RK,0,0,W.w,W.h);")
# the road behind the dock is drawn once into its own canvas; the moving layer is cleared each frame
i = b.index('const TRACKBG=surface(1280,300,c=>{'); j = b.index('},2);', i) + 5
b = b[:i] + """function paintTrackBg(){ const c=opt.trackBg.getContext('2d'); opt.trackBg.width=Math.round(1280*RK); opt.trackBg.height=Math.round(300*RK);
  c.setTransform(RK,0,0,RK,0,0);
  for(const L of [GLOWL,ROADL]){ const k=L.width/1400; c.drawImage(L,(-64-RX)*k,(-40-RY)*k,1280*k,300*k,0,0,1280,300); } }""" + b[j:]
R("__PHOTO__\nPHOTO.forEach(p=>{ p.img=new Image(); p.img.src=p.src; p.cache={}; });\n", "")
i = b.index('const track=$(\'#track\'), tctx=track.getContext(\'2d\');'); j = b.index('\n}\n', i) + 3
b = b[:i] + """const track=opt.track, tctx=track.getContext('2d'); track.width=Math.round(1280*RK); track.height=Math.round(300*RK);
function drawTrack(){
  tctx.setTransform(RK,0,0,RK,0,0); tctx.clearRect(0,0,1280,300);
  tctx.save(); tctx.translate(64,40);
  const j=tileOf(car.x), cp=PHOTO[CARFOR[j]], cc=D2[j].c, W=WIN[j]; car.dim=1;   // the tile's own tint: a blended one would build a new sprite every frame
  const hidden=car.x>W.x+40 && car.x<W.x+W.w-40 && car.y<TH-4;    // wholly behind a window: nothing to draw here
  if(pause<=0 && !hidden) drawCar(tctx,cp,cc,'under');
  drawSmoke(tctx,-64,1216);
  if(pause<=0 && !hidden) drawCar(tctx,cp,cc,'body');
  tctx.restore();
}
""" + b[j:]
# the city: colour per app (the mockup's tuned shades for the five usual apps), drawn at the screen's resolution
R("const CITYCOL=['#D3123F','#00A8E1','#FF4A2A','#5B4BFF','#F47521'];   // YouTube's red runs warmer than Netflix's",
  "const CITYTUNED={netflix:'#D3123F',prime:'#00A8E1',youtube:'#FF4A2A',hotstar:'#5B4BFF',crunchyroll:'#F47521'};   // YouTube's red runs warmer than Netflix's\nconst CITYCOL=D2.map(a=>CITYTUNED[a.key]||a.c);")
R("const S=CITY[L]; cv.width=1340*S.k; cv.height=720*S.k; const c=cv.getContext('2d'); c.scale(S.k,S.k);",
  "const S=CITY[L], k=Math.min(S.k,RK); cv.width=Math.round(1340*k); cv.height=Math.round(720*k); const c=cv.getContext('2d'); c.scale(k,k);")
R("const IMM=[$('#immA'),$('#immB')]; let immOn=-1;", "const IMM=opt.imm; let immOn=-1, bakeT=0;")
R("""  if(i<0){ IMM.forEach(el=>el.classList.remove('on')); $('#h2tint').style.opacity=0; return; }
  const col=CITYCOL[i], t=$('#h2tint');
  t.style.background=`radial-gradient(1200px 420px at ${10+i*20}% 104%,${hexA(col,.18)},transparent 75%)`; t.style.opacity=1;
  const cur=IMM.find(el=>el.classList.contains('on')), next=IMM.find(el=>el!==cur)||IMM[0];
  bakeCity(next,col,4242+i*97);
  slide(next,was<0?i:was); next.offsetWidth; slide(next,i);
  next.classList.add('on'); if(cur){ slide(cur,i); cur.classList.remove('on'); }""",
  """  clearTimeout(bakeT);
  if(i<0){ IMM.forEach(el=>el.classList.remove('on')); return; }
  // drawn once focus settles, so sliding quickly along the dock never waits on a city
  bakeT=setTimeout(()=>{ if(immOn!==i) return;
    const cur=IMM.find(el=>el.classList.contains('on')), next=IMM.find(el=>el!==cur)||IMM[0];
    bakeCity(next,CITYCOL[i],4242+i*97);
    slide(next,was<0?i:was); next.offsetWidth; slide(next,i);
    next.classList.add('on'); if(cur){ slide(cur,i); cur.classList.remove('on'); } },120);""")
R("const WIN=D2.map((_,i)=>({x:i*(TW+GAP), w:TW, h:TH}));",
  "const WIN=D2.map((a,i)=>({x:i*(TW+GAP), w:TW, h:TH, ctx:a.canvas.getContext('2d')}));")
R("const SCENERY=WIN.map((w,i)=>landscape(w,D2[i].c));", "const SCENERY=WIN.map((w,i)=>landscape(w,D2[i].c));\npaintTrackBg();\nWIN.forEach((W,i)=>{ D2[i].canvas.width=Math.round(W.w*RK); D2[i].canvas.height=Math.round(W.h*RK); });")
assert '$(' not in b.replace("$('#", "XX") or True

head = """/* Night drive for the Home dock: one winding skyway through the five windows, across the gaps and
   under the dock, with a cartoon sports car drifting along it, and the focused app's city filling the
   screen behind. Generated from design/nightdrive/ (see its README).
   Car sprites: Wikimedia Commons photos, backgrounds removed, illustrated by Codex and repainted.
   NightDrive.start({apps:[{c,key,canvas}x5], track, trackBg, imm:[setA,setB], k}) returns {frame(dt,focus), focus(i)}. */
(()=>{
if(!CanvasRenderingContext2D.prototype.roundRect||!window.Path2D||!window.DOMMatrix) return;   // older WebViews keep a plain dock
""" + photo + """
PHOTO.forEach(p=>{ p.img=new Image(); p.img.src=p.src; p.cache={}; });
function start(opt){
const D2=opt.apps, RK=opt.k;
"""
tail = """
let lastOn=-2;
function frame(dt,on){
  stepDragon(dt);
  // a window is redrawn only while the car or its smoke is in it, once more after, and when focus changes
  WIN.forEach((W,i)=>{ const isOn=on===i, busy=(pause<=0&&car.x>W.x-60&&car.x<W.x+W.w+60)||smoke.some(q=>q.x>W.x-40&&q.x<W.x+W.w+40);
    if(busy||W.busy||W.on!==isOn||!W.drawn){ drawWin(W.ctx,W,D2[i].c,isOn); W.drawn=true; }
    W.busy=busy; W.on=isOn; });
  drawTrack();
}
return {frame, focus:immerse};
}
window.NightDrive={start};
})();
"""
open(out, 'w').write(head + b + tail)
print('ok', len(head + b + tail))
