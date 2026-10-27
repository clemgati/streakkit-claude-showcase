package dev.clement.streakkit.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.clement.streakkit.StreakKit
import dev.clement.streakkit.android.theme.StreakKitTheme
import dev.clement.streakkit.android.ui.HabitListScreen
import dev.clement.streakkit.presentation.HabitsStore

class MainActivity : ComponentActivity() {
    /**
     * Created once per Activity instance. A real app would hoist this into a ViewModel so it
     * survives configuration changes; the sample keeps the graph visible instead.
     */
    private lateinit var store: HabitsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        store = StreakKit.createStore()
        store.refreshQuote()

        setContent {
            StreakKitTheme {
                HabitListScreen(store = store)
            }
        }
    }
}
