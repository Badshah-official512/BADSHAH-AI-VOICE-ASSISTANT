# DEPLOY — BADSHAH AI Web v6 (SAB KUCH FREE)

v6 update (2026-11-18): Analytics + Users + Site Control (migration 006), media provider keys (007),
Agent Platform tables (008), device command queue, admin Control Room. FT Fusion was REMOVED in v5 —
chat/image/voice/video run on Gemini models + Groq (see documentation/MODELS.md).

Deploy this whole folder to the Cloudflare Pages project (advanced mode —
`_worker.js` is the backend). v3 changes: no paid plans anymore — every model,
image and call is FREE for users, powered by the owner's API key pool in Supabase.

## 1) Supabase SQL — ✅ DONE (2026-11-18)
Migrations `20261118000004_web_upgrade.sql` (v2) and `20261118000005_api_keys.sql`
have already been run on project **aghlacmqlagmigoznxlm**. The three owner keys
(Gemini, Groq, ElevenLabs) are already in the `api_keys` pool and active.
Re-running the files is safe (idempotent) but not needed.

## 2) Firebase web config — ✅ DONE (2026-11-18)
Firebase Console → project **badshah-ai-badshah222** → Project settings → Your apps →
**Add app → Web** → copy **apiKey** and **appId** into `config.js`, replacing both
`PASTE_FROM_FIREBASE_CONSOLE` values. Also add the live domain(s)
(`badshah222-ai.pages.dev`, `badshah-xjc.pages.dev`) under Authentication →
Settings → Authorized domains (needed for login + Google sign-in + live calls).
Until this is done the app runs in Guest demo mode: chat works, calls need the
visitor's own key as a backup.

## 3) Cloudflare secrets (Pages project → Settings → Environment variables)
- `BADSHAH_API_KEY` — NO LONGER USED (FT Fusion removed in v5). Can be deleted.
- `SUPABASE_SERVICE_ROLE_KEY` — from Supabase → Project Settings → API → service_role.
  Server-side only. REQUIRED for: API key pool (calls/TTS/Groq), admin panel, analytics.
- `ADMIN_PASSWORD` — OPTIONAL fallback. The admin password is primarily verified
  against Supabase (`verify_admin_password`) — the same password as the app backend's
  admin. Only set this if you want a separate web-only fallback password.

## 4) APK download
Drop the GitHub-built release APK into the site root named exactly **BADSHAH-AI.apk**.

## 5) Check after deploy
- `/` landing loads — hero, gallery, live demo chat answers.
- `/app.html` → login → chat on any model (all free); image mode works (Uni + Astra).
- Call → connects WITHOUT asking for a key (pool key) and talks with feelings.
- 🔊 Suno on a reply → premium ElevenLabs voice plays.
- `/admin` → Supabase admin password → dashboard: users, tickets, key stats.
  API Keys tab: add a test key, OFF/ON it, delete it.

## How the free system works
- Chat/images: FT Fusion via `/api/chat` (Worker secret key).
- Groq chat: `/api/chat?provider=groq` — Worker uses the pooled Groq key.
- Live calls: app asks `/api/live-key` (login required) → Worker hands out the
  pooled Gemini key → browser connects to **gemini-3.8-live** only.
- Voice replies (TTS): `/api/tts` — Worker uses the pooled ElevenLabs key.
- Key rotation: least-used active key first; on 401/429 the Worker tries the next.
  Owner rotates/adds keys any time from Admin → API Keys. No user ever sees a key.


---
## v6 — naya (UI/UX + Admin + Analytics)

1. **Supabase SQL Editor** mein `sql/20261118000006_analytics_control.sql` ka poora text paste karke **Run** karo (sirf ek baar). Isse analytics, web users, site settings, device commands, announcements ki tables ban jati hain.
2. Poora folder Cloudflare Pages par dobara deploy karo (`_worker.js` naya hai). Env vars pehle jaise hi.
3. `/admin/` kholo → password dalo → **System** tab mein health check karo; sab table "OK" honi chahiye.
4. Naye endpoints: `GET /api/config` (public), `POST /api/track`, `POST /api/profile-sync`, `POST /api/device`; admin actions `/admin-api/*` (overview, analytics, live, web-users, settings-get/set, announcements, commands, health).
5. Admin se: website text/announcement/maintenance, features on/off, app welcome/system prompt/default model, APK link+version, GA4 ID, users block/delete/notes/CSV, API keys, support.
6. Android APK ka link: Admin → App control → APK download link (ya `BADSHAH-AI.apk` root mein rakho).
7. Admin password `_worker.js` mein `CODE_ADMIN_PASSWORD` hai — deploy se pehle badal lo.
8. Phone control: web se commands `device_commands` table mein queue hote hain; Android app/desktop agent ko `/api/device` se unhe uthana hoga.
9. Video generation beta hai; poori Gemini key chahiye.

10. **v6.1:** SQL files 7 aur 8 bhi Supabase mein run karo (`README.md` mein tafseel). Cloudflare Pages mein optional Workers AI binding `AI` jodo. Admin → API keys mein `stability`/`replicate` keys daal sakte ho.
