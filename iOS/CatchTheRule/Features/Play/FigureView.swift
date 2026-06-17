import SwiftUI

// MARK: - 도형 규칙 렌더러
//
// 두 가지 표현을 그린다.
//  - 숫자형(figure.hasSlots): 도형 윤곽 + 꼭짓점/세그먼트/중앙에 숫자. nil 슬롯이 빈칸.
//  - 시각형(no slots): rotation/filled/count 로 순수 모양을 그린다(시퀀스/보기 공용).

// MARK: 숫자형 — 예시 도형 여러 개를 한 줄에

/// 숫자형 퍼즐: 완성된 예시 도형 + 빈칸 도형을 나란히 보여줘 규칙을 추론하게 한다.
struct FigureNumberRow: View {
    let figures: [Figure]
    var blankText: String = "?"
    var feedback: AnswerFeedback? = nil

    var body: some View {
        // 도형 수에 따라 크기 축소(가로 넘침 방지).
        let n = max(1, figures.count)
        let side: CGFloat = n >= 3 ? 100 : (n == 2 ? 138 : 150)
        let spacing: CGFloat = n >= 3 ? 6 : 12
        HStack(spacing: spacing) {
            ForEach(Array(figures.enumerated()), id: \.offset) { _, fig in
                FigureNumberView(figure: fig, blankText: blankText, feedback: feedback, side: side)
            }
        }
        .frame(maxWidth: .infinity)
    }
}

/// 숫자형 도형 1개(삼각형/사각형/원). 빈칸 슬롯은 blankText 로 표시.
struct FigureNumberView: View {
    let figure: Figure
    var blankText: String = "?"
    var feedback: AnswerFeedback? = nil
    var side: CGFloat = 150

    private var slots: [String?] { figure.slots ?? [] }
    private var labelSide: CGFloat { max(28, side * 0.27) }
    private var labelFont: CGFloat { max(14, side * 0.135) }

    var body: some View {
        ZStack {
            outline
            ForEach(Array(anchors().enumerated()), id: \.offset) { i, pt in
                slotLabel(slots[safe: i] ?? "", at: pt)
            }
        }
        .frame(width: side, height: side)
    }

    // 도형 윤곽선
    private var outline: some View {
        let inset = side * 0.16
        return ZStack {
            switch figure.shape {
            case "square":
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .stroke(Theme.stroke, lineWidth: 2)
                    .padding(inset)
            case "circle":
                Circle().stroke(Theme.stroke, lineWidth: 2).padding(inset)
                dividers   // 세그먼트 구분선
            default: // triangle
                TriangleShape().stroke(Theme.stroke, style: StrokeStyle(lineWidth: 2, lineJoin: .round))
                    .padding(inset)
            }
        }
    }

    // 원 세그먼트 구분선(슬롯 경계마다 중심→가장자리).
    private var dividers: some View {
        let c = CGPoint(x: side / 2, y: side / 2)
        let edges: [CGPoint] = dividerEdges()
        return ZStack {
            ForEach(Array(edges.enumerated()), id: \.offset) { _, edge in
                Path { p in
                    p.move(to: c)
                    p.addLine(to: edge)
                }
                .stroke(Theme.stroke.opacity(0.6), lineWidth: 1)
            }
        }
    }

    private func dividerEdges() -> [CGPoint] {
        let n = max(1, slots.count)
        let cx = Double(side) / 2, cy = Double(side) / 2
        let r = Double(side) / 2 - Double(side) * 0.16
        let count = Double(n)
        return (0..<n).map { i -> CGPoint in
            let deg: Double = -90.0 + (Double(i) + 0.5) * 360.0 / count
            let ang: Double = deg * .pi / 180
            return CGPoint(x: cx + cos(ang) * r, y: cy + sin(ang) * r)
        }
    }

    // 슬롯 숫자(또는 빈칸).
    @ViewBuilder
    private func slotLabel(_ value: String?, at pt: CGPoint) -> some View {
        let isBlank = (value == nil)
        let text = isBlank ? blankText : (value ?? "")
        Text(text)
            .font(.system(size: labelFont, weight: .bold, design: .rounded))
            .minimumScaleFactor(0.5)
            .lineLimit(1)
            .foregroundStyle(isBlank ? Theme.textPrimary : Theme.textSecondary)
            .frame(width: labelSide, height: labelSide)
            .background(
                Circle().fill(isBlank ? Color.white.opacity(0.05) : Theme.card)
            )
            .overlay(
                Circle().stroke(isBlank ? blankStroke : AnyShapeStyle(Theme.stroke),
                                lineWidth: isBlank ? 2 : 1)
            )
            .shadow(color: Theme.success.opacity(isBlank && feedback == .correct ? 0.7 : 0), radius: 10)
            .position(pt)
    }

