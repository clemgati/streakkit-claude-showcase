package dev.clement.streakkit.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.clement.streakkit.presentation.HabitsStore

/**
 * The whole Android UI. It reads [HabitsStore.state] — the same shared type SwiftUI renders — so
 * neither platform owns any presentation logic of its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitListScreen(
    store: HabitsStore,
    modifier: Modifier = Modifier,
) {
    val state by store.state.collectAsStateWithLifecycle()
    var isAddSheetVisible by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text("StreakKit") })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { isAddSheetVisible = true },
                text = { Text("New habit") },
                icon = { Text("＋") },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = contentPadding.calculateTopPadding() + 12.dp,
                    bottom = contentPadding.calculateBottomPadding() + 96.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                QuoteCard(
                    quote = state.quote,
                    isLoading = state.isQuoteLoading,
                    isOffline = state.isQuoteOffline,
                    onRefresh = store::refreshQuote,
                )
            }

            if (!state.isEmpty) {
                item {
                    TodaySummary(
                        checkedIn = state.checkedInTodayCount,
                        total = state.habits.size,
                        bestStreak = state.bestCurrentStreak,
                    )
                }
            }

            if (state.isEmpty) {
                item { EmptyState() }
            } else {
                items(state.habits, key = { it.id }) { habit ->
                    HabitCard(
                        habit = habit,
                        onToggleToday = { store.toggleToday(habit.id) },
                        onToggleDay = { isoDate -> store.toggleDay(habit.id, isoDate) },
                        onDelete = { store.deleteHabit(habit.id) },
                    )
                }
            }

            item {
                // Proof that both apps read the same shared value.
                Text(
                    text = "shared module · ${state.platform}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                )
            }
        }
    }

    if (isAddSheetVisible) {
        AddHabitDialog(
            onDismiss = { isAddSheetVisible = false },
            onConfirm = { name, emoji ->
                store.addHabit(name, emoji)
                isAddSheetVisible = false
            },
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("🌱", style = MaterialTheme.typography.displaySmall)
        Text("No habits yet", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Tap “New habit” to start a streak.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
