# BADSHAH AI admin panel

Static HTML/CSS/JS (no build step, no third-party scripts, strict CSP).

```
index.html            password-only login (public)
css/admin.css         shared styles (public)
js/api.js, login.js   API client + login (public; never sees the session token)
assets/               logo, favicon
pages/dashboard.html  dashboard shell  } served ONLY to a valid session
pages/js/*.js         dashboard code   } (checked server-side by the Worker)
```
All data comes from `POST /admin/api` → Cloudflare Worker → Supabase Edge Function `admin-api`. See `documentation/ADMIN-GUIDE.md` and `documentation/HOSTING-GUIDE.md`.
