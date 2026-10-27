import SwiftUI

struct AddHabitView: View {
    private static let emojiChoices = ["📚", "🏃", "🌙", "💧", "🧘", "🎸", "🥗", "✍️"]

    let onConfirm: (String, String) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var emoji = AddHabitView.emojiChoices[0]

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
            }
            .navigationTitle("New habit")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add") { onConfirm(name, emoji) }
                        .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }
}
