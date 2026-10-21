import SwiftUI

// MARK: - 모드별 네온 컬러

extension Theme {
    private static func modeHex(_ track: String) -> UInt {
        switch track {
        case "shapes":        return 0x18C8FF   // 시안
        case "logic":         return 0xFFB020   // 앰버
        case "contradiction": return 0xFF5C8A   // 핑크
        case "target":        return 0x3DDC97   // 그린
        case "match":         return 0xFF7A45   // 오렌지
        case "fragment":      return 0xA3E635   // 라임
        case "cipher":        return 0x2DD4BF   // 틸
        case "scale":         return 0xE879F9   // 퓨샤
        default:              return 0x7C5CFF   // 바이올렛(numbers)
        }
    }

    static func modeColor(_ track: String) -> Color { Color(hex: modeHex(track)) }
    static func modeGlow(_ track: String) -> Color { modeColor(track).opacity(0.55) }
    static func modeTint(_ track: String) -> Color { modeColor(track).opacity(0.14) }

    /// 모드색 → 모드색을 보라 쪽으로 15% 섞은 색.
    static func modeGradient(_ track: String) -> LinearGradient {
        let c = modeHex(track), v: UInt = 0x7C5CFF
        func mix(_ shift: UInt) -> Double {
            let a = Double((c >> shift) & 0xFF), b = Double((v >> shift) & 0xFF)
            return (a + (b - a) * 0.15) / 255
        }
        let end = Color(.sRGB, red: mix(16), green: mix(8), blue: mix(0), opacity: 1)
        return LinearGradient(colors: [modeColor(track), end], startPoint: .topLeading, endPoint: .bottomTrailing)
    }

    /// 잠긴/비활성 항목용 회색.
    static let lockedColor = Color.white.opacity(0.25)
}

// MARK: - 애니메이션 활성 여부(환경)

private struct NeonAnimatingKey: EnvironmentKey { static let defaultValue = false }

extension EnvironmentValues {
    /// 화면이 보이고 앱이 활성이며 Reduce Motion 이 꺼져 있을 때만 true. 무한/장식 애니메이션이 참고.
    var neonAnimating: Bool {
        get { self[NeonAnimatingKey.self] }
        set { self[NeonAnimatingKey.self] = newValue }
    }
}

private struct NeonScreenModifier: ViewModifier {
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var onScreen = false

    func body(content: Content) -> some View {
        content
            .environment(\.neonAnimating, onScreen && scenePhase == .active && !reduceMotion)
            .onAppear { onScreen = true }
            .onDisappear { onScreen = false }
    }
}

extension View {
    /// 이 화면 하위의 네온 애니메이션을 화면 표시 상태에 맞춰 켜고 끈다.
    func neonScreen() -> some View { modifier(NeonScreenModifier()) }
}

// MARK: - 카드/눌림

private struct NeonCardModifier: ViewModifier {
    let color: Color
    var radius: CGFloat = 22
    var strong = false

    func body(content: Content) -> some View {
        content
            .background {
                ZStack {
                    RoundedRectangle(cornerRadius: radius, style: .continuous).fill(Theme.card)
                    RoundedRectangle(cornerRadius: radius, style: .continuous)
                        .fill(LinearGradient(colors: [color.opacity(0.20), color.opacity(0.03)],
                                             startPoint: .topLeading, endPoint: .bottomTrailing))
                }
                .shadow(color: color.opacity(strong ? 0.4 : 0.25), radius: strong ? 20 : 16, y: 6)
            }
            .overlay(
                RoundedRectangle(cornerRadius: radius, style: .continuous)
                    .stroke(color.opacity(strong ? 0.9 : 0.35), lineWidth: 1)
            )
    }
}

extension View {
    func neonCard(_ color: Color, radius: CGFloat = 22, strong: Bool = false) -> some View {
        modifier(NeonCardModifier(color: color, radius: radius, strong: strong))
    }
}

