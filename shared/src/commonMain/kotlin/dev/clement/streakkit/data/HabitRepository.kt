package dev.clement.streakkit.data

import dev.clement.streakkit.domain.Habit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.serialization.json.Json

/**
 * Single source of truth for habits. Reads once at construction, then keeps the list in a
 * [StateFlow] and writes through on every mutation.
 *
 * [newId] is injected so tests get deterministic ids — the same reason [dev.clement.streakkit.domain.StreakCalculator]
 * takes `today` as a parameter.
 */
class HabitRepository(
    private val store: KeyValueStore,
    private val json: Json = defaultJson,
    private val newId: () -> String = ::randomId,
) {
    private val _habits = MutableStateFlow(readAll())

    val habits: StateFlow<List<Habit>> = _habits.asStateFlow()

    fun add(
        name: String,
        emoji: String,
        today: LocalDate,
        weeklyGoal: Int = Habit.DEFAULT_WEEKLY_GOAL,
    ): Habit {
        val habit = Habit(
            id = newId(),
            name = name.trim(),
            emoji = emoji,
            createdOn = today,
            weeklyGoal = weeklyGoal,
        )
        mutate { it + habit }
        return habit
    }

    fun toggleCheckIn(
        habitId: String,
        date: LocalDate,
    ) {
        mutate { habits -> habits.map { if (it.id == habitId) it.toggling(date) else it } }
    }

    fun setArchived(
        habitId: String,
        archived: Boolean,
    ) {
        mutate { habits -> habits.map { if (it.id == habitId) it.archiving(archived) else it } }
    }

    fun delete(habitId: String) {
        mutate { habits -> habits.filterNot { it.id == habitId } }
    }

    /**
     * Puts three habits with a plausible history on disk the first time the app runs, so the
     * streak UI has something to show. Idempotent.
     */
    fun seedIfEmpty(today: LocalDate) {
        if (_habits.value.isNotEmpty()) return

        fun daysBack(vararg offsets: Int): Set<LocalDate> = offsets.map { today.minus(DatePeriod(days = it)) }.toSet()

        mutate {
            listOf(
                Habit(
                    id = newId(),
                    name = "Read 20 pages",
                    emoji = "📚",
                    createdOn = today.minus(DatePeriod(days = 40)),
                    checkIns = daysBack(0, 1, 2, 3, 4, 7, 8, 12),
                ),
                Habit(
                    id = newId(),
                    name = "Morning run",
                    emoji = "🏃",
                    createdOn = today.minus(DatePeriod(days = 21)),
                    checkIns = daysBack(1, 2, 5, 6, 9),
                ),
                Habit(
                    id = newId(),
                    name = "No phone after 22:00",
                    emoji = "🌙",
                    createdOn = today.minus(DatePeriod(days = 9)),
                    checkIns = daysBack(4, 5),
                ),
            )
        }
    }

    private fun mutate(transform: (List<Habit>) -> List<Habit>) {
        val next = transform(_habits.value)
        _habits.value = next
        store.write(HABITS_KEY, json.encodeToString(next))
    }

    private fun readAll(): List<Habit> {
        val raw = store.read(HABITS_KEY) ?: return emptyList()
        // A corrupt payload must not brick the app; losing demo data is the better failure.
        return runCatching { json.decodeFromString<List<Habit>>(raw) }.getOrElse { emptyList() }
    }

    companion object {
        const val HABITS_KEY: String = "streakkit.habits.v1"

        internal val defaultJson =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
    }
}

private const val ID_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

internal fun randomId(): String = buildString(12) {
    repeat(12) { append(ID_ALPHABET.random()) }
}
