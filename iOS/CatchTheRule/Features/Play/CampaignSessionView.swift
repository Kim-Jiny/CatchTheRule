import SwiftUI

/// 캠페인 플레이: 시작 인덱스부터 순차로 퍼즐을 풀어나간다.
struct CampaignSessionView: View {
    @Environment(ProgressStore.self) private var progress
    @Environment(StoreManager.self) private var store
    @Environment(AdsManager.self) private var ads
    @Environment(\.dismiss) private var dismiss

    private let track: String
    private let puzzles: [Puzzle]

    @State private var index: Int
    @State private var typed = ""
    @State private var hintsShown = 0
    @State private var feedback: AnswerFeedback?
    @State private var reveal = false
    @State private var shake: CGFloat = 0
    @State private var solved = false       // 현재 퍼즐 정답 처리 완료
    @State private var showHintShop = false // 힌트 0개일 때 광고/구매 팝업
    @State private var showCalc = false      // 플로팅 계산기
    @State private var advanceTask: Task<Void, Never>?  // 정답 후 지연 진행/광고 — 이탈 시 취소

    init(startIndex: Int, track: String = PuzzleStore.defaultTrack) {
        self.track = track
        self.puzzles = PuzzleStore.shared.puzzles(track: track)
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
                CampaignCompleteView(track: track) { dismiss() }
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
        .overlay {
            if showCalc {
                CalculatorPanel(isPresented: $showCalc)
                    .transition(.scale(scale: 0.85).combined(with: .opacity))
            }
        }
        .animation(.spring(response: 0.3, dampingFraction: 0.7), value: showCalc)
        .toolbar(.hidden, for: .navigationBar)
        .sheet(isPresented: $showHintShop) {
            HintShopSheet()
                .preferredColorScheme(.dark)
        }
        .onDisappear { advanceTask?.cancel() }   // 세션 이탈 시 대기 중인 진행/광고 취소
    }

    // MARK: - Content

