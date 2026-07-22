import SwiftUI

@main
struct CatchTheRuleApp: App {
    @State private var progress: ProgressStore
    @State private var store: StoreManager
    @State private var ads = AdsManager()

    init() {
        let progress = ProgressStore()
        let store = StoreManager()
        // 소비형(힌트) 지급 콜백을 앱 시작 즉시 연결 — 백그라운드 트랜잭션(Transaction.updates)이
        // 첫 화면 등장 전에 먼저 발생해도 힌트가 유실되지 않고 그 자리에서 적립된다.
        store.onHintsPurchased = { n in progress.hintsRemaining += n }
        _progress = State(initialValue: progress)
        _store = State(initialValue: store)
    }

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
