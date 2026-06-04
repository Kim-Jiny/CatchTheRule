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
            Text("타임어택")
                .font(.system(size: 28, weight: .bold, design: .rounded))
                .foregroundStyle(Theme.textPrimary)
            Text("60초 동안 최대한 많이 풀고 랭킹에 도전하세요")
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
                    Text("내 최고 점수")
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
            PrimaryButton("타임어택 시작", systemImage: "play.fill") { playing = true }
        }
        .padding(20)
        .card()
    }

    private var leaderboard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                SectionHeader(title: "랭킹")
                Spacer()
                if loading { ProgressView().tint(Theme.textSecondary) }
            }
            if entries.isEmpty && !loading {
                Text("아직 기록이 없어요. 첫 주자가 되어보세요!")
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
                Text("나")
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
