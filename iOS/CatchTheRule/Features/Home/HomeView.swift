import SwiftUI

struct HomeView: View {
    @Environment(ProgressStore.self) private var progress
    @State private var playing = false
    @State private var startIndex = 0
    @State private var expandedChapters: Set<Int> = []

    private var position: (chapter: Int, stage: Int)? {
        PuzzleStore.shared.position(of: min(progress.currentIndex, PuzzleStore.shared.totalCount - 1))
    }

    var body: some View {
        NavigationStack {
            ZStack {
                ScreenBackground()
                ScrollView {
                    VStack(spacing: 20) {
                        title
                        continueCard
                        BannerAd(unitID: BannerUnits.home)
                        chapterList
                    }
                    .padding(20)
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar(.hidden, for: .navigationBar)
            .fullScreenCover(isPresented: $playing) {
                CampaignSessionView(startIndex: startIndex)
            }
        }
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

    private var continueCard: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(String.loc(progress.isCampaignFinished ? "home_all_clear" : "home_current"))
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Theme.textSecondary)
                    if let p = position, !progress.isCampaignFinished {
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
                    Circle()
                        .stroke(Theme.stroke, lineWidth: 6)
                    Circle()
                        .trim(from: 0, to: progress.progressFraction)
                        .stroke(Theme.accentGradient,
                                style: StrokeStyle(lineWidth: 6, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                    Text("\(Int(progress.progressFraction * 100))%")
                        .font(.system(size: 13, weight: .bold, design: .rounded))
                        .foregroundStyle(Theme.textPrimary)
                }
                .frame(width: 62, height: 62)
            }

            HStack(spacing: 6) {
                Image(systemName: "star.fill").foregroundStyle(Theme.star)
                Text("\(progress.totalStars)")
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.textPrimary)
                Text(String.loc("stars_of_max", progress.maxStars))
                    .foregroundStyle(Theme.textTertiary)
            }
            .font(.system(size: 14))

            PrimaryButton(String.loc(progress.isCampaignFinished ? "home_retry" : "home_continue"),
                          systemImage: "play.fill") {
                startIndex = progress.currentIndex
                playing = true
            }
        }
        .padding(20)
        .card()
    }

    private var chapterList: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: String.loc("chapters"))
            ForEach(PuzzleStore.shared.chapters, id: \.self) { chapter in
                chapterBlock(chapter)
            }
        }
    }

    /// 챕터 한 칸. 완료(지나간) 챕터는 탭하면 스테이지 목록을 펼쳐 이전 문제를 다시 풀 수 있다.
    @ViewBuilder
    private func chapterBlock(_ chapter: Int) -> some View {
        let items = PuzzleStore.shared.puzzles(in: chapter)
        // 해당 챕터의 첫 전역 인덱스(=잠금 해제 기준). order 는 챕터 내 번호라 전역 인덱스로 비교해야 함.
        let firstGlobal = PuzzleStore.shared.puzzles.firstIndex { $0.chapter == chapter } ?? 0
        let lastGlobal = firstGlobal + items.count - 1
        let unlocked = progress.currentIndex >= firstGlobal
        let completed = progress.currentIndex > lastGlobal   // 챕터 전체를 지나감
        let expanded = expandedChapters.contains(chapter)

        VStack(spacing: 10) {
            Button {
                guard completed else { return }
                withAnimation(.easeInOut(duration: 0.2)) {
                    if expanded { expandedChapters.remove(chapter) } else { expandedChapters.insert(chapter) }
                }
            } label: {
                chapterRow(chapter, items: items, unlocked: unlocked, completed: completed, expanded: expanded)
            }
            .buttonStyle(.plain)
            .disabled(!completed)

            if completed && expanded {
                stageList(items: items, firstGlobal: firstGlobal)
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

    /// 완료된 챕터의 스테이지 목록 — 각 항목을 누르면 그 스테이지부터 다시 플레이.
    private func stageList(items: [Puzzle], firstGlobal: Int) -> some View {
        VStack(spacing: 8) {
            ForEach(Array(items.enumerated()), id: \.element.id) { offset, puzzle in
                Button {
                    startIndex = firstGlobal + offset
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
