import {winnerFor} from '../shared/domain.mjs';
import {healthServer,logClient,rabbit,projection,consumeRabbit,fatal} from '../shared/runtime.mjs';
const context={name:'match_simulator',ready:false};healthServer(context);
async function main(){
  const log=await logClient(context.name),mq=await rabbit(),view=await projection(context.name,log);
  await view.ready;
  await consumeRabbit(mq,'match_simulation_queue',async command=>{
    const match=view.state.matches.get(command.match_id);
    if(!match)throw new Error('Temporizador recibido antes del evento de creación');
    if(match.status==='CLOSED')return;
    if(Date.now()<Date.parse(match.closesAt))return mq.timer(match);
    await log.publish({event_id:`finish:${match.id}`,event_type:'MATCH_FINISHED',match_id:match.id,winner:winnerFor(match.id),occurred_at:match.closesAt});
    console.log('MATCH_FINISHED',match.id);
  });
  context.ready=true;console.log('READY: consumidor de temporizadores RabbitMQ');
}
main().catch(fatal);
