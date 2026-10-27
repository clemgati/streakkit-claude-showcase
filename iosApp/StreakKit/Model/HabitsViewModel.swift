import Foundation
import Observation
import Shared

/// The entire Kotlin↔Swift seam.
///
/// `HabitsStore` lives in the shared module and exposes a Kotlin `StateFlow`, which Swift cannot
/// observe directly. `observeState(onEach:)` turns it into a closure callback; this class turns
/// that callback into `@Observable` state SwiftUI can diff.
///
/// Note what is *not* here: no streak arithmetic, no formatting, no persistence. Every value the
/// views render was computed in `commonMain`, so iOS and Android cannot drift apart.
@Observable
final class HabitsViewModel {
    private let store: HabitsStore
    private var subscription: StoreSubscription?

    /// Last state published by the shared store.
    var state: HabitsUiState

    init() {
        store = StreakKit.shared.createStore()
        state = HabitsUiState(
            habits: [],
            quote: nil,
            isQuoteLoading: false,
            isQuoteOffline: false,
            platform: ""
        )

        // The store collects on Dispatchers.Main, so this closure arrives on the main thread.
        subscription = store.observeState { [weak self] newState in
            self?.state = newState
        }

        store.refreshQuote()
    }

    deinit {
        subscription?.cancel()
    }

    // MARK: - Intents

    /// Kotlin default arguments do not survive the Objective-C bridge, so weeklyGoal is explicit
    /// here where Compose can rely on the default.
    func addHabit(name: String, emoji: String, weeklyGoal: Int32) {
        store.addHabit(name: name, emoji: emoji, weeklyGoal: weeklyGoal)
    }

    func toggleToday(habitId: String) {
        store.toggleToday(habitId: habitId)
    }

    func toggleDay(habitId: String, isoDate: String) {
        store.toggleDay(habitId: habitId, isoDate: isoDate)
    }

    func deleteHabit(habitId: String) {
        store.deleteHabit(habitId: habitId)
    }

    func refreshQuote() {
        store.refreshQuote()
    }
}

/// `HabitSummary` already carries a stable `id`, so `ForEach` can use it directly.
extension HabitSummary: Identifiable {}
