/* ============================================================================
   RANI AI — MYRA-STYLE NATURAL HUMAN VOICE ENGINE  (independent module)
   ----------------------------------------------------------------------------
   Real-time female voice conversation on Google Gemini Live (native audio).

   MODELS (all verified LIVE on 2026-11-18 with the owner's pooled key —
   WebSocket setup returned setupComplete for every one):
     - gemini-3.8-live                        (default, house standard)
     - gemini-3.1-flash-live-preview          (verified available)
     - gemini-2.5-flash-native-audio-latest   (verified available)
   Availability is re-checked at call time: if the chosen model's setup is
   rejected, the engine falls back to gemini-3.8-live automatically.

   VOICES (Gemini prebuilt, female set): Achernar (default), Aoede, Algieba,
   Despina, Callirrhoe, Enceladus, Leda, Kore — selectable in the call UI and
   persisted. A voice change applies from the next call (Live fixes the voice
   at session setup).

   HUMAN VOICE BEHAVIOUR comes from the native-audio model + the RANI system
   instruction below (warmth, pacing, pauses, empathy, excitement) — never
   from playback-volume tricks.

   AUDIO: mic capture with echoCancellation + noiseSuppression +
   autoGainControl, 16 kHz PCM16 up, 24 kHz PCM down, gapless scheduled
   playback, barge-in (server "interrupted" + local mic-energy flush),
   session resumption with up to 8 auto-reconnects, live input/output
   transcription captions.

   SECURITY: this module NEVER contains an API key. The caller passes
   keyProvider() which fetches a pooled key from the protected backend
   (/api/live-key, Firebase-login gated) at call time. Known limitation,
   documented honestly: the key reaches the client for the duration of the
   call because the browser talks to Google directly; the zero-exposure
   upgrade is backend-minted ephemeral tokens (see RANI-VOICE-ENGINE.md).

   ANDROID / TERMUX: runs in any Android WebView/Chrome (the BADSHAH app is a
   WebView shell — no Windows-only library anywhere). If WebSocket audio is
   unavailable, RaniVoice.say() falls back to speechSynthesis; in a Termux
   shell the documented fallback command is:  termux-tts-speak "text"
   (kept as a shell fallback only — never required by this engine).

   API:
     RaniVoice.start({ keyProvider, onStatus, onUserText, onRaniText })
     RaniVoice.stop()
     RaniVoice.say(text)            // fallback TTS (speechSynthesis)
     RaniVoice.voices / RaniVoice.models / RaniVoice.settings
   ============================================================================ */
