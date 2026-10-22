import SwiftUI

// MARK: - 저울 추리 디스플레이

/// 균형 저울 목록(받침대 + 수평 빔 + 두 접시)과, 마지막에 `query 접시 = ?` 강조 카드.
struct ScaleDisplay: View {
    let puzzle: Puzzle
    var typed: String = ""
    var feedback: AnswerFeedback? = nil

    private var mode: Color { Theme.modeColor("scale") }
    private var scales: [[[String]]] { puzzle.scales ?? [] }
    private var query: [String] { puzzle.query ?? [] }

    var body: some View {
        VStack(spacing: 12) {
            queryCard   // 질문을 맨 위에 — 저울이 많아 카드가 길어도 키패드에 가리지 않는다
            ForEach(Array(scales.enumerated()), id: \.offset) { _, s in
                BalanceScale(left: s.first ?? [], right: s.count > 1 ? s[1] : [], color: mode)
            }
        }
        .padding(.vertical, 16).padding(.horizontal, 14)
        .frame(maxWidth: .infinity)
        .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(Color.black.opacity(0.35)))
        .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).stroke(mode.opacity(0.35), lineWidth: 1))
        .id(puzzle.id)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityText)
    }

    private var tint: Color {
        feedback == .correct ? Theme.success : (feedback == .wrong ? Theme.danger : mode)
    }

    private var queryCard: some View {
        HStack(spacing: 10) {
            ScalePan(tokens: query, color: mode, plain: true)
            Text("=")
                .font(.system(size: 24, weight: .bold, design: .rounded))
                .foregroundStyle(Theme.textSecondary)
            Text(typed.isEmpty ? "?" : typed)
                .font(.system(size: 32, weight: .heavy, design: .monospaced))
                .foregroundStyle(typed.isEmpty ? mode.opacity(0.7) : tint)
                .shadow(color: tint.opacity(typed.isEmpty ? 0 : 0.6), radius: 6)
                .lineLimit(1).minimumScaleFactor(0.6)
                .frame(minWidth: 56)
        }
        .padding(.vertical, 10).padding(.horizontal, 12)
        .frame(maxWidth: .infinity)
        .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(tint.opacity(0.10)))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(tint.opacity(0.85), lineWidth: 1.5))
        .shadow(color: tint.opacity(0.35), radius: 8)
        .padding(.bottom, 4)
    }

    private var accessibilityText: String {
        var lines = scales.map { s in
            s.first.map { $0.joined(separator: " ") }.map { l in
                l + " = " + (s.count > 1 ? s[1] : []).joined(separator: " ")
            } ?? ""
        }
        lines.append(query.joined(separator: " ") + " = " + (typed.isEmpty ? "?" : typed))
        return lines.joined(separator: ", ")
    }
}

// MARK: - 균형 저울 한 개

private struct BalanceScale: View {
    let left: [String]
    let right: [String]
    let color: Color

    private let standW: CGFloat = 16
    private let beamH: CGFloat = 5

    var body: some View {
        VStack(spacing: 0) {
            beam
            HStack(alignment: .top, spacing: 0) {
                panColumn(left)
                stand
                panColumn(right)
            }
            Capsule()
                .fill(LinearGradient(colors: [color.opacity(0.9), color.opacity(0.5)], startPoint: .top, endPoint: .bottom))
                .frame(width: 68, height: 7)
                .shadow(color: color.opacity(0.5), radius: 4)
        }
    }

    /// 수평 빔 + 가운데 피벗(양 접시의 중심 사이를 잇는다).
    private var beam: some View {
        GeometryReader { geo in
            let pan = (geo.size.width - standW) / 2
            ZStack {
                Capsule()
                    .fill(LinearGradient(colors: [color, color.opacity(0.6)], startPoint: .top, endPoint: .bottom))
                    .frame(width: max(0, geo.size.width - pan), height: beamH)
                    .shadow(color: color.opacity(0.6), radius: 5)
                Circle()
                    .fill(Color(hex: 0x1B1B2E))
                    .overlay(Circle().stroke(color, lineWidth: 2.5))
                    .frame(width: 16, height: 16)
                    .shadow(color: color.opacity(0.7), radius: 5)
            }
            .frame(width: geo.size.width, height: geo.size.height)
        }
        .frame(height: 16)
    }

    private var stand: some View {
        Rectangle()
            .fill(LinearGradient(colors: [color.opacity(0.7), color.opacity(0.35)], startPoint: .top, endPoint: .bottom))
            .frame(width: 5)
            .frame(width: standW)
            .frame(maxHeight: .infinity)
    }

    private func panColumn(_ tokens: [String]) -> some View {
        VStack(spacing: 0) {
            Rectangle().fill(color.opacity(0.55)).frame(width: 1.5, height: 10)
            ScalePan(tokens: tokens, color: color, plain: false)
        }
        .frame(maxWidth: .infinity)
        .frame(maxHeight: .infinity, alignment: .top)
    }
}

// MARK: - 접시(토큰 모음)

private struct ScalePan: View {
    let tokens: [String]
    let color: Color
    let plain: Bool   // true: query 카드 안(테두리 없는 토큰 묶음)

