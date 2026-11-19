import {readFileSync,writeFileSync,mkdirSync} from 'node:fs';
import assert from 'node:assert/strict';
const env={...process.env};
for(const line of readFileSync(new URL('../.env',import.meta.url),'utf8').split(/\r?\n/)){const match=line.match(/^([A-Z_]+)=(.*)$/);if(match&&!env[match[1]])env[match[1]]=match[2].replace(/^['"]|['"]$/g,'');}
const base=env.ARENA_URL||`http://localhost:${env.HTTP_PORT||8080}`;
const headers={Authorization:'Basic '+Buffer.from(`alex@arena.test:${env.DEMO_PASSWORD}`).toString('base64'),'Content-Type':'application/json'};
const before=await (await fetch(base+'/api/snapshot',{headers})).json();
const bet=before.bets[0];assert.ok(bet,'Ejecutar primero la E2E');
const payload={user_id:'u555',match_id:bet.matchId,bet_on:bet.selection==='HOME'?'A':'B',amount:bet.stake,request_id:bet.requestId};
const response=await fetch(base+'/bet',{method:'POST',headers,body:JSON.stringify(payload)});
assert.equal(response.status,202);assert.equal((await response.json()).id,bet.id);
const after=await (await fetch(base+'/api/snapshot',{headers})).json();assert.equal(after.user.balance,before.user.balance);
const impersonation=await fetch(base+'/bet',{method:'POST',headers,body:JSON.stringify({...payload,user_id:'u556'})});assert.equal(impersonation.status,403);
const report={status:'PASS',check:'snake_case API contract and A/B selection accepted; cross-contract retry does not debit twice; impersonation rejected',betId:bet.id,checkedAt:new Date().toISOString()};
mkdirSync(new URL('../artifacts/',import.meta.url),{recursive:true});writeFileSync(new URL('../artifacts/contract-report.json',import.meta.url),JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));
