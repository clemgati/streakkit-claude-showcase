package dev.clement.streakkit.data

/**
 * Test double for [KeyValueStore]. Lives in `commonTest`, so it runs on every target — the same
 * repository tests execute on the JVM and on Kotlin/Native.
 */
class InMemoryKeyValueStore(
    initial: Map<String, String> = emptyMap(),
) : KeyValueStore {
    private val values = initial.toMutableMap()

    var writeCount: Int = 0
        private set

    override fun read(key: String): String? = values[key]

    override fun write(
        key: String,
        value: String,
    ) {
        values[key] = value
        writeCount++
    }

    override fun clear(key: String) {
        values.remove(key)
    }

    fun snapshot(): Map<String, String> = values.toMap()
}
