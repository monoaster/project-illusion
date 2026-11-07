// Run the road + driving code from block.js outside the browser and print the car's state over time.
import fs from 'fs';
const src=fs.readFileSync(process.argv[2]||'block.js','utf8').split('\n');
const pick=(a,b)=>src.slice(a-1,b).join('\n');
const find=re=>src.findIndex(l=>re.test(l))+1;
const pathEnd=find(/^const turn=/), drvA=find(/^const CARLEN=/), drvB=find(/^const PUFF=/)-1;
const code=`const D2=[{},{},{},{},{}];\n`+pick(7,pathEnd)+'\n'+src.filter(l=>/^const smooth=|^const narrow=/.test(l)).join('\n')+'\n'+pick(drvA,drvB)+`
const out=[]; resetCar(); pause=0; let t=0;
for(;t<45&&pause<=0;t+=1/60){ stepDragon(1/60); out.push({t:+t.toFixed(3),s:+s.toFixed(1),x:+car.x.toFixed(1),y:+car.y.toFixed(1),k:+turn(s).toFixed(4),rot:+rot.toFixed(3),face:+face.toFixed(3),drift:+drift.toFixed(3)}); }
return {PL,out,PX,PY};`;
const r=new Function(code)();
fs.writeFileSync(process.argv[3]||'sim.json',JSON.stringify(r));
const yt=r.out.filter(o=>o.x>440&&o.x<700);
console.log('PL',r.PL,'total time',r.out.at(-1).t,'YouTube from',yt[0].t,'to',yt.at(-1).t);
for(const o of r.out.filter((o,i)=>o.x>440&&o.x<700&&i%6===0)) console.log(o.t,o.x,o.y,'k',o.k,'rot',o.rot,'face',o.face,'drift',o.drift);
