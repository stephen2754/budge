# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.example.budge.data.local.entity.** { *; }
-keep class com.example.budge.model.** { *; }
# The backup document is read and written reflectively by Gson, and it is the one
# place where a renamed field is *silent* data loss: R8 used to turn
# `transactions`/`categories` into `a`/`b`, so a release build exported JSON that
# its own import read back as an empty record set. The @SerializedName
# annotations in BackupCodec.kt are the real fix; this keeps the class intact so a
# field added later without an annotation still survives.
-keep class com.example.budge.data.backup.** { *; }
# The GitHub release payload is Gson-reflected too, and it is read from a remote
# response where a silently missing field would look like "no releases".
-keep class com.example.budge.data.update.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