/// 눌림 시 scale 0.96 스프링 + 가벼운 햅틱.
struct NeonPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        PressBody(configuration: configuration)
    }

    private struct PressBody: View {
        @Environment(ProgressStore.self) private var progress
        let configuration: ButtonStyleConfiguration

        var body: some View {
            configuration.label
                .scaleEffect(configuration.isPressed ? 0.96 : 1)
                .animation(.spring(response: 0.25, dampingFraction: 0.6), value: configuration.isPressed)
                .onChange(of: configuration.isPressed) { _, pressed in
                    if pressed && progress.hapticsOn { Haptics.tap() }
                }
        }
    }
}

// MARK: - 등장 스태거

private struct StaggerOnAppear: ViewModifier {
    let order: Int
    let step: Double
    let dy: CGFloat
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shown = false

    func body(content: Content) -> some View {
        content
            .opacity(shown ? 1 : 0)
            .offset(y: shown ? 0 : dy)
            .animation(reduceMotion ? nil : .spring(response: 0.5, dampingFraction: 0.82).delay(Double(order) * step),
                       value: shown)
            .onAppear { shown = true }
    }
}

extension View {
    /// 처음 보일 때 translateY(dy→0) + 페이드 인. 순서마다 step 초씩 지연.
    func staggerOnAppear(order: Int, step: Double = 0.06, dy: CGFloat = 24) -> some View {
        modifier(StaggerOnAppear(order: order, step: step, dy: dy))
    }
}

// MARK: - 카운트업

/// 값이 애니메이션되는 동안 매 프레임 content 를 다시 그린다.
private struct Interpolated<Content: View>: View, Animatable {
    var value: Double
    let content: (Double) -> Content
    var animatableData: Double {
        get { value }
        set { value = newValue }
    }
    var body: some View { content(value) }
}

/// 보일 때 0→target 으로 애니메이션(Reduce Motion 이면 즉시). target 이 바뀌면 다시 보간.
struct AnimatedValue<Content: View>: View {
    let target: Double
    var duration: Double = 0.9
    var delay: Double = 0
    @ViewBuilder let content: (Double) -> Content

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shown: Double = 0

    var body: some View {
        Interpolated(value: shown, content: content)
            .onAppear { update() }
            .onChange(of: target) { _, _ in update() }
    }

    private func update() {
        if reduceMotion {
            shown = target
        } else {
            withAnimation(.easeOut(duration: duration).delay(delay)) { shown = target }
        }
    }
}

/// 진행률 링 + 가운데 % 카운트업.
struct NeonRing: View {
    let track: String
    let fraction: Double
    var size: CGFloat = 76
    var lineWidth: CGFloat = 8

    var body: some View {
        AnimatedValue(target: fraction) { v in
            ZStack {
                Circle().stroke(Color.white.opacity(0.10), lineWidth: lineWidth)
                Circle()
                    .trim(from: 0, to: v)
                    .stroke(Theme.modeGradient(track), style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                    .shadow(color: Theme.modeGlow(track), radius: 6)
                Text("\(Int((v * 100).rounded()))%")
                    .font(.system(size: size * 0.24, weight: .heavy, design: .rounded))
                    .monospacedDigit()
                    .foregroundStyle(Theme.textPrimary)
            }
        }
        .frame(width: size, height: size)
    }
}

/// 얇은 진행 바(0→값).
struct NeonBar: View {
    let track: String
    let fraction: Double
    var height: CGFloat = 5

    var body: some View {
        AnimatedValue(target: fraction, duration: 0.8) { v in
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(Color.white.opacity(0.10))
                    Capsule()
                        .fill(Theme.modeGradient(track))
                        .frame(width: max(v > 0 ? height : 0, geo.size.width * v))
                        .shadow(color: Theme.modeGlow(track), radius: 4)
                }
            }
        }
        .frame(height: height)
    }
}

// MARK: - 모드 아이콘 타일(모드별 idle 애니메이션)

struct ModeIconTile: View {
    let track: String
    var size: CGFloat = 44
    @Environment(\.neonAnimating) private var active

