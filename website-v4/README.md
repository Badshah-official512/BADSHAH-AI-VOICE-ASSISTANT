# BADSHAH AI v6 — README

Deploy ke poore steps `DEPLOY.md` mein hain. Ye README Supabase ka kaam aur nayi image/video APIs batata hai.

## 1. Supabase mein kya karna hai (SQL Editor mein, is tarteeb se, har file sirf ek baar run karo)

| # | File | Kaam |
|---|------|------|
| 4 | `sql/20261118000004_web_upgrade.sql` | purani tables (agar pehle run nahi ki) |
| 5 | `sql/20261118000005_api_keys.sql` | API key pool |
| 6 | `sql/20261118000006_analytics_control.sql` | analytics, web users, site settings, announcements, device commands |
| 7 | `sql/20261118000007_media_providers.sql` | pool mein `stability` aur `replicate` keys allow karti hai |
| 8 | `sql/20261118000008_agent_platform.sql` | Agent platform ki tables (neeche) |

Files 5–8 ko pglite par do baar chala kar test kiya hai (dobara chalane se koi masla nahi). Asli Supabase par abhi run nahi hui.

### Agent platform (file 8) mein Supabase ka kaam
Backend (FastAPI + Redis/Valkey + Celery + Docker) Supabase Postgres ko database ki tarah use karega. Tables:

- `agent_workspaces`: har user ka apna Docker container/volume/noVNC port aur status. Ek user, ek workspace.
- `agent_tasks`: manager aur subagents ke tasks (queued, running, waiting_approval, done, failed, cancelled), progress, retries (`attempts` / `max_attempts`), result.
- `agent_task_logs`: har task ke live logs.
- `agent_approvals`: sensitive action (publish, send, delete, payment) se pehle user ki manzoori.
- `connectors`: Google, GitHub, Telegram, Discord, WhatsApp Business, MCP, custom API. Credentials **encrypted** (`credentials_enc`), `enabled_for_agents` switch ke saath.
- `phone_devices`: Android companion ki pairing aur `control_enabled` kill-switch.
- `audit_logs`: har sensitive action ka record.

Zaroori usool:
1. Login Firebase se hai, isliye har table `firebase_uid` (text) par chalti hai. Supabase Auth use nahi hoti.
2. Har table par RLS **ON** hai aur koi public policy nahi. Sirf backend ki **service-role key** (server env mein) padh/likh sakti hai. Isay kabhi browser ya app mein mat daalo.
3. Credentials ka encryption backend karta hai (AES-GCM, key server env mein). Supabase mein plaintext nahi jana chahiye.
4. Live progress chahiye to Supabase → Database → Replication mein `agent_tasks` ko `supabase_realtime` publication mein add karo.
5. Free tier: 500 MB database, 1 GB storage, project 7 din inactive rahe to pause ho jata hai. Workspace ki files Supabase mein nahi, Docker volumes mein rakhni hain.

Ye sirf **database ka hissa** hai. Docker workspaces, Playwright, noVNC, Android app, connectors, YouTube automation ka code abhi nahi bana (alag phases hain, `prompts/AGENT_PLATFORM_PROMPT.md` dekho).

## 2. Image aur video APIs (v6.1, `_worker.js` mein lag chuki hain)

**Image** (`/api/image`), is tarteeb se:
1. Gemini image models (pool ki key)
2. **Cloudflare Workers AI** (FLUX-1-schnell). Cloudflare Pages → Settings → Functions → Bindings → **Workers AI**, variable name `AI`. Free daily allowance hai.
3. **Stability AI** (admin → API keys → `stability`). Trial credits, baad mein paid.
4. **Replicate FLUX** (admin → API keys → `replicate`). Chhota trial credit, baad mein paid.
5. **Pollinations**: bina key ke, hamesha chalta hai (aakhri fallback).

**Video** (`/api/video`): pehle Gemini, na chale to **Replicate MiniMax video-01** (key `replicate`). Frontend status poll `rep:<id>` khud sambhalta hai.

Jo APIs **jaan-boojh kar nahi lagayi**:
- Wan 2.1/2.2, LTX-Video, HunyuanVideo, CogVideoX, AnimateDiff, Z-Image: ye hosted API nahi, model hain jinhe GPU par chalana padta hai. ComfyUI (`POST /prompt`, `GET /history/{id}`) ke peeche self-host karke baad mein jod sakte hain. Is hafte ka kaam nahi.
- Agnes AI: iski official API documentation verify nahi hui, isliye fake integration nahi banayi.

Sach ye hai ke bilkul free + hosted video koi nahi. Video ke liye Replicate ka paid credit ya apna GPU chahiye.
