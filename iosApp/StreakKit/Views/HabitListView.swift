import Shared
import SwiftUI

struct HabitListView: View {
    @State private var viewModel = HabitsViewModel()
    @State private var isAddSheetPresented = false

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 12) {
                    QuoteCardView(
                        quote: viewModel.state.quote,
                        isLoading: viewModel.state.isQuoteLoading,
                        isOffline: viewModel.state.isQuoteOffline,
                        onRefresh: viewModel.refreshQuote
                    )

                    if !viewModel.state.isEmpty {
                        TodaySummaryView(
                            checkedIn: Int(viewModel.state.checkedInTodayCount),
                            total: viewModel.state.habits.count,
                            bestStreak: Int(viewModel.state.bestCurrentStreak)
                        )
                    }

                    if viewModel.state.isEmpty {
                        emptyState
                    } else {
                        ForEach(viewModel.state.habits) { habit in
                            HabitCardView(
                                habit: habit,
                                onToggleToday: { viewModel.toggleToday(habitId: habit.id) },
                                onToggleDay: { isoDate in
                                    viewModel.toggleDay(habitId: habit.id, isoDate: isoDate)
                                },
                                onDelete: { viewModel.deleteHabit(habitId: habit.id) }
                            )
                        }
                    }

                    // Proof that both apps read the same shared value.
                    Text("shared module · \(viewModel.state.platform)")
                        .font(.caption2)
                        .foregroundStyle(.tertiary)
                        .padding(.top, 8)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("StreakKit")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        isAddSheetPresented = true
                    } label: {
                        Label("New habit", systemImage: "plus")
                    }
                }
            }
            .sheet(isPresented: $isAddSheetPresented) {
                AddHabitView { name, emoji in
                    viewModel.addHabit(name: name, emoji: emoji)
                    isAddSheetPresented = false
                }
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 8) {
            Text("🌱").font(.largeTitle)
            Text("No habits yet").font(.headline)
            Text("Tap + to start a streak.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 64)
    }
}

#Preview {
    HabitListView()
}
