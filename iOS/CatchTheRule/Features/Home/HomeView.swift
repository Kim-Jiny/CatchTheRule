import SwiftUI

/// 홈. 도형규칙 해금 전엔 숫자규칙 단일 캠페인(튜토리얼), 해금 후엔 모드 선택 허브.
struct HomeView: View {
    @Environment(ProgressStore.self) private var progress
    @State private var showUnlock = false

    var body: some View {
        NavigationStack {
            ZStack {
                ScreenBackground()
                ScrollView {
                    VStack(spacing: 24) {
                        title
                        if progress.isShapesUnlocked {
                            hub
                        } else {
                            // 잠금: 숫자규칙 캠페인을 홈에 직접 표시(튜토리얼)
                            BannerAd(unitID: BannerUnits.home)
                            CampaignTrackView(track: "numbers")
                        }
                    }
                    .padding(20)
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: String.self) { track in
                ModeDetailView(track: track)
            }
        }
        .sheet(isPresented: $showUnlock) {
            UnlockSheet { showUnlock = false }
                .preferredColorScheme(.dark)
        }
        .task { maybeShowUnlock() }
        .onChange(of: progress.isShapesUnlocked) { _, _ in maybeShowUnlock() }
    }

    /// 해금됐고 아직 알림을 안 봤으면 1회 표시.
    private func maybeShowUnlock() {
        guard progress.isShapesUnlocked, !progress.shapesUnlockSeen else { return }
        progress.shapesUnlockSeen = true
        showUnlock = true
    }

    private var title: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(String.loc("app_name"))
                .font(.system(size: 30, weight: .bold, design: .rounded))
                .foregroundStyle(Theme.textPrimary)
            Text(String.loc("home_subtitle"))
                .font(.system(size: 15))
                .foregroundStyle(Theme.textSecondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 8)
    }

    // MARK: - 허브(해금 후)

    private var hub: some View {
        VStack(alignment: .leading, spacing: 14) {
            SectionHeader(title: String.loc("select_mode"))
            BannerAd(unitID: BannerUnits.home)
            ForEach(PuzzleStore.shared.tracks, id: \.self) { track in
                NavigationLink(value: track) {
                    ModeCard(track: track)
                }
                .buttonStyle(.plain)
            }
        }
    }
}

// MARK: - 모드 카드(허브)

private struct ModeCard: View {
    @Environment(ProgressStore.self) private var progress
    let track: String

    var body: some View {
        let fraction = progress.progressFraction(track: track)
        let finished = progress.isCampaignFinished(track: track)
        HStack(spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Theme.accentGradient)
                    .frame(width: 50, height: 50)
                Image(systemName: track == "shapes" ? "triangle.fill" : "number")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(.white)
            }
            VStack(alignment: .leading, spacing: 4) {
                Text(modeTitle(track))
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(Theme.textPrimary)
                if finished {
                    HStack(spacing: 4) {
                        Image(systemName: "checkmark.seal.fill").foregroundStyle(Theme.success)
                        Text(String.loc("mode_complete")).foregroundStyle(Theme.success)
                    }
                    .font(.system(size: 13, weight: .semibold))
                } else {
                    Text(String.loc("mode_progress", Int(fraction * 100)))
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.textTertiary)
                }
            }
            Spacer()
            ZStack {
                Circle().stroke(Theme.stroke, lineWidth: 5)
                Circle()
                    .trim(from: 0, to: fraction)
                    .stroke(Theme.accentGradient, style: StrokeStyle(lineWidth: 5, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                Text("\(Int(fraction * 100))%")
                    .font(.system(size: 12, weight: .bold, design: .rounded))
                    .foregroundStyle(Theme.textPrimary)
            }
            .frame(width: 52, height: 52)
            Image(systemName: "chevron.right")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Theme.textTertiary)
        }
        .padding(18)
        .card()
    }
}

private func modeTitle(_ track: String) -> String {
    track == "shapes" ? String.loc("mode_shapes") : String.loc("mode_numbers")
}

// MARK: - 모드 상세 페이지

struct ModeDetailView: View {
    @Environment(\.dismiss) private var dismiss
    let track: String

