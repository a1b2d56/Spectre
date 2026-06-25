plugins {
    id("com.android.application")           version "9.2.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.20" apply false
    id("com.google.devtools.ksp")           version "2.3.9" apply false
    id("com.google.dagger.hilt.android")    version "2.59.2" apply false
}

// Force kotlin-metadata-jvm to match Kotlin 2.3.20 (metadata format 2.4.0)
subprojects {
    configurations.all {
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-metadata-jvm:2.3.20")
        }
    }
}
