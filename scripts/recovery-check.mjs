import {readFileSync,writeFileSync,mkdirSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {spawn,spawnSync} from 'node:child_process';
import assert from 'node:assert/strict';
const root=fileURLToPath(new URL('../',import.meta.url)),env={...process.env};
for(const line of readFileSync(new URL('../.env',import.meta.url),'utf8').split(/\r?\n/)){const match=line.match(/^([A-Z_]+)=(.*)$/);if(match&&!env[match[1]])env[match[1]]=match[2].replace(/^['"]|['"]$/g,'');}
const base=env.ARENA_URL||`http://localhost:${env.HTTP_PORT||8080}`,headers={Authorization:'Basic '+Buffer.from(`admin@arena.test:${env.ADMIN_PASSWORD}`).toString('base64')};
async function state(){const response=await fetch(base+'/api/admin/engine',{headers,signal:AbortSignal.timeout(10000)});assert.equal(response.status,200);return response.json();}
async function compose(args){
  let command='docker',prefix=['compose'];
  if(process.platform==='win32'){
    const native=spawnSync('docker',['version'],{stdio:'ignore'});
    if(native.error||native.status!==0){
      const path=spawnSync('wsl.exe',['-d','Ubuntu','--exec','wslpath','-a',root],{encoding:'utf8'});assert.equal(path.status,0);
      command='wsl.exe';prefix=['-d','Ubuntu','-u','root','--cd',path.stdout.trim(),'--exec','docker','compose'];
    }
  }
  await new Promise((resolve,reject)=>{const child=spawn(command,[...prefix,...args],{cwd:root,stdio:'inherit'});child.on('error',reject);child.on('exit',code=>code===0?resolve():reject(new Error(`Compose terminó con código ${code}`)));});
}
const before=await state();assert.ok(before.bets.length>0,'Ejecutar primero scripts/e2e.mjs');
assert.ok(before.bets.every(b=>!['ACCEPTED','PENDING'].includes(b.status)),'Esperar que terminen las apuestas activas');
await compose(['up','-d','--no-deps','--force-recreate','--wait','--wait-timeout','180','results-engine']);
let after;const deadline=Date.now()+30000;
while(Date.now()<deadline){try{after=await state();if(BigInt(after.offset)>=BigInt(before.offset))break;}catch{}await new Promise(resolve=>setTimeout(resolve,500));}
assert.ok(after&&BigInt(after.offset)>=BigInt(before.offset),'Reconstrucción incompleta');
assert.equal(after.financialDigest,before.financialDigest);
const report={status:'PASS',check:'empty process reconstructed bets, balances and ledger from Kafka only',beforeOffset:before.offset,afterOffset:after.offset,financialDigest:after.financialDigest,bets:after.bets.length,checkedAt:new Date().toISOString()};
mkdirSync(new URL('../artifacts/',import.meta.url),{recursive:true});writeFileSync(new URL('../artifacts/recovery-report.json',import.meta.url),JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));
