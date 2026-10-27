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
    val weeklyGoal: Int,
    val weeklyDone: Int,
    /** Pre-rendered so neither UI can format it differently. */
    val weeklyGoalLabel: String,
    /**
     * Pre-rendered for the same reason.
     *
     * Previously each app built this string itself — `roundToInt()` on Android and
     * `Int(_.rounded())` on iOS. Those happen to agree, but they are not the same function:
     * Kotlin rounds half away from zero, Swift's `rounded()` rounds half away from zero too,
     * while `String(format:)` rounds half to even. Three plausible spellings, two behaviours,
     * one of them silently different at the .5 boundary. Rendering it once removes the question.
     */
    val completionLabel: String,
    /**
     * Pre-rendered for the same reason, and the one that had actually drifted furthest: it was a
     * `when` in Compose and a chain of `if`s in SwiftUI, each spelling out the same three strings.
     * They happened to match. Nothing made them match.
     */
    val streakLabel: String,
) {
    val weeklyGoalMet: Boolean get() = weeklyDone >= weeklyGoal
}

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
    weeklyGoal = weeklyGoal,
    weeklyDone = StreakCalculator.weeklyProgress(checkIns, today),
    // Formatted here, once. If either UI built this string itself the two could drift —
    // which is the defect class this whole module exists to prevent.
    weeklyGoalLabel = "${StreakCalculator.weeklyProgress(checkIns, today)} / $weeklyGoal this week",
    completionLabel = formatPercent(StreakCalculator.completionRate(checkIns, today)) +
        "% of the last 30 days",
    streakLabel = streakLabel(
        current = StreakCalculator.currentStreak(checkIns, today),
        longest = StreakCalculator.longestStreak(checkIns),
    ),
)

/**
 * Whole-percent formatting, half away from zero, with no platform number formatter involved.
 *
 * Deliberately arithmetic rather than a locale-aware formatter: this string must be byte-identical
 * on both platforms, and a locale-aware formatter is the opposite of that.
 */
internal fun formatPercent(rate: Float): String = ((rate * 100f) + 0.5f).toInt().toString()

/** The three states a streak line can be in. Both apps render the result verbatim. */
internal fun streakLabel(
    current: Int,
    longest: Int,
): String = when {
    current == 0 && longest == 0 -> "No check-ins yet"
    current == 0 -> "Streak broken · best $longest"
    else -> "🔥 $current day streak · best $longest"
}
