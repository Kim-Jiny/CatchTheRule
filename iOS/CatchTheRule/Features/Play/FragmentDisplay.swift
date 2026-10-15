import SwiftUI

// MARK: - 조각 숫자 디스플레이

/// 7세그먼트 숫자를 여러 장의 조각으로 나눠 `frameMs` 마다 돌아가며(무한 순환) 보여 준다.
/// 꺼진 획은 흐린 고스트, 현재 장의 켜진 획만 장 색 + 글로우.
struct FragmentDisplay: View {
    let puzzle: Puzzle
    var typed: String = ""
    var feedback: AnswerFeedback? = nil

    /// 장 색(고정 순서).
    static let frameColors: [Color] = [
        Color(hex: 0x18C8FF), Color(hex: 0xFF5C8A), Color(hex: 0xFFB020), Color(hex: 0x3DDC97),
    ]
    private static let crossfade: Double = 0.12

    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var visible = false
    @State private var start = Date()
    @State private var availWidth: CGFloat = 300

    private var frames: [[String]] { puzzle.frames ?? [] }
    private var digitCount: Int { max(1, puzzle.answer.count, frames.first?.count ?? 1) }
    private var frameSeconds: Double { Double(max(300, puzzle.frameMs ?? 1500)) / 1000 }
    private var running: Bool { visible && scenePhase == .active && frames.count > 0 }

    var body: some View {
        VStack(spacing: 14) {
            displayCard
            typedRow
        }
        .id(puzzle.id)   // 문제가 바뀌면 상태(시작 시각)를 새로 만들어 첫 장부터 재시작
        .background(
            GeometryReader { geo in
                Color.clear
                    .onAppear { availWidth = geo.size.width }
                    .onChange(of: geo.size.width) { _, w in availWidth = w }
            }
        )
        .onAppear { visible = true; start = Date() }
        .onDisappear { visible = false }
        .onChange(of: running) { _, now in if now { start = Date() } }
        .onChange(of: puzzle.id) { _, _ in start = Date() }
    }

    // MARK: 카드

    private var digitWidth: CGFloat {
        let spacing: CGFloat = 12
        let inner = max(120, availWidth - 40)   // 카드 좌우 패딩
        return min(76, (inner - spacing * 3) / 4)
    }

    private var displayCard: some View {
        let w = digitWidth
        let h = w * 1.9
        return VStack(spacing: 16) {
            TimelineView(.animation(minimumInterval: 1.0 / 30, paused: !running)) { tl in
                let st = state(at: tl.date)
                VStack(spacing: 16) {
                    HStack(spacing: 12) {
                        ForEach(0..<digitCount, id: \.self) { d in
                            SegmentDigit(
                                width: w,
                                prevLit: lit(frame: st.prev, digit: d), prevColor: color(st.prev),
                                curLit: lit(frame: st.cur, digit: d), curColor: color(st.cur),
                                k: st.k)
                                .frame(width: w, height: h)
                        }
                    }
                    indicator(cur: st.cur, progress: st.progress)
                }
            }
        }
        .padding(.vertical, 22).padding(.horizontal, 20)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous).fill(Color.black.opacity(0.35))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .stroke(Theme.modeColor("fragment").opacity(0.35), lineWidth: 1)
        )
    }

    private struct CycleState { var cur: Int; var prev: Int; var k: Double; var progress: Double }

    private func state(at date: Date) -> CycleState {
        let n = max(1, frames.count)
        let elapsed = max(0, date.timeIntervalSince(start))
        let idx = Int(elapsed / frameSeconds)
        let cur = idx % n
        let inFrame = elapsed - Double(idx) * frameSeconds
        let prev = (cur - 1 + n) % n
        var k = 1.0
        if !reduceMotion && idx > 0 { k = min(1, inFrame / Self.crossfade) }
        return CycleState(cur: cur, prev: prev, k: k, progress: min(1, inFrame / frameSeconds))
    }

    private func color(_ frame: Int) -> Color {
        Self.frameColors[frame % Self.frameColors.count]
    }

    private func lit(frame: Int, digit: Int) -> Set<Character> {
        guard frames.indices.contains(frame), frames[frame].indices.contains(digit) else { return [] }
        return Set(frames[frame][digit])
    }

    // MARK: 장 점 + 진행 바

    private func indicator(cur: Int, progress: Double) -> some View {
        let n = frames.count
        let c = color(cur)
        return VStack(spacing: 10) {
            HStack(spacing: 10) {
                ForEach(0..<n, id: \.self) { i in
                    Circle()
                        .fill(i == cur ? color(i) : Color.white.opacity(0.14))
                        .overlay(Circle().stroke(color(i).opacity(i == cur ? 0 : 0.5), lineWidth: 1))
                        .frame(width: 10, height: 10)
                        .shadow(color: i == cur ? color(i).opacity(0.8) : .clear, radius: 5)
                }
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(Color.white.opacity(0.10))
                    Capsule().fill(c).frame(width: geo.size.width * progress)
                }
            }
            .frame(height: 4)
            .frame(maxWidth: 180)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(min(cur + 1, max(n, 1)))/\(n)")
    }

    // MARK: 입력 표시

    private var typedRow: some View {
        let chars = Array(typed)
        let tint: Color = feedback == .correct ? Theme.success : (feedback == .wrong ? Theme.danger : Theme.textPrimary)
        return HStack(spacing: 10) {
            ForEach(0..<digitCount, id: \.self) { i in
                Text(i < chars.count ? String(chars[i]) : " ")
                    .font(.system(size: 26, weight: .bold, design: .rounded))
                    .monospacedDigit()
                    .foregroundStyle(tint)
                    .frame(width: 42, height: 52)
                    .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Theme.card))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .stroke(i == chars.count && feedback == nil ? AnyShapeStyle(Theme.accentGradient)
                                    : AnyShapeStyle(feedback == nil ? Theme.stroke : tint.opacity(0.8)),
                                    lineWidth: i == chars.count && feedback == nil ? 2 : 1)
                    )
            }
        }
    }
}

