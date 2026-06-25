@file:Suppress("UnstableApiUsage")

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("kotlin-parcelize")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace   = "com.spectre.app"
    compileSdk  = 37

    defaultConfig {
        applicationId   = "com.spectre.app"
        minSdk          = 34
        targetSdk       = 37
        versionCode     = 16
        versionName     = "1.8.8"

        // Optimizations:
        // 1. Strip non-English localizations from dependencies
        androidResources {
            localeFilters += "en"
        }

        // 2. Only package modern 64-bit architectures (API 34+ runs on 64-bit systems)
        ndk {
            abiFilters.addAll(setOf("arm64-v8a", "x86_64"))
        }
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
                "DebugProbesKt.bin",
                "/META-INF/licenses/**",
                "**/kotlin/**/*.kotlin_metadata",
                "**/kotlin/**/*.kotlin_builtins",
                "**/META-INF/*.version"
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
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.miuix.blur)

    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Lifecycle + ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Room + SQLCipher
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    //noinspection Aligned16KB
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite.ktx)

    // Security / Keystore
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
    implementation(libs.converter.kotlinx.serialization)

    // Serialisation
    implementation(libs.kotlinx.serialization.json)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // DocumentFile helper for storage access
    implementation(libs.androidx.documentfile)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Image loading (favicons)
    implementation(libs.coil.compose)

    // BouncyCastle (Argon2id + RSA-OAEP)
    implementation(libs.bcprov.jdk18on)
    implementation(libs.bcpkix.jdk18on)

    // Credential Manager (passkeys)
    //noinspection PlaySdkIndex
    implementation(libs.androidx.credentials)
    //noinspection PlaySdkIndex
    implementation(libs.androidx.credentials.play.services.auth)

    // QR generation
    implementation(libs.core)

    // Autofill Extensions
    implementation(libs.androidx.autofill)
}
