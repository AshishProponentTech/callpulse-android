# R8 rules (§10).
#
# "A minified build that crashes on JSON parsing is the classic Stage F failure." The rules
# below exist so that assembleProductionRelease behaves identically to the debug build — which
# is why §19 #24 requires the whole QA matrix to be run against the minified build, not debug.

# ---- kotlinx.serialization ----------------------------------------------------------------
# Serializers are looked up reflectively from the companion object; R8 cannot see that link.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
    static **$* *;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep the DTOs themselves — their field names are the wire format.
-keep @kotlinx.serialization.Serializable class com.proponenttech.callpulse.** { *; }

# ---- Retrofit / OkHttp --------------------------------------------------------------------
# Retrofit builds implementations from the interface's generic signatures and annotations.
-keepattributes Signature, Exceptions, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation interface <1>
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---- Room / SQLCipher ---------------------------------------------------------------------
-keep class androidx.room.RoomDatabase { *; }
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.**

# ---- Hilt / Dagger ------------------------------------------------------------------------
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# ---- WorkManager --------------------------------------------------------------------------
# Workers are instantiated by name from the WorkManager database.
-keep class * extends androidx.work.ListenableWorker { public <init>(...); }

# ---- Diagnostics --------------------------------------------------------------------------
# Keep line numbers so a stack trace posted to /telemetry/events is actually readable, but
# hide the original file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