    private var blankStroke: AnyShapeStyle {
        switch feedback {
        case .correct: return AnyShapeStyle(Theme.success)
        case .wrong: return AnyShapeStyle(Theme.danger)
        case .none: return AnyShapeStyle(Theme.accentGradient)
        }
    }

    // 슬롯 앵커 좌표(시계방향, 위에서 시작). 마지막 1칸이 남으면 중앙(triangle/square).
    private func anchors() -> [CGPoint] {
        let n = slots.count
        let inset = side * 0.16
        let center = CGPoint(x: side / 2, y: side / 2)
        switch figure.shape {
        case "square":
            let lo = inset, hi = side - inset
            let corners = [CGPoint(x: lo, y: lo), CGPoint(x: hi, y: lo),
                           CGPoint(x: hi, y: hi), CGPoint(x: lo, y: hi)]
            return n > 4 ? corners + [center] : Array(corners.prefix(n))
        case "circle":
            let cx = Double(center.x), cy = Double(center.y)
            let r = Double(side) / 2 - Double(side) * 0.3
            let count = Double(max(1, n))
            return (0..<n).map { i -> CGPoint in
                let ang: Double = (-90.0 + Double(i) * 360.0 / count) * .pi / 180
                return CGPoint(x: cx + cos(ang) * r, y: cy + sin(ang) * r)
            }
        default: // triangle
            let lo = inset, hi = side - inset
            // 라벨이 변에 겹치지 않도록 꼭짓점을 살짝 안쪽으로.
            let verts = [CGPoint(x: side / 2, y: lo + 6),
                         CGPoint(x: hi - 6, y: hi - 4),
                         CGPoint(x: lo + 6, y: hi - 4)]
            // 삼각형 무게중심은 박스 정중앙보다 아래 → 중앙 슬롯을 꼭짓점 평균으로.
            let triCenter = CGPoint(x: side / 2,
                                    y: verts.reduce(0) { $0 + $1.y } / CGFloat(verts.count))
            return n > 3 ? verts + [triCenter] : Array(verts.prefix(n))
        }
    }
}

// MARK: 시각형 — 순수 모양

/// 시각 규칙용 도형(회전/채움/개수). 시퀀스 셀·보기 공용.
struct FigureGlyph: View {
    let figure: Figure
    var size: CGFloat = 44

    var body: some View {
        HStack(spacing: 4) {
            ForEach(0..<figure.repeatCount, id: \.self) { _ in
                glyph
            }
        }
    }

    @ViewBuilder
    private var glyph: some View {
        let s = figure.repeatCount > 1 ? size * 0.62 : size
        switch figure.shape {
        case "square":
            shapeFill(RoundedRectangle(cornerRadius: 6, style: .continuous), s: s)
        case "circle", "dot":
            shapeFill(Circle(), s: s)
        case "arrow":
            ArrowShape()
                .rotation(.degrees(figure.rotationDegrees))
                .fill(Theme.accent)
                .frame(width: s, height: s)
        default: // triangle
            shapeFill(TriangleShape().rotation(.degrees(figure.rotationDegrees)), s: s)
        }
    }

    @ViewBuilder
    private func shapeFill<S: Shape>(_ shape: S, s: CGFloat) -> some View {
        if figure.isFilled {
            shape.fill(Theme.accent).frame(width: s, height: s)
        } else {
            shape.stroke(Theme.accent, lineWidth: 2.5).frame(width: s, height: s)
        }
    }
}

// MARK: - Shapes

/// 위를 향하는 정삼각형.
struct TriangleShape: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: rect.midX, y: rect.minY))
        p.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        p.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
        p.closeSubpath()
        return p
    }
}

/// 위를 향하는 화살표(머리+기둥).
struct ArrowShape: Shape {
    func path(in rect: CGRect) -> Path {
        let w = rect.width, h = rect.height
        var p = Path()
        // 화살촉
        p.move(to: CGPoint(x: rect.midX, y: rect.minY))
        p.addLine(to: CGPoint(x: rect.minX, y: rect.minY + h * 0.5))
        p.addLine(to: CGPoint(x: rect.minX + w * 0.28, y: rect.minY + h * 0.5))
        // 기둥
        p.addLine(to: CGPoint(x: rect.minX + w * 0.28, y: rect.maxY))
        p.addLine(to: CGPoint(x: rect.minX + w * 0.72, y: rect.maxY))
        p.addLine(to: CGPoint(x: rect.minX + w * 0.72, y: rect.minY + h * 0.5))
        p.addLine(to: CGPoint(x: rect.maxX, y: rect.minY + h * 0.5))
        p.closeSubpath()
        return p
    }
}

// MARK: - Util

private extension Array {
    subscript(safe index: Int) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
