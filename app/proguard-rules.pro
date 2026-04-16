# Keep attributes required by reflection users (Hilt, Room, OkHttp).
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# Room: runtime inspects entities & DAOs via generated code that references them by name.
-keep class com.soap4tv.app.data.local.entity.** { *; }
-keep class com.soap4tv.app.data.local.dao.** { *; }
-keep class com.soap4tv.app.data.local.AppDatabase { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Kotlin / coroutines
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.debug.**

# OkHttp / Okio — bundled rules cover most, silence platform warnings.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Jsoup — reflection-free, but keep public API to be safe.
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Compose — Material icons referenced by reflection-free code, but keep for safety.
-dontwarn androidx.compose.**
