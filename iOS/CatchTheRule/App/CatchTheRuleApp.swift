import SwiftUI

@main
struct CatchTheRuleApp: App {
    @State private var progress = ProgressStore()
    @State private var store = StoreManager()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(progress)
                .environment(store)
                .preferredColorScheme(.dark)
                .tint(Theme.accent)
        }
    }
}
