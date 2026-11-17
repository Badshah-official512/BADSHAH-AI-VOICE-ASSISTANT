# RANI AI — Natural Human Voice Engine

Independent real-time female voice module: `website-v4/rani-voice.js`
Wired into `app.html`: in **RANI mode**, the 📞 Call button starts `RaniVoice`
(BADSHAH mode keeps the existing call engine). No build step; the Android app
shows the same website, so the module works in the app unchanged.

## Models — verified before activation (prompt requirement)

Live WebSocket setup tests run on **2026-11-18** with the owner's pooled
Gemini key (the exact production path — BidiGenerateContent, MYRA-style setup
payload, voice Achernar):

| Model | Result |
|---|---|
| `gemini-3.8-live` | ✅ setupComplete (default — house standard, user-confirmed working 2026-11-17) |
| `gemini-3.1-flash-live-preview` | ✅ setupComplete |
| `gemini-2.5-flash-native-audio-latest` | ✅ setupComplete |

If a selected model is ever rejected at setup, the engine automatically
falls back to `gemini-3.8-live` once, then uses session-resumption reconnects.

## Voices

Achernar (default), Aoede, Algieba, Despina, Callirrhoe, Enceladus, Leda,
Kore — selectable in the call screen; persisted; applies from the next call
(Gemini Live fixes the voice at session setup).

## Human-voice behaviour

Native audio generation + the RANI system instruction in `rani-voice.js`
(soft/warm default, calm explanations, brighter when excited, slower/softer
when the user is sad, natural pauses and short sentences, same-language
replies: Urdu / Pashto / English / Roman Urdu). No volume tricks are used.

## Audio pipeline

- Mic: `getUserMedia` with echoCancellation, noiseSuppression,
  autoGainControl; 16 kHz PCM16 uplink (ScriptProcessor — chosen for Android
  WebView compatibility).
- Downlink: 24 kHz PCM, gapless scheduled playback with a live voice ring.
- Barge-in: server `interrupted` event + local mic-energy flush (~140 ms of
  loud input while RANI speaks cuts her audio immediately).
- Session resumption: server handle stored; up to 8 reconnects with backoff;
  `goAway` handled by immediate reconnect.
- Transcription: input + output captions shown live on the call screen.

## Security

The module contains **no API key**. At call time the app fetches a pooled key
from the protected backend (`/api/live-key`, Firebase-login gated, key pool in
Supabase). Documented limitation: the key reaches the client for the call's
duration because the browser connects to Google directly — the zero-exposure
upgrade is backend-minted **ephemeral tokens**, planned as a follow-up.

## Test results (prompt checklist)

1. Natural English conversation — engine + persona ready; full conversation
   quality needs the owner's phone test. **Setup level: PASS.**
2. Urdu conversation — persona instructs same-language replies; Live native
   audio is multilingual. **Setup level: PASS; quality pending phone test.**
3. Pashto conversation — same as Urdu. **Setup level: PASS; quality pending
   phone test** (Pashto is the least-tested Live language — verdict reserved).
4. Voice interruption — implemented both ways (server event + local flush).
   **Code: PASS; feel pending phone test.**
5. Background noise — EC/NS/AGC constraints enabled. **Pending phone test.**
6. Continuous listening — socket stays open; transcription streams; no
   per-turn button. **PASS by design.**
7. Soft/expressive voice — Achernar default + persona guidance. **Pending
   owner's ear test** (Muse cannot listen to audio).
8. Disconnection & recovery — resumption handle + 8 retries + model fallback.
   **Code: PASS; field test pending.**

## Unsupported / fallback notes

- Termux TTS: kept as a documented shell fallback (`termux-tts-speak`) for
  Termux environments only — the engine never requires it; the web fallback
  is `RaniVoice.say()` (speechSynthesis).
- No Windows-only audio library is used anywhere.
- Voice/model changes mid-call are not supported by Gemini Live; the UI says
  they apply from the next call.