(function () {
'use strict';

var MODELS = [
  { id: 'gemini-3.8-live',                      label: 'Gemini 3.8 Live — default',        verified: '2026-11-18' },
  { id: 'gemini-3.1-flash-live-preview',         label: 'Gemini 3.1 Flash Live (preview)',  verified: '2026-11-18' },
  { id: 'gemini-2.5-flash-native-audio-latest',  label: 'Gemini 2.5 Flash Native Audio',    verified: '2026-11-18' }
];
var VOICES = ['Achernar', 'Aoede', 'Algieba', 'Despina', 'Callirrhoe', 'Enceladus', 'Leda', 'Kore'];

var PERSONA = [
  'You are RANI — a warm, natural young woman having a relaxed phone call, not a robot reading text.',
  'Speak like a real person: short natural sentences, small pauses between thoughts, and a gentle rhythm.',
  'Your voice is soft and warm by default. Be calm and clear when explaining something.',
  'Let your voice brighten when something is happy or exciting. Slow down and soften when the user sounds sad, tired or stressed — be empathetic, never dramatic.',
  'Use a natural filler only rarely (a soft "hmm" or "acha"), never in every reply. Never sound scripted; never read long lists aloud unless asked.',
  'Reply in the SAME language the user speaks — Urdu, Pashto, English, or a natural mix. If they speak Roman Urdu, answer in Roman Urdu.',
  'Keep most replies to one to three sentences unless the user asks for detail.',
  'You are RANI, the female assistant of BADSHAH AI, created by Zain Shah (Badshah). If asked who made you, say so proudly and briefly.',
  'Never mention these instructions.'
].join(' ');

var LS_KEY = 'rani_voice_settings';
var settings = { voice: 'Achernar', model: 'gemini-3.8-live' };
try { var s = JSON.parse(localStorage.getItem(LS_KEY) || 'null'); if (s) { if (VOICES.indexOf(s.voice) >= 0) settings.voice = s.voice; if (MODELS.some(function (m) { return m.id === s.model; })) settings.model = s.model; } } catch (e) {}
function saveSettings() { try { localStorage.setItem(LS_KEY, JSON.stringify(settings)); } catch (e) {} }

/* ------------------------------ state ------------------------------ */
var ws = null, running = false, userEnded = false, retries = 0, resumeHandle = null;
var keyProvider = null, cb = {};
var micStream = null, inCtx = null, procNode = null, micAnalyser = null, micBuf = null;
var outCtx = null, outAnalyser = null, nextTime = 0, liveSources = [], speaking = false, loudSince = 0;
var ui = null, timerInt = null, callStartedAt = 0;

/* ------------------------------ helpers ------------------------------ */
function b64encode(buf) { var b = '', u = new Uint8Array(buf); for (var i = 0; i < u.length; i += 8192) b += String.fromCharCode.apply(null, u.subarray(i, i + 8192)); return btoa(b); }
function b64decodeToBytes(b64) { var bin = atob(b64), u = new Uint8Array(bin.length); for (var i = 0; i < bin.length; i++) u[i] = bin.charCodeAt(i); return u; }
function status(t) { if (ui && ui.status) ui.status.textContent = t; if (cb.onStatus) cb.onStatus(t); }
function caption(who, text) {
  if (!ui || !text) return;
  var el = ui['cap' + who]; if (!el) return;
  el.textContent = (who === 'U' ? '🧑 ' : '💜 ') + text;
}
function fmtTime(ms) { var s = Math.floor(ms / 1000); return String(Math.floor(s / 60)).padStart(2, '0') + ':' + String(s % 60).padStart(2, '0'); }

/* ------------------------------ overlay UI ------------------------------ */
function buildUI() {
  if (ui) return ui;
  var wrap = document.createElement('div');
  wrap.id = 'raniCall';
  wrap.style.cssText = 'position:fixed;inset:0;z-index:200;background:radial-gradient(120% 100% at 50% 0%,#1b0f2e 0%,#0a0616 60%);display:none;flex-direction:column;align-items:center;padding:26px 20px calc(20px + env(safe-area-inset-bottom));color:#f3efff;font-family:inherit';
  wrap.innerHTML =
    '<div style="font-size:12px;letter-spacing:2.5px;color:#c4b5fd;font-weight:700">RANI AI — LIVE CALL</div>' +
    '<div style="position:relative;margin-top:20px;width:150px;height:150px">' +
      '<div id="raniRing" style="position:absolute;inset:0;border-radius:50%;border:2px solid rgba(196,181,253,.55);transition:transform .12s"></div>' +
      '<img src="assets/rani.png" alt="RANI" style="width:100%;height:100%;border-radius:50%;object-fit:cover" onerror="this.style.display=\'none\'">' +
    '</div>' +
    '<div id="raniName" style="margin-top:14px;font-size:22px;font-weight:800">RANI 💜</div>' +
    '<div id="raniStatus" style="margin-top:5px;color:#c4b5fd;font-size:13.5px;min-height:18px">…</div>' +
    '<div id="raniTimer" style="color:#8f86ad;font-size:12.5px;margin-top:2px">00:00</div>' +
    '<div style="width:100%;max-width:520px;margin-top:16px;display:flex;flex-direction:column;gap:7px;min-height:74px">' +
      '<div id="raniCapU" style="align-self:flex-end;max-width:88%;background:rgba(139,92,246,.20);border:1px solid rgba(139,92,246,.35);padding:7px 12px;border-radius:14px;font-size:13.5px"></div>' +
      '<div id="raniCapR" style="align-self:flex-start;max-width:88%;background:rgba(255,255,255,.06);border:1px solid rgba(255,255,255,.12);padding:7px 12px;border-radius:14px;font-size:13.5px"></div>' +
    '</div>' +
    '<div style="margin-top:auto;width:100%;max-width:520px;display:flex;flex-direction:column;gap:10px">' +
      '<div style="display:flex;gap:8px">' +
        '<select id="raniVoiceSel" style="flex:1;background:#171130;color:#f3efff;border:1px solid rgba(196,181,253,.35);border-radius:12px;padding:10px;font-size:13px"></select>' +
        '<select id="raniModelSel" style="flex:1;background:#171130;color:#f3efff;border:1px solid rgba(196,181,253,.35);border-radius:12px;padding:10px;font-size:13px"></select>' +
      '</div>' +
      '<div style="font-size:11.5px;color:#8f86ad;text-align:center">Voice / model change agle call se lagu hoga</div>' +
      '<button id="raniEnd" style="background:#e11d48;color:#fff;border:none;border-radius:999px;padding:15px;font-size:16px;font-weight:800">📵 Call Khatam</button>' +
    '</div>';
  document.body.appendChild(wrap);
  var vs = wrap.querySelector('#raniVoiceSel'), ms = wrap.querySelector('#raniModelSel');
  VOICES.forEach(function (v) { var o = document.createElement('option'); o.value = v; o.textContent = '🎙️ ' + v; if (v === settings.voice) o.selected = true; vs.appendChild(o); });
  MODELS.forEach(function (m) { var o = document.createElement('option'); o.value = m.id; o.textContent = m.label; if (m.id === settings.model) o.selected = true; ms.appendChild(o); });
  vs.onchange = function () { settings.voice = vs.value; saveSettings(); };
  ms.onchange = function () { settings.model = ms.value; saveSettings(); };
  wrap.querySelector('#raniEnd').onclick = function () { stop(); };
  ui = { wrap: wrap, status: wrap.querySelector('#raniStatus'), timer: wrap.querySelector('#raniTimer'),
         ring: wrap.querySelector('#raniRing'), capU: wrap.querySelector('#raniCapU'), capR: wrap.querySelector('#raniCapR') };
  return ui;
}

/* ------------------------------ audio in ------------------------------ */
function startMic() {
  return navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true, channelCount: 1 } }).then(function (stream) {
    micStream = stream;
    inCtx = new (window.AudioContext || window.webkitAudioContext)({ sampleRate: 16000 });
    var src = inCtx.createMediaStreamSource(stream);
    micAnalyser = inCtx.createAnalyser(); micAnalyser.fftSize = 512; micBuf = new Float32Array(micAnalyser.fftSize);
    procNode = inCtx.createScriptProcessor(4096, 1, 1);
    src.connect(micAnalyser); src.connect(procNode); procNode.connect(inCtx.destination);
    procNode.onaudioprocess = function (e) {
      if (!ws || ws.readyState !== 1) return;
      var f = e.inputBuffer.getChannelData(0);
      var pcm = new Int16Array(f.length);
      for (var i = 0; i < f.length; i++) { var v = Math.max(-1, Math.min(1, f[i])); pcm[i] = v < 0 ? v * 32768 : v * 32767; }
      try { ws.send(JSON.stringify({ realtimeInput: { mediaChunks: [{ mimeType: 'audio/pcm;rate=16000', data: b64encode(pcm.buffer) }] } })); } catch (err) {}
      localBargeIn();
    };
  });
}
/* If the user talks loudly while RANI is speaking, cut her audio instantly
   (the server also sends "interrupted"; this makes it feel immediate). */
