import Shared
import SwiftUI

struct HabitListView: View {
    @State private var viewModel = HabitsViewModel()
    @State private var isAddSheetPresented = false
    @State private var isArchiveExpanded = false

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
                                onArchive: { viewModel.archiveHabit(habitId: habit.id) },
                                onDelete: { viewModel.deleteHabit(habitId: habit.id) }
                            )
                        }
                    }

                    if viewModel.state.hasArchived {
                        archivedSection
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
                AddHabitView { name, emoji, weeklyGoal in
                    viewModel.addHabit(name: name, emoji: emoji, weeklyGoal: weeklyGoal)
                    isAddSheetPresented = false
                }
            }
        }
    }

    /// Mirrors the Compose archived section: a header carrying the shared label, then a compact
    /// row per hidden habit with the way back.
    private var archivedSection: some View {
        DisclosureGroup(viewModel.state.archivedSectionLabel, isExpanded: $isArchiveExpanded) {
            ForEach(viewModel.state.archivedHabits) { habit in
                HStack(spacing: 12) {
                    Text(habit.emoji).font(.headline)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(habit.name).font(.body)
                        Text(habit.streakLabel)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Button("Unarchive") {
                        viewModel.unarchiveHabit(habitId: habit.id)
                    }
                    .font(.subheadline)
                }
                .padding(.vertical, 6)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
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
