import Shared
import SwiftUI

struct HabitCardView: View {
    let habit: HabitSummary
    let onToggleToday: () -> Void
    let onToggleDay: (String) -> Void
    let onDelete: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                Text(habit.emoji).font(.title2)

                VStack(alignment: .leading, spacing: 2) {
                    Text(habit.name).font(.headline)
                    Text(streakLabel)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Spacer()

                Button(habit.checkedInToday ? "Undo" : "Check in", action: onToggleToday)
                    .buttonStyle(.bordered)
                    .font(.subheadline)
            }

            weekStrip

            // Sits directly under the strip because it counts exactly those seven days
            // (StreakCalculator invariant 5). The label text comes from the shared module.
            HStack(spacing: 6) {
                Text(habit.weeklyGoalLabel)
                    .font(.caption)
                    .fontWeight(habit.weeklyGoalMet ? .semibold : .regular)
                    .foregroundStyle(habit.weeklyGoalMet ? Color.accentColor : Color.secondary)
                if habit.weeklyGoalMet {
                    Text("🎯").font(.caption)
                }
            }

            VStack(alignment: .leading, spacing: 4) {
                ProgressView(value: Double(habit.completionRate30d))
                HStack {
                    Text(habit.completionLabel)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                    Spacer()
                    Button("Delete", action: onDelete)
                        .font(.caption2)
                        .foregroundStyle(.red)
                }
            }
        }
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 16).fill(cardTint))
    }

    /// Mirrors the Compose card's `primaryContainer` vs `surfaceVariant` switch.
    private var cardTint: Color {
        habit.checkedInToday
            ? Color.accentColor.opacity(0.14)
            : Color(.secondarySystemGroupedBackground)
    }

    /// Seven tappable cells. The dates are whatever `StreakCalculator.recentDays` returned, so
    /// this strip can never disagree with the Android one.
    private var weekStrip: some View {
        HStack {
            ForEach(habit.week, id: \.isoDate) { day in
                VStack(spacing: 4) {
                    Text(day.label)
                        .font(.caption2)
                        .foregroundStyle(.secondary)

                    ZStack {
                        Circle()
                            .fill(day.isChecked ? Color.accentColor : Color.clear)
                        Circle()
                            .strokeBorder(
                                day.isToday ? Color.accentColor : Color(.separator),
                                lineWidth: day.isToday ? 2 : 1
                            )
                        if day.isChecked {
                            Image(systemName: "checkmark")
                                .font(.caption.bold())
                                .foregroundStyle(.white)
                        }
                    }
                    .frame(width: 32, height: 32)
                    .onTapGesture { onToggleDay(day.isoDate) }
                }
                .frame(maxWidth: .infinity)
            }
        }
    }

    private var streakLabel: String {
        if habit.currentStreak == 0 && habit.longestStreak == 0 {
            return "No check-ins yet"
        }
        if habit.currentStreak == 0 {
            return "Streak broken · best \(habit.longestStreak)"
        }
        return "🔥 \(habit.currentStreak) day streak · best \(habit.longestStreak)"
    }
}