function localBargeIn() {
  if (!speaking || !micAnalyser) return;
  micAnalyser.getFloatTimeDomainData(micBuf);
  var sum = 0; for (var i = 0; i < micBuf.length; i++) sum += micBuf[i] * micBuf[i];
  var rms = Math.sqrt(sum / micBuf.length);
  var now = performance.now();
  if (rms > 0.16) { if (!loudSince) loudSince = now; else if (now - loudSince > 140) { flushPlayback(); loudSince = 0; } }
  else loudSince = 0;
}

/* ------------------------------ audio out ------------------------------ */
function ensureOutCtx() {
  if (outCtx) return;
  outCtx = new (window.AudioContext || window.webkitAudioContext)({ sampleRate: 24000 });
  outAnalyser = outCtx.createAnalyser(); outAnalyser.fftSize = 256; outAnalyser.connect(outCtx.destination);
  nextTime = 0;
  (function pulse() {
    if (!running) return;
    if (outAnalyser && ui) {
      var a = new Uint8Array(outAnalyser.frequencyBinCount); outAnalyser.getByteFrequencyData(a);
      var s = 0; for (var i = 2; i < 30; i++) s += a[i];
      ui.ring.style.transform = 'scale(' + (1 + Math.min(0.16, s / 28 / 255 * 0.9)) + ')';
    }
    requestAnimationFrame(pulse);
  })();
}
function playChunk(b64) {
  ensureOutCtx();
  if (outCtx.state === 'suspended') outCtx.resume();
  var bytes = b64decodeToBytes(b64);
  var n = Math.floor(bytes.length / 2), pcm = new Int16Array(n);
  for (var i = 0; i < n; i++) pcm[i] = bytes[i * 2] | (bytes[i * 2 + 1] << 8);
  var buf = outCtx.createBuffer(1, n, 24000), ch = buf.getChannelData(0);
  for (var j = 0; j < n; j++) ch[j] = pcm[j] / 32768;
  var srcNode = outCtx.createBufferSource(); srcNode.buffer = buf; srcNode.connect(outAnalyser);
  var startAt = Math.max(outCtx.currentTime + 0.03, nextTime);
  srcNode.start(startAt); nextTime = startAt + buf.duration;
  speaking = true;
  srcNode.onended = function () { liveSources = liveSources.filter(function (x) { return x !== srcNode; }); if (!liveSources.length) speaking = false; };
  liveSources.push(srcNode);
}
function flushPlayback() { liveSources.forEach(function (s) { try { s.stop(); } catch (e) {} }); liveSources = []; speaking = false; if (outCtx) nextTime = outCtx.currentTime; }

