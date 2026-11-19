'use strict';
const $ = selector => document.querySelector(selector);
const all = selector => [...document.querySelectorAll(selector)];
const fmt = new Intl.NumberFormat('es-CO', {maximumFractionDigits:2});
const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const date = value => new Date(value).toLocaleString('es-CO',{day:'2-digit',month:'short',hour:'2-digit',minute:'2-digit'});
let auth = '', snapshot = null, selected = null, socket = null, clockOffset = 0, activeView = 'live', refreshTimer, toastTimer, reconnectTimer;
const statuses = {PENDING:'En validación',ACCEPTED:'Aceptada',WON:'Ganada',LOST:'Perdida',REJECTED:'Reembolsada'};
const titles = {live:['En vivo','','En vivo'],history:['Mis apuestas','','Mis apuestas'],results:['Resultados','','Resultados'],admin:['Administración','','Administración']};
async function api(url, options={}) {
  const response = await fetch(url,{...options,headers:{Authorization:auth,...options.headers}});
  if (!response.ok) {
    let message = response.status===401?'Correo o contraseña incorrectos.':response.status===403?'No tienes acceso a esta sección.':'No se pudo completar la solicitud.';
    try { const body=await response.json(); message=body.error||message; } catch (_) { }
    throw new Error(message);
  }
  return response.json();
}
function toast(text) { $('#toast').textContent=text; $('#toast').classList.remove('hidden'); clearTimeout(toastTimer); toastTimer=setTimeout(()=>$('#toast').classList.add('hidden'),5500); }
function now() { return Date.now()+clockOffset; }
function remaining(match) { return Math.max(0,Math.ceil((new Date(match.closesAt).getTime()-now())/1000)); }
function countdown(seconds) { return `${String(Math.floor(seconds/60)).padStart(2,'0')}:${String(seconds%60).padStart(2,'0')}`; }
function initials(name) { return name.split(' ').map(v=>v[0]).slice(0,2).join(''); }
function matchName(id) { const match=snapshot?.matches.find(m=>m.id===id); return match?`${match.home} vs ${match.away}`:id.slice(0,8); }
function resultRows(matches) {
  if (!matches.length) return '<div class="empty"><p>Aún no hay resultados.</p></div>';
  return matches.map(m=>`<div class="result-row"><strong>${escapeHtml(m.home)} <span class="muted">vs</span> ${escapeHtml(m.away)}</strong><span class="winner">⚑ ${escapeHtml(m.winner==='HOME'?m.home:m.away)}</span><span class="result-time">${date(m.closesAt)}</span></div>`).join('');
}
function render() {
  const data=snapshot;
  $('#profile-name').textContent=data.user.name;
  $('.avatar').textContent=initials(data.user.name);
  $('#balance').innerHTML=`${fmt.format(data.user.balance)} <small>PTS</small>`;
  all('.admin-only').forEach(el=>el.classList.toggle('hidden',data.user.role!=='ADMIN'));
  const opened=data.matches.filter(m=>m.status==='OPEN_FOR_BETS'&&remaining(m)>0);
  const finished=data.matches.filter(m=>m.status==='FINISHED');
  $('#open-count').textContent=opened.length; $('#nav-count').textContent=opened.length; $('#live-badge').textContent=opened.length;
  $('#pending-count').textContent=data.bets.filter(b=>['PENDING','ACCEPTED'].includes(b.status)).length;
  $('#finished-count').textContent=finished.length;
  $('#empty').classList.toggle('hidden',opened.length>0);
  $('#matches').innerHTML=opened.map((m,i)=>`<article class="match-card" data-match="${m.id}"><div class="card-top"><span class="live-pill"><span class="dot green"></span> Abierto</span></div><div class="teams"><div><div class="crest" style="--crest:${['#4e6341','#735b38','#3b6660','#5c4d74'][i%4]}">${escapeHtml(initials(m.home))}</div><strong class="team-name">${escapeHtml(m.home)}</strong></div><span class="versus">VS</span><div class="away"><div class="crest">${escapeHtml(initials(m.away))}</div><strong class="team-name">${escapeHtml(m.away)}</strong></div></div><div class="timer">${countdown(remaining(m))}</div><div class="timer-label">Cierra en</div><div class="progress-track"><div class="progress-fill" style="width:${remaining(m)/60*100}%"></div></div><div class="picks"><button class="pick" data-id="${m.id}" data-selection="HOME"><span>Local</span><strong>2.00</strong></button><button class="pick" data-id="${m.id}" data-selection="AWAY"><span>Visitante</span><strong>2.00</strong></button></div><div class="card-footer"><span>▤ &nbsp; ${data.counts[m.id]||0} apuestas</span></div></article>`).join('');
  $('#recent-results').innerHTML=resultRows(finished.slice(0,4)); $('#all-results').innerHTML=resultRows(finished);
  $('#bet-history').innerHTML=data.bets.length?data.bets.map(b=>`<tr><td>${escapeHtml(matchName(b.matchId))}</td><td>${b.selection==='HOME'?'Local':'Visitante'}</td><td>${fmt.format(b.stake)} PTS</td><td><span class="pill ${b.status==='LOST'?'lost':''}">${statuses[b.status]||escapeHtml(b.status)}</span></td><td>${date(b.placedAt)}</td></tr>`).join(''):'<tr><td colspan="5">Aún no tienes apuestas.</td></tr>';
  const reasons={STAKE_RESERVED:'Reserva de apuesta',PAYOUT:'Retorno ganador',STAKE_REFUND:'Reembolso por rechazo'};
  $('#ledger').innerHTML=data.ledger.length?data.ledger.map(l=>`<tr><td>${reasons[l.reason]||escapeHtml(l.reason)}</td><td class="${l.amount>0?'winner':''}">${l.amount>0?'+':''}${fmt.format(l.amount)}</td><td>${date(l.createdAt)}</td></tr>`).join(''):'<tr><td colspan="3">Sin movimientos por ahora.</td></tr>';
}
let refreshing=false, refreshAgain=false;
async function refresh() {
  if (!auth) return;
  if (refreshing) { refreshAgain=true; return; }
  refreshing=true;
  try { const data=await api('/api/snapshot'); clockOffset=new Date(data.serverTime).getTime()-Date.now(); snapshot=data; render(); if (activeView==='admin') await loadAdmin(); }
  catch (error) { $('#connection-text').textContent='Error al actualizar'; }
  finally { refreshing=false; if (refreshAgain) { refreshAgain=false; refresh(); } }
}
async function loadAdmin() {
  try {
    const data=await api('/api/admin');
    $('#admin-users').innerHTML=data.users.map(u=>`<div class="admin-user"><strong>${escapeHtml(u.name)}</strong><span class="muted">${escapeHtml(u.email)}</span><span class="winner">${fmt.format(u.balance)} PTS</span><span class="pill">${u.role}</span></div>`).join('');
    $('#activity').innerHTML=data.activity.map(a=>`<div><time>${date(a.createdAt)}</time><section><strong>${escapeHtml(a.action)}</strong><p>${escapeHtml(a.detail)}</p></section></div>`).join('');
  } catch(error) { toast(error.message); }
}
function connect() {
  clearTimeout(reconnectTimer);
  socket=new WebSocket(`${location.protocol==='https:'?'wss:':'ws:'}//${location.host}/ws`);
  socket.onopen=()=>{ $('#connection-dot').classList.add('green'); $('#connection-text').textContent='Conectado en tiempo real'; refresh(); };
  socket.onmessage=()=>{ clearTimeout(refreshTimer); refreshTimer=setTimeout(refresh,150); };
  socket.onclose=()=>{ $('#connection-dot').classList.remove('green'); $('#connection-text').textContent='Reconectando…'; reconnectTimer=setTimeout(connect,3000); };
  socket.onerror=()=>socket.close();
}
function view(name) {
  if (name==='admin'&&snapshot?.user.role!=='ADMIN') return;
  activeView=name; all('.view').forEach(v=>v.classList.toggle('hidden',v.id!==`${name}-view`));
  all('.nav').forEach(v=>v.classList.toggle('active',v.dataset.view===name));
  $('#page-title').innerHTML=titles[name][0]; $('#page-description').textContent=titles[name][1]; $('#breadcrumb').textContent=titles[name][2];
  if (name==='admin') loadAdmin();
}
document.addEventListener('click',event=>{
  const nav=event.target.closest('[data-view]'); if (nav) view(nav.dataset.view);
  if (event.target.closest('.close-dialog')) event.target.closest('dialog').close();
  const pick=event.target.closest('.pick');
  if (pick) {
    if (!auth) { $('#login-dialog').showModal(); return; }
    const match=snapshot.matches.find(m=>m.id===pick.dataset.id); if (!match||remaining(match)<=0) return;
    selected={matchId:match.id,selection:pick.dataset.selection,requestId:crypto.randomUUID()};
    $('#bet-team').textContent=selected.selection==='HOME'?match.home:match.away;
    $('#bet-match').textContent=`${match.home} vs ${match.away}`;
    $('#bet-error').textContent=''; $('#confirm-bet').disabled=false; $('#bet-dialog').showModal();
  }
  const quick=event.target.closest('[data-stake]'); if (quick) { $('#stake').value=quick.dataset.stake; $('#stake').dispatchEvent(new Event('input')); }
});
$('#account-button').onclick=()=>$('#login-dialog').showModal();
$('#login-form').onsubmit=async event=>{
  event.preventDefault(); const button=event.submitter; button.disabled=true; $('#login-error').textContent='';
  const previous=auth; auth='Basic '+btoa($('#email').value+':'+$('#password').value);
  try { snapshot=await api('/api/snapshot'); clockOffset=new Date(snapshot.serverTime).getTime()-Date.now(); render(); $('#login-dialog').close(); $('#password').value=''; if(activeView==='admin'&&snapshot.user.role!=='ADMIN')view('live'); toast(`Hola, ${snapshot.user.name}.`); }
  catch(error) { auth=previous; $('#login-error').textContent=error.message; }
  finally { button.disabled=false; }
};
$('#stake').oninput=()=>{ $('#potential').textContent=fmt.format(Number($('#stake').value||0)*2)+' PTS'; if(selected)selected.requestId=crypto.randomUUID(); };
$('#bet-form').onsubmit=async event=>{
  event.preventDefault(); if (!selected) return;
  $('#confirm-bet').disabled=true; $('#bet-error').textContent='';
  try { const bet=await api('/bet',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({...selected,stake:$('#stake').value})}); $('#bet-dialog').close(); toast(`Apuesta registrada.`); await refresh(); }
  catch(error) { $('#bet-error').textContent=error.message; }
  finally { $('#confirm-bet').disabled=false; }
};
setInterval(()=>{
  if (!snapshot) return;
  all('.match-card').forEach(card=>{
    const match=snapshot.matches.find(m=>m.id===card.dataset.match); if (!match) return;
    const seconds=remaining(match); card.querySelector('.timer').textContent=countdown(seconds);
    card.querySelector('.progress-fill').style.width=seconds/60*100+'%';
    if (seconds<=0) { card.querySelectorAll('.pick').forEach(p=>p.disabled=true); card.querySelector('.live-pill').textContent='CERRADO'; }
  });
},250);
                                                                                        
setInterval(refresh,15000);
$('#empty').classList.remove('hidden'); connect(); $('#login-dialog').showModal();
