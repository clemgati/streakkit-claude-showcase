import SwiftUI

/// Three derived numbers, all computed by the shared module's `HabitsUiState`.
struct TodaySummaryView: View {
    let checkedIn: Int
    let total: Int
    let bestStreak: Int

    var body: some View {
        HStack {
            stat(value: "\(checkedIn)/\(total)", label: "done today")
            stat(value: "\(bestStreak)", label: "best live streak")
            stat(value: "\(total)", label: "tracked")
        }
        .frame(maxWidth: .infinity)
    }

    private func stat(value: String, label: String) -> some View {
        VStack(spacing: 2) {
            Text(value).font(.title3)
            Text(label)
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
    }
}
