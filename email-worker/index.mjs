import {healthServer,rabbit,consumeRabbit,fatal} from '../shared/runtime.mjs';
const context={name:'email_worker',ready:false};healthServer(context);const delivered=new Set();
async function main(){
  const mq=await rabbit();await consumeRabbit(mq,'email_notifications',async notification=>{
    if(delivered.has(notification.id))return;
    delivered.add(notification.id);
    const text=notification.outcome==='WON'?`¡Ganaste! Retorno de ${notification.payout} puntos virtuales (incluye apuesta).`:`Perdiste ${notification.stake} puntos virtuales.`;
    console.log(`ENVIANDO EMAIL SIMULADO a ${notification.user_id}: ${text} id=${notification.id}`);
  });context.ready=true;
}
main().catch(fatal);
