import {randomUUID} from 'node:crypto';
import {cents} from '../shared/domain.mjs';
import {healthServer,logClient,projection,authenticate,readBody,json,fatal} from '../shared/runtime.mjs';
const context={name:'betting_api',ready:false};let view,log;const commands=new Map();
function compare(previous,matchId,userId,selection,amount) {
  if(previous.matchId!==matchId||previous.userId!==userId||previous.selection!==selection||cents(previous.stake)!==amount)
    throw Object.assign(new Error('La requestId ya pertenece a otra apuesta'),{status:409});
}
healthServer(context,async(req,res)=>{
  if(req.method!=='POST'||req.url!=='/bet')return json(res,404,{error:'Ruta desconocida'});
  const user=authenticate(req),body=await readBody(req);
  if(body.user_id&&body.user_id!==user.id)throw Object.assign(new Error('No puedes apostar por otro usuario'),{status:403});
  const matchId=body.match_id??body.matchId,match=view.state.matches.get(matchId);
  if(!match)throw new Error('Partido desconocido');
  const rawSelection=body.bet_on??body.selection;
  const selection=rawSelection==='A'||rawSelection===match.home?'HOME':rawSelection==='B'||rawSelection===match.away?'AWAY':rawSelection;
  if(!['HOME','AWAY'].includes(selection))throw new Error('Selecciona HOME/A o AWAY/B');
  const amount=cents(body.amount??body.stake),requestId=body.request_id??body.requestId??randomUUID();
  if(typeof requestId!=='string'||!/^[A-Za-z0-9_-]{1,128}$/.test(requestId))throw new Error('requestId inválida');
  let previous=view.state.requests.get(requestId);
  if(previous){compare(previous,matchId,user.id,selection,amount);return json(res,previous.status==='REJECTED'?409:202,previous);}
  let event=commands.get(requestId);
  if(event)compare({matchId:event.match_id,userId:event.user_id,selection:event.bet_on,stake:event.amount},matchId,user.id,selection,amount);
  else {
    if(match.status!=='OPEN_FOR_BETS'||Date.now()>=Date.parse(match.closesAt))throw Object.assign(new Error('El partido ya cerró'),{status:409});
    if(view.state.users.get(user.id).balanceCents<amount)throw new Error('Saldo insuficiente');
    event={event_id:`bet:${requestId}`,event_type:'BET_PLACED',match_id:matchId,user_id:user.id,bet_id:requestId,request_id:requestId,bet_on:selection,amount:amount/100,occurred_at:new Date().toISOString()};
    commands.set(requestId,event);
  }
  await log.publish(event);previous=await view.waitForBet(requestId);commands.delete(requestId);
  return json(res,previous.status==='REJECTED'?409:202,previous.status==='REJECTED'?{...previous,error:previous.reason}:previous);
});
async function main(){log=await logClient(context.name);view=await projection(context.name,log);await view.ready;context.ready=true;console.log('READY: API reconstruida desde Kafka');}
main().catch(fatal);
