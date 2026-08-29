-keepattributes *Annotation*
# Retrofit needs generic type information at runtime (esp. Kotlin suspend + Response<T>).
# If Signature/EnclosingMethod are stripped, Retrofit can crash with:
# "Class cannot be cast to java.lang.reflect.ParameterizedType" during service method parsing.
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keep class kotlin.Metadata { *; }

# Keep our app packages (models, managers, API interfaces) to avoid minify removing reflections/serializers
-keep class com.bce.courier.** { *; }

# Keep Retrofit API interfaces
-keep interface com.bce.courier.data.api.** { *; }

# Keep data models used by kotlinx.serialization
-keep class com.bce.courier.data.model.** { *; }
-keepclassmembers class com.bce.courier.data.model.** { *; }

# Keep kotlinx.serialization runtime classes
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# Keep Room annotated classes and members
-keepclassmembers class * {
    @androidx.room.* *;
}
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# Keep ML Kit / native libraries usage (prevent stripping if referenced reflectively)
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Keep OkHttp/Retrofit/Converter packages
-keep class okhttp3.** { *; }
-keep class retrofit2.** { *; }
-keep class com.jakewharton.retrofit2.converter.kotlinx.serialization.** { *; }
-dontwarn okhttp3.**
-dontwarn retrofit2.**

# Retrofit Kotlin suspend support relies on kotlin.coroutines.Continuation being generic at runtime.
# If Continuation generic metadata is stripped, Retrofit can crash while parsing suspend methods.
-keep class kotlin.coroutines.Continuation { *; }
-keep class kotlin.coroutines.jvm.internal.** { *; }
-dontwarn kotlin.coroutines.**

# EncryptedSharedPreferences (androidx.security-crypto) uses Tink + shaded protobuf via reflection.
# When minify/shrink is enabled (even on debug), missing keep rules can break session persistence and appear as "login/network" failures.
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-keep class com.google.crypto.tink.shaded.protobuf.** { *; }
-dontwarn com.google.crypto.tink.shaded.protobuf.**
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# Keep anything that may be loaded by reflection from libraries
-keepclassmembers class * {
    public <init>(...);
}

# If you need to debug minified stacktraces, keep line numbers for debugging builds
-keepattributes SourceFile,LineNumberTable

