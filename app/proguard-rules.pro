# UniDownco ProGuard Rules

# Keep native methods and classes
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep youtubedl-android runtime and packages
-keep class com.yausername.** { *; }
-keep interface com.yausername.** { *; }
-dontwarn com.yausername.**

# Jackson databind (used by youtubedl-android for yt-dlp JSON parsing)
-keepattributes *Annotation*,EnclosingMethod,InnerClasses,Signature
-keep class com.fasterxml.jackson.** { *; }
-keep interface com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn javax.xml.**
-dontwarn java.xml.**
-dontwarn java.beans.**

# Apache Commons (used by youtubedl-android for asset extraction)
-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**
-keep class org.apache.commons.io.** { *; }
-dontwarn org.apache.commons.io.**

# Keep UniDownco Database Entities and DAOs
-keep class com.unidownco.core.database.** { *; }
-keep interface com.unidownco.core.database.** { *; }

# Keep Domain Models
-keep class com.unidownco.domain.models.** { *; }

# Keep OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep Kotlinx Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**
