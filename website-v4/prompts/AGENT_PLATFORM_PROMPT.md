# Chhota addition prompt (agent platform ke asal prompt ke neeche jodo)

```
ADDITION — IMAGE/VIDEO + SUPABASE

Existing BADSHAH-RANI AI mein ye pehle se hai: Cloudflare Pages `_worker.js`, Firebase login, Supabase REST (service-role sirf server par), API key pool (`api_keys` table), `/api/image`, `/api/video`. Inhe todna mat, inhi par build karo.

1. Supabase: `sql/20261118000008_agent_platform.sql` ki tables use karo (workspaces, tasks, logs, approvals, connectors, phone_devices, audit_logs). Sab `firebase_uid` par, RLS ON, access sirf backend service-role se. Naya schema banane se pehle isay extend karo.
2. Image providers, tarteeb se: Gemini -> Cloudflare Workers AI (FLUX-1-schnell) -> Stability AI -> Replicate FLUX -> Pollinations (free fallback).
3. Video providers: Gemini -> Replicate MiniMax video-01. Self-hosted options (Wan 2.1/2.2, LTX-Video, HunyuanVideo, CogVideoX, AnimateDiff) ComfyUI (`POST /prompt`, `GET /history/{id}`) ke peeche optional "Computer/Automation Agent" tool ki tarah, sirf tab jab GPU available ho.
4. Har provider ki asli API/auth documentation verify karo. Fake provider ya simulated output mat banao. Agar kisi ke liye key ya GPU nahi hai to saaf batao, chupke se paid service par mat jao.
5. Keys sirf server par rahen, admin panel ke key pool se rotate hon, frontend ko kabhi na dikhen.
6. Har phase ke baad test karo aur batao: kya bana, kya missing hai, free deployment kaise hogi.
```
