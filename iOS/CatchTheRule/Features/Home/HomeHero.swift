import SwiftUI

// MARK: - 헤더(타이틀 + 별 합계 칩)

struct HomeHeader: View {
    @Environment(ProgressStore.self) private var progress

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .center, spacing: 12) {
                Text(String.loc("app_name"))
                    .font(.system(size: 30, weight: .heavy, design: .rounded))
                    .foregroundStyle(LinearGradient(colors: [.white, Color(hex: 0x18C8FF)],
                                                    startPoint: .leading, endPoint: .trailing))
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                Spacer(minLength: 0)
                starChip
            }
            Text(String.loc("home_subtitle"))
                .font(.system(size: 15))
                .foregroundStyle(Theme.textSecondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 8)
    }

    private var starChip: some View {
        HStack(spacing: 5) {
            Image(systemName: "star.fill").foregroundStyle(Theme.star)
            AnimatedValue(target: Double(progress.totalStars), duration: 0.7) { v in
                Text(String.loc("home_total_stars", Int(v.rounded())))
                    .font(.system(size: 13, weight: .bold, design: .rounded))
                    .monospacedDigit()
                    .foregroundStyle(Theme.textPrimary)
            }
        }
        .font(.system(size: 13))
        .padding(.horizontal, 12)
        .padding(.vertical, 7)
        .background(Capsule().fill(Theme.star.opacity(0.12)))
        .overlay(Capsule().stroke(Theme.star.opacity(0.35), lineWidth: 1))
        .fixedSize()
    }
}

// MARK: - 이어하기 히어로 카드

/// 홈 히어로 / 모드 상세 이어하기 카드 공용. 카드 상단 탭 = onOpen(없으면 비활성), 버튼 = onContinue.
struct HeroCard: View {
    @Environment(ProgressStore.self) private var progress
    let track: String
    let caption: String
    var onOpen: (() -> Void)? = nil
    let onContinue: () -> Void

    var body: some View {
        let color = Theme.modeColor(track)
        let finished = progress.isCampaignFinished(track: track)
        return VStack(alignment: .leading, spacing: 16) {
            if let onOpen {
                Button(action: onOpen) { info(finished: finished, color: color) }
                    .buttonStyle(NeonPressStyle())
            } else {
                info(finished: finished, color: color)
            }

            if finished {
                Text(String.loc("home_wait_update"))
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Theme.textSecondary)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Theme.bgElevated))
                    .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(Theme.stroke, lineWidth: 1))
            } else {
                NeonContinueButton(track: track, title: String.loc("home_continue"), action: onContinue)
            }
        }
        .padding(18)
        .neonCard(color, strong: finished)
    }

    private func info(finished: Bool, color: Color) -> some View {
        let total = PuzzleStore.shared.totalCount(track: track)
        let index = min(progress.currentIndex(track: track), max(total - 1, 0))
        let pos = total > 0 ? PuzzleStore.shared.position(of: index, track: track) : nil
        return HStack(spacing: 14) {
            ModeIconTile(track: track, size: 64)
            VStack(alignment: .leading, spacing: 4) {
                Text(caption)
                    .font(.system(size: 12, weight: .heavy))
                    .foregroundStyle(color)
                Text(modeTitle(track))
                    .font(.system(size: 22, weight: .heavy))
                    .foregroundStyle(Theme.textPrimary)
                    .lineLimit(2)
                    .minimumScaleFactor(0.7)
                if let p = pos, !finished {
                    Text(String.loc("chapter_label", p.chapter) + " · " + String.loc("stage_label", p.stage))
                        .font(.system(size: 14, weight: .semibold, design: .rounded))
                        .foregroundStyle(Theme.textSecondary)
                } else {
                    Text(String.loc("home_congrats"))
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Theme.textSecondary)
                }
                HStack(spacing: 5) {
                    Image(systemName: "star.fill").foregroundStyle(Theme.star)
                    Text("\(progress.earnedStars(track: track))")
                        .fontWeight(.semibold)
                        .monospacedDigit()
                        .foregroundStyle(Theme.textPrimary)
                    Text(String.loc("stars_of_max", progress.maxStars(track: track)))
                        .foregroundStyle(Theme.textTertiary)
                }
                .font(.system(size: 12))
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            NeonRing(track: track, fraction: progress.progressFraction(track: track))
        }
        .contentShape(Rectangle())
    }
}
