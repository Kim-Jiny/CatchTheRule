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
    var onPickStatement: ((Int) -> Void)? = nil   // 모순찾기: 문장 번호(1-based) 탭

    @State private var popScale: CGFloat = 1   // 정답 칸 팝 애니메이션

    var body: some View {
        Group {
            if puzzle.isContradiction {
                contradictionList(puzzle.localizedStatements)
            } else if puzzle.isPrompt {
                promptCard(puzzle.localizedPrompt)
            } else if puzzle.isFigure, let figures = puzzle.figures {
                FigureNumberRow(figures: figures, blankText: blankText(), feedback: feedback)
            } else if puzzle.isFigureSequence {
                figureRow(puzzle.figureTokens ?? [])
            } else if puzzle.type == "equation", let grid = puzzle.grid, !grid.isEmpty {
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

    // MARK: - 모순찾기(문장 목록)

    private static let circledNumbers = ["①","②","③","④","⑤","⑥","⑦","⑧","⑨","⑩","⑪","⑫","⑬","⑭","⑮","⑯","⑰","⑱","⑲","⑳"]

    /// 번호 매긴 문장 카드 목록. 각 카드를 탭하면 그 번호를 제출.
    /// reveal 이면 정답(모순) 문장을 초록, 오답 탭은 빨강으로 강조.
    private func contradictionList(_ statements: [String]) -> some View {
        VStack(spacing: 10) {
            ForEach(Array(statements.enumerated()), id: \.offset) { i, text in
                statementCard(number: i + 1, text: text)
            }
        }
        .frame(maxWidth: .infinity)
    }

    @ViewBuilder
    private func statementCard(number: Int, text: String) -> some View {
        let isAnswer = String(number) == puzzle.answer
        let isPicked = typed == String(number)
        let showCorrect = reveal && isAnswer
        let showWrong = feedback == .wrong && isPicked
        let badge = number <= Self.circledNumbers.count ? Self.circledNumbers[number - 1] : "\(number)"
        Button {
            guard !reveal else { return }
            onPickStatement?(number)
        } label: {
            HStack(alignment: .top, spacing: 12) {
                Text(badge)
                    .font(.system(size: 20, weight: .bold, design: .rounded))
                    .foregroundStyle(showCorrect ? Theme.success : (showWrong ? Theme.danger : Theme.accent2))
                Text(text)
                    .font(.system(size: 16))
                    .foregroundStyle(Theme.textPrimary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
                if showCorrect {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.system(size: 15))
                        .foregroundStyle(Theme.success)
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(showCorrect ? Theme.success.opacity(0.12) : Theme.card)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(showCorrect ? AnyShapeStyle(Theme.success)
                            : (showWrong ? AnyShapeStyle(Theme.danger) : AnyShapeStyle(Theme.stroke)),
                            lineWidth: (showCorrect || showWrong) ? 2 : 1)
            )
        }
        .buttonStyle(.plain)
        .disabled(reveal)
    }

    // MARK: - 논리형(질문 문단)

    /// 논리 퍼즐의 질문 문단을 카드로 표시. 줄바꿈 보존, 왼쪽 정렬.
    private func promptCard(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 18, weight: .medium))
            .foregroundStyle(Theme.textPrimary)
            .lineSpacing(6)
            .frame(maxWidth: .infinity, alignment: .leading)
            .fixedSize(horizontal: false, vertical: true)
            .padding(20)
            .card()
    }

    // MARK: - 도형 시퀀스(시각형)

    /// 도형이 칸별로 변하는 시퀀스. nil 셀이 빈칸(보기에서 고를 도형 자리).
    private func figureRow(_ figs: [Figure?]) -> some View {
        let count = max(1, figs.count)
        let spacing: CGFloat = count >= 6 ? 6 : 10
        let glyph: CGFloat = count >= 6 ? 34 : 42
        return HStack(spacing: spacing) {
            ForEach(Array(figs.enumerated()), id: \.offset) { _, fig in
                if let fig {
                    FigureGlyph(figure: fig, size: glyph)
                        .frame(maxWidth: .infinity)
                        .frame(height: 76)
                        .card(cornerRadius: 16)
                } else {
                    figureBlankCell(glyph: glyph)
                }
            }
        }
        .frame(maxWidth: .infinity)
    }

    // 시퀀스의 빈칸. reveal 이면 정답 도형(code 일치)을 보여준다.
    @ViewBuilder
    private func figureBlankCell(glyph: CGFloat) -> some View {
        let answerFig = reveal ? puzzle.figureChoices?.first(where: { $0.code == puzzle.answer }) : nil
        ZStack {
            if let answerFig {
                FigureGlyph(figure: answerFig, size: glyph)
            } else {
                Text("?")
                    .font(.system(size: 30, weight: .bold, design: .rounded))
                    .foregroundStyle(Theme.textPrimary)
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: 76)
        .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Color.white.opacity(0.03)))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous)
            .stroke(blankStroke, lineWidth: 2))
        .shadow(color: Theme.success.opacity(feedback == .correct ? 0.75 : 0), radius: 14)
        .scaleEffect(popScale)
    }

    // MARK: - 수식형(type=equation)

    /// 각 줄을 "[2] + [3] = [13]" 처럼 — 숫자는 박스, 연산자는 사이 텍스트, 빈칸은 강조 박스.
    private func equationBody(_ grid: [[String?]]) -> some View {
        let cols = max(1, grid.map(\.count).max() ?? 1)
        // 칸 수가 많아도 가로로 넘치지 않게 폰트/박스를 단계적으로 축소.
        let fontSize: CGFloat = cols >= 9 ? 14 : (cols >= 8 ? 16 : (cols >= 7 ? 18 : (cols >= 6 ? 21 : (cols >= 5 ? 24 : 28))))
        let boxSide: CGFloat = fontSize * 1.8
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
                        .lineLimit(2)
                        .minimumScaleFactor(0.6)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 8)
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

// MARK: - Figure choices (시각형 보기)

/// 도형 보기 4개. 탭하면 그 도형의 code 를 제출한다.
struct FigureChoicesView: View {
    let choices: [Figure]
    var disabled: Bool = false
    let onPick: (String) -> Void

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 12), count: 2)

    var body: some View {
        LazyVGrid(columns: columns, spacing: 12) {
            ForEach(Array(choices.enumerated()), id: \.offset) { _, fig in
                Button { onPick(fig.code ?? "") } label: {
                    FigureGlyph(figure: fig, size: 46)
                        .frame(maxWidth: .infinity)
                        .frame(height: 72)
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
