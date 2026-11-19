import {Kafka,logLevel,Partitioners} from 'kafkajs';
import amqp from 'amqplib';
import {randomUUID,timingSafeEqual} from 'node:crypto';
import {createServer} from 'node:http';
import {Arena,USERS} from './domain.mjs';

export const TOPIC='match_events';
export function fatal(error) {console.error(error?.stack??error);process.exit(1);}
export function json(res,status,value) {res.writeHead(status,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store'});res.end(JSON.stringify(value));}
export function healthServer(context,handler=async(req,res)=>json(res,404,{error:'Ruta desconocida'})) {
  const server=createServer(async(req,res)=>{
    try {
      if(['/health','/actuator/health'].includes(req.url)) return json(res,context.ready?200:503,{status:context.ready?'UP':'STARTING',service:context.name});
      if(!context.ready)return json(res,503,{error:'Reconstruyendo desde Kafka'});
      await handler(req,res);
    } catch(error) {console.error(error.message);if(!res.headersSent)json(res,error.status??400,{error:error.message});else res.end();}
  });
  server.listen(Number(process.env.PORT||8080),'0.0.0.0');return server;
}
export function authenticate(req,admin=false) {
  let credentials='';
  if(req.headers.authorization?.startsWith('Basic ')) credentials=Buffer.from(req.headers.authorization.slice(6),'base64').toString('utf8');
  const separator=credentials.indexOf(':'); const email=credentials.slice(0,separator), password=credentials.slice(separator+1);
  const user=USERS.find(candidate=>candidate.email===email);
  const expected=user?.role==='ADMIN'?process.env.ADMIN_PASSWORD:process.env.DEMO_PASSWORD;
  const actualBytes=Buffer.from(password), expectedBytes=Buffer.from(expected??'');
  if(!user||!expected||actualBytes.length!==expectedBytes.length||!timingSafeEqual(actualBytes,expectedBytes)) throw Object.assign(new Error('Credenciales incorrectas'),{status:401});
  if(admin&&user.role!=='ADMIN')throw Object.assign(new Error('Acceso restringido'),{status:403});
  return user;
}
export async function readBody(req) {
  let body='';for await(const chunk of req) {body+=chunk;if(Buffer.byteLength(body)>16384)throw new Error('Petición demasiado grande');}
  try{return JSON.parse(body);}catch{throw new Error('JSON inválido');}
}
export async function rabbit() {
  const connection=await amqp.connect({hostname:process.env.RABBIT_HOST||'rabbitmq',username:process.env.RABBIT_USER||'arena',password:process.env.RABBIT_PASSWORD});
  connection.on('error',fatal); connection.on('close',()=>fatal(new Error('Conexión RabbitMQ cerrada')));
  const channel=await connection.createConfirmChannel();channel.on('error',fatal);channel.prefetch(10);
  await channel.assertExchange('match_timers','x-delayed-message',{durable:true,arguments:{'x-delayed-type':'direct'}});
  await channel.assertExchange('notifications','topic',{durable:true});
  for(const name of ['match_simulation_queue','email_notifications','dashboard_updates'])await channel.assertQueue(name,{durable:true});
  await channel.bindQueue('match_simulation_queue','match_timers','match.finish');
  await channel.bindQueue('email_notifications','notifications','notify.email.win');
  await channel.bindQueue('email_notifications','notifications','notify.email.loss');
  await channel.bindQueue('dashboard_updates','notifications','notify.dashboard.*');
  const send=(exchange,key,body,headers={})=>new Promise((resolve,reject)=>{
    channel.publish(exchange,key,Buffer.from(JSON.stringify(body)),{persistent:true,contentType:'application/json',messageId:body.id??body.match_id,headers,mandatory:false},error=>error?reject(error):resolve());
  });
  return {connection,channel,send,timer:(match)=>send('match_timers','match.finish',{match_id:match.id},{'x-delay':Math.max(1,Date.parse(match.closesAt)-Date.now())})};
}
export async function logClient(name) {
  const kafka=new Kafka({clientId:name,brokers:(process.env.KAFKA_SERVERS||'kafka:9092').split(','),logLevel:logLevel.WARN,retry:{retries:12},createPartitioner:Partitioners.DefaultPartitioner});
  const admin=kafka.admin();await admin.connect();
  await admin.createTopics({waitForLeaders:true,topics:[{topic:TOPIC,numPartitions:1,replicationFactor:1,configEntries:[{name:'retention.ms',value:'-1'},{name:'cleanup.policy',value:'delete'}]}]});
  let metadata;
  for(let attempt=0;attempt<30;attempt++){
    try {metadata=await admin.fetchTopicMetadata({topics:[TOPIC]});if(metadata.topics[0]?.partitions[0]?.leader>=0)break;}catch(error){if(attempt===29)throw error;}
    await new Promise(resolve=>setTimeout(resolve,500));
  }
  if(!metadata?.topics[0]?.partitions[0]||metadata.topics[0].partitions[0].leader<0)throw new Error('Kafka todavía no eligió el líder del topic');
  if(metadata.topics[0].partitions.length!==1)throw new Error('match_events debe tener una partición para ordenar también los saldos entre partidos');
  const producer=kafka.producer({idempotent:true,maxInFlightRequests:1,retry:{retries:Number.MAX_SAFE_INTEGER},createPartitioner:Partitioners.DefaultPartitioner});await producer.connect();
  return {kafka,admin,producer,publish:event=>producer.send({topic:TOPIC,acks:-1,messages:[{key:event.match_id,value:JSON.stringify(event)}]})};
}
export async function projection(name,log,onEvent=async()=>{}) {
  const state=new Arena(); const target=BigInt((await log.admin.fetchTopicOffsets(TOPIC))[0].high);
  const consumer=log.kafka.consumer({groupId:`${name}-replay-${randomUUID()}`,maxWaitTimeInMs:100,sessionTimeout:30000});
  await consumer.connect();await consumer.subscribe({topic:TOPIC,fromBeginning:true});
  consumer.on(consumer.events.CRASH,({payload})=>fatal(payload.error));
  let resolveReady;const ready=new Promise(resolve=>{resolveReady=resolve;});
  let settled=target===0n;if(settled)resolveReady();
  const waiting=new Set();
  await consumer.run({autoCommit:false,eachMessage:async({message,heartbeat})=>{
    const event=JSON.parse(message.value.toString());
    if(message.key?.toString()!==(event.match_id??event.matchId))throw new Error('Clave Kafka distinta de match_id');
    const effects=state.apply(event);
                                                                                                     
    await onEvent(event,effects,state);
    state.lastOffset=message.offset;
    if(!settled&&BigInt(message.offset)+1n>=target){settled=true;resolveReady();}
    for(const wake of waiting)wake();
    await heartbeat();
  }});
  const waitForBet=async id=>{
    if(state.bets.has(id))return state.bets.get(id);
    return new Promise((resolve,reject)=>{
      const timer=setTimeout(()=>{waiting.delete(wake);reject(Object.assign(new Error('Evento persistido; validación pendiente, reintenta con la misma requestId'),{status:503}));},10000);
      const wake=()=>{if(state.bets.has(id)){clearTimeout(timer);waiting.delete(wake);resolve(state.bets.get(id));}};waiting.add(wake);wake();
    });
  };
  return {state,consumer,ready,waitForBet};
}
export async function consumeRabbit(mq,queue,handler) {
  await mq.channel.consume(queue,message=>{
    if(!message)return;
    Promise.resolve().then(()=>handler(JSON.parse(message.content.toString()))).then(()=>mq.channel.ack(message)).catch(fatal);
  },{noAck:false});
}