    private func content(for puzzle: Puzzle) -> some View {
        VStack(spacing: 0) {
            header(for: puzzle)

            // 가운데 영역만 스크롤: 힌트가 늘어도 키패드·배너는 안 밀린다.
            // 내용이 짧으면 minHeight + center 로 기존처럼 가운데 정렬.
            GeometryReader { geo in
                ScrollView {
                    VStack(spacing: 28) {
                        if puzzle.isContradiction {
                            Text(String.loc("contradiction_prompt"))
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundStyle(Theme.textSecondary)
                                .multilineTextAlignment(.center)
                        } else if !puzzle.isPrompt {
                            Text(String.loc("play_prompt"))
                                .font(.system(size: 15))
                                .foregroundStyle(Theme.textSecondary)
                        }

                        SequenceDisplay(puzzle: puzzle, typed: typed, reveal: reveal, feedback: feedback) { picked in
                            guard !solved else { return }
                            typed = "\(picked)"
                            submit(puzzle: puzzle, value: "\(picked)")
                        }
                        .modifier(Shake(animatableData: shake))

                        if !shownHints.isEmpty {
                            hintBox
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 12)
                    .frame(maxWidth: .infinity, minHeight: geo.size.height, alignment: .center)
                }
                .scrollIndicators(.hidden)
            }

            inputArea(for: puzzle)
                .padding(.horizontal, 20)
                .padding(.bottom, 12)

            BannerAd(unitID: BannerUnits.stage, horizontalInset: 0)
        }
        .padding(.top, 8)
    }

    private func header(for puzzle: Puzzle) -> some View {
        let pos = PuzzleStore.shared.position(of: index, track: track)
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

            HStack(spacing: 8) {
                calcButton
                hintButton(for: puzzle)
            }
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

    private var calcButton: some View {
        Button { showCalc.toggle() } label: {
            Image(systemName: "function")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(showCalc ? Theme.accent2 : Theme.textSecondary)
                .frame(width: 38, height: 38)
                .card(cornerRadius: 12)
        }
        .buttonStyle(.plain)
    }

    private func hintButton(for puzzle: Puzzle) -> some View {
        // 이 퍼즐에 더 보여줄 힌트가 있고 아직 못 풀었으면 활성. 잔여 0이면 탭 시 구매 팝업.
        let hasMore = hintsShown < puzzle.localizedHints.count && !solved
        return Button {
            if progress.hintsRemaining > 0 {
                useHint(for: puzzle)
            } else {
                showHintShop = true
            }
        } label: {
            HStack(spacing: 4) {
                Image(systemName: "lightbulb")
                Text("\(progress.hintsRemaining)")
            }
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(hasMore ? Theme.star : Theme.textTertiary)
            .frame(height: 38)
            .padding(.horizontal, 12)
            .card(cornerRadius: 12)
        }
        .buttonStyle(.plain)
        .disabled(!hasMore)
    }

    private func useHint(for puzzle: Puzzle) {
        guard hintsShown < puzzle.localizedHints.count, progress.spendHint() else { return }
        if progress.hapticsOn { Haptics.tap() }
        withAnimation(.spring(duration: 0.3)) { hintsShown += 1 }
    }

    // MARK: - Input

    @ViewBuilder
    private func inputArea(for puzzle: Puzzle) -> some View {
        if puzzle.isContradiction {
            // 모순찾기는 문장 카드를 직접 탭 — 하단 입력 영역 없음.
            EmptyView()
        } else if puzzle.isFigureSequence {
            // 시각형(도형 시퀀스)은 도형 보기로 고른다.
            FigureChoicesView(choices: puzzle.figureChoices ?? [], disabled: solved) { picked in
                typed = picked
                submit(puzzle: puzzle, value: picked)
            }
        } else {
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
    }

    // MARK: - Submit

    private func submit(puzzle: Puzzle, value: String?) {
        guard let value, !value.isEmpty, !solved else { return }
        if puzzle.isCorrect(value) {
            solved = true
            feedback = .correct
            reveal = true
            let earned = max(1, 3 - hintsShown)
            progress.recordCampaignClear(puzzle: puzzle, atIndex: index, earnedStars: earned)
            if progress.soundOn { /* 효과음 자리 */ }
            if progress.hapticsOn { Haptics.success() }
            advanceTask = Task {
                try? await Task.sleep(nanoseconds: 900_000_000)
                if Task.isCancelled { return }   // 세션을 떠났으면 광고·진행 모두 취소
                // 스테이지 클리어 전면광고(챕터 2+ / 챕터별 확률 / 1분 쿨다운). 광고제거 구매 시 제외.
                if !store.removeAdsPurchased {
                    ads.maybeShowInterstitial(chapter: puzzle.chapter)
                }
                advance()
            }
        } else {
            feedback = .wrong
            if progress.hapticsOn { Haptics.error() }
            withAnimation(.linear(duration: 0.4)) { shake += 1 }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.45) {
                typed = ""
                feedback = nil
                // 오답 시 50% 확률 전면광고(광고제거 구매 시 제외).
                if !store.removeAdsPurchased {
                    ads.maybeShowInterstitialOnWrong()
                }
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

// MARK: - 힌트 상점(부족 시)

/// 힌트가 0개일 때 뜨는 "광고 볼래? / 구매할래?" 팝업.
/// 광고는 아직 미연동이라 "준비중"(비활성), 구매는 즉시 동작.
struct HintShopSheet: View {
    @Environment(StoreManager.self) private var store
    @Environment(ProgressStore.self) private var progress
    @Environment(AdsManager.self) private var ads
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack {
            ScreenBackground()
            ScrollView {
                VStack(spacing: 14) {
                    Image(systemName: "lightbulb.slash.fill")
                        .font(.system(size: 44))
                        .foregroundStyle(Theme.star)
                        .padding(.top, 12)
                    Text(String.loc("iap_need_hints_title"))
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(Theme.textPrimary)
                    Text(String.loc("iap_need_hints_msg"))
                        .font(.system(size: 14))
                        .foregroundStyle(Theme.textSecondary)
                        .multilineTextAlignment(.center)
                        .padding(.bottom, 4)

                    // 광고 보고 힌트 받기 (+1) — 준비됐을 때만 활성. 보상 시 +1 후 닫기.
                    Button {
                        ads.showRewarded {
                            progress.hintsRemaining += 1
                            dismiss()
                        }
                    } label: {
                        HStack(spacing: 10) {
                            Image(systemName: "play.circle.fill").foregroundStyle(Theme.accent)
                            Text(String.loc("iap_watch_ad")).foregroundStyle(Theme.textPrimary)
                            Spacer()
                            Text(ads.isReady ? "+1" : String.loc("iap_loading"))
                                .foregroundStyle(ads.isReady ? Theme.accent : Theme.textTertiary)
                        }
                        .font(.system(size: 16, weight: .semibold))
                        .padding(16)
                        .frame(maxWidth: .infinity)
                        .card()
                    }
                    .buttonStyle(.plain)
                    .disabled(!ads.isReady)

                    // 힌트 구매 (4 티어)
                    ForEach(StoreManager.hintTiers, id: \.self) { n in
                        Button {
                            Task {
                                if await store.purchaseHints(n) { dismiss() }  // 지급은 콜백(durable)
                            }
                        } label: {
                            HStack(spacing: 10) {
                                Image(systemName: "lightbulb.fill").foregroundStyle(Theme.star)
                                Text(String.loc("iap_hints_n", n)).foregroundStyle(Theme.textPrimary)
                                Spacer()
                                Text(store.hintsPrice(n).isEmpty ? String.loc("iap_loading") : store.hintsPrice(n))
                                    .foregroundStyle(Theme.accent2)
                            }
                            .font(.system(size: 16, weight: .semibold))
                            .padding(16)
                            .frame(maxWidth: .infinity)
                            .card()
                        }
                        .buttonStyle(.plain)
                    }

                    // 결제 전 환불·이용약관 고지
                    Link(String.loc("iap_refund_policy"),
                         destination: URL(string: "https://duo.jiny.shop/ctr/terms?lang=\(Locale.current.language.languageCode?.identifier ?? "en")")!)
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.accent2)
                        .padding(.top, 4)

                    Button(String.loc("close")) { dismiss() }
                        .font(.system(size: 15))
                        .foregroundStyle(Theme.textSecondary)
                        .padding(.top, 6)
                }
                .padding(24)
            }
        }
        .presentationDetents([.fraction(0.66), .large])
    }
}

// MARK: - Completion

struct CampaignCompleteView: View {
    @Environment(ProgressStore.self) private var progress
    var track: String = PuzzleStore.defaultTrack
    let onClose: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "checkmark.seal.fill")
                .font(.system(size: 64))
                .foregroundStyle(Theme.accentGradient)
            Text(String.loc("campaign_complete"))
                .font(.system(size: 22, weight: .bold))
                .foregroundStyle(Theme.textPrimary)
            Text(String.loc("stars_earned", progress.earnedStars(track: track), progress.maxStars(track: track)))
                .font(.system(size: 15))
                .foregroundStyle(Theme.textSecondary)
            Text(String.loc("home_wait_update"))
                .font(.system(size: 14))
                .foregroundStyle(Theme.textTertiary)
                .multilineTextAlignment(.center)
                .padding(.top, 4)
            PrimaryButton(String.loc("go_home"), systemImage: "house.fill", action: onClose)
                .padding(.horizontal, 40)
                .padding(.top, 12)
        }
        .padding(32)
    }
}
