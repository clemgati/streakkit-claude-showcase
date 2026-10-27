package dev.clement.streakkit.data

import platform.Foundation.NSUserDefaults

internal actual fun platformKeyValueStore(): KeyValueStore =
    UserDefaultsKeyValueStore(NSUserDefaults.standardUserDefaults)

internal class UserDefaultsKeyValueStore(
    private val defaults: NSUserDefaults,
) : KeyValueStore {
    override fun read(key: String): String? = defaults.stringForKey(key)

    override fun write(
        key: String,
        value: String,
    ) {
        defaults.setObject(value, forKey = key)
    }

    override fun clear(key: String) {
        defaults.removeObjectForKey(key)
    }
}
