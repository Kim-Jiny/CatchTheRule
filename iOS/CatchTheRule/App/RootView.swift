import SwiftUI

struct RootView: View {
    @Environment(StoreManager.self) private var store
    @Environment(ProgressStore.self) private var progress

    init() {
        // 다크 톤에 맞춘 탭바 외형.
        let appearance = UITabBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = UIColor(Theme.bgElevated)
        appearance.shadowColor = UIColor.white.withAlphaComponent(0.06)
        UITabBar.appearance().standardAppearance = appearance
        UITabBar.appearance().scrollEdgeAppearance = appearance
    }

    var body: some View {
        TabView {
            HomeView()
                .tabItem { Label(String.loc("tab_home"), systemImage: "house.fill") }

            ChallengeView()
                .tabItem { Label(String.loc("tab_challenge"), systemImage: "trophy.fill") }

            SettingsView()
                .tabItem { Label(String.loc("tab_settings"), systemImage: "gearshape.fill") }
        }
        .tint(Theme.accent)
        .task {
            // 소비형(힌트) 지급을 ProgressStore 에 연결 — 구매/복원/백그라운드 경로 모두 durable.
            store.onHintsPurchased = { n in progress.hintsRemaining += n }
            AnalyticsService.ping()
            await PuzzleStore.refreshFromServer()   // 추가 스테이지 캐시 갱신(다음 실행 반영)
        }
    }
}
