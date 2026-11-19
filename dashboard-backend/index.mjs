import {readFile} from 'node:fs/promises';
import {WebSocketServer} from 'ws';
import {healthServer,logClient,rabbit,projection,consumeRabbit,authenticate,readBody,json,fatal} from '../shared/runtime.mjs';
const context={name:'dashboard_backend',ready:false};let view;
const assets=new Map([
  ['/', ['index.html','text/html']],['/index.html',['index.html','text/html']],['/dashboard.html',['dashboard.html','text/html']],
  ['/css/arena.css',['css/arena.css','text/css']],['/js/arena.js',['js/arena.js','text/javascript']]
]);
const server=healthServer(context,async(req,res)=>{
  if(req.method==='GET'&&assets.has(req.url)) {
    const [file,type]=assets.get(req.url);res.writeHead(200,{'Content-Type':`${type}; charset=utf-8`});return res.end(await readFile(new URL(`../frontend/${file}`,import.meta.url)));
  }
  if(req.url==='/api/snapshot')return json(res,200,view.state.snapshot(authenticate(req).id));
  if(req.url==='/api/admin'){authenticate(req,true);return json(res,200,view.state.admin());}
  if(req.url==='/api/admin/engine'){
    authenticate(req,true);
    const response=await fetch(`${process.env.ENGINE_URL||'http://results-engine:8080'}/api/admin/state`,{headers:{Authorization:req.headers.authorization},signal:AbortSignal.timeout(10000)});
    return json(res,response.status,await response.json());
  }
  if(req.method==='POST'&&req.url==='/bet'){
    authenticate(req);const body=await readBody(req);
    let response;
    try {response=await fetch(`${process.env.BETTING_URL||'http://betting-api:8080'}/bet`,{method:'POST',headers:{Authorization:req.headers.authorization,'Content-Type':'application/json'},body:JSON.stringify(body),signal:AbortSignal.timeout(20000)});}
    catch{throw Object.assign(new Error('Servicio de apuestas no disponible'),{status:503});}
    const result=await response.json();
    if(response.status===202)await view.waitForBet(result.id);
    return json(res,response.status,result);
  }
  json(res,404,{error:'Ruta desconocida'});
});
const wss=new WebSocketServer({server,path:'/ws',verifyClient:({origin,req})=>!origin||new URL(origin).host===req.headers.host});
async function main(){
  const log=await logClient(context.name),mq=await rabbit();view=await projection(context.name,log);await view.ready;
  await consumeRabbit(mq,'dashboard_updates',async update=>{
    const deadline=Date.now()+10000;
    while(update.id&&!view.state.seen.has(update.id)&&Date.now()<deadline)await new Promise(resolve=>setTimeout(resolve,25));
    if(update.id&&!view.state.seen.has(update.id))throw new Error('La proyección del dashboard no alcanzó el evento notificado');
    for(const client of wss.clients)if(client.readyState===1)client.send('{"type":"UPDATE"}');
  });
  context.ready=true;console.log('READY: dashboard reconstruido desde Kafka');
}
main().catch(fatal);
