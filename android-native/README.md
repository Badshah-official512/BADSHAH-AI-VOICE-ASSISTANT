# BADSHAH AI — Native app (`android-native/`)

**Full native rebuild, Phase 1 scaffold.** Kotlin + Jetpack Compose, single Activity,
zero WebView. The old WebView shell (`android-app/`, v7.7.2) stays untouched until
this app reaches parity and the user approves the switch.

Plan: `~/workspace/native-rebuild/NATIVE-PLAN.md`
Design mapping: `~/workspace/native-rebuild/DESIGN-MAPPING.md`
Features roadmap (22 approved, #13 excluded): `~/workspace/native-rebuild/FEATURES-ROADMAP.md`
Chat themes (5, native only — website mein koi change nahi): `~/workspace/native-rebuild/THEMES.md`

## Structure

- `settings.gradle.kts`, `build.gradle.kts` — AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01
- `app/` — `dev.pages.badshah222ai` (same package + same keystore as the live app,
  so this stays an UPDATE: versionCode 28 / v8.0.0 is a PROPOSAL, user confirmation
  needed before Phase 2)
  - `MainActivity.kt` — single Activity + NavHost (splash/tour/auth/home/chat/call/tools/memory/settings/admin/help)
  - `BadshahApp.kt` — manual DI container (no DI plugin)
  - `ui/theme/` — `Theme.kt` + `Lighting.kt` (copied from the old project, WebView-free),
    `ChatThemes.kt` (5 chat themes)
  - `ui/components/` — `FloatingCircle.kt` (gold arch + 3 orbit rings + poster centre cut;
    floating avatar ONLY, never the call screen), `HeroCallStage.kt` (call screen hero)
  - `features/*` — Splash (premium mock), Auth, Home, Chat (real /api/chat), Call
    (minimize + End), Tools (real /api/tools/*), Memory (real /api/memories),
    Settings (theme picker), Admin, Help, PermissionTour (camera asked ONCE, in the tour)
  - `data/api/BadshahApi.kt` — OkHttp client for the worker (`https://badshah222-ai.pages.dev`)
  - `data/live/LiveCallManager.kt` — call state machine (minimize support)
  - `core/CallSession.kt` — app-wide call holder
  - `res/drawable-nodpi/` — `call-bg.jpg`, `char-full.webp`, `char-half.webp`,
    `char-face.jpg`, `poster_cut_avatar.png` (tight crop of poster-cut, no text),
    `crown_gold.png` (luminance-keyed realistic crown)

## Build (this VM)

The Gradle wrapper cannot download its distribution here (the egress proxy breaks
Java's HTTPS tunneling — `NoSuchElementException` in `HttpURLConnection.doTunneling`).
Workaround that works: a direct Gradle install was fetched with curl into
`~/workspace/native-rebuild/tools/gradle-8.10.2` (local toolchain, NOT for the repo):

```bash
export JAVA_HOME=/home/hatch/workspace/jdk17
~/workspace/native-rebuild/tools/gradle-8.10.2/bin/gradle :app:assembleDebug --console=plain
```

Gradle itself reads the proxy from `~/.gradle/gradle.properties` (runtime-managed),
so dependency downloads work. First build downloads AGP/Kotlin/Compose (~500MB).

On a normal dev machine (or Codespaces) the wrapper works as usual:
`./gradlew :app:assembleDebug`.

## What is NOT in this scaffold yet (Phase 2/3)

- Firebase (auth/messaging/crashlytics) + `google-services.json` — copy from the old
  project in Phase 2; the google-services plugin is deliberately not applied yet.
- `LiveSession` (native Gemini Live WebSocket), `SupabaseApi`, `CallService`,
  `FloatingOrbService`, `ScreenShareService`, `AgentService`, accessibility service —
  audit + move over from the old project in Phase 2/3.
- Signing: `keystore.properties` (local, gitignored) or `BADSHAH_KEYSTORE_*` env vars
  pointing at `~/workspace/badshah-ai-complete/keystore/badshah-ai.keystore`.
- No store publishing from this module until Phase 5 + user go-ahead.

## Rules (from the user's decisions)

- No `android.webkit.*` anywhere. Ever. (Uptodown rejected the WebView shell twice.)
- Poster is NEVER the whole avatar — always the centre cut.
- No `BADSHAH is ready / Classic voice mode` text anywhere.
- Camera permission once, in the tour. Front camera auto-ON at call start.
- Call minimize keeps the call running; End only via the End button.
- Website (Chrome wali) mein koi change nahi — themes/features sirf native app.
