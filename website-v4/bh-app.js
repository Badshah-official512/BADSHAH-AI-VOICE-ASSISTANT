/* BADSHAH AI app — v6 UI layer.
   Sits on top of app.html's existing auth / call / help code (kept untouched) and replaces
   message rendering, sending, history, voice, settings and the talking-portrait stage. */
(function(){
'use strict';
const detect=BadshahOrb.detectMood;
const inp=$('input');
const AV={badshah:'assets/avatar.jpg',rani:'assets/rani.png'};
const T=(n,m)=>{ try{ if(window.BHTrack) BHTrack.event(n,m); }catch(e){} };
const escH=s=>String(s==null?'':s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
const orb=new BadshahOrb($('orbHost'),{portrait:AV[state.mode]||AV.badshah,varTarget:$('chatPanel')});
let authOrb=null; try{ authOrb=new BadshahOrb($('authOrb'),{portrait:AV.badshah}); }catch(e){}
let callOrb=null,callT=null;

/* ---------------- settings ---------------- */
const defS={instr:'',lang:'auto',speak:false,vlang:'ur-PK',fs:15.5};
let S=Object.assign({},defS); try{ Object.assign(S,JSON.parse(localStorage.getItem('ba_settings')||'{}')); }catch(e){}
function applyS(){ document.documentElement.style.setProperty('--fs',(S.fs||15.5)+'px'); }
applyS();
let tempChat=false, voice=false, rec=null, speakingNow=false, curAudio=null, ac=null, ctrl=null, cancelReveal=false, idleT=null, baseMood='calm', composeMode='chat', lastChatModel=null;
function toast(m,ms){ const t=$('toast'); t.textContent=m; t.classList.remove('hidden'); clearTimeout(toast.t); toast.t=setTimeout(()=>t.classList.add('hidden'),ms||2600); }

/* ---------------- markdown ---------------- */
function inline(s){
  s=s.replace(/`([^`\n]+)`/g,'<code>$1</code>');
  s=s.replace(/\*\*([^*\n]+)\*\*/g,'<b>$1</b>');
  s=s.replace(/(^|[\s(])\*([^*\n]+)\*(?=[\s).,!?:;]|$)/g,'$1<i>$2</i>');
  s=s.replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g,'<a href="$2" target="_blank" rel="noopener">$1</a>');
  s=s.replace(/(^|[\s(])(https?:\/\/[^\s<)]+)/g,'$1<a href="$2" target="_blank" rel="noopener">$2</a>');
  return s;
}
function mdx(src){
  const codes=[]; src=String(src||'').replace(/```([\w+-]*)[^\n]*\n?([\s\S]*?)(```|$)/g,(m,l,c)=>{ codes.push({l,c:c.replace(/\n$/,'')}); return '\n\u0000C'+(codes.length-1)+'\u0000\n'; });
  const L=escH(src).split('\n'); const out=[]; let i=0;
  const isBlock=l=>/^(#{1,3}\s|\s*[-*•]\s+|\s*\d+[.)]\s+|&gt;|\u0000C|\s*(-{3,}|\*{3,})\s*$)/.test(l);
  while(i<L.length){
    const ln=L[i]; let m;
    if(!ln.trim()){ i++; continue; }
    if(/^\u0000C\d+\u0000$/.test(ln.trim())){ out.push(ln.trim()); i++; continue; }
    if((m=ln.match(/^(#{1,3})\s+(.*)$/))){ out.push('<h'+m[1].length+'>'+inline(m[2])+'</h'+m[1].length+'>'); i++; continue; }
    if(/^\s*(-{3,}|\*{3,})\s*$/.test(ln)){ out.push('<hr>'); i++; continue; }
    if(/^&gt;\s?/.test(ln)){ const q=[]; while(i<L.length&&/^&gt;\s?/.test(L[i])) q.push(L[i++].replace(/^&gt;\s?/,'')); out.push('<blockquote>'+inline(q.join('<br>'))+'</blockquote>'); continue; }
    if(/^\s*[-*•]\s+/.test(ln)){ const it=[]; while(i<L.length&&/^\s*[-*•]\s+/.test(L[i])) it.push('<li>'+inline(L[i++].replace(/^\s*[-*•]\s+/,''))+'</li>'); out.push('<ul>'+it.join('')+'</ul>'); continue; }
    if(/^\s*\d+[.)]\s+/.test(ln)){ const it=[]; while(i<L.length&&/^\s*\d+[.)]\s+/.test(L[i])) it.push('<li>'+inline(L[i++].replace(/^\s*\d+[.)]\s+/,''))+'</li>'); out.push('<ol>'+it.join('')+'</ol>'); continue; }
    if(ln.includes('|')&&L[i+1]&&/^\s*\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?\s*$/.test(L[i+1])){
      const cells=r=>r.trim().replace(/^\||\|$/g,'').split('|').map(x=>inline(x.trim()));
      const head=cells(ln); i+=2; const rows=[]; while(i<L.length&&L[i].includes('|')&&L[i].trim()) rows.push(cells(L[i++]));
      out.push('<table><thead><tr>'+head.map(h=>'<th>'+h+'</th>').join('')+'</tr></thead><tbody>'+rows.map(r=>'<tr>'+r.map(c=>'<td>'+c+'</td>').join('')+'</tr>').join('')+'</tbody></table>'); continue;
    }
    const p=[ln]; i++; while(i<L.length&&L[i].trim()&&!isBlock(L[i])) p.push(L[i++]);
    out.push('<p>'+inline(p.join('<br>'))+'</p>');
  }
  return out.join('').replace(/\u0000C(\d+)\u0000/g,(m,n)=>{ const c=codes[n]; return '<div class="codebox"><div class="codehead"><span>'+escH(c.l||'code')+'</span><button type="button" data-copy>📋 Copy</button></div><pre><code>'+escH(c.c)+'</code></pre></div>'; });
}
window.md=mdx;

/* ---------------- storage ---------------- */
window.saveChats=function(){
  if(tempChat) return;
  try{ localStorage.setItem('ba_chats',JSON.stringify(state.chats.slice(0,40).map(c=>({...c,msgs:c.msgs.map(m=>(m.img&&String(m.img).startsWith('data:'))?{...m,img:null}:m)})))); }catch(e){}
};

/* ---------------- messages ---------------- */
function scrollDown(force){ const m=$('msgs'); if(force||m.scrollHeight-m.scrollTop-m.clientHeight<220) m.scrollTop=m.scrollHeight; }
function markLast(){ const all=$('msgWrap').querySelectorAll('.msg.ai'); all.forEach(e=>e.classList.remove('last')); const l=all[all.length-1]; if(l) l.classList.add('last'); }
function btn(label,cls,fn){ const b=document.createElement('button'); b.type='button'; b.innerHTML=label; if(cls) b.className=cls; b.onclick=fn; return b; }
function share(text){ if(navigator.share){ navigator.share({text:text+'\n\n— BADSHAH AI'}).catch(()=>{}); } else { navigator.clipboard.writeText(text).then(()=>toast('Copy ho gaya')); } }
window.addMsgEl=function(role,content,img,idx){
  const w=$('msgWrap'); const d=document.createElement('div'); d.className='msg '+(role==='user'?'user':'ai'); if(idx!=null) d.dataset.i=idx;
  const av=role==='user'?'':'<img class="av" src="'+AV[state.mode]+'" alt="">';
  d.innerHTML=av+'<div class="body">'+(img?'<img class="gen" src="'+escH(img)+'" alt="AI image">':'')+'<div class="txt"></div><div class="acts"></div></div>';
  d.querySelector('.txt').innerHTML=role==='ai'?mdx(content):escH(content);
  const acts=d.querySelector('.acts');
  if(role==='ai'){
    if(!content&&!img){ acts.remove(); }
    else{
      acts.append(btn('📋','',()=>{ navigator.clipboard.writeText(content).then(()=>toast('Copy ho gaya ✓')); }),
        btn('🔊','',()=>speak(content)));
      if(img) acts.append(btn('⬇','',()=>{ const a=document.createElement('a'); a.href=img; a.download='badshah-ai.png'; a.target='_blank'; a.click(); }));
      acts.append(btn('🔄','rg',()=>window.regenerate()),
        btn('👍','',function(){ this.classList.toggle('on'); T('feedback',{v:'up'}); }),
        btn('👎','',function(){ this.classList.toggle('on'); T('feedback',{v:'down'}); }),
        btn('↗','',()=>share(content)));
    }
    const gi=d.querySelector('img.gen'); if(gi) gi.onclick=()=>window.open(img,'_blank');
  } else {
    acts.append(btn('✏️','',()=>{ const i=+d.dataset.i; const c=chat(); if(isNaN(i)||!c.msgs[i]) return; inp.value=c.msgs[i].content; c.msgs.length=i; renderMsgs(); saveChats(); syncComposer(); inp.focus(); }),
      btn('📋','',()=>{ navigator.clipboard.writeText(content).then(()=>toast('Copy ho gaya ✓')); }));
  }
  w.appendChild(d); markLast(); scrollDown(true); return d;
};
$('msgWrap').addEventListener('click',e=>{ const b=e.target.closest('[data-copy]'); if(!b) return; const code=b.closest('.codebox').querySelector('code').textContent; navigator.clipboard.writeText(code).then(()=>{ b.textContent='✓ Copied'; setTimeout(()=>b.innerHTML='📋 Copy',1500); }); });
window.renderMsgs=function(){
  const w=$('msgWrap'); w.innerHTML=''; chat().msgs.forEach((m,i)=>addMsgEl(m.role,m.content,m.img,i)); BH.sync(); scrollDown(true);
};

/* ---------------- history list ---------------- */
let chatQ='';
window.renderChats=function(){
  const L=$('chatList'); L.innerHTML='';
  const idx=state.chats.map((c,i)=>i).filter(i=>!chatQ||(state.chats[i].title||'').toLowerCase().includes(chatQ));
  idx.sort((a,b)=>(state.chats[b].pinned?1:0)-(state.chats[a].pinned?1:0));
  idx.forEach(i=>{ const c=state.chats[i]; const r=document.createElement('div'); r.className='chat-row'+(i===state.current?' on':'');
    const b=document.createElement('button'); b.className='chat-item'; b.textContent=(c.pinned?'📌 ':'')+(c.title||'New chat'); b.onclick=()=>{ state.current=i; renderChats(); renderMsgs(); openPanel('chat'); closeDrawer(); };
    const d=document.createElement('button'); d.className='chat-dots'; d.textContent='⋯'; d.onclick=e=>{ e.stopPropagation(); chatMenu(i,d); };
    r.append(b,d); L.appendChild(r); });
  if(!idx.length) L.innerHTML='<div style="color:var(--mut);font-size:13px;padding:10px 12px">Koi chat nahi mili</div>';
};
function chatMenu(i,anchor){
  document.querySelectorAll('.pop').forEach(p=>p.remove());
  const c=state.chats[i]; const p=document.createElement('div'); p.className='pop';
  const r=anchor.getBoundingClientRect(); p.style.cssText='position:fixed;z-index:2800;left:'+Math.min(r.left,innerWidth-190)+'px;top:'+(r.bottom+4)+'px;width:180px;background:#0f3126;border:1px solid var(--line);border-radius:14px;padding:6px;box-shadow:0 18px 40px rgba(0,0,0,.55);display:grid;gap:2px';
  const it=(t,f)=>{ const b=document.createElement('button'); b.textContent=t; b.style.cssText='text-align:left;padding:9px 12px;border:0;background:none;border-radius:9px;font-size:13.5px'; b.onmouseenter=()=>b.style.background='rgba(46,230,166,.12)'; b.onmouseleave=()=>b.style.background='none'; b.onclick=()=>{ p.remove(); f(); }; p.appendChild(b); };
  it('✏️ Rename',()=>{ const n=prompt('Chat ka naam:',c.title||''); if(n){ c.title=n.slice(0,60); saveChats(); renderChats(); } });
  it(c.pinned?'📌 Unpin':'📌 Pin',()=>{ c.pinned=!c.pinned; saveChats(); renderChats(); });
  it('💾 Export',()=>exportChat(c));
  it('🗑 Delete',()=>{ if(!confirm('Ye chat delete kar dun?')) return; state.chats.splice(i,1); if(!state.chats.length) state.chats=[{title:'New chat',msgs:[]}]; state.current=Math.min(state.current>i?state.current-1:state.current,state.chats.length-1); saveChats(); renderChats(); renderMsgs(); });
  document.body.appendChild(p); setTimeout(()=>document.addEventListener('click',()=>p.remove(),{once:true}),0);
}
$('chatSearch').addEventListener('input',e=>{ chatQ=e.target.value.trim().toLowerCase(); renderChats(); });
function exportChat(c){
  const t='# '+(c.title||'BADSHAH AI chat')+'\n\n'+c.msgs.map(m=>'**'+(m.role==='user'?'Aap':'BADSHAH AI')+':**\n'+m.content).join('\n\n---\n\n');
  const a=document.createElement('a'); a.href=URL.createObjectURL(new Blob([t],{type:'text/markdown'})); a.download=(c.title||'chat').replace(/[^\w\u0600-\u06FF-]+/g,'_').slice(0,40)+'.md'; a.click();
}
$('btnNewChat').onclick=()=>{ state.chats.unshift({title:'New chat',msgs:[]}); state.current=0; saveChats(); renderChats(); renderMsgs(); openPanel('chat'); closeDrawer(); setComposeMode('chat'); inp.focus(); };

/* ---------------- AI calls ---------------- */
const isImageModel=v=>(CFG.imageModels||[]).includes(v);
function niceErr(m){ m=String(m||''); if(/Failed to fetch|NetworkError|Load failed/i.test(m)) return 'Internet ka masla lag raha hai — connection check karke dobara try karo.'; return m; }
function buildPrompt(text){
  const cp=(window.BH_CONFIG&&BH_CONFIG.app&&BH_CONFIG.app.systemPrompt)||'';
  const persona=state.mode==='rani'?'You are RANI, the sweet, caring and expressive female AI assistant of BADSHAH AI, created by Zain Shah (Badshah) from Pakistan.':'You are BADSHAH AI Assistant — a smart, loyal and friendly male AI assistant created by Zain Shah (Badshah) from Pakistan.';
  const lang={auto:'Reply in the same language and script the user writes in (Urdu, Roman Urdu, English or Hindi).',roman:'Always reply in Roman Urdu (Urdu written with English letters).',en:'Always reply in English.',ur:'Always reply in Urdu script (اردو).'}[S.lang]||'';
  const sys=[persona,'Be accurate, helpful and to the point. Use markdown (lists, headings, code blocks) when it helps.',lang,cp,S.instr?('User instructions: '+S.instr):''].filter(Boolean).join('\n');
  const hist=chat().msgs.slice(0,-1).slice(-8).map(m=>(m.role==='user'?'User: ':'Assistant: ')+String(m.content).slice(0,800));
  const make=()=>sys+(hist.length?'\n\nConversation so far:\n'+hist.join('\n'):'')+'\n\nUser: '+text+'\nAssistant:';
  let p=make(); while(p.length>9000&&hist.length){ hist.shift(); p=make(); }
  return p.slice(-12000);
}
async function authHeaders(){ const t=await idToken(); return t?{'Authorization':'Bearer '+t}:{}; }
async function callChat(text){
  const model=$('modelSel').value; let q='?model=gemini-3.8-flash&provider=gemini';
  if(model.startsWith('groq:')) q='?provider=groq&model='+encodeURIComponent(model.slice(5));
  else if(model.startsWith('gemini:')) q='?provider=gemini&model='+encodeURIComponent(model.slice(7));
  const r=await fetch(CFG.apiUrl+q,{method:'POST',headers:{'Content-Type':'application/json',...(await authHeaders())},body:JSON.stringify({q:buildPrompt(text)}),signal:ctrl.signal});
  const d=await r.json().catch(()=>({}));
  if(!r.ok) throw new Error(d.error||('Server '+r.status));
  let reply=String(d.text||d.reply||d.response||d.answer||'').replace(/^\s*Assistant:\s*/i,'').trim();
  if(!reply) throw new Error('Khaali jawab aaya — dobara try karo.');
  return reply;
}
async function callImage(text){
  const model=isImageModel($('modelSel').value)?$('modelSel').value:CFG.imageModels[0];
  const r=await fetch(CFG.imageUrl+'?model='+encodeURIComponent(model)+'&q='+encodeURIComponent(text),{headers:await authHeaders(),signal:ctrl.signal});
  const d=await r.json().catch(()=>({}));
  if(!r.ok) throw new Error(d.error||('Server '+r.status));
  const img=d.image||d.image_url||d.url; if(!img) throw new Error('Image nahi bani — dobara try karo.');
  return {reply:'Ye lo tumhari image 🎨',img};
}
async function callVideo(text,waitEl){
  const model=(CFG.videoModels||[])[0]||'gemini-omni-1.1-flash';
  const r=await fetch(CFG.videoUrl,{method:'POST',headers:{'Content-Type':'application/json',...(await authHeaders())},body:JSON.stringify({prompt:text,model}),signal:ctrl.signal});
  const d=await r.json().catch(()=>({})); if(!r.ok) throw new Error(d.error||('Video start nahi hui ('+r.status+')'));
  if(!d.name) throw new Error('Video ka job nahi bana.');
  const t0=Date.now();
  for(let k=0;k<45;k++){
    await new Promise(res=>setTimeout(res,8000)); if(ctrl.signal.aborted) throw Object.assign(new Error('abort'),{name:'AbortError'});
    waitEl.querySelector('.txt').innerHTML='🎬 Video ban rahi hai… '+Math.round((Date.now()-t0)/1000)+'s <span class="typing"><i></i><i></i><i></i></span>';
    const s=await fetch(CFG.videoUrl+'?name='+encodeURIComponent(d.name),{headers:await authHeaders(),signal:ctrl.signal}); const sd=await s.json().catch(()=>({}));
    if(sd.done){ const url=(JSON.stringify(sd).match(/https?:[^"\\]+/)||[])[0]; return url?'🎬 Video tayyar!\n\n[Video dekho]('+url+')':'🎬 Video tayyar ho gayi, lekin link nahi mila.'; }
  }
  throw new Error('Video mein bohat waqt lag raha hai — baad mein try karo.');
}
function setBusy(b){ state.busy=b; $('btnStop').classList.toggle('hidden',!b); }
async function runAI(text){
  setBusy(true); ctrl=new AbortController();
  const wait=addMsgEl('ai','',null); wait.querySelector('.txt').innerHTML='<span class="typing"><i></i><i></i><i></i></span>';
  try{
    let reply='',img=null;
    if(composeMode==='video') reply=await callVideo(text,wait);
    else if(composeMode==='image'||isImageModel($('modelSel').value)) ({reply,img}=await callImage(text));
    else reply=await callChat(text);
    wait.remove(); chat().msgs.push({role:'ai',content:reply,img}); const el=addMsgEl('ai',reply,img,chat().msgs.length-1); saveChats();
    T('reply',{mode:composeMode});
    setBusy(false); BH.onReply(el,reply);
  }catch(e){
    wait.remove(); setBusy(false);
    if(e&&e.name==='AbortError'){ BH.onStop(); } else { addMsgEl('ai','⚠️ '+niceErr(e&&e.message)); BH.onError(); }
  }
  ctrl=null;
}
window.send=async function(){
  const text=inp.value.trim(); if(!text||state.busy) return;
  inp.value=''; autosize(); syncComposer();
  const c=chat(); c.msgs.push({role:'user',content:text}); if(c.msgs.length===1||c.title==='New chat') c.title=text.slice(0,34);
  addMsgEl('user',text,null,c.msgs.length-1); renderChats(); saveChats(); BH.onSend(text);
  T('message',{mode:composeMode});
  await runAI(text);
};
window.regenerate=async function(){
  if(state.busy) return; const c=chat(); while(c.msgs.length&&c.msgs[c.msgs.length-1].role==='ai') c.msgs.pop();
  const last=c.msgs[c.msgs.length-1]; if(!last) return; renderMsgs(); BH.onSend(last.content); await runAI(last.content);
};
$('btnStop').onclick=()=>{ if(ctrl) ctrl.abort(); cancelReveal=true; stopSpeak(); };

/* ---------------- composer ---------------- */
function autosize(){ inp.style.height='auto'; const h=inp.scrollHeight; if(h>0) inp.style.height=Math.min(140,h)+'px'; }
function syncComposer(){ $$c().classList.toggle('has-text',!!inp.value.trim()); }
const $$c=()=>document.querySelector('.comp-in');
inp.addEventListener('input',()=>{ autosize(); syncComposer(); BH.typed(); });
inp.addEventListener('keydown',e=>{ if(e.key==='Enter'&&!e.shiftKey&&!e.isComposing&&innerWidth>860){ e.preventDefault(); send(); } });
$('btnSend').onclick=()=>send();
function setComposeMode(m){
  composeMode=m; const imgs=CFG.imageModels||[]; const sel=$('modelSel');
  if(m==='image'){ if(!isImageModel(sel.value)){ lastChatModel=sel.value; sel.value=imgs[0]; } state.imgMode=true; }
  else { state.imgMode=false; if(isImageModel(sel.value)){ sel.value=lastChatModel||sel.options[0].value; } }
  inp.placeholder=m==='image'?'Kaisi image banani hai? Describe karo…':m==='video'?'Kaisi video banani hai? Describe karo…':(state.mode==='rani'?'RANI':'BADSHAH')+' se kuch bhi poochho…';
  $('compHint').textContent=m==='image'?'🎨 Image mode — jo likhoge uski image banegi (Gemini Image, free).':m==='video'?'🎬 Video mode (beta) — 1-3 minute lag sakte hain.':'Sab kuch FREE hai 👑 — chat, images, voice aur calls. Koi key, koi payment nahi.';
  document.querySelectorAll('#quickRow button[data-m]').forEach(b=>b.classList.toggle('on',b.dataset.m===m));
  chipTxt();
}
const QUICK=[
  {t:'🎨 Image',m:'image',f:'image'},{t:'🎬 Video',m:'video',f:'video'},{t:'💻 Code',p:'Mujhe ye code likh kar do: '},{t:'✍️ Likho',p:'Mere liye likho: '},
  {t:'🌐 Translate',p:'Is ko Urdu aur English dono mein translate karo: '},{t:'🗣️ Voice',v:1,f:'voice'},{t:'📞 Call',c:1,f:'call'}
];
QUICK.forEach(q=>{ const b=document.createElement('button'); b.type='button'; b.textContent=q.t; if(q.m) b.dataset.m=q.m; if(q.f) b.setAttribute('data-cfg-feature',q.f);
  b.onclick=()=>{ if(q.m) setComposeMode(composeMode===q.m?'chat':q.m); else if(q.v) toggleVoice(); else if(q.c) openCallSmart(); else { setComposeMode('chat'); inp.value=q.p; syncComposer(); } inp.focus(); };
  $('quickRow').appendChild(b); });

/* ---------------- model picker ---------------- */
function chipTxt(){ const o=$('modelSel').selectedOptions[0]; $('modelChipTxt').textContent=o?o.textContent.replace(/^[^\w]+/,'').replace(/ \(image\)/,'').replace(/ — .*/,''):'Model'; }
$('btnModel').onclick=()=>{
  const L=$('modelList'); L.innerHTML=''; let last='';
  [...$('modelSel').options].forEach(o=>{ const g=o.textContent.startsWith('💬')?'Chat':o.textContent.startsWith('⚡')?'Groq — super fast':'Image'; if(g!==last){ const h=document.createElement('div'); h.className='grp'; h.textContent=g; L.appendChild(h); last=g; }
    const b=document.createElement('button'); b.className=o.value===$('modelSel').value?'on':''; b.innerHTML='<span>'+escH(o.textContent)+'</span>'+(o.value===$('modelSel').value?'<em>✓</em>':'');
    b.onclick=()=>{ $('modelSel').value=o.value; $('modelSel').dispatchEvent(new Event('change')); setComposeMode(isImageModel(o.value)?'image':'chat'); localStorage.setItem('ba_model',o.value); closeSheets(); }; L.appendChild(b); });
  openSheet('modelSheet');
};
const _bm=window.buildModels; window.buildModels=function(){ _bm(); const saved=localStorage.getItem('ba_model'); const cfgd=window.BH_CONFIG&&BH_CONFIG.app&&BH_CONFIG.app.defaultModel; const want=saved||cfgd; if(want&&[...$('modelSel').options].some(o=>o.value===want)) $('modelSel').value=want; $('modelSel').dispatchEvent(new Event('change')); lastChatModel=isImageModel($('modelSel').value)?null:$('modelSel').value; chipTxt(); };

/* ---------------- sheets ---------------- */
function openSheet(id){ $('sheetWrap').classList.remove('hidden'); document.querySelectorAll('.sheet').forEach(s=>s.classList.toggle('open',s.id===id)); }
function closeSheets(){ $('sheetWrap').classList.add('hidden'); document.querySelectorAll('.sheet').forEach(s=>s.classList.remove('open')); }
$('sheetWrap').addEventListener('click',e=>{ if(e.target===$('sheetWrap')||e.target.closest('[data-close]')) closeSheets(); });
document.addEventListener('keydown',e=>{ if(e.key==='Escape'){ closeSheets(); closeDrawer(); } if(e.key==='/'&&document.activeElement===document.body&&!$('appView').classList.contains('hidden')){ e.preventDefault(); inp.focus(); } });
function closeDrawer(){ $('sidebar').classList.remove('open'); $('scrim').classList.remove('on'); }
$('menuBtn').onclick=()=>{ const o=$('sidebar').classList.toggle('open'); $('scrim').classList.toggle('on',o); };
$('scrim').onclick=closeDrawer;
$('btnPlus').onclick=()=>openSheet('plusSheet');
$('btnMore').onclick=()=>openSheet('moreSheet');
$('plusSheet').addEventListener('click',e=>{ const b=e.target.closest('[data-act]'); if(!b) return; const a=b.dataset.act; closeSheets();
  if(a==='image') setComposeMode('image'); else if(a==='video') setComposeMode('video'); else if(a==='voice') toggleVoice(); else if(a==='call') openCallSmart();
  else if(a==='code'){ setComposeMode('chat'); inp.value='Mujhe ye code likh kar do: '; syncComposer(); } else if(a==='translate'){ setComposeMode('chat'); inp.value='Is ko Urdu aur English dono mein translate karo: '; syncComposer(); }
  else if(a==='temp'){ tempChat=!tempChat; toast(tempChat?'🕶️ Temporary chat ON — save nahi hogi':'Temporary chat band'); $('stageName').textContent=(state.mode==='rani'?'RANI':'BADSHAH')+(tempChat?' · temp':''); }
  else if(a==='export') exportChat(chat());
  inp.focus(); });
$('moreSheet').addEventListener('click',e=>{ const b=e.target.closest('[data-more]'); if(!b) return; const a=b.dataset.more; closeSheets();
  if(a==='new') $('btnNewChat').click(); else if(a==='mode') setMode(state.mode==='rani'?'badshah':'rani'); else if(a==='device') openPanel('pc'); else if(a==='settings') openSettings(); else if(a==='help') openPanel('help'); else if(a==='logout') $('btnLogout').click(); });
$('btnSettings').onclick=()=>{ closeDrawer(); openSettings(); };
function openSettings(){ $('setInstr').value=S.instr; $('setLang').value=S.lang; $('setSpeak').checked=!!S.speak; $('setVlang').value=S.vlang; $('setFs').value=S.fs; openSheet('settingsModal'); }
$('setSave').onclick=()=>{ S={instr:$('setInstr').value.trim().slice(0,1200),lang:$('setLang').value,speak:$('setSpeak').checked,vlang:$('setVlang').value,fs:parseFloat($('setFs').value)||15.5}; localStorage.setItem('ba_settings',JSON.stringify(S)); applyS(); closeSheets(); toast('Settings save ho gayi ✓'); };
$('setFs').oninput=e=>document.documentElement.style.setProperty('--fs',e.target.value+'px');
$('setExport').onclick=()=>{ const t=state.chats.map(c=>'# '+(c.title||'chat')+'\n\n'+c.msgs.map(m=>'**'+(m.role==='user'?'Aap':'BADSHAH AI')+':** '+m.content).join('\n\n')).join('\n\n=====\n\n'); const a=document.createElement('a'); a.href=URL.createObjectURL(new Blob([t],{type:'text/markdown'})); a.download='badshah-ai-chats.md'; a.click(); };
$('setClear').onclick=()=>{ if(!confirm('Saari chats hamesha ke liye delete kar dun?')) return; state.chats=[{title:'New chat',msgs:[]}]; state.current=0; saveChats(); renderChats(); renderMsgs(); closeSheets(); toast('Saari chats delete ho gayin'); };
const _op=window.openPanel; window.openPanel=function(w){ _op(w); closeDrawer(); };

/* ---------------- speech ---------------- */
window.speak=function(text){
  return new Promise(async res=>{
    const clean=String(text).replace(/```[\s\S]*?```/g,' ').replace(/[*#`_>|]/g,'').replace(/\[([^\]]+)\]\([^)]+\)/g,'$1').slice(0,700).trim(); if(!clean){ res(); return; }
    stopSpeak(); speakingNow=true; let fin=false,jit=null;
    const done=()=>{ if(fin) return; fin=true; clearInterval(jit); orb.useLevel(null); speakingNow=false; curAudio=null; toIdle(); res(); };
    orb.setState('speaking');
    try{
      const r=await fetch(CFG.ttsUrl,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({text:clean,voice:state.mode})});
      if(r.ok){
        const b=await r.blob(); const a=new Audio(URL.createObjectURL(b)); curAudio=a;
        try{ const AC=window.AudioContext||window.webkitAudioContext; ac=ac||new AC(); if(ac.state==='suspended') await ac.resume();
          const src=ac.createMediaElementSource(a),an=ac.createAnalyser(); an.fftSize=256; src.connect(an); an.connect(ac.destination); const buf=new Uint8Array(an.frequencyBinCount);
          orb.useLevel(()=>{ an.getByteFrequencyData(buf); let s=0; for(let k=2;k<40;k++) s+=buf[k]; return Math.max(.12,Math.min(1,s/38/140)); }); }catch(e){}
        a.onended=done; a.onerror=done; await a.play(); return;
      }
    }catch(e){}
    try{ const u=new SpeechSynthesisUtterance(clean); const vs=speechSynthesis.getVoices(); const v=vs.find(v=>/ur|hi/i.test(v.lang))||vs[0]; if(v) u.voice=v; u.onend=done; u.onerror=done; speechSynthesis.cancel(); speechSynthesis.speak(u); jit=setInterval(()=>orb.say(Math.random()<.35?' ':'ae'),95); }catch(e){ done(); }
  });
};
function stopSpeak(){ try{ if(curAudio){ curAudio.pause(); curAudio=null; } speechSynthesis.cancel(); }catch(e){} speakingNow=false; orb.useLevel(null); }
function toIdle(){ if(!speakingNow&&!state.busy&&!rec) orb.setState('idle'); }
function listenOnce(autoSend){
  const SR=window.SpeechRecognition||window.webkitSpeechRecognition;
  if(!SR){ toast('Is browser mein voice input nahi — Chrome use karo.'); voice=false; return; }
  if(rec||state.busy) return; stopSpeak();
  const r=new SR(); rec=r; r.lang=S.vlang||'ur-PK'; r.interimResults=true; r.continuous=false;
  orb.setState('listening'); orb.ripple(); $('btnMic').classList.add('mic-on');
  r.onresult=e=>{ let t=''; for(const x of e.results) t+=x[0].transcript; inp.value=t; autosize(); syncComposer(); orb.pulse(.55); orb.setMood(detect(t)); if(e.results[e.results.length-1].isFinal){ r.stop(); if(autoSend) send(); } };
  r.onerror=()=>{};
  r.onend=()=>{ rec=null; $('btnMic').classList.remove('mic-on'); if(voice&&!state.busy&&!speakingNow&&!inp.value.trim()) setTimeout(()=>listenOnce(true),350); else toIdle(); };
  try{ r.start(); }catch(e){ rec=null; $('btnMic').classList.remove('mic-on'); }
}
$('btnMic').onclick=()=>{ if(rec){ try{ rec.stop(); }catch(e){} return; } listenOnce(false); };
function toggleVoice(){
  voice=!voice; toast(voice?'🗣️ Voice mode ON — bolo, main sun raha hoon':'Voice mode band');
  document.querySelectorAll('#quickRow button').forEach(b=>{ if(b.textContent.includes('Voice')) b.classList.toggle('on',voice); });
  if(voice) listenOnce(true); else { if(rec){ try{ rec.stop(); }catch(e){} } stopSpeak(); toIdle(); }
}

