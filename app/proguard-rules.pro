# Kotlin serialization
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** { *** Companion; *** serializer(...); }
-keep @kotlinx.serialization.Serializable class **$Companion { *; }
-keepclassmembers class **$Companion { *** serializer(...); }
-keepclassmembers class ** {
    @kotlinx.serialization.SerialName <fields>;
}
-keepclassmembers class * {
    *** INSTANCE;
}
-keep class kotlinx.serialization.internal.EnumSerializer { *; }
-keep class kotlinx.serialization.internal.ReferenceArraySerializer { *; }
-keep class kotlinx.serialization.internal.ObjectSerializer { *; }

# Retrofit + OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-keep class retrofit2.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# BouncyCastle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# SQLCipher (Zetetic)
-keep class net.zetetic.database.sqlcipher.** { *; }
-dontwarn net.zetetic.database.sqlcipher.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keepclasseswithmembernames class * { @javax.inject.Inject <init>(...); }

# Spectre models (must not be obfuscated — Room + serialisation rely on names)
-keep class com.spectre.app.core.data.** { *; }
-keep class com.spectre.app.core.network.model.** { *; }
-keep class com.spectre.app.core.navigation.** { *; }
-keep interface com.spectre.app.core.navigation.** { *; }

# Compose Navigation Serialisation
-keep class androidx.navigation.** { *; }
-keepclassmembers class androidx.navigation.** { *; }
