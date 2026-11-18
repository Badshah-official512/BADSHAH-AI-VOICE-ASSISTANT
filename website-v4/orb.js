/* BADSHAH living logo orb.
   States: idle (calm breathing) · listening (user typing/talking) · thinking · speaking.
   Moods tint the aura from what the user writes: calm, happy, love, sad, angry, curious.
   API: const o = new BadshahOrb(hostEl); o.setState(s); o.setMood(m); o.pulse(x);
        o.say(text) -> drives mouth-like level from text; o.useLevel(fn) -> real audio level (0..1). */
(function(){
const MOODS={
  calm:[46,230,166], happy:[255,201,77], love:[255,107,157],
  sad:[91,141,239], angry:[255,77,94], curious:[56,189,248]
};
const WORDS={
  love:/(pyar|pyaar|ishq|mohabbat|jaan|love|miss you|dil\b|shona|baby|❤|💜|😍|🥰)/i,
  sad:/(udaas|udas|sad\b|rona|ro raha|ro rahi|dukh|ghum|gham|tanha|akela|alone|depress|cry|tension|pareshan|😢|😭|💔)/i,
  angry:/(gussa|ghussa|angry|hate|nafrat|bakwas|stupid|pagal|fuck|idiot|😡|🤬)/i,
  happy:/(khush|happy|haha|lol|mubarak|mubarik|great|mast|zabardast|awesome|congrat|party|shukriya|thanks|thank you|😂|😊|😄|🎉)/i,
  curious:/(code|error|bug|website|api|python|javascript|html|css|react|server|database|software|script)/i
};
function detectMood(t){
  if(!t) return 'calm';
  for(const k of ['love','sad','angry','happy','curious']) if(WORDS[k].test(t)) return k;
  return 'calm';
}
const lerp=(a,b,t)=>a+(b-a)*t;

class BadshahOrb{
  constructor(host,opt={}){
    this.host=host; this.opt=opt;
    host.innerHTML='';
    this.el=document.createElement('div'); this.el.className='bhorb'; this.el.dataset.state='idle';
    this.cv=document.createElement('canvas');
    this.d3=document.createElement('div'); this.d3.className='orb-3d';
    this.img=document.createElement('img'); this.img.className='orb-logo'; this.img.alt='BADSHAH AI'; this.img.draggable=false;
    this.img.src=opt.logo||'assets/logo-chrome.png';
    this.sheen=document.createElement('div'); this.sheen.className='orb-sheen';
    this.d3.append(this.img,this.sheen);
    if(opt.portrait){ // talking portrait: photo in a circle, jaw + mouth move with the voice level
      this.face=document.createElement('canvas'); this.face.className='orb-face'; this.fctx=this.face.getContext('2d');
      this.mk=document.createElement('canvas'); this.mctx=this.mk.getContext('2d'); this.blink=0; this.nextBlink=2+Math.random()*3;
      this.jaw=true; this.pimg=new Image(); this.pimg.onload=()=>{ this.pready=true; }; this.pimg.src=opt.portrait;
      this.img.style.display='none'; this.d3.insertBefore(this.face,this.sheen);
    }
    this.el.append(this.cv,this.d3); host.append(this.el);
    this.ctx=this.cv.getContext('2d');
    this.state='idle'; this.mood='calm'; this.base='calm';
    this.col=MOODS.calm.slice(); this.tcol=MOODS.calm.slice();
    this.level=0; this.kick=0; this.ext=null; this.synth=0; this.t=0;
    this.N=72; this.ph=Array.from({length:this.N},()=>Math.random()*6.28);
    this.parts=Array.from({length:44},()=>({a:Math.random()*6.28,r:1.05+Math.random()*.9,s:(.15+Math.random()*.5)*(Math.random()<.5?-1:1),z:.6+Math.random()*1.6}));
    this.ripples=[]; this.px=0; this.py=0; this.tx=0; this.ty=0; this.visible=true;
    this._resize=()=>{ const r=host.getBoundingClientRect(); const dpr=Math.min(2,devicePixelRatio||1);
      this.S=Math.max(80,Math.min(r.width,r.height)||200); this.cv.width=this.cv.height=Math.round(this.S*dpr); this.dpr=dpr; if(this.face){ this.face.width=this.face.height=Math.round(this.S*.64*dpr); this.mk.width=this.face.width; this.mk.height=Math.round(this.face.height*.2); } };
    this._resize();
    if('ResizeObserver' in window){ new ResizeObserver(this._resize).observe(host); } else addEventListener('resize',this._resize);
    this._move=e=>{ const r=host.getBoundingClientRect(); const cx=r.left+r.width/2, cy=r.top+r.height/2;
      this.tx=Math.max(-1,Math.min(1,(e.clientX-cx)/(innerWidth*.5))); this.ty=Math.max(-1,Math.min(1,(e.clientY-cy)/(innerHeight*.5))); };
    addEventListener('pointermove',this._move,{passive:true});
    if('IntersectionObserver' in window) new IntersectionObserver(es=>{this.visible=es[0].isIntersecting;}).observe(host);
    this.el.addEventListener('pointerdown',()=>{ this.pulse(1); this.ripple(); });
    this.last=performance.now(); requestAnimationFrame(t=>this.loop(t));
  }
  setState(s){ if(this.state===s) return; this.state=s; this.el.dataset.state=s; if(s==='thinking') this.ripple(); if(this.onState) this.onState(s); }
  setPortrait(src,jaw){ if(!this.face) return; this.jaw=jaw!==false; this.pready=false; const im=new Image(); im.onload=()=>{ this.pimg=im; this.pready=true; }; im.src=src; }
  setMood(m){ this.mood=MOODS[m]?m:'calm'; this.tcol=MOODS[this.mood].slice(); }
  setBase(m){ this.base=m; if(this.mood==='calm') this.setMood(m); }
  pulse(x=.6){ this.kick=Math.min(1.4,this.kick+x); }
  ripple(){ this.ripples.push({t:0}); if(this.ripples.length>5) this.ripples.shift(); }
  useLevel(fn){ this.ext=fn; }
  /* synthetic "mouth" while text is revealed / spoken */
  say(chunk){ for(const ch of chunk){ this.synth=/[aeiouAEIOU]/.test(ch)?.95:/\s/.test(ch)?.12:/[.,!?;:]/.test(ch)?0:.55; } }
  loop(now){
    requestAnimationFrame(t=>this.loop(t));
    const dt=Math.min(.05,(now-this.last)/1000); this.last=now; if(!this.visible||document.hidden) return;
    this.t+=dt; const t=this.t;
    for(let i=0;i<3;i++) this.col[i]=lerp(this.col[i],this.tcol[i],.06);
    this.kick*=Math.pow(.02,dt);
    this.synth*=Math.pow(.004,dt);
    let target=.07+.03*Math.sin(t*1.4);
    if(this.state==='listening') target=.2+this.kick*.55;
    else if(this.state==='thinking') target=.3+.08*Math.sin(t*5);
    else if(this.state==='speaking') target=this.ext?this.ext():Math.max(.18,this.synth*.9);
    target+=this.kick*.25;
    this.level+=(Math.min(1.2,target)-this.level)*Math.min(1,dt*14);
    const lv=this.level;
    this.px+=(this.tx-this.px)*.08; this.py+=(this.ty-this.py)*.08;
    // 3D logo tilt + breathing
    const typeTilt=this.state==='listening'?-6:0;
    const rx=-this.py*14+typeTilt+Math.sin(t*.9)*2, ry=this.px*18+Math.cos(t*.7)*3;
    const sc=1+lv*.07+(this.state==='idle'?Math.sin(t*1.3)*.012:0);
    this.d3.style.transform=`rotateX(${rx.toFixed(2)}deg) rotateY(${ry.toFixed(2)}deg) scale(${sc.toFixed(3)})`;
    const c=this.col.map(Math.round).join(',');
    this.el.style.setProperty('--mc',c); this.el.style.setProperty('--lv',lv.toFixed(3));
    this.el.style.setProperty('--sx',(35+this.px*25).toFixed(1)+'%'); this.el.style.setProperty('--sy',(28+this.py*25).toFixed(1)+'%');
    (this.opt.varTarget||this.host).style.setProperty('--mc',c);
    this.draw(lv,c);
    if(this.face&&this.pready) this.drawFace(lv,t,dt);
  }
  drawFace(lv,t,dt){
    const g=this.fctx,S=this.face.width,P=this.pimg;
    // crop head+shoulders from the 720px portrait
    const k=P.naturalWidth/800; let sx=140*k, sy=0, sw=560*k;
    if(!this.jaw){ const w=P.naturalWidth,h=P.naturalHeight,m=Math.min(w,h); sx=(w-m)/2; sy=0; sw=m; }
    g.clearRect(0,0,S,S);
    const open=this.jaw?Math.max(0,Math.min(1,(lv-.1)*1.25)):0;
    g.drawImage(P,sx,sy,sw,sw,0,0,S,S);
    if(open>.02){
      // lower-face slab drops with the jaw (feathered so no seam shows)
      const x0=.29*S,x1=.71*S,y0=.585*S,y1=.715*S,w=x1-x0,h=y1-y0; const m=this.mctx;
      this.mk.width=Math.round(w); this.mk.height=Math.round(h);
      m.clearRect(0,0,w,h); m.drawImage(P,sx+(.29)*sw,sy+(.585)*sw,.42*sw,.13*sw,0,0,w,h);
      m.globalCompositeOperation='destination-in';
      const rg=m.createRadialGradient(w/2,h*.45,Math.min(w,h)*.2,w/2,h*.5,w*.52); rg.addColorStop(0,'rgba(0,0,0,1)'); rg.addColorStop(.45,'rgba(0,0,0,.9)'); rg.addColorStop(.8,'rgba(0,0,0,0)');
      m.fillStyle=rg; m.fillRect(0,0,w,h); m.globalCompositeOperation='source-over';
      const dy=open*S*.0075;
      g.drawImage(this.mk,x0,y0+dy,w,h*(1+open*.02));
      // soft dark mouth opening between the lips
      const cx=.496*S,cy=.6*S+dy*.4,rx=S*.04*(.75+open*.25),ry=S*.0085*open;
      g.save(); g.filter='blur('+(S*.004).toFixed(1)+'px)'; g.fillStyle='rgba(70,12,18,'+(.42*open).toFixed(2)+')';
      g.beginPath(); g.ellipse(cx,cy,rx,ry,0,0,6.283); g.fill(); g.restore();
    }
    // natural idle motion: breathing sway + tiny nod on speech
    this.blink+=dt; 
    const sway=Math.sin(t*.8)*.9+(this.state==='speaking'?Math.sin(t*5.3)*lv*1.1:0);
    this.face.style.transform=`translateZ(46px) rotate(${sway.toFixed(2)}deg) translateY(${(Math.sin(t*1.1)*1.2+lv*1.5).toFixed(2)}px) scale(${(1+lv*.018).toFixed(3)})`;
  }
  draw(lv,c){
    const g=this.ctx,S=this.cv.width,m=S/2,u=S/this.S, R0=S*.355, st=this.state, t=this.t;
    g.clearRect(0,0,S,S);
    // aura
    const ag=g.createRadialGradient(m,m,S*.1,m,m,S*(.5));
    ag.addColorStop(0,`rgba(${c},${.22+lv*.38})`); ag.addColorStop(.55,`rgba(${c},${.06+lv*.12})`); ag.addColorStop(1,`rgba(${c},0)`);
    g.fillStyle=ag; g.fillRect(0,0,S,S);
    // ripples
    for(let i=this.ripples.length-1;i>=0;i--){ const r=this.ripples[i]; r.t+=.016; const k=r.t/1.4; if(k>1){this.ripples.splice(i,1);continue;}
      g.strokeStyle=`rgba(${c},${(1-k)*.55})`; g.lineWidth=2*u; g.beginPath(); g.arc(m,m,R0*(.9+k*.75),0,6.283); g.stroke(); }
    if(st==='listening'&&Math.floor(t*1.2)!==Math.floor((t-.017)*1.2)) this.ripple();
    // base ring
    g.strokeStyle=`rgba(${c},${.18+lv*.25})`; g.lineWidth=1.2*u; g.beginPath(); g.arc(m,m,R0*.97,0,6.283); g.stroke();
    // thinking arcs
    if(st==='thinking'){
      for(let k=0;k<3;k++){ const a0=t*(2.4+k*.7)*(k%2?-1:1)+k*2.1; g.strokeStyle=`rgba(${c},${.85-k*.2})`; g.lineWidth=(3-k*.6)*u; g.lineCap='round';
        g.beginPath(); g.arc(m,m,R0*(1.02+k*.07),a0,a0+1.15); g.stroke(); }
    }
    // equaliser ring
    const N=this.N; g.lineCap='round';
    for(let i=0;i<N;i++){
      const a=i/N*6.283-1.5708; let amp;
      const w=.5+.5*Math.sin(i*.55+t*(st==='speaking'?9:3)+this.ph[i]);
      if(st==='speaking') amp=lv*(.25+.75*w);
      else if(st==='listening') amp=lv*.55*(.3+.7*Math.abs(Math.sin(i*.35-t*4)))+this.kick*.25*w;
      else if(st==='thinking') amp=.08+.05*w;
      else amp=.03+.025*Math.sin(i*.4+t*1.2);
      const len=(S*.012)+amp*S*.12, r1=R0*1.04, r2=r1+len;
      g.strokeStyle=`rgba(${c},${.35+Math.min(.65,amp*2.2)})`; g.lineWidth=Math.max(1.6,S*.0085);
      g.beginPath(); g.moveTo(m+Math.cos(a)*r1,m+Math.sin(a)*r1); g.lineTo(m+Math.cos(a)*r2,m+Math.sin(a)*r2); g.stroke();
    }
    // particles
    const spd=st==='thinking'?3.2:st==='speaking'?1.6:st==='listening'?1.2:.6;
    for(const p of this.parts){
      p.a+=p.s*spd*.016; const rr=R0*(p.r+lv*.35*p.z); const x=m+Math.cos(p.a)*rr, y=m+Math.sin(p.a)*rr;
      g.fillStyle=`rgba(${c},${.25+lv*.5})`; g.beginPath(); g.arc(x,y,(1.1+p.z*.9)*u,0,6.283); g.fill();
    }
  }
}
BadshahOrb.detectMood=detectMood; BadshahOrb.MOODS=MOODS;
window.BadshahOrb=BadshahOrb;
})();
