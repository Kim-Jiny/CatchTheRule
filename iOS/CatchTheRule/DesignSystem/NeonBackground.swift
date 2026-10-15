import SwiftUI

/// 화면 공통 배경. `animated` 가 true 이고 화면이 활성일 때만 오로라 블롭 + 떠다니는 글리프가 움직인다.
/// (기본 false = 기존 정적 배경. 플레이 화면은 그대로 둔다.)
struct ScreenBackground: View {
    var animated = false

    var body: some View {
        if animated {
            NeonBackground()
        } else {
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
}

/// 단일 Canvas 로 그리는 살아 있는 배경. 큰 블러 없이 라디얼 그라데이션으로 블롭을 표현.
private struct NeonBackground: View {
    @Environment(\.neonAnimating) private var active

    private struct Blob { let color: UInt; let x: Double; let y: Double; let radius: Double; let period: Double; let phase: Double; let opacity: Double }
    private struct Glyph { let text: String; let x: Double; let size: Double; let speed: Double; let phase: Double; let spin: Double; let opacity: Double }

    private static let blobs = [
        Blob(color: 0x7C5CFF, x: 0.15, y: 0.18, radius: 260, period: 24, phase: 0, opacity: 0.20),
        Blob(color: 0x18C8FF, x: 0.90, y: 0.50, radius: 230, period: 28, phase: 2.1, opacity: 0.14),
        Blob(color: 0xFF5C8A, x: 0.30, y: 0.88, radius: 240, period: 30, phase: 4.2, opacity: 0.12),
    ]

    private static let glyphs: [Glyph] = {
        let symbols = ["+", "−", "×", "÷", "=", "%", "7", "9", "▲", "●", "■", "∑", "π", "?", "√", "∞"]
        return symbols.enumerated().map { i, s in
            let f = Double(i)
            return Glyph(text: s,
                         x: (f * 0.37 + 0.08).truncatingRemainder(dividingBy: 1),
                         size: 18 + (f * 7).truncatingRemainder(dividingBy: 27),
                         speed: 1 / (55 + (f * 5).truncatingRemainder(dividingBy: 25)),
                         phase: (f * 0.61).truncatingRemainder(dividingBy: 1),
                         spin: (i % 2 == 0 ? 1 : -1) * (8 + (f * 3).truncatingRemainder(dividingBy: 10)),
                         opacity: 0.04 + (f * 0.013).truncatingRemainder(dividingBy: 0.06))
        }
    }()

    var body: some View {
        ZStack {
            Theme.bgGradient
            TimelineView(.animation(minimumInterval: 1.0 / 30, paused: !active)) { tl in
                let t = tl.date.timeIntervalSinceReferenceDate
                Canvas { ctx, size in
                    for b in Self.blobs {
                        let a = t * 2 * .pi / b.period + b.phase
                        let center = CGPoint(x: size.width * (b.x + 0.08 * sin(a)),
                                             y: size.height * (b.y + 0.06 * cos(a * 0.8)))
                        let rect = CGRect(x: center.x - b.radius, y: center.y - b.radius, width: b.radius * 2, height: b.radius * 2)
                        let c = Color(hex: b.color)
                        ctx.fill(Path(ellipseIn: rect),
                                 with: .radialGradient(Gradient(colors: [c.opacity(b.opacity), c.opacity(0)]),
                                                       center: center, startRadius: 0, endRadius: b.radius))
                    }
                    for g in Self.glyphs {
                        let progress = (t * g.speed + g.phase).truncatingRemainder(dividingBy: 1)
                        let y = size.height + 40 - progress * (size.height + 80)
                        let x = size.width * g.x + sin(t * 0.3 + g.phase * 6) * 12
                        var c = ctx
                        c.translateBy(x: x, y: y)
                        c.rotate(by: .degrees(t * g.spin))
                        c.draw(Text(g.text).font(.system(size: g.size, weight: .bold, design: .rounded))
                            .foregroundColor(.white.opacity(g.opacity)), at: .zero)
                    }
                }
            }
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
    }
}
