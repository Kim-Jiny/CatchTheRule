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

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 12) {
                ForEach(Array(puzzle.tokens.enumerated()), id: \.offset) { _, value in
                    cell(value: value, isBlank: value == nil)
                }
            }
            .padding(.horizontal, 4)
            .frame(maxWidth: .infinity)
        }
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
    private func cell(value: String?, isBlank: Bool) -> some View {
        Text(isBlank ? blankText() : (value ?? ""))
            .font(.system(size: 30, weight: .bold, design: .rounded))
            .foregroundStyle(isBlank ? Theme.textPrimary : Theme.textSecondary)
            .frame(minWidth: 64)
            .frame(height: 76)
            .padding(.horizontal, 6)
            .background(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(isBlank ? Color.white.opacity(0.03) : Theme.card)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .stroke(isBlank ? blankStroke : AnyShapeStyle(Theme.stroke),
                            lineWidth: isBlank ? 2 : 1)
            )
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
