# BADSHAH AI — Documentation

## AI API Integration (direct HTTP only)

**Rule (owner, 2026-11-16):** integrations are direct HTTP REST calls only. No CLI
tools, no terminal authentication, no interactive logins. A GitHub repository is
never assumed to be a hosted API — engines that need self-hosting sit behind the
adapter in `backend/self-hosted/`.

### Unified API service

- **What:** Supabase Edge Function `ai-api` (`backend/supabase/functions/ai-api/`)
- **Base URL:** `https://aghlacmqlagmigoznxlm.supabase.co/functions/v1/ai-api`
- **Auth:** header `x-gateway-secret: <GATEWAY_SECRET>` on every call. Provider
  keys live ONLY as Edge Function secrets on the backend. A caller may pass its
  own key per request (`userKey` in the JSON body) — used once, never stored.
- **Machine-readable config:** `backend/ai-providers.json`
- **Deploy:** Supabase dashboard → Edge Functions → create `ai-api`, paste
  `index.ts` + `handler.ts`, **Verify JWT = OFF**, then add the provider secrets
  (below) under Edge Functions → Secrets. Redeploy after changing secrets.

### Routes

| Route | Body | Returns |
|---|---|---|
| `GET /health` | — | live status of every provider (real test calls) |
| `POST /chat` | `{provider, model?, system?, messages:[{role,content}], stream?, userKey?}` | `{text, provider, model, usage}` or an SSE stream when `stream:true` (gemini/groq/openrouter) |
| `POST /image` | `{provider: fusion-uni\|fusion-astra\|pollinations\|huggingface, prompt, userKey?}` | `{imageUrl}` or `{imageBase64, mimeType}` (HF) |
| `POST /vision` | `{provider: gemini\|claude, model?, prompt, imageBase64, mimeType, userKey?}` | `{text}` |
| `POST /file` | `{prompt, fileBase64, mimeType, model?, userKey?}` | `{text}` (Gemini file analysis) |
| `POST /tts` | `{provider: google\|elevenlabs, text, voice?, userKey?}` | `{audioBase64, mimeType}` |
| `POST /stt` | `{provider: groq\|gemini, audioBase64, mimeType, userKey?}` | `{text}` |
| `POST /video` | `{prompt, model?, userKey?}` | `{operation}` (Google Veo, PAID) |
| `POST /video/status` | `{name, userKey?}` | `{done, operation}` |
| `POST /browse` | `{url, task}` | self-hosted browser engine result, or 501 until configured |

Errors are always `{ ok:false, error:{ provider, code, message } }`. Timeouts:
chat 60s, image 120s, vision/file 90s, tts 60s, stt 90s, health 8–20s per provider.

### The 11 required integrations — status after REAL HTTP tests (2026-11-16)