// MARK: - 7세그먼트 한 자리

private struct SegmentDigit: View {
    let width: CGFloat
    let prevLit: Set<Character>
    let prevColor: Color
    let curLit: Set<Character>
    let curColor: Color
    let k: Double

    private static let all: [Character] = Array("abcdefg")

    var body: some View {
        let h = width * 1.9
        let t = width * 0.2
        let gap = t * 0.14
        let hLen = width - t - 2 * gap
        let vLen = h / 2 - t / 2 - 2 * gap
        func rect(_ s: Character) -> (CGRect, Bool) {
            switch s {
            case "a": return (CGRect(x: t / 2 + gap, y: 0, width: hLen, height: t), true)
            case "g": return (CGRect(x: t / 2 + gap, y: h / 2 - t / 2, width: hLen, height: t), true)
            case "d": return (CGRect(x: t / 2 + gap, y: h - t, width: hLen, height: t), true)
            case "f": return (CGRect(x: 0, y: t / 2 + gap, width: t, height: vLen), false)
            case "b": return (CGRect(x: width - t, y: t / 2 + gap, width: t, height: vLen), false)
            case "e": return (CGRect(x: 0, y: h / 2 + gap, width: t, height: vLen), false)
            default:  return (CGRect(x: width - t, y: h / 2 + gap, width: t, height: vLen), false)  // c
            }
        }
        return ZStack(alignment: .topLeading) {
            ForEach(Self.all, id: \.self) { s in
                let (r, horiz) = rect(s)
                let shape = LEDSegment(horizontal: horiz)
                ZStack {
                    shape.fill(Color.white.opacity(0.07))
                    if prevLit.contains(s) {
                        shape.fill(prevColor).shadow(color: prevColor.opacity(0.9), radius: 6)
                            .opacity(1 - k)
                    }
                    if curLit.contains(s) {
                        shape.fill(curColor).shadow(color: curColor.opacity(0.9), radius: 6)
                            .opacity(k)
                    }
                }
                .frame(width: r.width, height: r.height)
                .offset(x: r.minX, y: r.minY)
            }
        }
        .frame(width: width, height: h, alignment: .topLeading)
    }
}

/// LED 획: 양 끝이 뾰족한 둥근 육각형.
private struct LEDSegment: Shape {
    let horizontal: Bool

    func path(in rect: CGRect) -> Path {
        let w = rect.width, h = rect.height
        var pts: [CGPoint]
        if horizontal {
            let c = h / 2
            pts = [CGPoint(x: 0, y: c), CGPoint(x: c, y: 0), CGPoint(x: w - c, y: 0),
                   CGPoint(x: w, y: c), CGPoint(x: w - c, y: h), CGPoint(x: c, y: h)]
        } else {
            let c = w / 2
            pts = [CGPoint(x: c, y: 0), CGPoint(x: w, y: c), CGPoint(x: w, y: h - c),
                   CGPoint(x: c, y: h), CGPoint(x: 0, y: h - c), CGPoint(x: 0, y: c)]
        }
        var p = Path()
        p.move(to: pts[0])
        for q in pts.dropFirst() { p.addLine(to: q) }
        p.closeSubpath()
        return p.strokedPath(StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
            .union(p)
    }
}
