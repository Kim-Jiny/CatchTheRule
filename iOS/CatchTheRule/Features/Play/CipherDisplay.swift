import SwiftUI

// MARK: - 암호 해독 디스플레이

/// 예시 행(`이모지들 → 숫자`)을 세로로 나열하고, 마지막 행에 query 와 입력값(or ?)을 강조해 보여 준다.
struct CipherDisplay: View {
    let puzzle: Puzzle
    var typed: String = ""
    var feedback: AnswerFeedback? = nil

    private var mode: Color { Theme.modeColor("cipher") }
    private var pairs: [[[String]]] { puzzle.pairs ?? [] }
    private var query: [String] { puzzle.query ?? [] }

    var body: some View {
        VStack(spacing: 10) {
            ForEach(Array(pairs.enumerated()), id: \.offset) { _, pair in
                exampleRow(input: pair.first ?? [], output: (pair.count > 1 ? pair[1] : []).joined())
            }
            queryRow
        }
        .padding(.vertical, 16).padding(.horizontal, 14)
        .frame(maxWidth: .infinity)
        .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(Color.black.opacity(0.35)))
        .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).stroke(mode.opacity(0.35), lineWidth: 1))
        .id(puzzle.id)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityText)
    }

    private func emojis(_ tokens: [String]) -> some View {
        HStack(spacing: 4) {
            ForEach(Array(tokens.enumerated()), id: \.offset) { _, t in
                Text(t).font(.system(size: 30))
            }
        }
        .lineLimit(1)
        .minimumScaleFactor(0.6)
    }

    private func arrow(_ color: Color) -> some View {
        Image(systemName: "arrow.right")
            .font(.system(size: 14, weight: .bold))
            .foregroundStyle(color)
            .frame(width: 22)
    }

    private func exampleRow(input: [String], output: String) -> some View {
        HStack(spacing: 8) {
            emojis(input).frame(maxWidth: .infinity, alignment: .trailing)
            arrow(Theme.textTertiary)
            Text(output)
                .font(.system(size: 24, weight: .semibold, design: .monospaced))
                .foregroundStyle(Theme.textSecondary)
                .lineLimit(1).minimumScaleFactor(0.6)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .frame(minHeight: 40)
    }

    private var tint: Color {
        feedback == .correct ? Theme.success : (feedback == .wrong ? Theme.danger : mode)
    }

    private var queryRow: some View {
        HStack(spacing: 8) {
            emojis(query).frame(maxWidth: .infinity, alignment: .trailing)
            arrow(tint)
            Text(typed.isEmpty ? "?" : typed)
                .font(.system(size: 30, weight: .heavy, design: .monospaced))
                .foregroundStyle(typed.isEmpty ? mode.opacity(0.7) : tint)
                .shadow(color: tint.opacity(typed.isEmpty ? 0 : 0.6), radius: 6)
                .lineLimit(1).minimumScaleFactor(0.6)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .frame(minHeight: 52)
        .padding(.horizontal, 8)
        .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(tint.opacity(0.10)))
        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(tint.opacity(0.85), lineWidth: 1.5))
        .shadow(color: tint.opacity(0.35), radius: 8)
        .padding(.top, 4)
    }

    private var accessibilityText: String {
        var lines = pairs.map { p in
            (p.first ?? []).joined(separator: " ") + " → " + (p.count > 1 ? p[1] : []).joined()
        }
        lines.append(query.joined(separator: " ") + " → " + (typed.isEmpty ? "?" : typed))
        return lines.joined(separator: ", ")
    }
}

#Preview("Cipher") {
    let p = Puzzle(
        id: "cp_prev", type: "cipher", track: "cipher", chapter: 5, order: 3,
        tokens: nil, grid: nil, figures: nil, figureTokens: nil, figureChoices: nil, prompt: nil,
        statements: nil, numbers: nil, target: nil, equation: nil, moves: nil,
        frames: nil, combine: nil, frameMs: nil,
        pairs: [[["🍎", "🍌"], ["7", "3"]], [["🍌", "🍇"], ["3", "9"]], [["🍇", "🍎", "🍇", "🍌"], ["9", "7", "9", "3"]]],
        scales: nil, query: ["🍌", "🍎", "🍇", "🍎"],
        answer: "3797", inputType: .keypad, choices: nil, hints: [:], explanation: [:])
    return ZStack {
        Color.black.ignoresSafeArea()
        VStack(spacing: 20) {
            CipherDisplay(puzzle: p, typed: "37")
            CipherDisplay(puzzle: p, typed: "3797", feedback: .wrong)
        }.padding(20)
    }
}
