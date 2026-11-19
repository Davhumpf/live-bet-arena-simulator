                                                              
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
const env={...process.env};
try { for (const line of readFileSync(new URL('../.env',import.meta.url),'utf8').split(/\r?\n/)) { const match=line.match(/^([A-Z_]+)=(.*)$/); if(match&&!env[match[1]]) env[match[1]]=match[2].replace(/^['"]|['"]$/g,''); } } catch { }
const base=env.ARENA_URL||`http://localhost:${env.HTTP_PORT||8080}`;
if (!env.DEMO_PASSWORD) throw new Error('Configura DEMO_PASSWORD en .env o en el entorno');
const auth='Basic '+Buffer.from(`alex@arena.test:${env.DEMO_PASSWORD}`).toString('base64');
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function request(path,options={}) {
  const response=await fetch(base+path,{...options,headers:{Authorization:auth,...options.headers},signal:AbortSignal.timeout(10000)});
  let body; try {body=await response.json();} catch {body={};}
  return {status:response.status,body};
}
async function waitFor(description,action,timeout=90000) {
  const until=Date.now()+timeout;
  while(Date.now()<until) { const value=await action(); if(value) {console.log('OK',description);return value;} await pause(1000); }
  throw new Error(`Tiempo agotado: ${description}`);
}
const report={startedAt:new Date().toISOString(),checks:[]};
let ws;
try {
  await waitFor('dashboard saludable',async()=>{
    try { return (await fetch(base+'/actuator/health',{signal:AbortSignal.timeout(5000)})).ok; } catch { return false; }
  },180000);
  for(const asset of ['/','/dashboard.html','/css/arena.css','/js/arena.js']) {
    assert.equal((await fetch(base+asset)).status,200,`Recurso ausente: ${asset}`);
  }
  report.checks.push('HTML, CSS and JavaScript served');
  const unauth=await fetch(base+'/api/snapshot'); assert.equal(unauth.status,401); report.checks.push('private API requires authentication');
  await waitFor('cuenta demo disponible',async()=>{const response=await request('/api/snapshot');return response.status===200;},30000);
  const forbidden=await request('/api/admin'); assert.equal(forbidden.status,403); report.checks.push('USER cannot access admin');
  if(env.ADMIN_PASSWORD) {
    const admin=await request('/api/admin',{headers:{Authorization:'Basic '+Buffer.from(`admin@arena.test:${env.ADMIN_PASSWORD}`).toString('base64')}});
    assert.equal(admin.status,200); assert.equal(admin.body.users.length,3);
    assert.ok(admin.body.users.every(user=>!Object.hasOwn(user,'password')));
    report.checks.push('ADMIN can inspect users without exposing password hashes');
  }
  await waitFor('apuestas anteriores resueltas',async()=>{
    const response=await request('/api/snapshot');
    return response.status===200&&response.body.bets.every(bet=>!['PENDING','ACCEPTED'].includes(bet.status));
  },120000);
  const state=await waitFor('partido automático con más de 40 segundos disponibles',async()=>{
    const response=await request('/api/snapshot'); if(response.status!==200)return null;
    const match=response.body.matches.find(m=>m.status==='OPEN_FOR_BETS'&&new Date(m.closesAt)-new Date(response.body.serverTime)>40000);
    return match?{snapshot:response.body,match}:null;
  });
  const match=state.match;
  let signals=0;
  ws=new WebSocket(base.replace(/^http/,'ws')+'/ws');
  await new Promise((resolve,reject)=>{ws.onopen=resolve;ws.onerror=reject;setTimeout(()=>reject(new Error('WebSocket no conectó')),10000).unref();});
  ws.onmessage=()=>signals++;
  const requestId=randomUUID(); const payload={matchId:match.id,selection:'HOME',stake:100,requestId};
  const placed=await waitFor('apuesta registrada',async()=>{
    const response=await request('/bet',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});
    if(response.status===409&&response.body.error?.includes('sincronizándose')) return null;
    assert.equal(response.status,202,JSON.stringify(response.body)); return response.body;
  },10000);
  const duplicate=await request('/bet',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({user_id:'u555',match_id:match.id,bet_on:'A',amount:100,request_id:requestId})});
  assert.equal(duplicate.status,202); assert.equal(duplicate.body.id,placed.id);
  const reserved=(await request('/api/snapshot')).body;
  assert.equal(Number(reserved.user.balance),Number(state.snapshot.user.balance)-100);
  report.checks.push('bet reserves 100 points once despite repeated request');
  const invalid=await request('/bet',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({...payload,requestId:randomUUID(),stake:-1})}); assert.equal(invalid.status,400);
  const poor=await request('/bet',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({...payload,requestId:randomUUID(),stake:1000000})}); assert.equal(poor.status,400);
  await waitFor('motor acepta BET_PLACED',async()=>{const s=(await request('/api/snapshot')).body;return s.bets.find(b=>b.id===placed.id)?.status==='ACCEPTED';},15000);
  console.log('Esperando cierre real de 60 segundos vía RabbitMQ…');
  const final=await waitFor('resultado y liquidación',async()=>{
    const s=(await request('/api/snapshot')).body; const bet=s.bets.find(b=>b.id===placed.id); const result=s.matches.find(m=>m.id===match.id);
    return result?.status==='FINISHED'&&['WON','LOST'].includes(bet?.status)?{snapshot:s,bet,result}:null;
  },90000);
  const won=final.result.winner==='HOME';
  assert.equal(final.bet.status,won?'WON':'LOST');
  assert.equal(Number(final.snapshot.user.balance),Number(state.snapshot.user.balance)-100+(won?200:0));
  assert.equal(final.snapshot.ledger.filter(l=>l.betId===placed.id&&l.reason==='STAKE_RESERVED').length,1);
  assert.equal(final.snapshot.ledger.filter(l=>l.betId===placed.id&&l.reason==='PAYOUT').length,won?1:0);
  const late=await request('/bet',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({...payload,requestId:randomUUID()})}); assert.equal(late.status,409);
  assert.ok(signals>0,'No se recibió actualización WebSocket');
  report.checks.push('winner generated after RabbitMQ timer','settlement and balance correct','late bet rejected','WebSocket update received');
  report.matchId=match.id; report.betId=placed.id; report.winner=final.result.winner; report.outcome=final.bet.status; report.websocketSignals=signals; report.status='PASS';
  console.log('PASS: flujo distribuido verificado, saldo correcto y WebSocket activo.');
} catch(error) {report.status='FAIL';report.error=error.message;console.error(error.message);process.exitCode=1;}
finally {ws?.close();report.finishedAt=new Date().toISOString();mkdirSync(new URL('../artifacts/',import.meta.url),{recursive:true});writeFileSync(new URL('../artifacts/e2e-report.json',import.meta.url),JSON.stringify(report,null,2));}
