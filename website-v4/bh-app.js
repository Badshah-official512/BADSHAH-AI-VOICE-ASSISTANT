/* BADSHAH app — living logo layer (v4). Sits on top of app.html; backend and existing logic untouched. */
(function(){
const detect=BadshahOrb.detectMood;
const inp=$('input');
const orb=new BadshahOrb($('orbHost'),{varTarget:$('chatPanel')});
let authOrb=null; try{ authOrb=new BadshahOrb($('authOrb')); }catch(e){}

const STATUS={idle:'Online — sun raha hoon',listening:'Sun raha hoon…',thinking:'Soch raha hoon…',speaking:'Bol raha hoon…'};
const LIVE={idle:'Type karo ya 🗣️ dabao — logo sunta bhi hai, bolta bhi.',listening:'Tumhari baat dhyan se sun raha hoon 👂',thinking:'Jawab tayyar ho raha hai…',speaking:'Jawab de raha hoon 💬'};
orb.onState=s=>{ $('orbStatusTxt').textContent=STATUS[s]; $('stageLive').textContent=LIVE[s]; };

let idleT=null, speakingNow=false, voice=false, rec=null, curAudio=null, ac=null, baseMood='calm';
const setBaseMood=()=>{ baseMood=state.mode==='rani'?'love':'calm'; };
const toIdle=()=>{ if(!speakingNow&&!state.busy&&!rec) orb.setState('idle'); };

/* ---------- empty-state suggestions ---------- */
const CHIPS=['Meri website ke liye ek acha naam batao','Ek chhoti si kahani sunao 📖','Python mein calculator banao','Udaas hoon, thodi baat karo 💜','Ek zabardast image banao 🎨'];
CHIPS.forEach(t=>{ const b=document.createElement('button'); b.textContent=t; b.onclick=()=>{ inp.value=t; BH.typed(); send(); }; $('stageChips').appendChild(b); });

function titleFor(m){
  $('stageTitle').innerHTML = m==='rani' ? 'Hi jaan! Main <span class="grad">RANI</span> hoon 💜' : 'Assalam o Alaikum! <span class="grad">BADSHAH</span> yahan hai 👑';
}

/* ---------- typewriter reveal + talking logo ---------- */
function reveal(el,text,done){
  const box=el.querySelector('.txt'); if(!box){ done&&done(); return; }
  const acts=el.querySelector('.acts'); if(acts) acts.style.visibility='hidden';
  const total=text.length; let i=0; const t0=performance.now();
  const dur=Math.min(5200,Math.max(900,total*22));
  orb.setState('speaking'); orb.ripple();
  (function tick(now){
    const target=Math.min(total,Math.ceil(((now-t0)/dur)*total)); if(target>i){ orb.say(text.slice(i,target)); i=target; }
    let part=text.slice(0,i); const open=(part.match(/```/g)||[]).length%2; if(open) part+='\n```';
    box.innerHTML=md(part)+(i<total?'<span class="caret"></span>':'');
    $('msgs').scrollTop=$('msgs').scrollHeight;
    if(i<total) requestAnimationFrame(tick); else { if(acts) acts.style.visibility=''; done&&done(); }
  })(t0);
}

/* ---------- TTS with real audio level driving the logo ---------- */
window.speak=function(text){
  return new Promise(async res=>{
    const clean=String(text).replace(/[*#`]/g,'').slice(0,600); if(!clean){ res(); return; }
    stopSpeak(); speakingNow=true; let fin=false; let jit=null;
    const done=()=>{ if(fin) return; fin=true; clearInterval(jit); orb.useLevel(null); speakingNow=false; curAudio=null; toIdle(); res(); };
    orb.setState('speaking');
    try{
      const r=await fetch(CFG.ttsUrl,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({text:clean,voice:state.mode})});
      if(r.ok){
        const b=await r.blob(); const a=new Audio(URL.createObjectURL(b)); curAudio=a;
        try{ const AC=window.AudioContext||window.webkitAudioContext; ac=ac||new AC(); if(ac.state==='suspended') await ac.resume();
          const src=ac.createMediaElementSource(a), an=ac.createAnalyser(); an.fftSize=256; src.connect(an); an.connect(ac.destination);
          const buf=new Uint8Array(an.frequencyBinCount);
          orb.useLevel(()=>{ an.getByteFrequencyData(buf); let s=0; for(let k=2;k<40;k++) s+=buf[k]; return Math.max(.12,Math.min(1,s/38/140)); });
        }catch(e){}
        a.onended=done; a.onerror=done; await a.play(); return;
      }
    }catch(e){}
    try{
      const u=new SpeechSynthesisUtterance(clean); const vs=speechSynthesis.getVoices(); const v=vs.find(v=>/ur|hi/i.test(v.lang))||vs[0]; if(v) u.voice=v;
      u.onend=done; u.onerror=done; speechSynthesis.cancel(); speechSynthesis.speak(u);
      jit=setInterval(()=>orb.say(Math.random()<.35?' ':'ae'),95);
    }catch(e){ done(); }
  });
};
function stopSpeak(){ try{ if(curAudio){ curAudio.pause(); curAudio=null; } speechSynthesis.cancel(); }catch(e){} speakingNow=false; orb.useLevel(null); }

/* ---------- voice input ---------- */
function listenOnce(autoSend){
  const SR=window.SpeechRecognition||window.webkitSpeechRecognition;
  if(!SR){ alert('Is browser mein voice input nahi hai — Chrome use karo.'); voice=false; $('btnVoice').classList.remove('on'); return; }
  if(rec||state.busy) return; stopSpeak();
  const r=new SR(); rec=r; r.lang='ur-PK'; r.interimResults=true; r.continuous=false;
  orb.setState('listening'); orb.ripple();
  r.onresult=e=>{ let t=''; for(const x of e.results) t+=x[0].transcript; inp.value=t; orb.pulse(.55); orb.setMood(detect(t));
    if(e.results[e.results.length-1].isFinal){ r.stop(); if(autoSend) send(); } };
  r.onerror=()=>{};
  r.onend=()=>{ rec=null; if(voice&&!state.busy&&!speakingNow&&!inp.value.trim()) setTimeout(()=>listenOnce(true),350); else if(!state.busy&&!speakingNow) toIdle(); };
  try{ r.start(); }catch(e){ rec=null; }
}
$('btnMic').onclick=()=>{ if(rec){ try{rec.stop();}catch(e){} return; } listenOnce(false); };
$('btnVoice').onclick=()=>{
  voice=!voice; $('btnVoice').classList.toggle('on',voice);
  $('compHint').textContent=voice?'🗣️ Voice mode ON — bolo, BADSHAH sunega aur bol kar jawab dega. Band karne ke liye dobara 🗣️ dabao.':'Sab kuch FREE hai 👑 — saare models, images aur calls. Koi key, koi payment nahi.';
  if(voice) listenOnce(true); else { if(rec){ try{rec.stop();}catch(e){} } stopSpeak(); toIdle(); }
};

/* ---------- public hooks used by send() ---------- */
window.BH={
  sync(){ const has=!!(chat()&&chat().msgs.length); $('chatPanel').classList.toggle('has-msgs',has); titleFor(state.mode); },
  typed(){
    const v=inp.value; clearTimeout(idleT);
    if(!v.trim()){ orb.setMood(baseMood); orb.setState('idle'); return; }
    if(state.busy) return;
    orb.setState('listening'); orb.pulse(.42);
    const m=detect(v); orb.setMood(m==='calm'?baseMood:m);
    idleT=setTimeout(()=>{ if(!state.busy&&!rec) orb.setState('idle'); },1600);
  },
  onSend(text){
    clearTimeout(idleT); stopSpeak(); BH.sync();
    const m=detect(text); orb.setMood(m==='calm'?baseMood:m); orb.setState('thinking');
  },
  onReply(el,reply){
    const rm=detect(reply); if(rm!=='calm'&&rm!=='curious') orb.setMood(rm);
    const finish=()=>{ if(voice){ speak(reply).then(()=>{ if(voice) setTimeout(()=>{ inp.value=''; listenOnce(true); },250); }); } else { toIdle(); } setTimeout(()=>{ if(!speakingNow&&!state.busy&&!inp.value.trim()) orb.setMood(baseMood); },9000); };
    reveal(el,reply,finish);
  },
  onError(){ orb.setMood('angry'); orb.setState('idle'); setTimeout(()=>orb.setMood(baseMood),2500); if(voice) setTimeout(()=>listenOnce(true),1200); }
};

/* ---------- wiring into existing functions ---------- */
inp.addEventListener('input',BH.typed);
inp.addEventListener('blur',()=>{ if(!state.busy&&!rec) setTimeout(toIdle,400); });
const _rm=renderMsgs; renderMsgs=function(){ _rm(); BH.sync(); };
const _sm=setMode; setMode=function(m){ _sm(m); setBaseMood(); orb.setBase(m==='rani'?'love':'calm'); orb.setMood(baseMood); titleFor(m); };
setBaseMood(); orb.setMood(baseMood); BH.sync();
})();
