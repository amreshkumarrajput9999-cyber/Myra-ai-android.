# Proguard rules for MYRA AI
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class com.myra.ai.network.** { *; }
