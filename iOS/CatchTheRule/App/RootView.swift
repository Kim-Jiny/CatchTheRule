import SwiftUI

struct RootView: View {
    @Environment(StoreManager.self) private var store
    @Environment(ProgressStore.self) private var progress
    @Environment(AdsManager.self) private var ads

    @State private var selection: RootTab = .home

    var body: some View {
        TabView(selection: $selection) {
            HomeView()
                .toolbar(.hidden, for: .tabBar)
                .tag(RootTab.home)

            ChallengeView()
                .toolbar(.hidden, for: .tabBar)
                .tag(RootTab.challenge)

            SettingsView()
                .toolbar(.hidden, for: .tabBar)
                .tag(RootTab.settings)
        }
        .tint(Theme.accent)
        .overlay(alignment: .bottom) {
            NeonTabBar(selection: $selection)
                .padding(.horizontal, 16)
                .padding(.bottom, 12)
                .ignoresSafeArea(.keyboard)
        }
        .task {
            // 힌트 지급 콜백은 앱 init 에서 이미 연결됨(CatchTheRuleApp) — 여기선 광고/네트워크 초기화만.
            ads.start()   // 리워드 광고 SDK 초기화 + 미리 로드
            AnalyticsService.ping()
            await PuzzleStore.refreshFromServer()   // 추가 스테이지 캐시 갱신(다음 실행 반영)
        }
    }
}

extension View {
    /// 플로팅 탭바(64) + 하단 간격(12) + 여유(16)만큼 스크롤 콘텐츠 끝에 여백. 탭바가 보이는 모든 ScrollView 에 적용.
    /// (하단 세이프에어리어는 스크롤뷰가 자동으로 더하고, 탭바는 세이프에어리어 위에 떠 있다.)
    func tabBarSafeArea() -> some View {
        contentMargins(.bottom, 64 + 12 + 16, for: .scrollContent)
    }
}

enum RootTab: Int, CaseIterable {
    case home, challenge, settings

    var icon: String {
        switch self {
        case .home: return "house.fill"
        case .challenge: return "trophy.fill"
        case .settings: return "gearshape.fill"
        }
    }

    var titleKey: String {
        switch self {
        case .home: return "tab_home"
        case .challenge: return "tab_challenge"
        case .settings: return "tab_settings"
        }
    }
}

/// 플로팅 네온 캡슐 탭바. 선택 알약이 슬라이드하고 선택 항목만 라벨을 보여준다.
struct NeonTabBar: View {
    @Binding var selection: RootTab
    @Environment(ProgressStore.self) private var progress
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Namespace private var pillNS
    @State private var bounce: RootTab?

    var body: some View {
        HStack(spacing: 4) {
            ForEach(RootTab.allCases, id: \.self) { tab in
                item(tab)
            }
        }
        .padding(8)
        .frame(height: 64)
        .background(
            Capsule().fill(Color(hex: 0x10141C, alpha: 0.92))
        )
        .overlay(Capsule().stroke(Color.white.opacity(0.08), lineWidth: 1))
        .shadow(color: .black.opacity(0.45), radius: 16, y: 8)
    }

    private func item(_ tab: RootTab) -> some View {
        let selected = selection == tab
        return Button {
            guard selection != tab else { return }
            if progress.hapticsOn { Haptics.tap() }
            withAnimation(reduceMotion ? nil : .spring(response: 0.4, dampingFraction: 0.78)) { selection = tab }
            if !reduceMotion {
                bounce = tab
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) { bounce = nil }
            }
        } label: {
            HStack(spacing: 7) {
                Image(systemName: tab.icon)
                    .font(.system(size: 19, weight: .bold))
                    .scaleEffect(bounce == tab ? 1.18 : 1)
                    .animation(reduceMotion ? nil : .spring(response: 0.25, dampingFraction: 0.5), value: bounce)
                if selected {
                    Text(String.loc(tab.titleKey))
                        .font(.system(size: 14, weight: .heavy, design: .rounded))
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                        .transition(reduceMotion ? .identity : .opacity.combined(with: .move(edge: .leading)))
                }
            }
            .foregroundStyle(selected ? Color.white : Color.white.opacity(0.45))
            .frame(maxWidth: selected ? .infinity : 56)
            .frame(height: 48)
            .background {
                if selected {
                    Capsule()
                        .fill(Theme.accentGradient)
                        .shadow(color: Theme.accent.opacity(0.55), radius: 10, y: 2)
                        .matchedGeometryEffect(id: "pill", in: pillNS)
                }
            }
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(String.loc(tab.titleKey))
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }
}