/* ---------------- stage / talking portrait ---------------- */
const STATUS={idle:'Online — sun raha hoon',listening:'Sun raha hoon…',thinking:'Soch raha hoon…',speaking:'Bol raha hoon…'};
const LIVE={idle:'Type karo ya 🗣️ dabao — main sunta bhi hoon, bolta bhi.',listening:'Tumhari baat dhyan se sun raha hoon 👂',thinking:'Jawab tayyar ho raha hai…',speaking:'Jawab de raha hoon 💬'};
orb.onState=s=>{ $('orbStatusTxt').textContent=STATUS[s]; $('stageLive').textContent=LIVE[s]; };
const CARDS=[
  {i:'🎨',t:'Image banao',s:'Ek shandar tasveer',m:'image',p:'Raat ke waqt Karachi ki ek cinematic tasveer banao'},
  {i:'💻',t:'Website ka code',s:'HTML · CSS · JS',p:'Ek modern landing page ka HTML/CSS/JS code likho'},
  {i:'✍️',t:'Likhne mein madad',s:'Email, post, caption',p:'Meri website ke liye 5 acche brand naam batao'},
  {i:'📞',t:'Live call',s:'Seedha baat karo',c:1},
  {i:'💜',t:'Dil ki baat',s:'Sunne wala dost',p:'Aaj main thoda udaas hoon, mujhse baat karo'},
  {i:'🧠',t:'Kuch seekho',s:'Aasan alfaaz mein',p:'AI kaise kaam karta hai? Aasan alfaaz mein samjhao'}
];
function buildCards(list){
  const box=$('stageChips'); box.innerHTML='';
  (list&&list.length?list:CARDS).forEach(c=>{ const b=document.createElement('button'); b.type='button'; b.innerHTML='<i>'+escH(c.i||'✨')+'</i><b>'+escH(c.t)+'</b><small>'+escH(c.s||'')+'</small>'; if(c.c) b.setAttribute('data-cfg-feature','call');
    b.onclick=()=>{ if(c.c) return openCallSmart(); setComposeMode(c.m||'chat'); inp.value=c.p||c.t; syncComposer(); send(); }; box.appendChild(b); });
}
buildCards();
function titleFor(m){ $('stageTitle').innerHTML=m==='rani'?'Hi jaan! Main <span class="grad">RANI</span> hoon 💜':'Assalam o Alaikum! <span class="grad">BADSHAH</span> yahan hai 👑'; }
function reveal(el,text,done){
  const box=el.querySelector('.txt'); if(!box){ done&&done(); return; }
  const acts=el.querySelector('.acts'); if(acts) acts.style.visibility='hidden'; cancelReveal=false;
  const total=text.length,t0=performance.now(),dur=Math.min(5200,Math.max(900,total*20)); let i=0;
  orb.setState('speaking'); orb.ripple();
  (function tick(now){
    const target=cancelReveal?total:Math.min(total,Math.ceil(((now-t0)/dur)*total)); if(target>i){ orb.say(text.slice(i,target)); i=target; }
    box.innerHTML=mdx(text.slice(0,i))+(i<total?'<span class="caret"></span>':''); scrollDown();
    if(i<total) requestAnimationFrame(tick); else { if(acts) acts.style.visibility=''; markLast(); done&&done(); }
  })(t0);
}
window.BH={
  orb:orb,
  sync(){ const has=!!(chat()&&chat().msgs.length); $('chatPanel').classList.toggle('has-msgs',has); },
  typed(){
    const v=inp.value; clearTimeout(idleT);
    if(!v.trim()){ orb.setMood(baseMood); orb.setState('idle'); return; }
    if(state.busy) return; orb.setState('listening'); orb.pulse(.42); const m=detect(v); orb.setMood(m==='calm'?baseMood:m);
    idleT=setTimeout(()=>{ if(!state.busy&&!rec) orb.setState('idle'); },1600);
  },
  onSend(text){ clearTimeout(idleT); stopSpeak(); cancelReveal=true; BH.sync(); const m=detect(text); orb.setMood(m==='calm'?baseMood:m); orb.setState('thinking'); },
  onReply(el,reply){
    const rm=detect(reply); if(rm!=='calm'&&rm!=='curious') orb.setMood(rm);
    const finish=()=>{ if(voice||S.speak){ speak(reply).then(()=>{ if(voice) setTimeout(()=>{ inp.value=''; listenOnce(true); },250); }); } else toIdle();
      setTimeout(()=>{ if(!speakingNow&&!state.busy&&!inp.value.trim()) orb.setMood(baseMood); },9000); };
    reveal(el,reply,finish);
  },
  onError(){ orb.setMood('angry'); orb.setState('idle'); setTimeout(()=>orb.setMood(baseMood),2500); if(voice) setTimeout(()=>listenOnce(true),1200); },
  onStop(){ orb.setState('idle'); },
  callOpen(){
    if(!callOrb){ try{ callOrb=new BadshahOrb($('callOrbHost'),{portrait:AV[state.mode]||AV.badshah}); }catch(e){} }
    if(callOrb){ callOrb.setPortrait(AV[state.mode]||AV.badshah,state.mode==='badshah'); callOrb.setState('listening'); }
  },
  callPulse(){ if(!callOrb) return; callOrb.setState('speaking'); callOrb.pulse(.9); callOrb.say('ae'); clearTimeout(callT); callT=setTimeout(()=>callOrb.setState('listening'),650); }
};
function setBaseMood(){ baseMood=state.mode==='rani'?'love':'calm'; }
window.setMode=function(m){
  state.mode=m; localStorage.setItem('ba_mode',m);
  $('modeBadshah').classList.toggle('on',m==='badshah'); $('modeRani').classList.toggle('on',m==='rani');
  const nm=m==='rani'?'RANI':'BADSHAH'; $('whoImg').src=AV[m]; $('whoName').textContent=nm; $('stageName').textContent=nm+(tempChat?' · temp':''); $('modeFlipTxt').textContent=m==='rani'?'BADSHAH se baat karo':'RANI se baat karo';
  orb.setPortrait(AV[m],m==='badshah'); setBaseMood(); orb.setBase(m==='rani'?'love':'calm'); orb.setMood(baseMood); titleFor(m); setComposeMode(composeMode); renderMsgs();
};
$('modeBadshah').onclick=()=>setMode('badshah'); $('modeRani').onclick=()=>setMode('rani');

