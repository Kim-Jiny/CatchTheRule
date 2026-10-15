import SwiftUI

struct TimeAttackView: View {
    @Environment(ProgressStore.self) private var progress
    @Environment(\.dismiss) private var dismiss

    private static let duration = 60
    private static let skipPenalty = 3

    @State private var deck = TimeAttackDeck(pools: [:])
    @State private var puzzle: Puzzle?
    @State private var typed = ""
    @State private var score = 0
    @State private var timeLeft = duration
    @State private var feedback: AnswerFeedback?
    @State private var advancing = false        // 카드 전환 중 재제출(중복 채점) 방지
    @State private var shake: CGFloat = 0
    @State private var finished = false
    @State private var expr: TargetExpression?   // 숫자 만들기(문제 전환 시 재생성)
    @State private var incompleteShown = false
    @State private var match: MatchBoard?        // 성냥개비(문제 전환 시 재생성)
    @State private var matchMessage: String?

    private let ticker = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

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
            if let puzzle {
                modeChip(for: puzzle)
                    .padding(.top, 10)
                GeometryReader { geo in
                    ScrollView {
                        problemArea(for: puzzle)
                            .padding(.horizontal, 20)
                            .padding(.vertical, 12)
                            .frame(maxWidth: .infinity, minHeight: geo.size.height, alignment: .center)
                    }
                    .scrollIndicators(.hidden)
                }
                .id(puzzle.id)
                skipButton
                    .padding(.horizontal, 20)
                    .padding(.bottom, 8)
                inputArea(for: puzzle)
                    .padding(.horizontal, 20)
                    .padding(.bottom, 12)
            } else {
                Spacer()
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

    private func modeChip(for puzzle: Puzzle) -> some View {
        let color = Theme.modeColor(puzzle.trackKey)
        return HStack(spacing: 6) {
            Image(systemName: modeIcon(puzzle.trackKey))
            Text(modeTitle(puzzle.trackKey))
        }
        .font(.system(size: 12, weight: .bold))
        .foregroundStyle(color)
        .padding(.horizontal, 12).padding(.vertical, 6)
        .background(Capsule().fill(color.opacity(0.15)))
        .overlay(Capsule().strokeBorder(color.opacity(0.4), lineWidth: 1))
    }

    private var skipButton: some View {
        Button { skip() } label: {
            HStack(spacing: 6) {
                Image(systemName: "forward.fill")
                Text(String.loc("ta_skip"))
            }
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(advancing ? Theme.textTertiary : Theme.textSecondary)
            .padding(.horizontal, 16).padding(.vertical, 8)
            .background(Capsule().fill(Theme.card))
        }
        .buttonStyle(.plain)
        .disabled(advancing)
    }

    // MARK: - Problem

    @ViewBuilder
    private func problemArea(for puzzle: Puzzle) -> some View {
        VStack(spacing: 28) {
            if puzzle.isFragment {
                VStack(spacing: 6) {
                    promptText("fragment_prompt", bold: true)
                    if puzzle.combine == "xor" {
                        Text(String.loc("fragment_rule_xor"))
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(Theme.textTertiary)
                            .multilineTextAlignment(.center)
                    }
                }
            } else if puzzle.isMatch {
                promptText("match_prompt", bold: true)
            } else if puzzle.isTarget {
                promptText("target_prompt", bold: true)
            } else if puzzle.isContradiction {
                promptText("contradiction_prompt", bold: true)
            } else if !puzzle.isPrompt {
                promptText("play_prompt", bold: false)
            }

            if puzzle.isMatch, match != nil {
                matchSection
            } else if puzzle.isTarget, let expr {
                TargetBoardView(expr: expr, incompleteShown: incompleteShown) { i in
                    targetEdit { $0.addTile(i) }
                }
                .modifier(Shake(animatableData: shake))
            } else {
                SequenceDisplay(puzzle: puzzle, typed: typed, feedback: feedback) { picked in
                    guard !advancing else { return }
                    typed = "\(picked)"
                    submit(puzzle: puzzle, value: "\(picked)")
                }
                .modifier(Shake(animatableData: shake))
            }
        }
    }

    private func promptText(_ key: String, bold: Bool) -> some View {
        Text(String.loc(key))
            .font(.system(size: bold ? 15 : 14, weight: bold ? .semibold : .regular))
            .foregroundStyle(Theme.textSecondary)
            .multilineTextAlignment(.center)
    }

    // MARK: - Input

    @ViewBuilder
    private func inputArea(for puzzle: Puzzle) -> some View {
        if puzzle.isMatch, let m = match {
            matchControls(m, puzzle: puzzle)
        } else if puzzle.isTarget, let e = expr {
            TargetControlsView(
                expr: e, disabled: advancing,
                onOp: { o in targetEdit { $0.addOp(o) } },
                onOpen: { targetEdit { $0.addOpen() } },
                onClose: { targetEdit { $0.addClose() } },
                onDelete: { targetEdit { $0.deleteLast(); return true } },
                onReset: { targetEdit { $0.reset(); return true } },
                onCheck: { checkTarget(puzzle: puzzle) }
            )
        } else if puzzle.isContradiction {
            EmptyView()   // 문장 카드를 직접 탭
        } else if puzzle.isFigureSequence {
            FigureChoicesView(choices: puzzle.figureChoices ?? [], disabled: advancing) { picked in
                typed = picked
                submit(puzzle: puzzle, value: picked)
            }
        } else {
            switch puzzle.inputType {
            case .keypad:
                KeypadView(
                    onDigit: { d in
                        guard !advancing, typed.count < 4 else { return }
                        typed.append("\(d)")
                        if progress.hapticsOn { Haptics.soft() }
                    },
                    onDelete: { if !typed.isEmpty { typed.removeLast() } },
                    onSubmit: { submit(puzzle: puzzle, value: typed) },
                    canSubmit: !typed.isEmpty && !advancing
                )
            case .choices:
                ChoicesView(choices: puzzle.choices ?? [], disabled: advancing) { picked in
                    typed = picked
                    submit(puzzle: puzzle, value: picked)
                }
            }
        }
    }

    // MARK: - 숫자 만들기

    private func targetEdit(_ edit: (inout TargetExpression) -> Bool) {
        guard !advancing, var e = expr else { return }
        let ok = edit(&e)
        if ok { expr = e }
        if progress.hapticsOn { ok ? Haptics.soft() : Haptics.tap() }
    }

    private func checkTarget(puzzle: Puzzle) {
        guard !advancing, let e = expr else { return }
        guard let v = e.value else {
            if progress.hapticsOn { Haptics.error() }
            withAnimation(.linear(duration: 0.4)) { shake += 1 }
            incompleteShown = true
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.4) { incompleteShown = false }
            return
        }
        submit(puzzle: puzzle, value: e.isCorrect ? String(e.target) : "x" + v.text)
    }

