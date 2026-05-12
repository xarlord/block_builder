# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Moshi
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Kotlin
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata { *; }

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
