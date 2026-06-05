import SwiftUI

/// 캠페인 플레이: 시작 인덱스부터 순차로 퍼즐을 풀어나간다.
struct CampaignSessionView: View {
    @Environment(ProgressStore.self) private var progress
    @Environment(\.dismiss) private var dismiss

    private let puzzles = PuzzleStore.shared.puzzles

    @State private var index: Int
    @State private var typed = ""
    @State private var hintsShown = 0
    @State private var feedback: AnswerFeedback?
    @State private var reveal = false
    @State private var shake: CGFloat = 0
    @State private var solved = false       // 현재 퍼즐 정답 처리 완료

    init(startIndex: Int) {
        _index = State(initialValue: max(0, startIndex))
    }

    private var puzzle: Puzzle? {
        puzzles.indices.contains(index) ? puzzles[index] : nil
    }

    var body: some View {
        ZStack {
            ScreenBackground()
            if let puzzle {
                content(for: puzzle)
            } else {
                CampaignCompleteView { dismiss() }
            }
        }
        .overlay {
            if feedback == .correct {
                CorrectBadge()
                    .offset(y: -150)
                    .transition(.scale(scale: 0.5).combined(with: .opacity))
            }
        }
        .animation(.spring(response: 0.35, dampingFraction: 0.55), value: feedback)
        .toolbar(.hidden, for: .navigationBar)
    }

    // MARK: - Content

    private func content(for puzzle: Puzzle) -> some View {
        VStack(spacing: 0) {
            header(for: puzzle)

            Spacer(minLength: 12)

            VStack(spacing: 28) {
                Text(String.loc("play_prompt"))
                    .font(.system(size: 15))
                    .foregroundStyle(Theme.textSecondary)

                SequenceDisplay(puzzle: puzzle, typed: typed, reveal: reveal, feedback: feedback)
                    .modifier(Shake(animatableData: shake))

                if !shownHints.isEmpty {
                    hintBox
                }
            }
            .padding(.horizontal, 20)

            Spacer(minLength: 12)

            inputArea(for: puzzle)
                .padding(.horizontal, 20)
                .padding(.bottom, 12)
        }
        .padding(.top, 8)
    }

    private func header(for puzzle: Puzzle) -> some View {
        let pos = PuzzleStore.shared.position(of: index)
        return HStack {
            Button { dismiss() } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Theme.textSecondary)
                    .frame(width: 38, height: 38)
                    .card(cornerRadius: 12)
            }
            .buttonStyle(.plain)

            Spacer()

            VStack(spacing: 2) {
                Text(String.loc("chapter_label", pos?.chapter ?? puzzle.chapter))
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Theme.textTertiary)
                Text(String.loc("stage_label", pos?.stage ?? puzzle.order))
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(Theme.textPrimary)
            }

            Spacer()

            hintButton(for: puzzle)
        }
        .padding(.horizontal, 20)
    }

    // MARK: - Hints

    private var shownHints: [String] {
        guard let puzzle else { return [] }
        return Array(puzzle.localizedHints.prefix(hintsShown))
    }

    private var hintBox: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(shownHints.enumerated()), id: \.offset) { i, hint in
                HStack(alignment: .top, spacing: 8) {
                    Image(systemName: "lightbulb.fill")
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.star)
                        .padding(.top, 2)
                    Text(hint)
                        .font(.system(size: 14))
                        .foregroundStyle(Theme.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity)
        .card()
    }

    private func hintButton(for puzzle: Puzzle) -> some View {
        let canHint = hintsShown < puzzle.localizedHints.count && progress.hintsRemaining > 0 && !solved
        return Button {
            useHint(for: puzzle)
        } label: {
            HStack(spacing: 4) {
                Image(systemName: "lightbulb")
                Text("\(progress.hintsRemaining)")
            }
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(canHint ? Theme.star : Theme.textTertiary)
            .frame(height: 38)
            .padding(.horizontal, 12)
            .card(cornerRadius: 12)
        }
        .buttonStyle(.plain)
        .disabled(!canHint)
    }

    private func useHint(for puzzle: Puzzle) {
        guard hintsShown < puzzle.localizedHints.count, progress.spendHint() else { return }
        if progress.hapticsOn { Haptics.tap() }
        withAnimation(.spring(duration: 0.3)) { hintsShown += 1 }
    }

    // MARK: - Input

    @ViewBuilder
    private func inputArea(for puzzle: Puzzle) -> some View {
        switch puzzle.inputType {
        case .keypad:
            KeypadView(
                onDigit: { d in
                    guard !solved, typed.count < 4 else { return }
                    typed.append("\(d)")
                    if progress.hapticsOn { Haptics.soft() }
                },
                onDelete: {
                    guard !solved, !typed.isEmpty else { return }
                    typed.removeLast()
                },
                onSubmit: { submit(puzzle: puzzle, value: typed) },
                canSubmit: !typed.isEmpty && !solved
            )
        case .choices:
            ChoicesView(choices: puzzle.choices ?? [], disabled: solved) { picked in
                typed = picked
                submit(puzzle: puzzle, value: picked)
            }
        }
    }

    // MARK: - Submit

    private func submit(puzzle: Puzzle, value: String?) {
        guard let value, !value.isEmpty, !solved else { return }
        if puzzle.isCorrect(value) {
            solved = true
            feedback = .correct
            reveal = true
            let earned = max(1, 3 - hintsShown)
            progress.recordCampaignClear(puzzle: puzzle, atGlobalIndex: index, earnedStars: earned)
            if progress.soundOn { /* 효과음 자리 */ }
            if progress.hapticsOn { Haptics.success() }
            Task {
                try? await Task.sleep(nanoseconds: 900_000_000)
                advance()
            }
        } else {
            feedback = .wrong
            if progress.hapticsOn { Haptics.error() }
            withAnimation(.linear(duration: 0.4)) { shake += 1 }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.45) {
                typed = ""
                feedback = nil
            }
        }
    }

    private func advance() {
        withAnimation(.easeInOut(duration: 0.25)) {
            index += 1
            typed = ""
            hintsShown = 0
            feedback = nil
            reveal = false
            solved = false
        }
    }
}

// MARK: - Completion

struct CampaignCompleteView: View {
    @Environment(ProgressStore.self) private var progress
    let onClose: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "checkmark.seal.fill")
                .font(.system(size: 64))
                .foregroundStyle(Theme.accentGradient)
            Text(String.loc("campaign_complete"))
                .font(.system(size: 22, weight: .bold))
                .foregroundStyle(Theme.textPrimary)
            Text(String.loc("stars_earned", progress.totalStars, progress.maxStars))
                .font(.system(size: 15))
                .foregroundStyle(Theme.textSecondary)
            PrimaryButton(String.loc("go_home"), systemImage: "house.fill", action: onClose)
                .padding(.horizontal, 40)
                .padding(.top, 12)
        }
        .padding(32)
    }
}