    var body: some View {
        ZStack {
            ScreenBackground()
            ScrollView {
                VStack(spacing: 16) {
                    CampaignTrackView(track: track)
                }
                .padding(20)
            }
        }
        .safeAreaInset(edge: .top) {
            HStack {
                Button { dismiss() } label: {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Theme.textSecondary)
                        .frame(width: 38, height: 38)
                        .card(cornerRadius: 12)
                }
                .buttonStyle(.plain)
                Spacer()
                Text(modeTitle(track))
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(Theme.textPrimary)
                Spacer()
                Color.clear.frame(width: 38, height: 38)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 8)
            .background(.ultraThinMaterial)
        }
        .navigationBarBackButtonHidden(true)
        .toolbar(.hidden, for: .navigationBar)
    }
}

// MARK: - 해금 알림

struct UnlockSheet: View {
    let onClose: () -> Void
    var body: some View {
        ZStack {
            ScreenBackground()
            VStack(spacing: 18) {
                Image(systemName: "lock.open.fill")
                    .font(.system(size: 54))
                    .foregroundStyle(Theme.accentGradient)
                Text(String.loc("unlock_title"))
                    .font(.system(size: 22, weight: .bold))
                    .foregroundStyle(Theme.textPrimary)
                    .multilineTextAlignment(.center)
                Text(String.loc("unlock_msg"))
                    .font(.system(size: 15))
                    .foregroundStyle(Theme.textSecondary)
                    .multilineTextAlignment(.center)
                PrimaryButton(String.loc("unlock_ok"), systemImage: "sparkles", action: onClose)
                    .padding(.top, 8)
            }
            .padding(28)
        }
        .presentationDetents([.fraction(0.5)])
    }
}

// MARK: - 트랙 캠페인 뷰 (이어하기 + 챕터 목록 + 플레이)

/// 한 트랙의 이어하기 카드 + 챕터 목록. 잠금 홈(numbers)·모드 상세 공용.
struct CampaignTrackView: View {
    @Environment(ProgressStore.self) private var progress
    let track: String

    @State private var playing = false
    @State private var startIndex = 0
    @State private var expanded: Set<Int> = []

