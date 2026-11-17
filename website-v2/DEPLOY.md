# DEPLOY — BADSHAH AI Web Upgrade (badshah222-ai.pages.dev)

Deploy this whole folder to the Cloudflare Pages project **badshah222-ai**
(advanced mode — `_worker.js` is the backend). Owner steps, in order:

## 1) Supabase SQL (one time)
Supabase → project **aghlacmqlagmigoznxlm** → SQL Editor → run
`sql/20261118000004_web_upgrade.sql`. It only ADDS columns/tables (safe, idempotent).

## 2) Firebase web config (one time)
Firebase Console → project **badshah-ai-badshah222** → Project settings → Your apps →
**Add app → Web** → register (nickname "BADSHAH AI Web") → copy **apiKey** and **appId**
into `config.js`, replacing both `PASTE_FROM_FIREBASE_CONSOLE` values.
Also: Authentication → Settings → Authorized domains → make sure
`badshah222-ai.pages.dev` is listed (Google sign-in needs it).

## 3) Cloudflare secrets (Pages project → Settings → Environment variables)
- `BADSHAH_API_KEY` — already set (FT Fusion key). Do not change.
- `ADMIN_PASSWORD` — set to the admin panel password (the one the owner chose).
- `SUPABASE_SERVICE_ROLE_KEY` — from Supabase → Project Settings → API → service_role.
  Server-side only (the Worker uses it for plan checks + admin actions). Never put it in any file.

Without `SUPABASE_SERVICE_ROLE_KEY`: free chat works; paid models answer
"setup pending" and the admin panel shows a setup error — by design, nothing fails silently.

## 4) APK download
Drop the GitHub-built release APK into the site root named exactly **BADSHAH-AI.apk**
(the landing page button already points to `./BADSHAH-AI.apk`). The old
`Badshah222-ai.apk` is removed in this upgrade.

## 5) Check after deploy
- `/` landing loads, hero + modes + pricing visible.
- `/app.html` → sign up → chat on Flex (free) works; a Paid model asks to upgrade.
- `/admin` → login with the admin password → dashboard numbers load.
- Send a test paid request from the app → approve it in admin → plan shows Paid in the app.
- Help Center message from the app → appears in admin inbox (and mailto fallback is shown in-app).

## Notes
- Calls use each visitor's OWN Gemini key (browser localStorage only) with
  **gemini-3.8-live** — the only Live model the code will ever request.
- The FT key and admin password exist ONLY as Cloudflare secrets — never in this package.