/* ---------------- device control ---------------- */
let devTarget='phone';
document.querySelectorAll('#devSeg button').forEach(b=>b.onclick=()=>{ devTarget=b.dataset.t; document.querySelectorAll('#devSeg button').forEach(x=>x.classList.toggle('on',x===b)); renderDevChips(); loadDev(); });
function renderDevChips(){ const L=devTarget==='phone'?['WhatsApp kholo','YouTube kholo','Torch on karo','Volume kam karo','Screenshot lo']:['Chrome kholo','YouTube kholo','Volume kam karo','Screenshot lo','Computer lock karo']; $('devChips').innerHTML=''; L.forEach(t=>{ const b=document.createElement('button'); b.textContent=t; b.onclick=()=>{ $('devCmd').value=t; }; $('devChips').appendChild(b); }); }
async function loadDev(){
  try{ const r=await fetch('/api/device',{method:'POST',headers:{'Content-Type':'application/json',...(await authHeaders())},body:JSON.stringify({action:'list'})}); const d=await r.json();
    const rows=(d.rows||[]).filter(x=>x.target===devTarget); $('devList').innerHTML=rows.length?rows.map(x=>'<div><span>'+escH(x.command)+'</span><small>'+escH(x.status)+'</small></div>').join(''):'<small style="color:var(--mut)">Abhi koi command nahi bheji.</small>'; }catch(e){}
}
$('devSend').onclick=async()=>{
  const c=$('devCmd').value.trim(); if(!c) return;
  if(!(auth&&auth.currentUser)) return toast('Pehle login karo');
  try{ const r=await fetch('/api/device',{method:'POST',headers:{'Content-Type':'application/json',...(await authHeaders())},body:JSON.stringify({command:c,target:devTarget})}); const d=await r.json();
    if(d.ok){ toast('✅ Command queue mein chali gayi'); $('devCmd').value=''; loadDev(); } else toast('⚠️ '+(d.error||'Command nahi gayi')); }catch(e){ toast('⚠️ Network masla'); }
};
$('btnPc').onclick=()=>{ openPanel('pc'); renderDevChips(); loadDev(); };

