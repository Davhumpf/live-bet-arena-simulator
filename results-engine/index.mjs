import {createHash} from 'node:crypto';
import {healthServer,logClient,rabbit,projection,authenticate,json,fatal} from '../shared/runtime.mjs';
const context={name:'results_engine',ready:false};let view;
healthServer(context,async(req,res)=>{
  if(req.url!=='/api/admin/state')return json(res,404,{error:'Ruta desconocida'});
  authenticate(req,true);
  const state=view.state;
  const financialDigest=createHash('sha256').update(JSON.stringify({users:[...state.users],bets:[...state.bets],ledger:state.ledger})).digest('hex');
  json(res,200,{offset:state.lastOffset,stateDigest:state.digest(),financialDigest,users:state.admin().users,bets:[...state.bets.values()],matches:state.matches.size,events:state.seen.size});
});
async function main(){
  const mq=await rabbit(),log=await logClient(context.name);
  view=await projection(context.name,log,async(event,effects)=>{
    for(const effect of effects)await mq.send('notifications',effect.key,effect.body);
  });
  await view.ready;context.ready=true;
  console.log('READY: REPLAY_FROM_BEGINNING completado',JSON.stringify({offset:view.state.lastOffset,matches:view.state.matches.size,bets:view.state.bets.size,digest:view.state.digest()}));
}
main().catch(fatal);
