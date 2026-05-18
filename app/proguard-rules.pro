# General Android
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.aivoice.input.model.** { *; }
-keep class com.aivoice.input.ai.RouterAgent$*Dto { *; }

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Accessibility Service
-keep class * extends android.accessibilityservice.AccessibilityService { *; }

# Android components
-keep class * extends android.app.Service { *; }
-keep class * extends android.app.Activity { *; }
