import {createHash} from 'node:crypto';

export const USERS = [
  {id:'u555',name:'Alex Rivera',email:'alex@arena.test',role:'USER'},
  {id:'u556',name:'Sam Torres',email:'sam@arena.test',role:'USER'},
  {id:'admin',name:'Administrador',email:'admin@arena.test',role:'ADMIN'}
];
export function cents(value) {
  const text=String(value);
  if(!/^\d+(\.\d{1,2})?$/.test(text)) throw new Error('Cantidad positiva con máximo dos decimales');
  const [integer,decimal='']=text.split('.');
  const result=Number(integer)*100+Number(decimal.padEnd(2,'0'));
  if(!Number.isSafeInteger(result)||result<=0||result>100000000) throw new Error('Cantidad inválida');
  return result;
}
export const points = amount => amount/100;
export const winnerFor = id => createHash('sha256').update(id).digest()[0]%2===0?'HOME':'AWAY';
export function normalize(input) {
                                                                                       
  return {
    ...input,event_id:input.event_id??input.eventId,event_type:input.event_type??input.type,
    match_id:input.match_id??input.matchId,occurred_at:input.occurred_at??input.occurredAt,
    closes_at:input.closes_at??input.closesAt,bet_id:input.bet_id??input.betId,
    user_id:input.user_id??input.userId,bet_on:input.bet_on??input.selection,
    amount:input.amount??input.stake
  };
}
export class Arena {
  constructor() {
    this.matches=new Map(); this.bets=new Map(); this.requests=new Map(); this.seen=new Set();
    this.users=new Map(USERS.map(user=>[user.id,{...user,balanceCents:1000000}]));
    this.ledger=[]; this.activity=[]; this.lastOffset='-1';
  }
  apply(raw) {
    const e=normalize(raw);
    if(!e.event_id||!e.match_id||!Number.isFinite(Date.parse(e.occurred_at))) throw new Error('Evento incompleto');
    if(this.seen.has(e.event_id)) return [];
    let notifications=[];
    if(e.event_type==='MATCH_CREATED') {
      if(this.matches.has(e.match_id)) throw new Error('Partido creado dos veces con eventos distintos');
      if(!Array.isArray(e.teams)||e.teams.length!==2||e.teams[0]===e.teams[1]||Date.parse(e.closes_at)<=Date.parse(e.occurred_at)) throw new Error('Partido inválido');
      this.matches.set(e.match_id,{id:e.match_id,home:e.teams[0],away:e.teams[1],createdAt:e.occurred_at,closesAt:e.closes_at,status:'OPEN_FOR_BETS',winner:null,bets:[]});
    } else if(e.event_type==='BET_PLACED') {
      const match=this.matches.get(e.match_id), user=this.users.get(e.user_id);
      let amount,reason;
      try {amount=cents(e.amount);} catch(error) {reason=error.message;}
      if(!match) reason='Partido desconocido';
      else if(match.status!=='OPEN_FOR_BETS'||Date.parse(e.occurred_at)>=Date.parse(match.closesAt)) reason='El partido ya cerró';
      if(!user) reason='Usuario desconocido';
      if(!['HOME','AWAY'].includes(e.bet_on)) reason='Selección inválida';
      if(user&&amount>user.balanceCents) reason='Saldo insuficiente';
      if(!e.bet_id||!e.request_id) throw new Error('Apuesta sin identificadores');
      if(this.bets.has(e.bet_id)||this.requests.has(e.request_id)) throw new Error('Identificador de apuesta reutilizado');
      const bet={id:e.bet_id,matchId:e.match_id,userId:e.user_id,selection:e.bet_on,stake:amount?points(amount):0,
        stakeCents:amount??0,placedAt:e.occurred_at,requestId:e.request_id,status:reason?'REJECTED':'ACCEPTED',reason:reason??null};
      this.bets.set(bet.id,bet); this.requests.set(bet.requestId,bet);
      if(!reason) {
        user.balanceCents-=amount; match.bets.push(bet.id);
        this.move(bet,-amount,'STAKE_RESERVED',e.occurred_at);
      }
    } else if(e.event_type==='MATCH_FINISHED') {
      const match=this.matches.get(e.match_id);
      if(!match) throw new Error('Resultado sin MATCH_CREATED');
      if(!['HOME','AWAY'].includes(e.winner)||Date.parse(e.occurred_at)<Date.parse(match.closesAt)) throw new Error('Resultado inválido o prematuro');
      if(match.status==='CLOSED') {
        if(match.winner!==e.winner) throw new Error('Resultado definitivo incompatible');
      } else {
        match.status='CLOSED'; match.winner=e.winner;
        for(const id of match.bets) {
          const bet=this.bets.get(id); const won=bet.selection===e.winner;
          bet.status=won?'WON':'LOST'; bet.payout=won?points(bet.stakeCents*2):0;
          if(won) {this.users.get(bet.userId).balanceCents+=bet.stakeCents*2;this.move(bet,bet.stakeCents*2,'PAYOUT',e.occurred_at);}
          notifications.push({key:won?'notify.email.win':'notify.email.loss',body:{id:`${bet.id}:${bet.status}`,user_id:bet.userId,outcome:bet.status,stake:bet.stake,payout:bet.payout}});
        }
      }
    } else throw new Error(`Tipo de evento desconocido: ${e.event_type}`);
    this.seen.add(e.event_id);
    this.activity.push({id:e.event_id,action:e.event_type,detail:e.match_id,createdAt:e.occurred_at});
    const match=this.matches.get(e.match_id);
    notifications.push({key:'notify.dashboard.update',body:{id:e.event_id,match_id:e.match_id,status:match?.status??'UNKNOWN',winner:match?.winner??null}});
    return notifications;
  }
  move(bet,amount,reason,at) {
    this.ledger.push({id:`${bet.id}:${reason}`,userId:bet.userId,betId:bet.id,amount:points(amount),reason,createdAt:at});
  }
  safeUser(user) {const {balanceCents,...safe}=user;return {...safe,balance:points(balanceCents)};}
  snapshot(userId) {
    const user=this.users.get(userId); if(!user)throw new Error('Usuario desconocido');
    return {user:this.safeUser(user),matches:[...this.matches.values()].reverse().slice(0,60).map(match=>({...match,status:match.status==='CLOSED'?'FINISHED':match.status})),
      counts:Object.fromEntries([...this.matches.values()].map(match=>[match.id,match.bets.length])),
      bets:[...this.bets.values()].filter(b=>b.userId===userId).reverse().slice(0,100),
      ledger:this.ledger.filter(l=>l.userId===userId).reverse().slice(0,100),serverTime:new Date().toISOString()};
  }
  admin() {return {users:[...this.users.values()].map(user=>this.safeUser(user)),activity:this.activity.slice(-25).reverse()};}
  digest() {
    return createHash('sha256').update(JSON.stringify({matches:[...this.matches],bets:[...this.bets],users:[...this.users],ledger:this.ledger})).digest('hex');
  }
}