    var body: some View {
        FlowLayout(spacing: 3, lineSpacing: 2) {
            ForEach(Array(tokens.enumerated()), id: \.offset) { _, t in
                ScaleToken(token: t)
            }
        }
        .padding(.horizontal, plain ? 0 : 6).padding(.vertical, plain ? 0 : 6)
        .frame(maxWidth: plain ? nil : .infinity, minHeight: plain ? 0 : 44)
        .frame(maxHeight: plain ? nil : .infinity)   // 양쪽 접시 높이를 맞추려면 배경보다 먼저 늘린다
        .background {
            if !plain {
                UnevenRoundedRectangle(topLeadingRadius: 6, bottomLeadingRadius: 18, bottomTrailingRadius: 18, topTrailingRadius: 6, style: .continuous)
                    .fill(color.opacity(0.10))
            }
        }
        .overlay {
            if !plain {
                UnevenRoundedRectangle(topLeadingRadius: 6, bottomLeadingRadius: 18, bottomTrailingRadius: 18, topTrailingRadius: 6, style: .continuous)
                    .stroke(color.opacity(0.55), lineWidth: 1.2)
            }
        }
    }
}

/// 이모지(미지수) 또는 숫자 추(둥근 무게 배지).
private struct ScaleToken: View {
    let token: String

    var body: some View {
        if Int(token) != nil {
            Text(token)
                .font(.system(size: token.count > 2 ? 10 : 12, weight: .heavy, design: .rounded))
                .monospacedDigit()
                .foregroundStyle(Color(hex: 0x11111C))
                .minimumScaleFactor(0.6)
                .frame(width: 24, height: 24)
                .background(
                    Circle().fill(LinearGradient(colors: [Color(hex: 0xE5E7EB), Color(hex: 0x8B90A0)],
                                                 startPoint: .topLeading, endPoint: .bottomTrailing))
                )
                .overlay(Circle().stroke(Color.white.opacity(0.55), lineWidth: 1))
                .shadow(color: .black.opacity(0.4), radius: 2, y: 1)
                .frame(width: 30, height: 30)
        } else {
            Text(token)
                .font(.system(size: 25))
                .frame(minWidth: 30, minHeight: 30)
        }
    }
}

// MARK: - 줄바꿈 레이아웃

private struct FlowLayout: Layout {
    var spacing: CGFloat = 4
    var lineSpacing: CGFloat = 4

    private func rows(_ subviews: Subviews, width: CGFloat) -> (positions: [CGPoint], size: CGSize) {
        var pos: [CGPoint] = []
        var x: CGFloat = 0, y: CGFloat = 0, rowH: CGFloat = 0, maxW: CGFloat = 0
        for s in subviews {
            let sz = s.sizeThatFits(.unspecified)
            if x > 0 && x + sz.width > width {
                x = 0; y += rowH + lineSpacing; rowH = 0
            }
            pos.append(CGPoint(x: x, y: y))
            x += sz.width + spacing
            rowH = max(rowH, sz.height)
            maxW = max(maxW, x - spacing)
        }
        return (pos, CGSize(width: maxW, height: y + rowH))
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        rows(subviews, width: proposal.width ?? .infinity).size
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        // 줄별로 가운데 정렬
        let r = rows(subviews, width: bounds.width)
        var lines: [[Int]] = []
        var lastY: CGFloat = -1
        for (i, p) in r.positions.enumerated() {
            if p.y != lastY { lines.append([]); lastY = p.y }
            lines[lines.count - 1].append(i)
        }
        for line in lines {
            let first = r.positions[line[0]].x
            let lastIdx = line[line.count - 1]
            let end = r.positions[lastIdx].x + subviews[lastIdx].sizeThatFits(.unspecified).width
            let shift = (bounds.width - (end - first)) / 2 - first
            for i in line {
                subviews[i].place(at: CGPoint(x: bounds.minX + r.positions[i].x + max(0, shift),
                                              y: bounds.minY + r.positions[i].y), proposal: .unspecified)
            }
        }
    }
}

#Preview("Scale") {
    let p = Puzzle(
        id: "sc_prev", type: "scale", track: "scale", chapter: 5, order: 3,
        tokens: nil, grid: nil, figures: nil, figureTokens: nil, figureChoices: nil, prompt: nil,
        statements: nil, numbers: nil, target: nil, equation: nil, moves: nil,
        frames: nil, combine: nil, frameMs: nil,
        pairs: nil,
        scales: [[["🍎", "🍎"], ["🍌", "6"]], [["🍌"], ["🍇", "🍇", "12"]], [["🍎", "🍎", "🍎", "🍇", "🍌"], ["20", "20", "7"]]],
        query: ["🍎", "🍇", "🍌", "🍎", "🍎"],
        board: nil, rules: nil, lt: nil, cages: nil, size: nil, start: nil, trail: nil, total: nil,
        answer: "9", inputType: .keypad, choices: nil, hints: [:], explanation: [:])
    return ZStack {
        Color.black.ignoresSafeArea()
        ScrollView { ScaleDisplay(puzzle: p, typed: "12").padding(20) }
    }
}