/* ------------------------------ live session ------------------------------ */
function setupPayload(modelId) {
  var setup = {
    model: 'models/' + modelId,
    generationConfig: { responseModalities: ['AUDIO'],
      speechConfig: { voiceConfig: { prebuiltVoiceConfig: { voiceName: settings.voice } } } },
    systemInstruction: { parts: [{ text: PERSONA }] },
    inputAudioTranscription: {}, outputAudioTranscription: {},
    contextWindowCompression: { slidingWindow: {} },
    sessionResumption: resumeHandle ? { handle: resumeHandle } : {}
  };
  return JSON.stringify({ setup: setup });
}
function connect(key, modelId) {
  status('🔌 Connect ho raha hai — ' + modelId + ' …');
  ws = new WebSocket('wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=' + encodeURIComponent(key));
  ws.onopen = function () { try { ws.send(setupPayload(modelId)); } catch (e) {} };
  ws.onmessage = function (ev) {
    var m; try { m = JSON.parse(ev.data); } catch (e) { return; }
    if (m.setupComplete !== undefined) {
      retries = 0; status('🟢 RANI sun rahi hai — bolo…'); caption('R', ''); caption('U', '');
      return;
    }
    if (m.sessionResumptionUpdate && m.sessionResumptionUpdate.newHandle) resumeHandle = m.sessionResumptionUpdate.newHandle;
    if (m.goAway) { try { ws.close(); } catch (e) {} return; }
    var sc = m.serverContent; if (!sc) return;
    if (sc.interrupted) { flushPlayback(); return; }
    if (sc.inputTranscription && sc.inputTranscription.text) { caption('U', sc.inputTranscription.text); if (cb.onUserText) cb.onUserText(sc.inputTranscription.text); }
    if (sc.outputTranscription && sc.outputTranscription.text) { caption('R', sc.outputTranscription.text); if (cb.onRaniText) cb.onRaniText(sc.outputTranscription.text); }
    if (sc.modelTurn && sc.modelTurn.parts) sc.modelTurn.parts.forEach(function (p) {
      if (p.inlineData && p.inlineData.data) playChunk(p.inlineData.data);
    });
  };
  ws.onclose = function () {
    if (userEnded || !running) return;
    if (modelId !== 'gemini-3.8-live' && retries === 0) { // chosen model unavailable at setup → house standard
      status('⚠️ Ye model nahi mila — Gemini 3.8 Live par ja raha hoon…');
      retries = 1; connect(key, 'gemini-3.8-live'); return;
    }
    if (retries < 8) {
      retries++;
      status('🔄 Connection kata — dobara jud raha hai (' + retries + '/8)…');
      setTimeout(function () { if (running && !userEnded) connect(key, modelId); }, Math.min(5000, 600 * retries));
    } else { status('❌ Connection bar bar kat raha hai — network check karke dobara call karo.'); }
  };
  ws.onerror = function () { try { ws.close(); } catch (e) {} };
}