    /// (주기, 위상) — 모드마다 달라서 동시에 움직이지 않게.
    private var phase: Double {
        switch track {
        case "shapes": return 1.3
        case "logic": return 2.1
        case "contradiction": return 0.7
        case "target": return 3.4
        case "match": return 0.2
        case "fragment": return 1.7
        case "cipher": return 2.6
        case "scale": return 0.9
        default: return 0
        }
    }

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30, paused: !active)) { tl in
            tile(t: tl.date.timeIntervalSinceReferenceDate + phase)
        }
    }

    private func tile(t: Double) -> some View {
        var offsetY: CGFloat = 0
        var rotation = 0.0
        var scale: CGFloat = 1
        var glow = 0.0   // 아이콘 자체 발광(0...1)
        switch track {
        case "shapes":
            rotation = (t / 9).truncatingRemainder(dividingBy: 1) * 360
        case "logic":
            glow = 0.5 + 0.5 * sin(t * 2 * .pi / 3.1)
        case "contradiction":
            // 3.7초마다 0.8초 동안 좌우로 흔들림
            let c = t.truncatingRemainder(dividingBy: 3.7)
            if c < 0.8 { rotation = sin(c * 2 * .pi * 3) * 14 * (1 - c / 0.8) }
        case "target":
            scale = 1 + 0.10 * sin(t * 2 * .pi / 1.9)
        case "match":
            scale = 1 + 0.08 * sin(t * 2 * .pi / 1.3)
            rotation = sin(t * 2 * .pi / 0.9) * 4
        case "fragment":
            // 조각이 번갈아 켜지듯 아이콘이 깜빡
            glow = max(0, sin(t * 2 * .pi / 1.6))
            scale = 1 + 0.05 * glow
        case "cipher":
            // 자물쇠가 열리려는 듯 살짝 흔들리며 발광
            rotation = sin(t * 2 * .pi / 1.8) * 8
            glow = max(0, sin(t * 2 * .pi / 2.4)) * 0.8
        case "scale":
            // 저울이 평형을 찾듯 좌우로 기울었다 돌아옴
            rotation = sin(t * 2 * .pi / 2.8) * 9
        default:
            offsetY = CGFloat(sin(t * 2 * .pi / 2.6)) * size * 0.07
        }
        return ZStack {
            RoundedRectangle(cornerRadius: size * 0.3, style: .continuous)
                .fill(Theme.modeGradient(track))
                .shadow(color: Theme.modeGlow(track), radius: 10)
            Image(systemName: modeIcon(track))
                .font(.system(size: size * 0.45, weight: .bold))
                .foregroundStyle(.white)
                .shadow(color: .white.opacity(glow * 0.9), radius: glow * 6)
                .offset(y: offsetY)
                .rotationEffect(.degrees(rotation))
                .scaleEffect(scale)
        }
        .frame(width: size, height: size)
    }
}

// MARK: - 이어하기 버튼(펄스 글로우 + 샤인 스윕)

struct NeonContinueButton: View {
    let track: String
    let title: String
    var systemImage = "play.fill"
    let action: () -> Void
    @Environment(\.neonAnimating) private var active

    var body: some View {
        Button(action: action) {
            TimelineView(.animation(minimumInterval: 1.0 / 30, paused: !active)) { tl in
                label(t: tl.date.timeIntervalSinceReferenceDate)
            }
        }
        .buttonStyle(NeonPressStyle())
    }

    private func label(t: Double) -> some View {
        let pulse = active ? 0.5 + 0.5 * sin(t * .pi) : 0.5       // 2초 주기
        let sweep = active ? t.truncatingRemainder(dividingBy: 3) / 0.9 : 2  // 3초마다 0.9초 동안 통과
        return HStack(spacing: 8) {
            Image(systemName: systemImage)
            Text(title).fontWeight(.heavy)
        }
        .font(.system(size: 17))
        .foregroundStyle(.white)
        .frame(maxWidth: .infinity)
        .padding(.vertical, 15)
        .background(Theme.modeGradient(track))
        .overlay {
            GeometryReader { geo in
                if sweep < 1 {
                    LinearGradient(colors: [.clear, .white.opacity(0.4), .clear], startPoint: .leading, endPoint: .trailing)
                        .frame(width: geo.size.width * 0.35, height: geo.size.height * 2)
                        .rotationEffect(.degrees(20))
                        .offset(x: geo.size.width * (-0.4 + 1.6 * sweep), y: -geo.size.height * 0.5)
                }
            }
        }
        .clipShape(Capsule())
        .shadow(color: Theme.modeColor(track).opacity(0.25 + 0.25 * pulse), radius: 8 + 8 * pulse, y: 2)
    }
}
