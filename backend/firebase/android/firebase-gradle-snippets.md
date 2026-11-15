# Android — Firebase Gradle Snippets & Dependencies

App: **BADSHAH AI** · Package: `dev.pages.badshah222ai` ·
Firebase project: `badshah-ai-badshah222`

Kotlin DSL (`build.gradle.kts`) snippets. Plugin/library versions marked
`<version>` must be set to a current stable release at integration time —
**except Firebase libraries, whose versions are decided by the Firebase
Bill of Materials (BoM)**. Do not pin individual Firebase library
versions; add/remove Firebase libraries without version numbers and let
the BoM align them.

Prerequisite: the real `google-services.json` sits in `app/`
(see `google-services-placement.txt`).

---

## 1. Project-level `build.gradle.kts` (repository root)

```kotlin
// Top-level build file
plugins {
    // ... existing Android / Kotlin plugins ...

    // Firebase — declare here, apply in the app module.
    id("com.google.gms.google-services") version "<version>" apply false
    id("com.google.firebase.crashlytics") version "<version>" apply false
}
```

## 2. App-level `build.gradle.kts` (`app/build.gradle.kts`)

```kotlin
plugins {
    id("com.android.application")
    // ... existing plugins ...

    id("com.google.gms.google-services")   // reads app/google-services.json
    id("com.google.firebase.crashlytics")  // Crashlytics + mapping upload
}

android {
    // namespace / applicationId must remain:
    // applicationId = "dev.pages.badshah222ai"
}

dependencies {
    // Firebase Bill of Materials — controls all Firebase versions below.
    implementation(platform("com.google.firebase:firebase-bom:<bom-version>"))

    // Authentication (Email/Password + Google credential exchange)
    implementation("com.google.firebase:firebase-auth-ktx")

    // Analytics (Google Analytics for Firebase)
    implementation("com.google.firebase:firebase-analytics-ktx")

    // Cloud Messaging (push notifications)
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Crashlytics (crash reporting)
    implementation("com.google.firebase:firebase-crashlytics-ktx")

    // Google Sign-In client (used to obtain the Google ID token that is
    // exchanged for a Firebase credential — see Doc 02).
    implementation("com.google.android.gms:play-services-auth:<version>")
}
```

Notes:

- `<bom-version>`: use the current Firebase BoM release at integration
  time (check the Firebase Android SDK release notes). All
  `firebase-*-ktx` artifacts then resolve to the BoM-managed versions.
- `play-services-auth` is **not** part of the Firebase BoM — it carries
  its own `<version>`.
- The google-services plugin generates `R.string.default_web_client_id`
  from `google-services.json`; Doc 02's Google Sign-In code depends on
  it. If that resource is missing, the Google provider is not enabled or
  the JSON is stale — re-download it (Doc 01 Step 3), do not hard-code an
  ID.
- Crashlytics with R8/ProGuard: the plugin uploads mapping files for
  release builds automatically; keep `minifyEnabled` builds as they are
  and do not disable the upload task.

## 3. AndroidManifest.xml — FCM default notification channel

Inside `<application>`, so notifications received while the app is in the
background/killed use the right channel and icon:

```xml
<!-- Default notification channel for FCM messages.
     The value MUST match the NotificationChannel created in code. -->
<meta-data
    android:name="com.google.firebase.messaging.default_notification_channel_id"
    android:value="badshah_ai_default" />

<!-- Optional: default notification icon (use the app's own drawable) -->
<meta-data
    android:name="com.google.firebase.messaging.default_notification_icon"
    android:resource="@drawable/ic_stat_notification" />

<!-- Optional: default notification color -->
<meta-data
    android:name="com.google.firebase.messaging.default_notification_color"
    android:resource="@color/notification_accent" />
```

And the FCM service (only needed once you handle messages/tokens in code —
the messaging library declares its own service for basic receipt, but a
custom service is required for `onMessageReceived` / `onNewToken`):

```xml
<service
    android:name=".fcm.BadshahFirebaseMessagingService"
    android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>
```

Runtime obligations (code, not manifest):

- Create the `badshah_ai_default` NotificationChannel on Android 8+
  before notifications are expected.
- Request `POST_NOTIFICATIONS` at runtime on Android 13+ (declare it in
  the manifest as a normal uses-permission as well):

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.INTERNET" />
```

## 4. Dependency summary

| Library | Purpose | Version source |
|---|---|---|
| `com.google.firebase:firebase-bom` | Aligns all Firebase versions | Pinned by you (current release) |
| `com.google.firebase:firebase-auth-ktx` | Firebase Authentication | BoM |
| `com.google.firebase:firebase-analytics-ktx` | Firebase Analytics | BoM |
| `com.google.firebase:firebase-messaging-ktx` | FCM push notifications | BoM |
| `com.google.firebase:firebase-crashlytics-ktx` | Crashlytics | BoM |
| `com.google.android.gms:play-services-auth` | Google Sign-In client | Own version (not in BoM) |
| Plugin `com.google.gms.google-services` | Processes `google-services.json` | Own version |
| Plugin `com.google.firebase.crashlytics` | Crashlytics build integration | Own version |

Optional later (not required for this setup): `firebase-config-ktx`
(Remote Config), `firebase-firestore-ktx` / `firebase-database-ktx`
(only if a Firebase database is ever adopted — BADSHAH AI's database
stays on Supabase).
