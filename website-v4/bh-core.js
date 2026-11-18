/* BADSHAH core (v6): live site config from the admin panel + visitor analytics.
   - GET /api/config  -> announcement bar, maintenance screen, feature flags, texts, socials, accent, Google Analytics id
   - POST /api/track  -> sessions / page views / heartbeat (own analytics, stored in Supabase)
   Every call fails soft: if the backend or migration 006 is missing the site still works normally. */
(function(){
  if(window.__bhCore) return; window.__bhCore=true;
  var LS=function(k,v){ try{ if(v===undefined) return localStorage.getItem(k); localStorage.setItem(k,v); }catch(e){ return null; } };
  var SS=function(k,v){ try{ if(v===undefined) return sessionStorage.getItem(k); sessionStorage.setItem(k,v); }catch(e){ return null; } };
  var rid=function(){ return (Math.random().toString(36).slice(2,10)+Date.now().toString(36)).slice(0,18); };
  var isAdmin=/^\/admin/.test(location.pathname);

  /* ---------------- config ---------------- */
  var cfg={}; try{ cfg=JSON.parse(LS('bh_cfg')||'{}')||{}; }catch(e){}
  window.BH_CONFIG=cfg;
  function get(o,p){ return p.split('.').reduce(function(a,k){ return a==null?undefined:a[k]; },o); }
  function bar(c){
    var a=c.announcement; var old=document.getElementById('bhBar'); if(old) old.remove();
    if(!a||!a.enabled||!a.text) return;
    var key='bh_bar_'+(a.text+'').length+'_'+(a.text+'').charCodeAt(0); if(LS(key)==='1') return;
    var d=document.createElement('div'); d.id='bhBar';
    var kind=a.type==='warn'?'#FFC94D':a.type==='danger'?'#FF5C6C':'#2EE6A6';
    d.style.cssText='position:fixed;left:0;right:0;top:0;z-index:2147483000;display:flex;gap:12px;align-items:center;justify-content:center;padding:9px 44px 9px 16px;background:linear-gradient(90deg,rgba(8,32,26,.96),rgba(13,42,34,.96));border-bottom:1px solid '+kind+';color:#EAFBF4;font:600 13.5px Inter,system-ui,sans-serif;text-align:center;backdrop-filter:blur(10px)';
    var t=document.createElement('span'); t.textContent=a.text; d.appendChild(t);
    if(a.link){ var l=document.createElement('a'); l.href=a.link; l.textContent=(a.linkText||'Dekho')+' →'; l.style.cssText='color:'+kind+';text-decoration:underline'; d.appendChild(l); }
    var x=document.createElement('button'); x.textContent='✕'; x.setAttribute('aria-label','Close'); x.style.cssText='position:absolute;right:12px;top:50%;transform:translateY(-50%);background:none;border:0;color:#EAFBF4;font-size:15px;cursor:pointer';
    x.onclick=function(){ LS(key,'1'); d.remove(); document.documentElement.style.setProperty('--bar-h','0px'); };
    d.appendChild(x); document.body.appendChild(d); document.documentElement.style.setProperty('--bar-h','40px');
  }
  function maintenance(c){
    var m=c.maintenance; var old=document.getElementById('bhMaint'); if(old) old.remove();
    if(!m||!m.enabled||isAdmin) return;
    var d=document.createElement('div'); d.id='bhMaint';
    d.style.cssText='position:fixed;inset:0;z-index:2147483600;display:grid;place-items:center;padding:24px;background:radial-gradient(120% 90% at 50% 0%,#0f3a2e,#06130f);color:#EAFBF4;font-family:Inter,system-ui,sans-serif;text-align:center';
    d.innerHTML='<div style="max-width:460px"><img src="'+(location.pathname.indexOf('/admin')===0?'../':'')+'assets/logo-poster.jpg" alt="" style="width:132px;height:132px;border-radius:30px;box-shadow:0 0 60px rgba(46,230,166,.35)"><h1 style="font:700 28px \'Space Grotesk\',Inter,sans-serif;margin-top:22px">🛠️ Maintenance</h1><p style="margin-top:12px;line-height:1.7;color:#8FB5A7;font-size:16px"></p></div>';
    d.querySelector('p').textContent=m.message||'BADSHAH AI ko behtar bana rahe hain — thodi dair mein wapas aao.';
    document.body.appendChild(d);
  }
  function binds(c){
    document.querySelectorAll('[data-cfg]').forEach(function(el){ var v=get(c,el.getAttribute('data-cfg')); if(typeof v==='string'&&v.trim()) el.textContent=v; });
    document.querySelectorAll('[data-cfg-html]').forEach(function(el){ var v=get(c,el.getAttribute('data-cfg-html')); if(typeof v==='string'&&v.trim()) el.innerHTML=v.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/\*\*([^*]+)\*\*/g,'<b>$1</b>'); });
    document.querySelectorAll('[data-cfg-href]').forEach(function(el){ var v=get(c,el.getAttribute('data-cfg-href')); if(typeof v==='string'&&v.trim()) el.setAttribute('href',v); });
    document.querySelectorAll('[data-cfg-feature]').forEach(function(el){ var v=get(c,'features.'+el.getAttribute('data-cfg-feature')); el.style.display=(v===false)?'none':''; });
    if(c.brand&&c.brand.name){ document.querySelectorAll('[data-brand]').forEach(function(el){ el.textContent=c.brand.name; }); }
    var ac=c.theme&&c.theme.accent; if(ac&&/^#[0-9a-f]{6}$/i.test(ac)){ var r=document.documentElement.style; r.setProperty('--violet',ac); r.setProperty('--accent',ac); }
  }
  function ga(c){
    var id=c.ga_id; if(!id||!/^G-[A-Z0-9]{6,14}$/i.test(id)||isAdmin||window.__bhGa) return; window.__bhGa=1;
    var s=document.createElement('script'); s.async=true; s.src='https://www.googletagmanager.com/gtag/js?id='+id; document.head.appendChild(s);
    window.dataLayer=window.dataLayer||[]; window.gtag=function(){ window.dataLayer.push(arguments); }; gtag('js',new Date()); gtag('config',id);
  }
  function apply(c){ window.BH_CONFIG=c; try{ bar(c); maintenance(c); binds(c); ga(c); }catch(e){} try{ window.dispatchEvent(new CustomEvent('bh:config',{detail:c})); }catch(e){} }
  function boot(){ apply(cfg); }
  if(document.readyState==='loading') document.addEventListener('DOMContentLoaded',boot); else boot();
  fetch('/api/config').then(function(r){ return r.json(); }).then(function(d){ if(d&&d.config){ cfg=d.config; LS('bh_cfg',JSON.stringify(cfg)); apply(cfg); } }).catch(function(){});

  /* ---------------- analytics ---------------- */
  if(isAdmin) return;
  var vid=LS('bh_vid'), isNew=!vid; if(!vid){ vid=rid(); LS('bh_vid',vid); }
  var sid=SS('bh_sid'), started=!!sid; if(!sid){ sid=rid(); SS('bh_sid',sid); }
  var n=parseInt(SS('bh_n')||'0',10), user={};
  function send(type,extra){
    var b=Object.assign({sid:sid,vid:vid,type:type,path:location.pathname+(location.hash&&location.pathname==='/app.html'?'':''),title:document.title,n:n},user,extra||{});
    var s=JSON.stringify(b);
    try{ if(navigator.sendBeacon && navigator.sendBeacon('/api/track',s)) return; }catch(e){}
    try{ fetch('/api/track',{method:'POST',body:s,keepalive:true}).catch(function(){}); }catch(e){}
  }
  n++; SS('bh_n',String(n));
  send(started?'pageview':'start',{ref:document.referrer,isNew:isNew});
  setInterval(function(){ if(!document.hidden) send('heartbeat'); },30000);
  document.addEventListener('visibilitychange',function(){ if(!document.hidden) send('heartbeat'); });
  window.BHTrack={
    setUser:function(u){ user=u?{uid:u.uid,email:u.email,name:u.displayName||u.name}:{}; send('heartbeat'); },
    event:function(name,meta){ send('event',{name:name,meta:meta||null}); }
  };
})();
