# ProGuard rules for HexGrid Launcher

# Keep launcher components
-keep class com.hexgrid.launcher.MainActivity { *; }
-keep class com.hexgrid.launcher.ui.** { *; }
-keep class com.hexgrid.launcher.data.** { *; }

# Keep application class
-keep class com.hexgrid.launcher.HexGridLauncherApp { *; }

# Keep Parcelable classes
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep ViewBinding
-keep class * extends androidx.viewbinding.ViewBinding {
    public static * inflate(android.view.LayoutInflater);
    public static * bind(android.view.View);
}

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# AndroidX
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-dontwarn androidx.**

# Coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
