plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("kotlin-parcelize")
}

android {
    namespace   = "com.spectre.app"
    compileSdk  = 36

    defaultConfig {
        applicationId   = "com.spectre.app"
        minSdk          = 34
        targetSdk       = 36
        versionCode     = 1
        versionName     = "0.1.0-alpha"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable          = true
            applicationIdSuffix   = ".debug"
            versionNameSuffix     = "-debug"
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true; buildConfig = true }

    lint {
        disable += setOf("MissingDefaultResource", "ExpensiveKeepRuleInspection")
        abortOnError = false
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/INDEX.LIST",
                "/META-INF/*.kotlin_module",
                "DebugProbesKt.bin"
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
        )
    }
}

dependencies {
    // ── Core ─────────────────────────────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-splashscreen:1.2.0")

    // ── Compose BOM ──────────────────────────────────────────────────────────
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ── Navigation ───────────────────────────────────────────────────────────
    implementation("androidx.navigation:navigation-compose:2.9.8")

    // ── Lifecycle + ViewModel ────────────────────────────────────────────────
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")

    // ── Hilt ─────────────────────────────────────────────────────────────────
    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-android-compiler:2.59.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")
    implementation("androidx.hilt:hilt-work:1.3.0")
    ksp("androidx.hilt:hilt-compiler:1.3.0")

    // ── Room + SQLCipher ─────────────────────────────────────────────────────
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    //noinspection Aligned16KB
    implementation("net.zetetic:sqlcipher-android:4.15.0@aar")
    implementation("androidx.sqlite:sqlite-ktx:2.6.2")

    // ── Security / Keystore ──────────────────────────────────────────────────
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    // ── Networking ───────────────────────────────────────────────────────────
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("com.squareup.okhttp3:logging-interceptor:5.3.2")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:3.0.0")

    // ── Serialisation ────────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // ── DataStore ────────────────────────────────────────────────────────────
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // ── WorkManager ──────────────────────────────────────────────────────────
    implementation("androidx.work:work-runtime-ktx:2.11.2")

    // ── Coroutines ───────────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // ── Image loading (favicons) ─────────────────────────────────────────────
    implementation("io.coil-kt:coil-compose:2.7.0")

    // ── BouncyCastle (Argon2id + RSA-OAEP) ───────────────────────────────────
    implementation("org.bouncycastle:bcprov-jdk18on:1.84")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.84")

    // ── Credential Manager (passkeys) ────────────────────────────────────────
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")

    // ── QR generation ────────────────────────────────────────────────────────
    implementation("com.google.zxing:core:3.5.4")
}
