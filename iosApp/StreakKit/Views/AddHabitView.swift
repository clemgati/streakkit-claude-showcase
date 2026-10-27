import Shared
import SwiftUI

struct AddHabitView: View {
    private static let emojiChoices = ["📚", "🏃", "🌙", "💧", "🧘", "🎸", "🥗", "✍️"]
    /// Int32 because Kotlin Int bridges to Int32; the range comes from the shared module so the
    /// two pickers cannot offer different options.
    private static let goalChoices: [Int32] = Array(1 ... StreakCalculator.shared.WEEK_LENGTH)

    let onConfirm: (String, String, Int32) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var emoji = AddHabitView.emojiChoices[0]
    @State private var weeklyGoal = Habit.companion.DEFAULT_WEEKLY_GOAL

    var body: some View {
        NavigationStack {
            Form {
                Section("What will you do daily?") {
                    TextField("Read 20 pages", text: $name)
                }

                Section("Icon") {
                    HStack {
                        ForEach(Self.emojiChoices, id: \.self) { choice in
                            Text(choice)
                                .frame(width: 36, height: 36)
                                .background(
                                    Circle().fill(
                                        choice == emoji
                                            ? Color.accentColor.opacity(0.2)
                                            : Color.clear
                                    )
                                )
                                .onTapGesture { emoji = choice }
                        }
                    }
                    .frame(maxWidth: .infinity)
                }

                Section("Days per week: \(weeklyGoal)") {
                    HStack {
                        ForEach(Self.goalChoices, id: \.self) { goal in
                            Text("\(goal)")
                                .frame(width: 36, height: 36)
                                .background(
                                    Circle().fill(
                                        goal == weeklyGoal
                                            ? Color.accentColor.opacity(0.2)
                                            : Color.clear
                                    )
                                )
                                .onTapGesture { weeklyGoal = goal }
                        }
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .navigationTitle("New habit")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add") { onConfirm(name, emoji, weeklyGoal) }
                        .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }
}
