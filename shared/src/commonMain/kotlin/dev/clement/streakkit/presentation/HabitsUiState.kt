package dev.clement.streakkit.presentation

import dev.clement.streakkit.domain.Habit
import dev.clement.streakkit.domain.StreakCalculator
import dev.clement.streakkit.remote.Quote
import kotlinx.datetime.LocalDate

/**
 * Everything either UI needs to render, already computed.
 *
 * Both platforms consume this exact type, so a presentation bug can only be fixed once. Nothing
 * here is a `kotlinx-datetime` type: day labels are pre-formatted strings, which keeps the
 * Swift-facing surface free of bridged value classes.
 */
data class HabitsUiState(
    val habits: List<HabitSummary> = emptyList(),
    val quote: Quote? = null,
    val isQuoteLoading: Boolean = false,
    val isQuoteOffline: Boolean = false,
    val platform: String = "",
) {
    val checkedInTodayCount: Int get() = habits.count { it.checkedInToday }
    val bestCurrentStreak: Int get() = habits.maxOfOrNull { it.currentStreak } ?: 0
    val isEmpty: Boolean get() = habits.isEmpty()
}

data class HabitSummary(
    val id: String,
    val name: String,
    val emoji: String,
    val currentStreak: Int,
    val longestStreak: Int,
    val completionRate30d: Float,
    val checkedInToday: Boolean,
    val week: List<DayMark>,
)

/** One cell of the seven-day strip under each habit. */
data class DayMark(
    val label: String,
    val isChecked: Boolean,
    val isToday: Boolean,
    val isoDate: String,
)

internal fun Habit.summarize(today: LocalDate): HabitSummary = HabitSummary(
    id = id,
    name = name,
    emoji = emoji,
    currentStreak = StreakCalculator.currentStreak(checkIns, today),
    longestStreak = StreakCalculator.longestStreak(checkIns),
    completionRate30d = StreakCalculator.completionRate(checkIns, today),
    checkedInToday = isCheckedIn(today),
    week =
        StreakCalculator.recentDays(today).map { date ->
            DayMark(
                label = date.dayOfWeek.name.take(1),
                isChecked = isCheckedIn(date),
                isToday = date == today,
                isoDate = date.toString(),
            )
        },
)
