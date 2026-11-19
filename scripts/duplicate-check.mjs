import assert from 'node:assert/strict';
import {logClient,projection} from '../shared/runtime.mjs';
const log=await logClient('duplicate-check'),view=await projection('duplicate-check',log);await view.ready;
const headers={Authorization:'Basic '+Buffer.from(`admin@arena.test:${process.env.ADMIN_PASSWORD}`).toString('base64')};
const fetchState=async()=>{const response=await fetch('http://results-engine:8080/api/admin/state',{headers});assert.equal(response.status,200);return response.json();};
try {
  const match=[...view.state.matches.values()].find(match=>match.status==='CLOSED'&&match.bets.length>0);
  assert.ok(match,'Primero ejecutar la prueba E2E y esperar una liquidación');
  const before=await fetchState();
  const result=await log.publish({event_id:`finish:${match.id}`,event_type:'MATCH_FINISHED',match_id:match.id,winner:match.winner,occurred_at:match.closesAt});
  const offset=BigInt(result[0].baseOffset);let after;
  const deadline=Date.now()+15000;
  while(Date.now()<deadline){after=await fetchState();if(BigInt(after.offset)>=offset)break;await new Promise(resolve=>setTimeout(resolve,200));}
  assert.ok(BigInt(after.offset)>=offset,'El motor no consumió el duplicado');
  assert.equal(after.financialDigest,before.financialDigest);
  console.log(JSON.stringify({status:'PASS',check:'duplicate MATCH_FINISHED consumed without financial changes',matchId:match.id,offset:offset.toString(),financialDigest:after.financialDigest},null,2));
} finally {await view.consumer.disconnect();await log.producer.disconnect();await log.admin.disconnect();}