/* ---------------- config (admin controlled) ---------------- */
function applyCfg(c){
  c=c||{}; const f=c.features||{};
  document.querySelectorAll('[data-cfg-feature]').forEach(el=>{ el.style.display=(f[el.getAttribute('data-cfg-feature')]===false)?'none':''; });
  if(c.app&&c.app.welcome) $('stageSub').textContent=c.app.welcome;
  if(c.app&&Array.isArray(c.app.cards)&&c.app.cards.length) buildCards(c.app.cards);
  if(c.apk&&c.apk.url) $('apkBtn').href=c.apk.url;
  if(c.app&&c.app.defaultModel&&!localStorage.getItem('ba_model')&&[...$('modelSel').options].some(o=>o.value===c.app.defaultModel)){ $('modelSel').value=c.app.defaultModel; $('modelSel').dispatchEvent(new Event('change')); chipTxt(); }
}
window.addEventListener('bh:config',e=>applyCfg(e.detail)); applyCfg(window.BH_CONFIG);

/* ---------------- user sync / block ---------------- */
function syncUser(u,login){
  if(!u||!u.getIdToken) return;
  u.getIdToken().then(t=>fetch('/api/profile-sync',{method:'POST',headers:{'Authorization':'Bearer '+t,'Content-Type':'application/json'},body:JSON.stringify({login:!!login,name:u.displayName||''})}))
   .then(r=>r.json()).then(d=>{ if(d.blocked){ $('blockedView').classList.remove('hidden'); try{ auth.signOut(); }catch(e){} } if(d.isNew&&window.BHTrack) BHTrack.event('signup'); }).catch(()=>{});
  if(window.BHTrack) BHTrack.setUser(u);
}
if(typeof auth!=='undefined'&&auth){
  auth.onAuthStateChanged(u=>{ if(u){ syncUser(u,true); setTimeout(()=>syncUser(auth.currentUser,false),3500); } else if(window.BHTrack) BHTrack.setUser(null); });
  setInterval(()=>{ if(auth.currentUser&&!document.hidden) syncUser(auth.currentUser,false); },240000);
}
const _sa=window.showAuth; window.showAuth=function(){ _sa(); };
window.addEventListener('online',()=>toast('Internet wapas aa gaya ✓')); window.addEventListener('offline',()=>toast('Internet band hai'));

/* ---------------- init ---------------- */
setBaseMood(); orb.setMood(baseMood); titleFor(state.mode);
const _ra=window.renderAll; window.renderAll=function(){ setMode(state.mode); buildModels(); renderChats(); renderMsgs(); };
renderAll(); syncComposer(); autosize();
})();