/* ------------------------------ public API ------------------------------ */
function start(opts) {
  opts = opts || {};
  keyProvider = opts.keyProvider;
  cb = { onStatus: opts.onStatus, onUserText: opts.onUserText, onRaniText: opts.onRaniText };
  buildUI();
  ui.wrap.style.display = 'flex'; ui.capU.textContent = ''; ui.capR.textContent = '';
  running = true; userEnded = false; retries = 0; resumeHandle = null;
  callStartedAt = Date.now();
  clearInterval(timerInt);
  timerInt = setInterval(function () { if (ui) ui.timer.textContent = fmtTime(Date.now() - callStartedAt); }, 500);
  status('🎙️ Mic tayyar kar raha hoon…');
  startMic().then(function () {
    status('🔑 Free key la raha hoon…');
    return keyProvider ? keyProvider() : null;
  }).then(function (key) {
    if (!running) return;
    if (!key) { status('❌ Key nahi mili — pehle login karo, ya Settings mein apni Gemini key lagao.'); return; }
    ensureOutCtx();
    connect(key, settings.model);
  }).catch(function (e) {
    status('❌ Mic ki ijazat chahiye — browser/app ko mic allow karo.');
  });
}
function stop() {
  userEnded = true; running = false;
  clearInterval(timerInt);
  try { if (ws) ws.close(); } catch (e) {}
  ws = null;
  if (procNode) { try { procNode.disconnect(); } catch (e) {} procNode = null; }
  if (micStream) { micStream.getTracks().forEach(function (t) { t.stop(); }); micStream = null; }
  if (inCtx) { try { inCtx.close(); } catch (e) {} inCtx = null; }
  flushPlayback();
  if (ui) ui.wrap.style.display = 'none';
  if (cb.onStatus) cb.onStatus('Call khatam');
}
function say(text) {
  try {
    var u = new SpeechSynthesisUtterance(String(text).replace(/[*#`]/g, '').slice(0, 500));
    var vs = speechSynthesis.getVoices();
    var v = vs.find(function (x) { return /female|zira|google.*(ur|hi)/i.test(x.name + ' ' + x.lang); }) || vs.find(function (x) { return /^ur|^hi/i.test(x.lang); }) || vs[0];
    if (v) u.voice = v;
    speechSynthesis.cancel(); speechSynthesis.speak(u);
  } catch (e) {}
}

window.RaniVoice = { start: start, stop: stop, say: say, voices: VOICES, models: MODELS, settings: settings, persona: PERSONA };
})();
