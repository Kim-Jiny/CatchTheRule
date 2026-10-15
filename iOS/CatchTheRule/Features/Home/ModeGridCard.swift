import SwiftUI

/// 홈 모드 그리드의 카드(2열). 완료 모드는 왕관 배지 + 테두리 글로우 강화.
struct ModeGridCard: View {
    @Environment(ProgressStore.self) private var progress
    let track: String

    var body: some View {
        let color = Theme.modeColor(track)
        let fraction = progress.progressFraction(track: track)
        let finished = progress.isCampaignFinished(track: track)
        let total = PuzzleStore.shared.totalCount(track: track)
        let solved = min(progress.currentIndex(track: track), total)
        return VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top) {
                ModeIconTile(track: track, size: 44)
                Spacer(minLength: 0)
                if finished {
                    Image(systemName: "crown.fill")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(Theme.star)
                        .shadow(color: Theme.star.opacity(0.6), radius: 6)
                }
            }
            Text(modeTitle(track))
                .font(.system(size: 15, weight: .heavy))
                .foregroundStyle(Theme.textPrimary)
                .lineLimit(2)
                .minimumScaleFactor(0.8)
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, minHeight: 38, alignment: .topLeading)
            Spacer(minLength: 0)
            NeonBar(track: track, fraction: fraction)
            HStack {
                AnimatedValue(target: fraction * 100, duration: 0.8) { v in
                    Text("\(Int(v.rounded()))%")
                        .font(.system(size: 13, weight: .heavy, design: .rounded))
                        .monospacedDigit()
                        .foregroundStyle(color)
                }
                Spacer(minLength: 4)
                Text("\(solved)/\(total)")
                    .font(.system(size: 11, weight: .medium))
                    .monospacedDigit()
                    .foregroundStyle(Theme.textTertiary)
            }
        }
        .padding(14)
        .frame(height: 158)
        .neonCard(color, strong: finished)
    }
}