    // MARK: - 성냥개비

    private var matchSection: some View {
        VStack(spacing: 14) {
            if let m = match {
                Text(String.loc("match_moves", m.displacement, m.moves))
                    .font(.system(size: 15, weight: .bold, design: .rounded))
                    .foregroundStyle(m.displacement >= m.moves ? Theme.star : Theme.textPrimary)
                    .padding(.horizontal, 14).padding(.vertical, 6)
                    .background(Capsule().fill(Theme.card))
            }
            MatchBoardView(
                board: Binding(get: { match ?? MatchBoard(equation: [], moves: 0) }, set: { match = $0 }),
                disabled: advancing,
                onPick: { if progress.hapticsOn { Haptics.soft() } },
                onDrop: { result in
                    switch result {
                    case .placed: if progress.hapticsOn { Haptics.tap() }
                    case .returned: break
                    case .rejectedLimit: matchWarn("match_limit")
                    }
                }
            )
            .frame(height: 130)
            .modifier(Shake(animatableData: shake))
            Text(matchMessage ?? " ")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Theme.danger)
                .multilineTextAlignment(.center)
                .frame(minHeight: 18)
        }
    }

    private func matchWarn(_ key: String) {
        if progress.hapticsOn { Haptics.error() }
        withAnimation(.linear(duration: 0.4)) { shake += 1 }
        matchMessage = String.loc(key)
        let shown = matchMessage
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.8) {
            if matchMessage == shown { matchMessage = nil }
        }
    }

    private func matchControls(_ m: MatchBoard, puzzle: Puzzle) -> some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                matchKey("arrow.uturn.backward", String.loc("match_undo"), on: m.canUndo && !advancing) { match?.undo() }
                matchKey("arrow.counterclockwise", String.loc("match_reset"), on: m.canReset && !advancing) { match?.reset(); matchMessage = nil }
            }
            Button { checkMatch(puzzle: puzzle) } label: {
                Text(String.loc("match_check"))
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity, minHeight: 50)
                    .background(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .fill(m.canCheck && !advancing ? AnyShapeStyle(Theme.accentGradient) : AnyShapeStyle(Theme.accent.opacity(0.3)))
                    )
            }
            .buttonStyle(.plain)
            .disabled(!m.canCheck || advancing)
            .opacity(m.canCheck && !advancing ? 1 : 0.6)
        }
    }

    private func matchKey(_ icon: String, _ title: String, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: icon)
                Text(title).lineLimit(1).minimumScaleFactor(0.7)
            }
            .font(.system(size: 14, weight: .semibold))
            .foregroundStyle(on ? Theme.textSecondary : Theme.textTertiary)
            .frame(maxWidth: .infinity, minHeight: 40)
            .card(cornerRadius: 12)
        }
        .buttonStyle(.plain)
        .disabled(!on)
    }

    private func checkMatch(puzzle: Puzzle) {
        guard !advancing, let m = match, m.canCheck else { return }
        guard let text = m.text, let truth = MatchBoard.evaluate(text) else {
            matchWarn("match_invalid")
            return
        }
        submit(puzzle: puzzle, value: truth ? puzzle.answer : "x" + text)
    }

    // MARK: - Logic

    private func setup() {
        deck = TimeAttackDeck(unlocked: progress.isShapesUnlocked)
        score = 0
        timeLeft = Self.duration
        finished = false
        advancing = false
        load(deck.nextPuzzle())
    }

    /// 새 문제로 교체하며 모든 입력/보드 상태를 초기화.
    private func load(_ next: Puzzle?) {
        puzzle = next
        typed = ""
        feedback = nil
        incompleteShown = false
        matchMessage = nil
        expr = nil
        match = nil
        if let p = next {
            if p.isTarget, let nums = p.numbers, let t = p.target {
                expr = TargetExpression(numbers: nums, target: t)
            }
            if p.isMatch, let eq = p.equation {
                match = MatchBoard(equation: eq, moves: p.moves ?? 1)
            }
        }
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

    private func skip() {
        guard !finished, !advancing else { return }
        if progress.hapticsOn { Haptics.tap() }
        timeLeft = max(0, timeLeft - Self.skipPenalty)
        if timeLeft <= 0 {
            finish()
            return
        }
        load(deck.nextPuzzle())
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
            guard !finished else { return }
            load(deck.nextPuzzle())
            advancing = false
        }
    }

    private func finish() {
        finished = true
        progress.recordTimeAttack(score: score)
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
                Text(String.loc("ta_end"))
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Theme.textSecondary)
                Text(String.loc("ta_score", score))
                    .font(.system(size: 44, weight: .bold, design: .rounded))
                    .foregroundStyle(Theme.textPrimary)
            }

            if let myRank {
                Text(String.loc("ta_rank_registered", myRank))
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Theme.accent2)
            } else {
                VStack(spacing: 12) {
                    TextField(String.loc("nickname_placeholder"), text: $nickname)
                        .textInputAutocapitalization(.never)
                        .disableAutocorrection(true)
                        .multilineTextAlignment(.center)
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(Theme.textPrimary)
                        .padding(.vertical, 14)
                        .frame(maxWidth: .infinity)
                        .card(cornerRadius: 14)
                    PrimaryButton(String.loc(submitting ? "registering" : "register_ranking"),
                                  systemImage: "trophy.fill",
                                  enabled: canSubmit) {
                        submit()
                    }
                }
                .padding(.horizontal, 32)
            }

            Spacer()
            SecondaryButton(title: String.loc("close")) { onClose() }
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
