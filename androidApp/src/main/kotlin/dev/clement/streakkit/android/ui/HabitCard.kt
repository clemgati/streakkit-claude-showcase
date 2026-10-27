package dev.clement.streakkit.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.clement.streakkit.android.theme.StreakKitTheme
import dev.clement.streakkit.presentation.DayMark
import dev.clement.streakkit.presentation.HabitSummary
import kotlin.math.roundToInt

@Composable
fun HabitCard(
    habit: HabitSummary,
    onToggleToday: () -> Unit,
    onToggleDay: (isoDate: String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (habit.checkedInToday) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(habit.emoji, style = MaterialTheme.typography.headlineSmall)

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = habit.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = streakLabel(habit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                TextButton(onClick = onToggleToday) {
                    Text(if (habit.checkedInToday) "Undo" else "Check in")
                }
            }

            WeekStrip(week = habit.week, onToggleDay = onToggleDay)

            // Sits directly under the strip because it counts exactly those seven days
            // (StreakCalculator invariant 5). The label itself comes from the shared module.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = habit.weeklyGoalLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color =
                        if (habit.weeklyGoalMet) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    fontWeight = if (habit.weeklyGoalMet) FontWeight.SemiBold else FontWeight.Normal,
                )
                if (habit.weeklyGoalMet) {
                    Text(
                        text = "🎯",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { habit.completionRate30d },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${(habit.completionRate30d * 100).roundToInt()}% of the last 30 days",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onDelete) {
                        Text("Delete", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

/** Seven tappable day cells. The dates come from the shared `StreakCalculator.recentDays`. */
@Composable
private fun WeekStrip(
    week: List<DayMark>,
    onToggleDay: (isoDate: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        week.forEach { day ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = day.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier =
                        Modifier
                            .size(32.dp)
                            .background(
                                color =
                                    if (day.isChecked) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        Color.Transparent
                                    },
                                shape = CircleShape,
                            ).border(
                                width = if (day.isToday) 2.dp else 1.dp,
                                color =
                                    if (day.isToday) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                shape = CircleShape,
                            ).clickable { onToggleDay(day.isoDate) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (day.isChecked) {
                        Text(
                            text = "✓",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }
}

private fun streakLabel(habit: HabitSummary): String = when {
    habit.currentStreak == 0 && habit.longestStreak == 0 -> "No check-ins yet"
    habit.currentStreak == 0 -> "Streak broken · best ${habit.longestStreak}"
    else -> "🔥 ${habit.currentStreak} day streak · best ${habit.longestStreak}"
}

@Preview
@Composable
private fun HabitCardPreview() {
    StreakKitTheme {
        HabitCard(
            habit =
                HabitSummary(
                    id = "preview",
                    name = "Read 20 pages",
                    emoji = "📚",
                    currentStreak = 5,
                    longestStreak = 12,
                    completionRate30d = 0.6f,
                    checkedInToday = true,
                    week =
                        listOf("M", "T", "W", "T", "F", "S", "S").mapIndexed { index, label ->
                            DayMark(
                                label = label,
                                isChecked = index >= 2,
                                isToday = index == 6,
                                isoDate = "2026-10-2$index",
                            )
                        },
                    weeklyGoal = 5,
                    weeklyDone = 5,
                    weeklyGoalLabel = "5 / 5 this week",
                ),
            onToggleToday = {},
            onToggleDay = {},
            onDelete = {},
        )
    }
}
