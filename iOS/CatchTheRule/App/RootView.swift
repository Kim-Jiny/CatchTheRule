import SwiftUI

struct RootView: View {
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
        .task { AnalyticsService.ping() }
    }
}
