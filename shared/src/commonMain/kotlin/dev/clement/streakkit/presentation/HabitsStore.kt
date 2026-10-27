package dev.clement.streakkit.presentation

import dev.clement.streakkit.data.HabitRepository
import dev.clement.streakkit.domain.Habit
import dev.clement.streakkit.domain.StreakCalculator
import dev.clement.streakkit.platform.platformName
import dev.clement.streakkit.platform.systemToday
import dev.clement.streakkit.remote.OfflineQuotes
import dev.clement.streakkit.remote.Quote
import dev.clement.streakkit.remote.QuoteApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * Shared presentation layer. Compose collects [state] directly; SwiftUI goes through
 * [observeState], which hands each new value to a closure.
 *
 * Keeping this in `commonMain` is the whole point of the project: if a streak renders wrong, there
 * is exactly one place it can be wrong, and `/kmp:parity-check` can assert that both apps consume
 * the same type.
 */
class HabitsStore(
    private val repository: HabitRepository,
    private val quoteApi: QuoteApi,
    private val today: () -> LocalDate = ::systemToday,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private val quoteState = MutableStateFlow(QuoteState())

    val state: StateFlow<HabitsUiState> =
        combine(repository.habits, quoteState) { habits, quote ->
            val now = today()
            HabitsUiState(
                habits = habits.map { it.summarize(now) },
                quote = quote.quote,
                isQuoteLoading = quote.isLoading,
                isQuoteOffline = quote.isOffline,
                platform = platformName,
            )
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = HabitsUiState(platform = platformName),
        )

    // --- intents -------------------------------------------------------------------------------

    /**
     * [weeklyGoal] is clamped rather than rejected: a UI that offers a bad value is a UI bug, and
     * silently dropping the user's new habit over it would be the worse failure.
     */
    fun addHabit(
        name: String,
        emoji: String,
        weeklyGoal: Int = Habit.DEFAULT_WEEKLY_GOAL,
    ) {
        if (name.isBlank()) return
        repository.add(
            name = name,
            emoji = emoji.ifBlank { DEFAULT_EMOJI },
            today = today(),
            weeklyGoal = weeklyGoal.coerceIn(1, StreakCalculator.WEEK_LENGTH),
        )
    }

    fun toggleToday(habitId: String) = repository.toggleCheckIn(habitId, today())

    fun toggleDay(
        habitId: String,
        isoDate: String,
    ) = repository.toggleCheckIn(habitId, LocalDate.parse(isoDate))

    fun deleteHabit(habitId: String) = repository.delete(habitId)

    fun refreshQuote() {
        scope.launch {
            quoteState.value = quoteState.value.copy(isLoading = true)
            val result = runCatching { quoteApi.randomQuote() }
            quoteState.value =
                QuoteState(
                    quote = result.getOrElse { OfflineQuotes.pick(today().dayOfYear) },
                    isLoading = false,
                    isOffline = result.isFailure,
                )
        }
    }

    // --- Swift-facing bridge -------------------------------------------------------------------

    /**
     * Pushes every [HabitsUiState] into [onEach] until the returned subscription is cancelled.
     *
     * Kotlin `Flow` does not bridge to Swift concurrency on its own, so this closure-based shim is
     * the seam. `iosApp/StreakKit/Model/HabitsViewModel.swift` wraps it in an `@Observable`.
     */
    fun observeState(onEach: (HabitsUiState) -> Unit): StoreSubscription {
        val job =
            scope.launch {
                state.collect { onEach(it) }
            }
        return StoreSubscription(job)
    }

    private data class QuoteState(
        val quote: Quote? = null,
        val isLoading: Boolean = false,
        val isOffline: Boolean = false,
    )

    companion object {
        const val DEFAULT_EMOJI: String = "⭐️"
    }
}

/** Handle returned by [HabitsStore.observeState]; call [cancel] from `deinit`. */
class StoreSubscription internal constructor(
    private val job: Job,
) {
    fun cancel() = job.cancel()
}
