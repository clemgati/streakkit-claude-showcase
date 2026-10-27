package dev.clement.streakkit.data

/**
 * The entire persistence contract. An interface rather than an `expect class` so that tests can
 * supply a fake without needing a platform; the platform-specific *construction* is what varies,
 * and that is what [platformKeyValueStore] expects/actuals.
 */
interface KeyValueStore {
    fun read(key: String): String?

    fun write(
        key: String,
        value: String,
    )

    fun clear(key: String)
}

/**
 * Android resolves this to `SharedPreferences`, iOS to `NSUserDefaults`.
 *
 * This is one of the two `expect` declarations in the project; the other is
 * [dev.clement.streakkit.platform.platformName]. `/kmp:parity-check` and the
 * `kmp-parity-auditor` subagent both assert that every `expect` here has an `actual` on both
 * platforms.
 */
internal expect fun platformKeyValueStore(): KeyValueStore
