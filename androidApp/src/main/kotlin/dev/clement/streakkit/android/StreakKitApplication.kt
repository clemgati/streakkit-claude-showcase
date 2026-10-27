package dev.clement.streakkit.android

import android.app.Application
import dev.clement.streakkit.data.StreakKitAndroid

/**
 * Hands the application context to the shared module so `platformKeyValueStore()` can resolve
 * `SharedPreferences`. This is the one piece of Android-specific wiring the shared code needs.
 */
class StreakKitApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        StreakKitAndroid.initialize(this)
    }
}
