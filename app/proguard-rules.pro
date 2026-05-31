-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**

-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text.** { *; }
-dontwarn com.google.mlkit.**

-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

-dontwarn org.apache.**
-dontwarn javax.**

# Keep Compose internals
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep Coil
-keep class coil.** { *; }
-dontwarn coil.**
