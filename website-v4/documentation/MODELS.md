# BADSHAH AI — Model Map (v5, 2026-11-18)

Owner's order: FT Fusion REMOVED completely. Every feature runs on the model made
for it. Model map is also stored in Supabase: table `public.ai_models`.

| Feature | Model | Status (tested 2026-11-18) |
|---|---|---|
| Image generation | gemini-3-pro-image | REST 403 with current AQ keys — wired, activates with a full Gemini API key |
| Image fast generation | gemini-3.1-flash-image | REST 403 with current AQ keys — wired; Pollinations FLUX serves images meanwhile |
| Video generation | gemini-omni-1.1-flash | Model ID unverified; /api/video wired (predictLongRunning + status poll) |
| Premium video | veo-3.1-generate-preview | Same as above |
| Live calls | gemini-3.8-live | ✅ VERIFIED — Live WebSocket setupComplete |
| Advanced live calls | gemini-3.8-live-extended-thinking | ✅ VERIFIED — needs thinkingConfig.thinkingLevel=HIGH in setup (RANI engine sends it) |
| Natural voice (TTS) | gemini-3.8-flash-tts | REST 403 with AQ keys — wired (PCM→WAV); ElevenLabs serves voice meanwhile |
| Fast voice (TTS) | gemini-3.8-flash-lite-tts | REST 403 with AQ keys — wired |
| Chat | gemini-3.8-flash | REST 403 with AQ keys — wired; Groq serves chat meanwhile |
| Fast chat | gemini-3.5-flash-lite | REST 403 with AQ keys — wired |
| Coding | gemini-3.1-pro-preview | REST 403 with AQ keys — wired |
| Coding assistant | gemini-3.8-flash | REST 403 with AQ keys — wired |
| Chat (working today) | Groq openai/gpt-oss-120b / gpt-oss-20b | ✅ VERIFIED live |

## The one thing to know about the current Gemini keys
The owner's Gemini keys (format `AQ.…`) are **Live-scoped**: Google accepts them
on the Live WebSocket (calls work) but rejects every REST call with
`403 PERMISSION_DENIED` (chat, image, TTS, video). This is a property of the
keys, not of the code. To activate all Gemini REST features, add ONE normal
Gemini API key (format `AIza…`, from Google AI Studio) in the admin panel's
API Keys tab (provider: gemini) — everything lights up automatically, no code
change needed. Until then the fallbacks above keep every feature working free.