| # | Integration | Provider + endpoint | Tier | Real test result |
|---|---|---|---|---|
| 1 | Gemini text | `POST generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`, header `x-goog-api-key` | Free tier (AI Studio key) | Endpoint + error format verified (403 without key). Success test = `/health` after the key is set on the backend |
| 2 | Claude Messages | `POST api.anthropic.com/v1/messages`, headers `x-api-key`, `anthropic-version: 2023-06-01` | **PAID — no free tier exists** | Endpoint + error format verified (401 `invalid x-api-key`). Success needs a paid key |
| 3 | Groq Chat | `POST api.groq.com/openai/v1/chat/completions`, `Authorization: Bearer` | Free tier | Endpoint + error format verified (`invalid_api_key` JSON). Success via `/health` after key is set |
| 4 | Image generation | FT Fusion (uni/astra), Pollinations, Hugging Face FLUX | Free / owner gateway | **Pollinations VERIFIED (real JPEG). FT image VERIFIED (real hosted URL).** HF endpoint verified (401 without token) |
| 5 | Video generation | Google Veo `:predictLongRunning` | **PAID only** — no free official REST API exists | Adapter implemented from the official API; not success-tested (paid). Self-hosted Wan/LTX/CogVideo = adapter in `backend/self-hosted/` |
| 6 | Text-to-speech | Google Cloud TTS `POST texttospeech.googleapis.com/v1/text:synthesize`; ElevenLabs `POST api.elevenlabs.io/v1/text-to-speech/{voiceId}` | Google: free quota then paid. ElevenLabs: free 10k chars/mo | Google endpoint verified (403 without key). ElevenLabs account connected; quota resets 2026-12-09. Pollinations audio is NOT free (tested 404) |
| 7 | Speech-to-text | Groq Whisper `POST api.groq.com/openai/v1/audio/transcriptions` (multipart); Gemini audio understanding | Free tier | Adapters implemented; success via `/health`-style call after keys are set. The app also has Android on-device STT today |
| 8 | Real-time voice | Gemini Live WebSocket `wss://generativelanguage.googleapis.com/ws/...BidiGenerateContent?key=` | Free tier (AI Studio key) | App connects directly (an HTTP edge function cannot proxy WebSocket). Google rejected one model's setup (1007) on 2026-11-16; the app now auto-retries setup variants + v1alpha |
| 9 | Browser automation | Self-hosted (browser-use / Playwright / Skyvern) behind `BROWSER_ENGINE_URL` | Self-hosted | Not deployed (needs a server). Contract + adapter skeleton in `backend/self-hosted/`. The app ships its own in-app WebView browser meanwhile |
| 10 | Image understanding | Gemini vision / Claude vision (see `/vision`) | Free tier (Gemini) / PAID (Claude) | Adapters implemented on the verified chat endpoints; success after keys are set |
| 11 | File analysis | Gemini inline files (see `/file`) | Free tier | Adapter implemented on the verified Gemini endpoint |

**Extra verified free routes:** OpenRouter models list (200; 469 models, 14 free at
test time) and Pollinations text (`openai-fast` answered live). FT Fusion TEXT
still returns its own server-side "Session issue" error (2026-11-16 22:57 PKT) —
the gateway reports it honestly; the app falls back to Pollinations meanwhile.

### Backend secrets to set (Supabase → Edge Functions → Secrets)

`GATEWAY_SECRET` (already set), `GEMINI_API_KEY`, `GROQ_API_KEY`,
`OPENROUTER_API_KEY`, `HF_TOKEN`, `GOOGLE_TTS_KEY`, `ELEVENLABS_API_KEY`,
`ANTHROPIC_API_KEY` (paid, optional), `BROWSER_ENGINE_URL` (later, self-hosted).

### Android app

The app currently calls providers directly with the user's own keys AND has FT
Fusion + Pollinations built in. The follow-up step after this gateway is
deployed and `/health` is green: point the app's `AiRepository` at the gateway
(`POST /ai-api/chat` etc.) so the app only talks to our backend.

### Addendum — owner's API collection (tested live 2026-11-16, ~23:02 PKT)

- **Pollinations `gen.pollinations.ai` (newer API):** chat completions answered
  with **no key at all** (OpenAI-compatible, proper system+messages support);
  image returned a real 445 KB FLUX JPEG with **no key**. The gateway's
  Pollinations chat now prefers this endpoint. **Video** (`/video/{prompt}`,
  Wan models) exists but returned **401 — an API key is required** (free account
  key at enter.pollinations.ai/keys; cost per their live catalog). The gateway
  implements it behind `POLLINATIONS_API_KEY` and streams the bytes through.
- **Cloudflare Workers AI** added to the gateway: chat via the OpenAI-compatible
  `/ai/v1/chat/completions`, images via FLUX.1-schnell, health via model search.
  Needs the owner's `CLOUDFLARE_ACCOUNT_ID` + `CLOUDFLARE_API_TOKEN` as backend
  secrets (Workers AI has a free daily Neuron allocation).
- **Groq TTS** (Orpheus, `POST /openai/v1/audio/speech`) and **Gemini TTS**
  (`generateContent`, AUDIO modality, PCM out) added as `/tts` providers.
  Orpheus voices are English/Arabic per Groq's docs — no Urdu/Pashto assumption.
- Screen sharing follows the owner's spec (MediaProjection → frames → Gemini
  Vision → voice/text reply, always with the user's permission and a visible
  indicator) — that is the Phase-2 Android build.
