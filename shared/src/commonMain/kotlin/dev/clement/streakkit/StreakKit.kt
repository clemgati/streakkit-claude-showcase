package dev.clement.streakkit

import dev.clement.streakkit.data.HabitRepository
import dev.clement.streakkit.data.platformKeyValueStore
import dev.clement.streakkit.platform.systemToday
import dev.clement.streakkit.presentation.HabitsStore
import dev.clement.streakkit.remote.HttpQuoteApi
import dev.clement.streakkit.remote.createHttpClient

/**
 * The single entry point both apps call. Hand-rolled instead of a DI framework because the graph
 * is three objects deep and a reader should be able to see all of it at once.
 *
 * Swift: `StreakKit.shared.createStore()`  ·  Kotlin: `StreakKit.createStore()`
 */
object StreakKit {
    fun createStore(): HabitsStore {
        val repository = HabitRepository(platformKeyValueStore())
        repository.seedIfEmpty(systemToday())
        return HabitsStore(
            repository = repository,
            quoteApi = HttpQuoteApi(createHttpClient()),
        )
    }
}
