import SwiftUI

// MARK: - Feedback state

enum AnswerFeedback: Equatable {
    case correct
    case wrong
}

// MARK: - Sequence display

/// 수열을 카드 셀로 표시. 빈칸(nil)은 그라데이션 테두리로 강조하며
/// 현재 입력값 또는 정답(reveal)을 보여준다.
struct SequenceDisplay: View {
    let puzzle: Puzzle
    var typed: String = ""
    var reveal: Bool = false
    var feedback: AnswerFeedback? = nil

    @State private var popScale: CGFloat = 1   // 정답 칸 팝 애니메이션

    var body: some View {
        Group {
            if puzzle.type == "equation", let grid = puzzle.grid, !grid.isEmpty {
                equationBody(grid)
            } else if let grid = puzzle.grid, !grid.isEmpty {
                gridBody(grid)
            } else {
                rowBody(puzzle.tokens ?? [])
            }
        }
        .onChange(of: feedback) { _, newValue in
            if newValue == .correct { triggerPop() }
        }
    }

    // MARK: - 수식형(type=equation)

    /// 각 줄을 "[2] + [3] = [13]" 처럼 — 숫자는 박스, 연산자는 사이 텍스트, 빈칸은 강조 박스.
    private func equationBody(_ grid: [[String?]]) -> some View {
        let cols = max(1, grid.map(\.count).max() ?? 1)
        let fontSize: CGFloat = cols >= 7 ? 19 : (cols >= 6 ? 22 : (cols >= 5 ? 25 : 28))
        let boxSide: CGFloat = fontSize * 1.95
        return VStack(spacing: 12) {
            ForEach(Array(grid.enumerated()), id: \.offset) { _, row in
                HStack(spacing: 6) {
                    ForEach(Array(row.enumerated()), id: \.offset) { _, value in
                        if value == nil {
                            eqBox(blankText(), fontSize: fontSize, side: boxSide, isBlank: true)
                        } else if isOperator(value!) {
                            Text(value!)
                                .font(.system(size: fontSize, weight: .semibold, design: .rounded))
                                .foregroundStyle(Theme.textTertiary)
                                .frame(minWidth: fontSize * 0.7)
                        } else {
                            eqBox(value!, fontSize: fontSize, side: boxSide, isBlank: false)
                        }
                    }
                }
            }
        }
        .frame(maxWidth: .infinity)
    }

    private func isOperator(_ s: String) -> Bool {
        ["+", "-", "−", "×", "x", "*", "÷", "/", "=", "·", ">", "<", "→"].contains(s)
    }

