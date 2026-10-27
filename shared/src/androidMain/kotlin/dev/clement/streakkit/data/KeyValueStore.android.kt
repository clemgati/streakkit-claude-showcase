package dev.clement.streakkit.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Android needs an `Application` context before it can touch storage, and `expect fun` takes no
 * parameters — so the app hands the context over once at startup.
 *
 * Call this from `Application.onCreate()`. `androidApp/.../StreakKitApplication.kt` does.
 */
object StreakKitAndroid {
    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    internal fun requireContext(): Context = appContext ?: error(
        "StreakKitAndroid.initialize(context) was never called. " +
            "Add it to your Application.onCreate() before creating the store.",
    )
}

internal actual fun platformKeyValueStore(): KeyValueStore = SharedPreferencesKeyValueStore(
    StreakKitAndroid
        .requireContext()
        .getSharedPreferences("streakkit", Context.MODE_PRIVATE),
)

internal class SharedPreferencesKeyValueStore(
    private val prefs: SharedPreferences,
) : KeyValueStore {
    override fun read(key: String): String? = prefs.getString(key, null)

    override fun write(
        key: String,
        value: String,
    ) {
        prefs.edit().putString(key, value).apply()
    }

    override fun clear(key: String) {
        prefs.edit().remove(key).apply()
    }
}
