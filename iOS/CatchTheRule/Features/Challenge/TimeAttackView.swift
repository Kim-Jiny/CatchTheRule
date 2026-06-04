import SwiftUI

struct TimeAttackView: View {
    @Environment(ProgressStore.self) private var progress
    @Environment(\.dismiss) private var dismiss

    private static let duration = 60

    @State private var deck: [Puzzle] = []
    @State private var deckIndex = 0
    @State private var typed = ""
    @State private var score = 0
    @State private var timeLeft = duration
    @State private var feedback: AnswerFeedback?
    @State private var advancing = false        // 카드 전환 중 재제출(중복 채점) 방지
    @State private var shake: CGFloat = 0
    @State private var finished = false

    private let ticker = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    private var puzzle: Puzzle? {
        deck.indices.contains(deckIndex) ? deck[deckIndex] : nil
    }

    var body: some View {
        ZStack {
            ScreenBackground()
            if finished {
                TimeAttackResultView(score: score, onClose: { dismiss() })
            } else {
                playing
            }
        }
        .toolbar(.hidden, for: .navigationBar)
        .onAppear(perform: setup)
        .onReceive(ticker) { _ in tick() }
    }

    private var playing: some View {
        VStack(spacing: 0) {
            topBar
            Spacer(minLength: 12)
            VStack(spacing: 28) {
                if let puzzle {
                    Text("규칙을 찾아 빈칸을 채우세요")
                        .font(.system(size: 14))
                        .foregroundStyle(Theme.textSecondary)
                    SequenceDisplay(puzzle: puzzle, typed: typed, feedback: feedback)
                        .modifier(Shake(animatableData: shake))
                }
            }
            .padding(.horizontal, 20)
            Spacer(minLength: 12)
            if let puzzle {
                inputArea(for: puzzle)
                    .padding(.horizontal, 20)
                    .padding(.bottom, 12)
            }
        }
        .padding(.top, 8)
    }

    private var topBar: some View {
        HStack {
            Button { dismiss() } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Theme.textSecondary)
                    .frame(width: 38, height: 38)
                    .card(cornerRadius: 12)
            }
            .buttonStyle(.plain)
            Spacer()
            HStack(spacing: 6) {
                Image(systemName: "timer")
                Text(String(format: "%02d:%02d", timeLeft / 60, timeLeft % 60))
                    .monospacedDigit()
            }
            .font(.system(size: 17, weight: .bold, design: .rounded))
            .foregroundStyle(timeLeft <= 10 ? Theme.danger : Theme.textPrimary)
            Spacer()
            HStack(spacing: 6) {
                Image(systemName: "star.fill").foregroundStyle(Theme.star)
                Text("\(score)")
                    .font(.system(size: 17, weight: .bold, design: .rounded))
                    .foregroundStyle(Theme.textPrimary)
            }
            .frame(minWidth: 38)
        }
        .padding(.horizontal, 20)
    }

    @ViewBuilder
    private func inputArea(for puzzle: Puzzle) -> some View {
        switch puzzle.inputType {
        case .keypad:
            KeypadView(
                onDigit: { d in
                    guard typed.count < 4 else { return }
                    typed.append("\(d)")
                    if progress.hapticsOn { Haptics.soft() }
                },
                onDelete: { if !typed.isEmpty { typed.removeLast() } },
                onSubmit: { submit(puzzle: puzzle, value: typed) },
                canSubmit: !typed.isEmpty && !advancing
            )
        case .choices:
            ChoicesView(choices: puzzle.choices ?? [], disabled: advancing) { picked in
                submit(puzzle: puzzle, value: picked)
            }
        }
    }

    // MARK: - Logic

    private func setup() {
        deck = PuzzleStore.shared.puzzles.shuffled()
        deckIndex = 0
        score = 0
        timeLeft = Self.duration
        finished = false
    }

    private func tick() {
        guard !finished else { return }
        if timeLeft > 0 {
            timeLeft -= 1
        }
        if timeLeft <= 0 {
            finish()
        }
    }

    private func submit(puzzle: Puzzle, value: String?) {
        guard let value, !value.isEmpty, !finished, !advancing else { return }
        if puzzle.isCorrect(value) {
            advancing = true
            score += 1
            feedback = .correct
            if progress.hapticsOn { Haptics.success() }
            nextCard()
        } else {
            feedback = .wrong
            if progress.hapticsOn { Haptics.error() }
            withAnimation(.linear(duration: 0.4)) { shake += 1 }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
                typed = ""
                feedback = nil
            }
        }
    }

    private func nextCard() {
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
            typed = ""
            feedback = nil
            if deckIndex + 1 >= deck.count {
                deck.shuffle()
                deckIndex = 0
            } else {
                deckIndex += 1
            }
            advancing = false
        }
    }

    private func finish() {
        finished = true
        if score > progress.bestTimeAttack {
            progress.bestTimeAttack = score
        }
    }
}

// MARK: - Result + submit

struct TimeAttackResultView: View {
    @Environment(ProgressStore.self) private var progress
    let score: Int
    let onClose: () -> Void

    @State private var nickname = ""
    @State private var submitting = false
    @State private var myRank: Int?

    var body: some View {
        VStack(spacing: 22) {
            Spacer()
            Image(systemName: "flag.checkered")
                .font(.system(size: 52))
                .foregroundStyle(Theme.accentGradient)
            VStack(spacing: 6) {
                Text("타임어택 종료")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Theme.textSecondary)
                Text("\(score)문제")
                    .font(.system(size: 44, weight: .bold, design: .rounded))
                    .foregroundStyle(Theme.textPrimary)
            }

            if let myRank {
                Text("현재 \(myRank)위에 등록됐어요!")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Theme.accent2)
            } else {
                VStack(spacing: 12) {
                    TextField("닉네임 입력", text: $nickname)
                        .textInputAutocapitalization(.never)
                        .disableAutocorrection(true)
                        .multilineTextAlignment(.center)
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(Theme.textPrimary)
                        .padding(.vertical, 14)
                        .frame(maxWidth: .infinity)
                        .card(cornerRadius: 14)
                    PrimaryButton(submitting ? "등록 중..." : "랭킹 등록",
                                  systemImage: "trophy.fill",
                                  enabled: canSubmit) {
                        submit()
                    }
                }
                .padding(.horizontal, 32)
            }

            Spacer()
            SecondaryButton(title: "닫기") { onClose() }
                .padding(.horizontal, 32)
                .padding(.bottom, 20)
        }
        .onAppear {
            if nickname.isEmpty { nickname = progress.nickname }
        }
    }

    private var trimmed: String { nickname.trimmingCharacters(in: .whitespaces) }
    private var canSubmit: Bool { !trimmed.isEmpty && !submitting }

    private func submit() {
        submitting = true
        progress.nickname = trimmed
        Task {
            let rank = try? await Ranking.service.submit(score: score,
                                                         nickname: trimmed,
                                                         mode: .timeAttack)
            myRank = rank
            submitting = false
        }
    }
}
