import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

// ---- Release signing: read from keystore.properties (local) or environment (CI). Never committed. ----
// Same keystore as the live app so this build stays an UPDATE, not a new app:
//   ~/workspace/badshah-ai-complete/keystore/badshah-ai.keystore (alias "badshah")
val ksProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(propKey: String, envKey: String): String? =
    ksProps.getProperty(propKey) ?: System.getenv(envKey)

val releaseStoreFile = signingValue("storeFile", "BADSHAH_KEYSTORE_FILE")
val hasReleaseSigning = releaseStoreFile != null && file(releaseStoreFile).exists() &&
    signingValue("storePassword", "BADSHAH_KEYSTORE_PASSWORD") != null &&
    signingValue("keyAlias", "BADSHAH_KEY_ALIAS") != null &&
    signingValue("keyPassword", "BADSHAH_KEY_PASSWORD") != null

android {
    namespace = "dev.pages.badshah222ai"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.pages.badshah222ai" // must match Firebase (google-services.json, Phase 2)
        minSdk = 26
        targetSdk = 35
        // PROPOSAL (user confirmation needed before Phase 2): first native release = v8.0.0, code 28.
        // Live WebView app is v7.7.2 / code 27 — this must stay above it.
        versionCode = 28
        versionName = "8.0.0"
        // Public backend values (already shipped in the website's public
        // config.js) — gradle.properties may override them locally/CI.
        val supabaseUrl = providers.gradleProperty("SUPABASE_URL")
            .orElse("https://aghlacmqlagmigoznxlm.supabase.co").get()
        val supabaseKey = providers.gradleProperty("SUPABASE_PUBLISHABLE_KEY")
            .orElse("sb_publishable_cxY5POeM8kbv0CyJ4LAJ_Q_ao8vaFKn").get()
        val workerUrl = providers.gradleProperty("WORKER_BASE_URL")
            .orElse("https://badshah222-ai.pages.dev").get()
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"$supabaseKey\"")
        buildConfigField("String", "WORKER_BASE_URL", "\"$workerUrl\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = signingValue("storePassword", "BADSHAH_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "BADSHAH_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "BADSHAH_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug { isDebuggable = true }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Firebase (project badshah-ai-badshah222) — Auth now; Messaging later.
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-analytics")

    // Google sign-in via Credential Manager.
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // CameraX — call camera (front auto-on at call start, front/back switch).
    val cameraxVersion = "1.4.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