    /// 수식형 숫자/빈칸 박스.
    private func eqBox(_ text: String, fontSize: CGFloat, side: CGFloat, isBlank: Bool) -> some View {
        Text(text)
            .font(.system(size: fontSize, weight: .bold, design: .rounded))
            .foregroundStyle(isBlank ? Theme.textPrimary : Theme.textSecondary)
            .lineLimit(1)
            .minimumScaleFactor(0.5)
            .frame(minWidth: side, minHeight: side)
            .padding(.horizontal, 6)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(isBlank ? Color.white.opacity(0.03) : Theme.card)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(isBlank ? blankStroke : AnyShapeStyle(Theme.stroke), lineWidth: isBlank ? 2 : 1)
            )
            .shadow(color: Theme.success.opacity(isBlank && feedback == .correct ? 0.7 : 0), radius: 10)
            .scaleEffect(isBlank ? popScale : 1)
    }

    private func triggerPop() {
        popScale = 1
        withAnimation(.spring(response: 0.22, dampingFraction: 0.42)) { popScale = 1.22 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.26) {
            withAnimation(.spring(response: 0.32, dampingFraction: 0.6)) { popScale = 1 }
        }
    }

    /// 단일 행 표시 (칸 수에 따라 폰트/간격 축소 → 스크롤 없이 화면 폭에 맞춤).
    private func rowBody(_ tokens: [String?]) -> some View {
        let count = max(1, tokens.count)
        let spacing: CGFloat = count >= 7 ? 6 : (count >= 6 ? 8 : (count >= 5 ? 10 : 12))
        let fontSize: CGFloat = count >= 7 ? 20 : (count >= 6 ? 23 : (count >= 5 ? 26 : 30))
        let height: CGFloat = count >= 6 ? 66 : 76
        return HStack(spacing: spacing) {
            ForEach(Array(tokens.enumerated()), id: \.offset) { _, value in
                cell(value: value, isBlank: value == nil, fontSize: fontSize, height: height)
            }
        }
        .frame(maxWidth: .infinity)
    }

    /// 격자형(두 줄/매트릭스/수식형) 표시.
    private func gridBody(_ grid: [[String?]]) -> some View {
        let cols = max(1, grid.map(\.count).max() ?? 1)
        let spacing: CGFloat = cols >= 5 ? 8 : 10
        let fontSize: CGFloat = cols >= 5 ? 22 : (cols >= 4 ? 25 : 28)
        let rows = grid.count
        let height: CGFloat = rows >= 3 ? 52 : 60
        return VStack(spacing: spacing) {
            ForEach(Array(grid.enumerated()), id: \.offset) { _, row in
                HStack(spacing: spacing) {
                    ForEach(Array(row.enumerated()), id: \.offset) { _, value in
                        cell(value: value, isBlank: value == nil, fontSize: fontSize, height: height)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity)
    }

    private func blankText() -> String {
        if reveal { return "\(puzzle.answer)" }
        return typed.isEmpty ? "?" : typed
    }

    private var blankStroke: AnyShapeStyle {
        switch feedback {
        case .correct: return AnyShapeStyle(Theme.success)
        case .wrong: return AnyShapeStyle(Theme.danger)
        case .none: return AnyShapeStyle(Theme.accentGradient)
        }
    }

    @ViewBuilder
    private func cell(value: String?, isBlank: Bool, fontSize: CGFloat, height: CGFloat) -> some View {
        Text(isBlank ? blankText() : (value ?? ""))
            .font(.system(size: fontSize, weight: .bold, design: .rounded))
            .lineLimit(1)
            .minimumScaleFactor(0.4)            // 긴 토큰(예: 1211)도 칸 안에 맞춤
            .foregroundStyle(isBlank ? Theme.textPrimary : Theme.textSecondary)
            .frame(maxWidth: .infinity)         // 칸들이 화면 폭을 균등 분할
            .frame(height: height)
            .padding(.horizontal, 2)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(isBlank ? Color.white.opacity(0.03) : Theme.card)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(isBlank ? blankStroke : AnyShapeStyle(Theme.stroke),
                            lineWidth: isBlank ? 2 : 1)
            )
            .shadow(color: Theme.success.opacity(isBlank && feedback == .correct ? 0.75 : 0),
                    radius: 14)
            .scaleEffect(isBlank ? popScale : 1)
            .zIndex(isBlank ? 1 : 0)
    }
}

// MARK: - Correct badge

/// 정답 시 잠깐 나타나는 "정답!" 배지.
struct CorrectBadge: View {
    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "checkmark.circle.fill")
            Text(String.loc("correct")).fontWeight(.heavy)
        }
        .font(.system(size: 20, weight: .bold, design: .rounded))
        .foregroundStyle(.white)
        .padding(.horizontal, 22)
        .padding(.vertical, 12)
        .background(Theme.success, in: Capsule())
        .shadow(color: Theme.success.opacity(0.5), radius: 18, y: 6)
    }
}

// MARK: - Keypad

struct KeypadView: View {
    let onDigit: (Int) -> Void
    let onDelete: () -> Void
    let onSubmit: () -> Void
    var canSubmit: Bool

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 12), count: 3)

    var body: some View {
        LazyVGrid(columns: columns, spacing: 12) {
            ForEach(1...9, id: \.self) { n in
                digitKey(n)
            }
            deleteKey
            digitKey(0)
            submitKey
        }
    }

    private func digitKey(_ n: Int) -> some View {
        Button { onDigit(n) } label: {
            Text("\(n)")
                .font(.system(size: 26, weight: .semibold, design: .rounded))
                .foregroundStyle(Theme.textPrimary)
                .frame(maxWidth: .infinity)
                .frame(height: 60)
                .card(cornerRadius: 16)
        }
        .buttonStyle(.plain)
    }

    private var deleteKey: some View {
        Button(action: onDelete) {
            Image(systemName: "delete.left")
                .font(.system(size: 22, weight: .medium))
                .foregroundStyle(Theme.textSecondary)
                .frame(maxWidth: .infinity)
                .frame(height: 60)
                .card(cornerRadius: 16)
        }
        .buttonStyle(.plain)
    }

    private var submitKey: some View {
        Button(action: onSubmit) {
            Image(systemName: "checkmark")
                .font(.system(size: 22, weight: .bold))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 60)
                .background {
                    if canSubmit { Theme.accentGradient } else { Color.white.opacity(0.08) }
                }
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(!canSubmit)
    }
}

// MARK: - Choices

struct ChoicesView: View {
    let choices: [String]
    var disabled: Bool = false
    let onPick: (String) -> Void

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 12), count: 2)

    var body: some View {
        LazyVGrid(columns: columns, spacing: 12) {
            ForEach(choices, id: \.self) { c in
                Button { onPick(c) } label: {
                    Text(c)
                        .font(.system(size: 24, weight: .bold, design: .rounded))
                        .foregroundStyle(Theme.textPrimary)
                        .frame(maxWidth: .infinity)
                        .frame(height: 64)
                        .card(cornerRadius: 16)
                }
                .buttonStyle(.plain)
            }
        }
        .disabled(disabled)
    }
}

// MARK: - Shake effect

struct Shake: GeometryEffect {
    var animatableData: CGFloat
    func effectValue(size: CGSize) -> ProjectionTransform {
        let translation = 10 * sin(animatableData * .pi * 4)
        return ProjectionTransform(CGAffineTransform(translationX: translation, y: 0))
    }
}
