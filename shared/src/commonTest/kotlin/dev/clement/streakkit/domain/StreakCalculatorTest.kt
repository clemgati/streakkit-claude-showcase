package dev.clement.streakkit.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * These tests encode the invariants documented on [StreakCalculator]. They are the reference the
 * `kmp-parity-auditor` subagent and `/kmp:parity-check` point at when either platform's streak
 * display looks wrong.
 */
class StreakCalculatorTest {
    private val today = LocalDate.parse("2026-10-26")

    private fun daysAgo(vararg offsets: Int): Set<LocalDate> =
        offsets.map { today.minus(DatePeriod(days = it)) }.toSet()

    // --- currentStreak -------------------------------------------------------------------------

    @Test
    fun `no check-ins means no streak`() {
        assertEquals(0, StreakCalculator.currentStreak(emptySet(), today))
    }

    @Test
    fun `checked in today only is a streak of one`() {
        assertEquals(1, StreakCalculator.currentStreak(daysAgo(0), today))
    }

    @Test
    fun `checked in yesterday but not yet today still counts`() {
        // Invariant 1: the day is not over, so the streak is not broken.
        assertEquals(1, StreakCalculator.currentStreak(daysAgo(1), today))
    }

    @Test
    fun `consecutive days ending today accumulate`() {
        assertEquals(4, StreakCalculator.currentStreak(daysAgo(0, 1, 2, 3), today))
    }

    @Test
    fun `consecutive days ending yesterday accumulate`() {
        assertEquals(3, StreakCalculator.currentStreak(daysAgo(1, 2, 3), today))
    }

    @Test
    fun `a two-day-old check-in does not start a current streak`() {
        // Invariant 2: nothing older than yesterday can anchor a current streak.
        assertEquals(0, StreakCalculator.currentStreak(daysAgo(2, 3, 4, 5), today))
    }

    @Test
    fun `a gap stops the count without erasing it`() {
        assertEquals(2, StreakCalculator.currentStreak(daysAgo(0, 1, 3, 4, 5), today))
    }

    @Test
    fun `future check-ins are ignored by the current streak`() {
        val withTomorrow = daysAgo(0, 1) + today.minus(DatePeriod(days = -1))
        assertEquals(2, StreakCalculator.currentStreak(withTomorrow, today))
    }

    // --- longestStreak -------------------------------------------------------------------------

    @Test
    fun `longest streak is historical and ignores recency`() {
        // Invariant 3: a five-day run last month beats a two-day run right now.
        val checkIns = daysAgo(0, 1) + daysAgo(20, 21, 22, 23, 24)
        assertEquals(5, StreakCalculator.longestStreak(checkIns))
    }

    @Test
    fun `longest streak of a single check-in is one`() {
        assertEquals(1, StreakCalculator.longestStreak(daysAgo(9)))
    }

    @Test
    fun `longest streak of nothing is zero`() {
        assertEquals(0, StreakCalculator.longestStreak(emptySet()))
    }

    // --- completionRate ------------------------------------------------------------------------

    @Test
    fun `completion rate counts only days inside the window`() {
        val checkIns = daysAgo(0, 1, 2) + daysAgo(40, 41)
        assertEquals(3f / 30f, StreakCalculator.completionRate(checkIns, today))
    }

    @Test
    fun `completion rate is one when every day in the window is checked`() {
        assertEquals(1f, StreakCalculator.completionRate(daysAgo(0, 1, 2, 3, 4, 5, 6), today, windowDays = 7))
    }

    @Test
    fun `completion rate rejects a non-positive window`() {
        assertFailsWith<IllegalArgumentException> {
            StreakCalculator.completionRate(daysAgo(0), today, windowDays = 0)
        }
    }

    // --- recentDays ----------------------------------------------------------------------------

    @Test
    fun `recent days are oldest first and end today`() {
        val week = StreakCalculator.recentDays(today)
        assertEquals(7, week.size)
        assertEquals(today.minus(DatePeriod(days = 6)), week.first())
        assertEquals(today, week.last())
    }
}
