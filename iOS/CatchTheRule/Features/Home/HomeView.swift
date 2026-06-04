import SwiftUI

struct HomeView: View {
    @Environment(ProgressStore.self) private var progress
    @State private var playing = false

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
                        chapterList
                    }
                    .padding(20)
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar(.hidden, for: .navigationBar)
            .fullScreenCover(isPresented: $playing) {
                CampaignSessionView(startIndex: progress.currentIndex)
            }
        }
    }

    private var title: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("규칙찾기")
                .font(.system(size: 30, weight: .bold, design: .rounded))
                .foregroundStyle(Theme.textPrimary)
            Text("패턴을 발견하고 다음을 맞혀보세요")
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
                    Text(progress.isCampaignFinished ? "전체 클리어" : "현재 도전")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Theme.textSecondary)
                    if let p = position, !progress.isCampaignFinished {
                        Text("Chapter \(p.chapter) · Stage \(p.stage)")
                            .font(.system(size: 22, weight: .bold, design: .rounded))
                            .foregroundStyle(Theme.textPrimary)
                    } else {
                        Text("축하해요! 🎉")
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
                Text("/ \(progress.maxStars) 별")
                    .foregroundStyle(Theme.textTertiary)
            }
            .font(.system(size: 14))

            PrimaryButton(progress.isCampaignFinished ? "다시 도전" : "이어하기",
                          systemImage: "play.fill") {
                playing = true
            }
        }
        .padding(20)
        .card()
    }

    private var chapterList: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: "챕터")
            ForEach(PuzzleStore.shared.chapters, id: \.self) { chapter in
                chapterRow(chapter)
            }
        }
    }

    private func chapterRow(_ chapter: Int) -> some View {
        let items = PuzzleStore.shared.puzzles(in: chapter)
        let earned = items.reduce(0) { $0 + progress.starCount(for: $1.id) }
        let maxStars = items.count * 3
        // 해당 챕터의 첫 전역 인덱스에 도달했는지(=잠금 해제). order 는 챕터 내 번호라 전역 인덱스로 비교해야 함.
        let firstGlobal = PuzzleStore.shared.puzzles.firstIndex { $0.chapter == chapter } ?? 0
        let unlocked = progress.currentIndex >= firstGlobal
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
                Text("\(items.count)단계")
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
        }
        .padding(16)
        .card()
    }

    private func chapterTitle(_ chapter: Int) -> String {
        switch chapter {
        case 1: return "기초 패턴"
        case 2: return "곱셈과 제곱"
        case 3: return "수학 수열"
        case 4: return "문자 패턴"
        case 5: return "모양 찾기"
        case 6: return "고급"
        case 7: return "마스터"
        default: return "Chapter \(chapter)"
        }
    }
}
