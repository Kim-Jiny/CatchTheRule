import SwiftUI

struct ChallengeView: View {
    @Environment(ProgressStore.self) private var progress
    @State private var entries: [RankEntry] = []
    @State private var loading = false
    @State private var playing = false

    var body: some View {
        NavigationStack {
            ZStack {
                ScreenBackground()
                ScrollView {
                    VStack(spacing: 20) {
                        header
                        startCard
                        BannerAd(unitID: BannerUnits.challenge)
                        leaderboard
                    }
                    .padding(20)
                }
            }
            .toolbar(.hidden, for: .navigationBar)
            .fullScreenCover(isPresented: $playing, onDismiss: reload) {
                TimeAttackView()
            }
            .task { reload() }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(String.loc("challenge_title"))
                .font(.system(size: 28, weight: .bold, design: .rounded))
                .foregroundStyle(Theme.textPrimary)
            Text(String.loc("challenge_subtitle"))
                .font(.system(size: 14))
                .foregroundStyle(Theme.textSecondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 8)
    }

    private var startCard: some View {
        VStack(spacing: 16) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(String.loc("challenge_best"))
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Theme.textSecondary)
                    Text("\(progress.bestTimeAttack)")
                        .font(.system(size: 34, weight: .bold, design: .rounded))
                        .foregroundStyle(Theme.textPrimary)
                }
                Spacer()
                Image(systemName: "bolt.fill")
                    .font(.system(size: 30))
                    .foregroundStyle(Theme.accentGradient)
            }
            PrimaryButton(String.loc("challenge_start"), systemImage: "play.fill") { playing = true }
        }
        .padding(20)
        .card()
    }

    private var leaderboard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                SectionHeader(title: String.loc("ranking"))
                Spacer()
                if loading { ProgressView().tint(Theme.textSecondary) }
            }
            if entries.isEmpty && !loading {
                Text(String.loc("ranking_empty"))
                    .font(.system(size: 14))
                    .foregroundStyle(Theme.textTertiary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.vertical, 8)
            }
            ForEach(entries) { entry in
                LeaderboardRow(entry: entry)
            }
        }
    }

    private func reload() {
        loading = true
        Task {
            let result = (try? await Ranking.service.leaderboard(mode: .timeAttack)) ?? []
            let mine = progress.nickname
            entries = result.map { e in
                var e = e
                e.isMe = !mine.isEmpty && e.nickname == mine
                return e
            }
            loading = false
        }
    }
}

struct LeaderboardRow: View {
    let entry: RankEntry

    private var medal: String? {
        switch entry.rank {
        case 1: return "🥇"
        case 2: return "🥈"
        case 3: return "🥉"
        default: return nil
        }
    }

    var body: some View {
        HStack(spacing: 14) {
            Group {
                if let medal {
                    Text(medal).font(.system(size: 20))
                } else {
                    Text("\(entry.rank)")
                        .font(.system(size: 15, weight: .bold, design: .rounded))
                        .foregroundStyle(Theme.textSecondary)
                }
            }
            .frame(width: 30)

            if let flag = entry.flagEmoji {
                Text(flag).font(.system(size: 18))
            }
            Text(entry.nickname)
                .font(.system(size: 16, weight: entry.isMe ? .bold : .medium))
                .foregroundStyle(entry.isMe ? Theme.accent2 : Theme.textPrimary)
            if entry.isMe {
                Text(String.loc("me"))
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 6).padding(.vertical, 2)
                    .background(Theme.accent, in: Capsule())
            }
            Spacer()
            Text("\(entry.score)")
                .font(.system(size: 16, weight: .bold, design: .rounded))
                .foregroundStyle(Theme.textPrimary)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(entry.isMe ? Theme.accent.opacity(0.12) : Theme.card)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(entry.isMe ? Theme.accent.opacity(0.4) : Theme.stroke, lineWidth: 1)
        )
    }
}
