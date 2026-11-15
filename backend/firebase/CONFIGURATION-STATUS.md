# BADSHAH AI — Firebase Configuration Status

Verified in the live Firebase Console on **2026-11-15** (account:
`zainartsstudio@gmail.com`, 2-Step Verification enabled the same day).
Project console: https://console.firebase.google.com/project/badshah-ai-badshah222

## Project identity (real values — do not change)

| Item | Value |
|---|---|
| Project name | BADSHAH AI |
| Firebase Project ID | `badshah-ai-badshah222` |
| Project number / FCM Sender ID | `949903255351` |
| Android package name | `dev.pages.badshah222ai` |
| Android App ID | `1:949903255351:android:053b6a0441c5810dae1cb2` |
| Storage bucket | `badshah-ai-badshah222.firebasestorage.app` |
| SHA-1 fingerprint | `D3:B2:3F:C1:B8:E1:16:59:9B:D0:C0:17:44:A8:1B:7A:A2:4B:62:F5` |
| SHA-256 fingerprint | `6D:81:F8:E8:58:9D:A2:67:11:5D:B2:7D:16:48:B1:34:B7:39:F0:1D:9E:6E:19:05:C9:7C:5A:0E:22:46:DF:FD` |

The SHA fingerprints come from the signing certificate of the user's real
APK (`Badshah222-ai.apk`) and were added in Project settings → General →
Your apps; the console validated each one ("valid SHA1/SHA256 certificate")
and confirmed "Success: New certificate fingerprints added". The Android
OAuth client inside `google-services.json` carries certificate hash
`d3b23fc1b8e116599bd0c01744a81b7aa24b62f5` — the same SHA-1 without colons.

## DONE ✅ (console-verified, 2026-11-15)

1. **Firebase project created** — `badshah-ai-badshah222`.
2. **Android app registered** — package `dev.pages.badshah222ai`, nickname
   "BADSHAH AI Android".
3. **SHA-1 + SHA-256 fingerprints configured** (see above).
4. **google-services.json downloaded** — the copy in this package is the
   FINAL version, re-downloaded after the Google provider was enabled; it
   contains the Android OAuth client (type 1) and the Web OAuth client
   (type 3, used as `default_web_client_id` for Google Sign-In).
5. **Firebase Authentication**
   - Email/Password — **Enabled** (passwordless email-link sign-in left OFF).
   - Google — **Enabled**; public-facing name "BADSHAH AI"; project support
     email `zainartsstudio@gmail.com`.
   - No other providers enabled.
6. **Google Analytics — Enabled** at project creation (existing "Default
   Account for Firebase" account; a new Analytics property was
   auto-created). The Analytics dashboard loads; it shows no data yet
   because no app has reported events — expected at this stage.
7. **Firebase Cloud Messaging — configured**: Cloud Messaging API (V1)
   **Enabled**, Sender ID `949903255351`, Legacy API Disabled. No Web Push
   key pair was generated (not needed for Android), and no APNs or
   service-account keys were created.

## PREPARED IN THIS PACKAGE 📦 (code/docs ready, deployment pending)

8. **Custom claims (`role: authenticated`) + Supabase JWT integration** —
   Cloud Functions source in `functions/`:
   - `setSupabaseClaims` (callable) — sets `{ role: 'authenticated',
     supabase_role: 'authenticated' }` via the Admin SDK.
   - `beforeUserCreated` (identity blocking hook) — same claims at sign-up;
     blocking functions must be enabled in the console (Auth → Settings).
   - `mintSupabaseToken` (Option B) — mints a Supabase-compatible JWT;
     secret read only from Firebase Secret Manager
     (`SUPABASE_JWT_SECRET`), never from code.
   - Integration guide: `docs/03-SUPABASE-JWT-INTEGRATION.md`.
9. **Android integration** — Gradle snippets + dependencies in
   `android/firebase-gradle-snippets.md`; placement instructions in
   `android/google-services-placement.txt`.
10. **Crashlytics** — SDK dependencies and onboarding/test-crash steps are
    documented (Docs 01 & 05).

## PENDING ⏳ (cannot be completed from the console alone — exact steps in the guides)

| # | Item | Why pending | Exact next step |
|---|---|---|---|
| 1 | Crashlytics activation | The console page shows "Add SDK"; Crashlytics only activates after the SDK is in the app, the app runs, and a crash is reported | Integrate SDK (Doc 01 Step 6 / Doc 05), force a test crash, reopen the Crashlytics page |
| 2 | Cloud Functions deployment | Deploying Cloud Functions requires the **Blaze (pay-as-you-go)** plan; this project was created on the default plan and billing was NOT changed | Owner upgrades the project to Blaze in the console, then `firebase deploy --only functions` (Doc 05) |
| 3 | Supabase Third-Party Auth (Firebase) | Must be switched on in the Supabase Dashboard of project `aghlacmqlagmigoznxlm` | Supabase Dashboard → Authentication → Third-Party Auth → Firebase → enter Project ID `badshah-ai-badshah222` (Doc 03, Option A) |
| 4 | First FCM test message / Analytics events | Need the integrated app running on a device | Follow the test checklist in Doc 05 |

## Security note

No service-account private key, no Supabase `service_role` key, and no JWT
secret exists in this package — by design. `google-services.json` is client
configuration (safe for the app/ZIP), not a secret.