    private func position() -> (chapter: Int, stage: Int)? {
        let total = PuzzleStore.shared.totalCount(track: track)
        guard total > 0 else { return nil }
        return PuzzleStore.shared.position(of: min(progress.currentIndex(track: track), total - 1), track: track)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            continueCard
            chapterList
        }
        .fullScreenCover(isPresented: $playing) {
            CampaignSessionView(startIndex: startIndex, track: track)
        }
    }

    private var continueCard: some View {
        let finished = progress.isCampaignFinished(track: track)
        let pos = position()
        let earned = progress.earnedStars(track: track)
        let maxStars = progress.maxStars(track: track)
        return VStack(alignment: .leading, spacing: 18) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(String.loc(finished ? "home_all_clear" : "home_current"))
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Theme.textSecondary)
                    if let p = pos, !finished {
                        Text(String.loc("chapter_label", p.chapter) + " · " + String.loc("stage_label", p.stage))
                            .font(.system(size: 22, weight: .bold, design: .rounded))
                            .foregroundStyle(Theme.textPrimary)
                    } else {
                        Text(String.loc("home_congrats"))
                            .font(.system(size: 22, weight: .bold, design: .rounded))
                            .foregroundStyle(Theme.textPrimary)
                    }
                }
                Spacer()
                ZStack {
                    Circle().stroke(Theme.stroke, lineWidth: 6)
                    Circle()
                        .trim(from: 0, to: progress.progressFraction(track: track))
                        .stroke(Theme.accentGradient, style: StrokeStyle(lineWidth: 6, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                    Text("\(Int(progress.progressFraction(track: track) * 100))%")
                        .font(.system(size: 13, weight: .bold, design: .rounded))
                        .foregroundStyle(Theme.textPrimary)
                }
                .frame(width: 62, height: 62)
            }

            HStack(spacing: 6) {
                Image(systemName: "star.fill").foregroundStyle(Theme.star)
                Text("\(earned)")
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.textPrimary)
                Text(String.loc("stars_of_max", maxStars))
                    .foregroundStyle(Theme.textTertiary)
            }
            .font(.system(size: 14))

            if finished {
                Text(String.loc("home_wait_update"))
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Theme.textSecondary)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Theme.bgElevated))
                    .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(Theme.stroke, lineWidth: 1))
            } else {
                PrimaryButton(String.loc("home_continue"), systemImage: "play.fill") {
                    startIndex = progress.currentIndex(track: track)
                    playing = true
                }
            }
        }
        .padding(20)
        .card()
    }

    private var chapterList: some View {
        VStack(alignment: .leading, spacing: 12) {
            ForEach(PuzzleStore.shared.chapters(track: track), id: \.self) { chapter in
                chapterBlock(chapter)
            }
        }
    }

    @ViewBuilder
    private func chapterBlock(_ chapter: Int) -> some View {
        let items = PuzzleStore.shared.puzzles(in: chapter, track: track)
        let trackPuzzles = PuzzleStore.shared.puzzles(track: track)
        let firstIndex = trackPuzzles.firstIndex { $0.chapter == chapter } ?? 0
        let lastIndex = firstIndex + items.count - 1
        let current = progress.currentIndex(track: track)
        let unlocked = current >= firstIndex
        let completed = current > lastIndex
        let isExpanded = expanded.contains(chapter)

        VStack(spacing: 10) {
            Button {
                guard completed else { return }
                withAnimation(.easeInOut(duration: 0.2)) {
                    if isExpanded { expanded.remove(chapter) } else { expanded.insert(chapter) }
                }
            } label: {
                chapterRow(chapter, items: items, unlocked: unlocked, completed: completed, expanded: isExpanded)
            }
            .buttonStyle(.plain)
            .disabled(!completed)

            if completed && isExpanded {
                stageList(items: items, firstIndex: firstIndex)
            }
        }
    }

    private func chapterRow(_ chapter: Int, items: [Puzzle], unlocked: Bool, completed: Bool, expanded: Bool) -> some View {
        let earned = items.reduce(0) { $0 + progress.starCount(for: $1.id) }
        let maxStars = items.count * 3
        return HStack(spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Theme.accentGradient.opacity(unlocked ? 1 : 0.25))
                    .frame(width: 46, height: 46)
                Text("\(chapter)")
                    .font(.system(size: 18, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
            }
            VStack(alignment: .leading, spacing: 4) {
                Text(chapterTitle(chapter))
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Theme.textPrimary)
                Text(String.loc("chapter_stages", items.count))
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.textTertiary)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 4) {
                StarRow(count: maxStars == 0 ? 0 : Int(round(Double(earned) / Double(maxStars) * 3)))
                Text("\(earned)/\(maxStars)")
                    .font(.system(size: 12))
                    .foregroundStyle(Theme.textTertiary)
            }
            if completed {
                Image(systemName: "chevron.down")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Theme.textTertiary)
                    .rotationEffect(.degrees(expanded ? 180 : 0))
            }
        }
        .padding(16)
        .card()
    }

    private func stageList(items: [Puzzle], firstIndex: Int) -> some View {
        VStack(spacing: 8) {
            ForEach(Array(items.enumerated()), id: \.element.id) { offset, puzzle in
                Button {
                    startIndex = firstIndex + offset
                    playing = true
                } label: {
                    HStack(spacing: 12) {
                        Text(String.loc("stage_label", offset + 1))
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Theme.textPrimary)
                            .frame(minWidth: 64, alignment: .leading)
                        StarRow(count: progress.starCount(for: puzzle.id), size: 12)
                        Spacer()
                        Image(systemName: "arrow.clockwise")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(Theme.accent2)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .card()
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.leading, 12)
        .transition(.opacity.combined(with: .move(edge: .top)))
    }

    private func chapterTitle(_ chapter: Int) -> String {
        if track == "shapes" {
            switch chapter {
            case 1: return String.loc("shape_chapter_1")
            case 2: return String.loc("shape_chapter_2")
            case 3: return String.loc("shape_chapter_3")
            case 4: return String.loc("shape_chapter_4")
            case 5: return String.loc("shape_chapter_5")
            case 6: return String.loc("shape_chapter_6")
            case 7: return String.loc("shape_chapter_7")
            case 8: return String.loc("shape_chapter_8")
            case 9: return String.loc("shape_chapter_9")
            case 10: return String.loc("shape_chapter_10")
            default: return String.loc("chapter_label", chapter)
            }
        }
        switch chapter {
        case 1: return String.loc("chapter_1")
        case 2: return String.loc("chapter_2")
        case 3: return String.loc("chapter_3")
        case 4: return String.loc("chapter_4")
        case 5: return String.loc("chapter_5")
        case 6: return String.loc("chapter_6")
        case 7: return String.loc("chapter_7")
        case 8: return String.loc("chapter_8")
        case 9: return String.loc("chapter_9")
        case 10: return String.loc("chapter_10")
        case 11: return String.loc("chapter_11")
        default: return String.loc("chapter_label", chapter)
        }
    }
}
