// Run: node docs/reviews/2026-10-25-ui-ux/capture-prototype.mjs
// Uses the installed Chromium; only renders the local throwaway HTML prototype.
import {spawn} from 'node:child_process';
import {writeFile,mkdtemp} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join,dirname} from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
const root=dirname(fileURLToPath(import.meta.url));
const profile=await mkdtemp(join(tmpdir(),'rep-ui-browser-'));
const browser=spawn(process.env.CHROMIUM||'/usr/bin/chromium',['--headless','--no-sandbox','--disable-dev-shm-usage','--disable-gpu','--no-first-run','--hide-scrollbars','--remote-debugging-port=0',`--user-data-dir=${profile}`,'about:blank'],{stdio:['ignore','ignore','pipe']});
let endpoint='';
const wsUrl=await new Promise((resolve,reject)=>{browser.stderr.on('data',b=>{endpoint+=b;const m=endpoint.match(/DevTools listening on (ws:\/\/[^\s]+)/);if(m)resolve(m[1]);});browser.on('exit',c=>reject(new Error(`Chromium exited: ${c}`)));setTimeout(()=>reject(new Error('Chromium startup timed out')),20000)});
const ws=new WebSocket(wsUrl);await new Promise(r=>ws.addEventListener('open',r,{once:true}));
let id=0;const requests=new Map();ws.addEventListener('message',e=>{const m=JSON.parse(e.data);if(m.id){const p=requests.get(m.id);requests.delete(m.id);m.error?p.reject(new Error(JSON.stringify(m.error))):p.resolve(m.result)}});
function send(method,params={},sessionId){const n=++id;return new Promise((resolve,reject)=>{requests.set(n,{resolve,reject});ws.send(JSON.stringify({id:n,method,params,...(sessionId?{sessionId}:{})}))})}
const {targetId}=await send('Target.createTarget',{url:'about:blank'});const {sessionId}=await send('Target.attachToTarget',{targetId,flatten:true});
const call=(m,p={})=>send(m,p,sessionId);
async function evaluate(expression){const r=await call('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(r.exceptionDetails)throw new Error(JSON.stringify(r.exceptionDetails));return r.result.value}
await call('Page.enable');
async function open(query,width=390,height=844){await call('Emulation.setDeviceMetricsOverride',{width,height,deviceScaleFactor:1,mobile:false});await call('Page.navigate',{url:pathToFileURL(join(root,'prototype.html')).href+'?'+query});for(let n=0;n<40;n++){try{if(await evaluate('document.readyState==="complete" && document.querySelectorAll(".phone").length>0'))break}catch{}await new Promise(r=>setTimeout(r,100))}await evaluate('document.fonts.ready.then(()=>true)');await new Promise(r=>setTimeout(r,80))}
async function capture(file,query,width,height){await open(query,width,height);const h=await evaluate('Math.max(document.documentElement.scrollHeight,innerHeight)');const r=await call('Page.captureScreenshot',{format:'png',captureBeyondViewport:true,clip:{x:0,y:0,width,height:h,scale:1}});await writeFile(join(root,'screenshots',file),Buffer.from(r.data,'base64'));console.log(`${file}: ${width}×${h}`)}
const results=[];
try{
await capture('proposed-layout-options.png','view=compare&capture=1',1320,1390);
await capture('proposed-flow.png','view=flow&capture=1',1320,2140);
for(const s of ['home','workout','entry','catalog','progress','finish','routine','settings'])await capture(`proposed-${s}.png`,`view=single&screen=${s}&variant=A`,390,844);
await capture('proposed-workout-dark.png','view=single&screen=workout&variant=A&theme=dark',390,844);
await capture('proposed-workout-narrow.png','view=single&screen=workout&variant=A',320,640);
for(const width of [320,390])for(const v of ['A','B','C']){await open(`view=single&screen=workout&variant=${v}`,width,844);results.push({width,variant:v,horizontalOverflow:await evaluate('document.documentElement.scrollWidth>innerWidth'),logVisible:await evaluate('(()=>{const r=document.querySelector(".bottom .primary").getBoundingClientRect();return r.top>=0&&r.bottom<=innerHeight})()')})}
await open('view=single&screen=entry');await evaluate('document.getElementById("weight").value="-5";saveEntry()');results.push({invalidLoadBlocked:await evaluate('document.getElementById("validation").textContent.includes("nonnegative")'),draftPreserved:await evaluate('document.getElementById("weight").value==="-5"')});
await open('view=single&screen=workout');await evaluate('logSet()');results.push({oneSetPerLog:await evaluate('state.logged===3'),nextMovementAfterPlanMet:await evaluate('document.querySelector(".bottom .primary").textContent.includes("Next movement")')});
await open('view=single&screen=workout');await evaluate("selectMovement('Seated Cable Rows')");results.push({movementHasOwnValues:await evaluate("state.weight===40 && state.logged===0 && selected().sets.length===0"),restPreservedOnSelection:await evaluate('state.rest===true')});
await open('variant=A');await evaluate('cycle(1)');results.push({variantSwitchUpdatesURL:await evaluate('new URLSearchParams(location.search).get("variant")==="B"')});
await writeFile(join(root,'prototype-checks.json'),JSON.stringify(results,null,2)+'\n');
if(results.some(r=>r.horizontalOverflow===true||Object.entries(r).some(([k,v])=>k!=='horizontalOverflow'&&typeof v==='boolean'&&!v)))throw new Error(JSON.stringify(results));
console.log('Prototype interaction and viewport checks passed.');
}finally{await send('Browser.close').catch(()=>{});ws.close();browser.kill()}
