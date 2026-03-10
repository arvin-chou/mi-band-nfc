# Room entities and DAOs
-keep class com.mibandnfc.data.db.entity.** { *; }

# Data model classes used in serialization
-keep class com.mibandnfc.model.** { *; }

# BLE GATT callback subclasses
-keep class * extends android.bluetooth.BluetoothGattCallback { *; }

# WorkManager coroutine workers
-keepclassmembers class * extends androidx.work.CoroutineWorker { *; }

# Hilt generated classes (kept by default, belt-and-suspenders)
-keep class dagger.hilt.internal.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Suppress warnings for javax annotations used by Dagger
-dontwarn javax.annotation.**

# Google Mobile Ads
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**
