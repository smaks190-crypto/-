# Room Database & Entities ProGuard/R8 rules for :core:database
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.Dao
-keep class **_Impl { *; }
-dontwarn androidx.room.paging.**
-keep class com.example.data.db.** { *; }
-keepclassmembers class com.example.data.db.** {
    <fields>;
    <methods>;
}
