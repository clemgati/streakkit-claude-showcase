package dev.clement.streakkit.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus

/**
 * The one piece of real logic in this app, and deliberately so: it is pure, it has edge cases that
 * matter, and both platforms must agree on it exactly.
 *
 * ## Invariants
 * 1. A streak is "current" if it includes **today or yesterday**. Yesterday counts because a user
 *    who has not yet checked in today has not yet broken anything.
 * 2. A check-in two or more days old never contributes to the current streak.
 * 3. [longestStreak] is historical and ignores today entirely.
 * 4. Nothing here reads a clock. The caller supplies `today`, which is what makes it testable.
 * 5. [weeklyProgress] counts the same rolling seven days that [recentDays] draws, so the figure
 *    and the strip can never contradict each other. Rolling, not calendar — see its KDoc.
 */
object StreakCalculator {
    /** Days in the rolling week used by [recentDays] and [weeklyProgress]. */
    const val WEEK_LENGTH: Int = 7

    private val oneDay = DatePeriod(days = 1)

    /** Consecutive days ending today or yesterday. `0` when the streak is broken or unstarted. */
    fun currentStreak(
        checkIns: Set<LocalDate>,
        today: LocalDate,
    ): Int {
        if (checkIns.isEmpty()) return 0

        val yesterday = today.minus(oneDay)
        var cursor =
            when {
                today in checkIns -> today
                yesterday in checkIns -> yesterday
                else -> return 0
            }

        var streak = 0
        while (cursor in checkIns) {
            streak++
            cursor = cursor.minus(oneDay)
        }
        return streak
    }

    /** Longest run of consecutive days ever recorded. */
    fun longestStreak(checkIns: Set<LocalDate>): Int {
        if (checkIns.isEmpty()) return 0

        val ordered = checkIns.sorted()
        var best = 1
        var run = 1
        for (index in 1 until ordered.size) {
            run = if (ordered[index - 1].daysUntil(ordered[index]) == 1) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }

    /**
     * Check-ins falling inside the last [windowDays] days, inclusive of [today].
     *
     * The shared primitive behind both [completionRate] and [weeklyProgress], so the two can
     * never disagree about where a window starts.
     */
    fun checkInsInWindow(
        checkIns: Set<LocalDate>,
        today: LocalDate,
        windowDays: Int,
    ): Int {
        require(windowDays > 0) { "windowDays must be positive, was $windowDays" }

        val windowStart = today.minus(DatePeriod(days = windowDays - 1))
        return checkIns.count { it >= windowStart && it <= today }
    }

    /**
     * Share of the last [windowDays] days (inclusive of [today]) that were checked in, as `0f..1f`.
     * Days before the habit existed are not excluded — this is a plain window, not an average.
     */
    fun completionRate(
        checkIns: Set<LocalDate>,
        today: LocalDate,
        windowDays: Int = 30,
    ): Float = checkInsInWindow(checkIns, today, windowDays).toFloat() / windowDays

    /**
     * Days checked in during the last seven, inclusive of [today] — invariant 5.
     *
     * This is deliberately the *same* window [recentDays] draws, so the "3 / 5 this week" figure
     * can never contradict the ticks on the strip directly above it. It is a rolling seven days,
     * not a calendar week: a Monday reset would make the number drop to zero overnight, which
     * reads as data loss to a user mid-streak.
     */
    fun weeklyProgress(
        checkIns: Set<LocalDate>,
        today: LocalDate,
    ): Int = checkInsInWindow(checkIns, today, WEEK_LENGTH)

    /**
     * The last [days] dates ending at [today], oldest first. Used by both UIs to draw the
     * week strip, so the two platforms cannot disagree about which days are shown.
     */
    fun recentDays(
        today: LocalDate,
        days: Int = WEEK_LENGTH,
    ): List<LocalDate> {
        require(days > 0) { "days must be positive, was $days" }
        return (days - 1 downTo 0).map { offset -> today.minus(DatePeriod(days = offset)) }
    }
}
