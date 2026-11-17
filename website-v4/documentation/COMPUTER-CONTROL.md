# COMPUTER CONTROL — Pairing Protocol (Design Spec)

Status: web-side pairing UI is LIVE in the app (Connect Computer → pairing code).
The desktop agent is the next phase; this document is its contract.

## Idea
The web app generates a short-lived pairing code for the signed-in user.
A small desktop agent (Windows first) asks for that code once, exchanges it
for a long-lived device token, and then keeps a WebSocket open to the backend.
BADSHAH (web/app) sends commands down that socket; the agent executes them
locally and streams results/screenshots back.

## Flow
1. User opens BADSHAH web app → **Connect Computer** → "Naya Pairing Code Banao".
2. App inserts a row in `public.computer_pairings` (code, firebase_uid, status=waiting).
   Codes are 6 digits and expire in 10 minutes.
3. Desktop agent: user pastes the code → agent calls the pairing endpoint with
   its machine name → row becomes status=paired, paired_at set, device token issued.
4. Agent opens WSS to the command relay with the device token.
5. In the app, the paired computer appears under Connect Computer; user (or the
   assistant, with the user's command) can send actions.

## Command set (v1)
- `open_app` { name } — launch an application
- `open_url` { url } — open in default browser
- `type_text` { text } — type into the focused field
- `key` { combo } — hotkeys (e.g. "ctrl+s")
- `screenshot` {} — returns a JPEG frame (base64) for vision questions
- `list_files` { path } / `open_file` { path }
- `media` { action: play|pause|next|volume, value }
- `shell` { command } — DISABLED by default; owner enables per-device in settings
- `shutdown` / `restart` / `sleep` — always ask the user to confirm in the app first

## Safety rules
- Pairing is one-to-one: a code pairs exactly one computer, once.
- Every destructive action (shell, shutdown, file delete) needs an in-app confirm.
- The agent shows a persistent tray icon + "BADSHAH connected" state; one click disconnects.
- Device tokens can be revoked from the app (sets the pairing row to status=revoked).

## Android note
The Android app is the phone-side equivalent (opens apps, messages, calls,
browsing, screen vision). It can be set as the phone's **default digital
assistant** (Settings → Apps → Default apps → Digital assistant app →
BADSHAH-RANI AI), so a long-press of Home/Power summons BADSHAH directly.
