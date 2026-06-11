import SwiftUI

@main
struct CatchTheRuleApp: App {
    @State private var progress = ProgressStore()
    @State private var store = StoreManager()
    @State private var ads = AdsManager()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(progress)
                .environment(store)
                .environment(ads)
                .preferredColorScheme(.dark)
                .tint(Theme.accent)
        }
    }
}
