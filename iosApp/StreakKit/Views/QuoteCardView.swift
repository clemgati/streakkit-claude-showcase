import Shared
import SwiftUI

struct QuoteCardView: View {
    let quote: Quote?
    let isLoading: Bool
    let isOffline: Bool
    let onRefresh: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if isLoading && quote == nil {
                HStack(spacing: 12) {
                    ProgressView()
                    Text("Fetching today's quote…").font(.subheadline)
                }
            } else if let quote {
                Text("“\(quote.text)”")
                    .font(.body)
                    .italic()
                Text("— \(quote.author)")
                    .font(.caption)
            } else {
                Text("Pull a quote to start the day.").font(.subheadline)
            }

            HStack {
                Text(isOffline ? "offline · bundled quote" : "dummyjson.com")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                Spacer()
                Button("Shuffle", action: onRefresh)
                    .font(.caption)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color(.tertiarySystemGroupedBackground))
        )
    }
}
