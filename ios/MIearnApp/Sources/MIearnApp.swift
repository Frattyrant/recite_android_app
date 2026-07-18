import SwiftUI

@main
struct MIearnApp: App {
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var store = AppStore()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
                .tint(MIearnPalette.purple)
                .preferredColorScheme(nil)
                .task { await store.bootstrap() }
        }
        .onChange(of: scenePhase) { phase in
            if phase != .active {
                AudioService.shared.stop()
                store.persist()
            }
        }
    }
}
