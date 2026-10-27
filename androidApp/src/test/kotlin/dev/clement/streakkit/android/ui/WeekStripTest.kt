package dev.clement.streakkit.android.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.clement.streakkit.presentation.DayMark
import dev.clement.streakkit.presentation.HabitSummary
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Interaction tests for the week strip — the one part of this UI a user taps that is not a
 * `Button`, and therefore the one place a tap target can silently stop working.
 *
 * Runs on the JVM under Robolectric rather than on a device, so CI needs no emulator. The iOS
 * counterpart is `iosApp/StreakKitUITests/WeekStripUITests.swift`, which asserts the same
 * behaviour through the same accessibility identifiers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeekStripTest {
    @get:Rule
    val compose = createComposeRule()

    private fun summary(checkedIndices: Set<Int>) = HabitSummary(
        id = "test",
        name = "Floss",
        emoji = "🦷",
        currentStreak = 0,
        longestStreak = 0,
        completionRate30d = 0f,
        checkedInToday = false,
        week = (0..6).map { index ->
            DayMark(
                label = "D",
                isChecked = index in checkedIndices,
                isToday = index == 6,
                isoDate = "2026-10-${20 + index}",
            )
        },
        weeklyGoal = 5,
        weeklyDone = checkedIndices.size,
        weeklyGoalLabel = "${checkedIndices.size} / 5 this week",
        completionLabel = "0% of the last 30 days",
        streakLabel = "No check-ins yet",
    )

    private fun hasState(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    @Test
    fun `tapping an unchecked day reports the tap`() {
        var toggled: String? = null
        compose.setContent {
            HabitCard(
                habit = summary(checkedIndices = emptySet()),
                onToggleToday = {},
                onToggleDay = { toggled = it },
                onDelete = {},
            )
        }

        // An unchecked cell draws a transparent fill. On iOS that made the cell unhittable
        // until `.contentShape(Circle())` was added; this asserts the Compose side never had
        // the same hole, and fails loudly if someone removes the clickable modifier.
        compose.onNodeWithTag("week-day-2026-10-22").performClick()

        assert(toggled == "2026-10-22") { "expected the ISO date of the tapped cell, got $toggled" }
    }

    @Test
    fun `every day in the strip is tappable, not just the checked ones`() {
        val tapped = mutableListOf<String>()
        compose.setContent {
            HabitCard(
                habit = summary(checkedIndices = setOf(0, 1)),
                onToggleToday = {},
                onToggleDay = { tapped += it },
                onDelete = {},
            )
        }

        (0..6).forEach { index ->
            compose.onNodeWithTag("week-day-2026-10-${20 + index}").performClick()
        }

        assert(tapped.size == 7) { "expected all seven cells to register a tap, got ${tapped.size}" }
    }

    @Test
    fun `cells announce their checked state to accessibility services`() {
        compose.setContent {
            HabitCard(
                habit = summary(checkedIndices = setOf(0, 1, 2)),
                onToggleToday = {},
                onToggleDay = {},
                onDelete = {},
            )
        }

        compose.onAllNodesWithTag("week-day-2026-10-20", useUnmergedTree = true)
            .assertCountEquals(1)
        compose.onNodeWithTag("week-day-2026-10-20").assert(hasState(DayMark.CHECKED))
        compose.onNodeWithTag("week-day-2026-10-26").assert(hasState(DayMark.NOT_CHECKED))
    }
}
