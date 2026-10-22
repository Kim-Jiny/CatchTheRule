import SwiftUI

// MARK: - 경로 추적 상태

/// 경로 추적 퍼즐의 현재 선택. 정답은 JSON `answer`(1-기반 행 우선 번호)와 비교한다.
struct PathState: Equatable {
    let size: Int
    let trail: [[Int]]
    let total: Int
    var selected: Int?   // 선택 칸 번호(1-기반 행 우선)

    init?(puzzle: Puzzle) {
        guard let t = puzzle.trail, !t.isEmpty, let s = puzzle.size else { return nil }
        size = s
        trail = t
        total = puzzle.total ?? t.count
    }

    var moveCount: Int { trail.count - 1 }
    var canCheck: Bool { selected != nil }

    mutating func tap(_ r: Int, _ c: Int) {
        let num = r * size + c + 1
        selected = selected == num ? nil : num
    }

    var answerString: String { selected.map(String.init) ?? "" }
}

// MARK: - 경로 보드

struct PathBoardView: View {
    @Binding var state: PathState
    var disabled: Bool = false
    var onTap: () -> Void = {}

    private var mode: Color { Theme.modeColor("path") }

    var body: some View {
        VStack(spacing: 12) {
            movesBadge
            GeometryReader { geo in
                let side = min(geo.size.width, geo.size.height)
                let cell = side / CGFloat(state.size)
                ZStack(alignment: .topLeading) {
                    ForEach(0..<state.size, id: \.self) { r in
                        ForEach(0..<state.size, id: \.self) { c in
                            cellView(r, c)
                                .frame(width: cell, height: cell)
                                .offset(x: CGFloat(c) * cell, y: CGFloat(r) * cell)
                        }
                    }
                    gridLines(cell: cell)
                    trailLines(cell: cell)
                    ForEach(Array(state.trail.enumerated()), id: \.offset) { i, p in
                        if p.count == 2 { dot(i, cell: cell).position(center(p, cell)) }
                    }
                }
                .frame(width: side, height: side)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            .aspectRatio(1, contentMode: .fit)
            .frame(maxWidth: 380)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityText)
    }

    private var accessibilityText: String {
        let s = state.trail.first ?? [0, 0]
        return "\(state.size)×\(state.size), \(s[0] + 1)-\(s[1] + 1), \(state.moveCount), \(state.total)"
    }

    /// "N번 이동 후" 배지: 문구 대신 언어 중립 기호+숫자.
    private var movesBadge: some View {
        HStack(spacing: 6) {
            Image(systemName: "flag.checkered")
            Text("× \(state.total)").monospacedDigit()
        }
        .font(.system(size: 15, weight: .bold, design: .rounded))
        .foregroundStyle(mode)
        .padding(.horizontal, 14).padding(.vertical, 6)
        .background(Capsule().fill(mode.opacity(0.15)))
        .overlay(Capsule().strokeBorder(mode.opacity(0.4), lineWidth: 1))
    }

    private func center(_ p: [Int], _ cell: CGFloat) -> CGPoint {
        CGPoint(x: (CGFloat(p[1]) + 0.5) * cell, y: (CGFloat(p[0]) + 0.5) * cell)
    }

    private func cellView(_ r: Int, _ c: Int) -> some View {
        let selected = state.selected == r * state.size + c + 1
        return ZStack {
            Rectangle().fill(Color.black.opacity(0.25))
            if selected {
                Rectangle().fill(mode.opacity(0.35))
                Rectangle().strokeBorder(mode, lineWidth: 2.5).shadow(color: Theme.modeGlow("path"), radius: 6)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture {
            guard !disabled else { return }
            state.tap(r, c)
            onTap()
        }
    }

    private func gridLines(cell: CGFloat) -> some View {
        let n = state.size
        let side = cell * CGFloat(n)
        return ZStack {
            Path { p in
                for k in 1..<n {
                    let x = CGFloat(k) * cell
                    p.move(to: CGPoint(x: x, y: 0)); p.addLine(to: CGPoint(x: x, y: side))
                    p.move(to: CGPoint(x: 0, y: x)); p.addLine(to: CGPoint(x: side, y: x))
                }
            }
            .stroke(Color.white.opacity(0.12), lineWidth: 1)
            Rectangle().strokeBorder(Color.white.opacity(0.3), lineWidth: 1.5)
        }
        .allowsHitTesting(false)
    }

    /// 이동 선 + 화살촉(점 반지름만큼 짧게).
    private func trailLines(cell: CGFloat) -> some View {
        let r = dotRadius(cell)
        return Path { p in
            for i in 1..<max(1, state.trail.count) {
                let a = state.trail[i - 1], b = state.trail[i]
                guard a.count == 2, b.count == 2 else { continue }
                let pa = center(a, cell), pb = center(b, cell)
                let dx = pb.x - pa.x, dy = pb.y - pa.y
                let len = hypot(dx, dy)
                guard len > r * 2 else { continue }
                let ux = dx / len, uy = dy / len
                let s = CGPoint(x: pa.x + ux * r, y: pa.y + uy * r)
                let e = CGPoint(x: pb.x - ux * r, y: pb.y - uy * r)
                p.move(to: s); p.addLine(to: e)
                // 화살촉
                let h = r * 0.7
                let nx = -uy, ny = ux
                p.move(to: e)
                p.addLine(to: CGPoint(x: e.x - ux * h + nx * h * 0.6, y: e.y - uy * h + ny * h * 0.6))
                p.move(to: e)
                p.addLine(to: CGPoint(x: e.x - ux * h - nx * h * 0.6, y: e.y - uy * h - ny * h * 0.6))
            }
        }
        .stroke(mode.opacity(0.85), style: StrokeStyle(lineWidth: 2.2, lineCap: .round, lineJoin: .round))
        .allowsHitTesting(false)
    }

    private func dotRadius(_ cell: CGFloat) -> CGFloat { min(cell * 0.3, 17) }

    /// 번호 점: 0=출발(이중 원), 마지막 보인 점은 채워 강조.
    private func dot(_ i: Int, cell: CGFloat) -> some View {
        let r = dotRadius(cell)
        let isLast = i == state.trail.count - 1
        return ZStack {
            Circle().fill(isLast ? mode : Color(white: 0.12))
            Circle().strokeBorder(mode, lineWidth: 2)
            if i == 0 { Circle().strokeBorder(mode.opacity(0.7), lineWidth: 1.5).padding(-4) }
            Text("\(i)")
                .font(.system(size: r * 1.05, weight: .bold, design: .rounded))
                .foregroundStyle(isLast ? Color.black.opacity(0.8) : Theme.textPrimary)
                .minimumScaleFactor(0.6)
        }
        .frame(width: r * 2, height: r * 2)
        .shadow(color: isLast ? Theme.modeGlow("path") : .clear, radius: 6)
        .allowsHitTesting(false)
    }
}

// MARK: - 확인 버튼

struct PathControlsView: View {
    let state: PathState
    var disabled: Bool = false
    let onCheck: () -> Void

    var body: some View {
        let on = state.canCheck && !disabled
        Button(action: onCheck) {
            Text(String.loc("path_check"))
                .font(.system(size: 17, weight: .bold))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity, minHeight: 50)
                .background(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(on ? AnyShapeStyle(Theme.modeGradient("path")) : AnyShapeStyle(Theme.modeColor("path").opacity(0.25)))
                )
        }
        .buttonStyle(.plain)
        .disabled(!on)
        .opacity(on ? 1 : 0.6)
    }
}
