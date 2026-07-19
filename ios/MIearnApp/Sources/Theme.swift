import SwiftUI

enum MIearnPalette {
    static let purple = Color(red: 0.39, green: 0.08, blue: 0.68)
    static let violet = Color(red: 0.60, green: 0.39, blue: 0.82)
    static let peach = Color(red: 1.00, green: 0.75, blue: 0.53)
    static let cream = Color(red: 1.00, green: 0.98, blue: 0.94)
    static let ink = Color(red: 0.13, green: 0.10, blue: 0.17)
}

struct SoftBackground: View {
    var body: some View {
        LinearGradient(
            colors: [
                Color(uiColor: .systemBackground),
                MIearnPalette.cream.opacity(0.72),
                MIearnPalette.violet.opacity(0.10),
            ],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
        .ignoresSafeArea()
    }
}

struct SoftCard<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        content
            .padding(18)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .stroke(MIearnPalette.violet.opacity(0.16), lineWidth: 1)
            }
            .shadow(color: MIearnPalette.purple.opacity(0.08), radius: 18, y: 8)
    }
}

struct PrimaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 17)
            .foregroundStyle(.white)
            .background(
                LinearGradient(
                    colors: [MIearnPalette.purple, MIearnPalette.violet],
                    startPoint: .leading,
                    endPoint: .trailing
                ),
                in: RoundedRectangle(cornerRadius: 18, style: .continuous)
            )
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
            .animation(.easeOut(duration: 0.18), value: configuration.isPressed)
    }
}

struct EmptyState: View {
    let icon: String
    let title: String
    let message: String

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 34, weight: .medium))
                .foregroundStyle(MIearnPalette.violet)
            Text(title).font(.headline)
            Text(message)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(28)
    }
}
