import SwiftUI

struct RootView: View {
    @Environment(StoreManager.self) private var store
    @Environment(ProgressStore.self) private var progress
    @Environment(AdsManager.self) private var ads

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
            // 힌트 지급 콜백은 앱 init 에서 이미 연결됨(CatchTheRuleApp) — 여기선 광고/네트워크 초기화만.
            ads.start()   // 리워드 광고 SDK 초기화 + 미리 로드
            AnalyticsService.ping()
            await PuzzleStore.refreshFromServer()   // 추가 스테이지 캐시 갱신(다음 실행 반영)
        }
    }
}
