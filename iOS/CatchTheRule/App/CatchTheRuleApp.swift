import SwiftUI

@main
struct CatchTheRuleApp: App {
    @State private var progress = ProgressStore()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(progress)
                .preferredColorScheme(.dark)
                .tint(Theme.accent)
        }
    }
}
