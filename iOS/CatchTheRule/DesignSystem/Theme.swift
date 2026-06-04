import SwiftUI

/// 앱 전역 디자인 토큰. 다크 베이스 + 바이올렛→시안 포인트 그라데이션.
enum Theme {
    // Backgrounds
    static let bg = Color(hex: 0x0E1116)
    static let bgElevated = Color(hex: 0x151A22)
    static let card = Color(hex: 0x1B212B)
    static let stroke = Color.white.opacity(0.07)

    // Text
    static let textPrimary = Color.white
    static let textSecondary = Color.white.opacity(0.62)
    static let textTertiary = Color.white.opacity(0.34)

    // Accent & states
    static let accent = Color(hex: 0x7C5CFF)
    static let accent2 = Color(hex: 0x39E5C8)
    static let success = Color(hex: 0x3DDC97)
    static let danger = Color(hex: 0xFF6B6B)
    static let star = Color(hex: 0xFFD166)

    static var accentGradient: LinearGradient {
        LinearGradient(colors: [accent, accent2],
                       startPoint: .topLeading, endPoint: .bottomTrailing)
    }

    static var bgGradient: LinearGradient {
        LinearGradient(colors: [bg, bgElevated],
                       startPoint: .top, endPoint: .bottom)
    }
}

extension Color {
    init(hex: UInt, alpha: Double = 1) {
        self.init(.sRGB,
                  red: Double((hex >> 16) & 0xFF) / 255,
                  green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255,
                  opacity: alpha)
    }
}

/// 화면 공통 배경.
struct ScreenBackground: View {
    var body: some View {
        Theme.bgGradient
            .ignoresSafeArea()
            .overlay(alignment: .top) {
                Theme.accent
                    .opacity(0.18)
                    .frame(height: 280)
                    .blur(radius: 120)
                    .offset(y: -120)
                    .ignoresSafeArea()
            }
    }
}
