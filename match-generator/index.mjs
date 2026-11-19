import {randomUUID} from 'node:crypto';
import {healthServer,logClient,rabbit,projection,fatal} from '../shared/runtime.mjs';
const context={name:'match_generator',ready:false};healthServer(context);
const teams=['Cóndores FC','Atlético Aurora','Lobos del Norte','Deportivo Solaris','Titanes del Sur','Unión Pacífico','Águilas Doradas','Racing Eclipse','Inter Andino','Real Cumbre','Estrella Azul','Sporting Volcán'];
async function main() {
  const mq=await rabbit(),log=await logClient(context.name);
  const view=await projection(context.name,log,async(event,effects,state)=>{
    if(event.event_type==='MATCH_CREATED')await mq.timer(state.matches.get(event.match_id));
  });
  await view.ready;context.ready=true;
  console.log('READY: estado reconstruido desde Kafka; temporizadores recuperados');
  const generate=async()=>{
    const shuffled=[...teams].sort(()=>Math.random()-0.5),now=new Date();
    const event={event_id:randomUUID(),event_type:'MATCH_CREATED',match_id:randomUUID(),teams:shuffled.slice(0,2),occurred_at:now.toISOString(),closes_at:new Date(now.getTime()+60000).toISOString()};
    await log.publish(event);console.log('MATCH_CREATED',event.match_id);
  };
  const tick=async()=>{try{await generate();setTimeout(tick,10000);}catch(error){fatal(error);}};
  await tick();
}
main().catch(fatal);
