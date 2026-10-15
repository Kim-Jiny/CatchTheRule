import SwiftUI

private enum ChallengeColor {
    static let amber = Color(hex: 0xFFB020)
    static let pink = Color(hex: 0xFF5C8A)
    static let gold = Color(hex: 0xFFD166)
    static let silver = Color(hex: 0xC0C8D8)
    static let bronze = Color(hex: 0xFF9F6B)
    static var gradient: LinearGradient {
        LinearGradient(colors: [amber, pink], startPoint: .topLeading, endPoint: .bottomTrailing)
    }
}

struct ChallengeView: View {
    @Environment(ProgressStore.self) private var progress
    @State private var entries: [RankEntry] = []
    @State private var loading = false
    @State private var failed = false
    @State private var playing = false

    var body: some View {
        NavigationStack {
            ZStack {
                ScreenBackground(animated: true)
                ScrollView {
                    VStack(spacing: 20) {
                        header.staggerOnAppear(order: 0, dy: -24)
                        startCard.staggerOnAppear(order: 1)
                        BannerAd(unitID: BannerUnits.challenge)
                            .staggerOnAppear(order: 2)
                        leaderboard.staggerOnAppear(order: 3)
                    }
                    .padding(20)
                }
                .tabBarSafeArea()
            }
            .neonScreen()
            .toolbar(.hidden, for: .navigationBar)
            .fullScreenCover(isPresented: $playing, onDismiss: reload) {
                TimeAttackView()
            }
            .task { reload() }
        }
    }

    // MARK: - 헤더

    private var header: some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(String.loc("challenge_title"))
                    .font(.system(size: 30, weight: .heavy, design: .rounded))
                    .foregroundStyle(LinearGradient(colors: [.white, ChallengeColor.amber],
                                                    startPoint: .leading, endPoint: .trailing))
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                Text(String.loc("challenge_subtitle"))
                    .font(.system(size: 15))
                    .foregroundStyle(Theme.textSecondary)
            }
            Spacer(minLength: 0)
            GlowPulse(color: ChallengeColor.amber) {
                Image(systemName: "trophy.fill")
                    .font(.system(size: 26, weight: .bold))
                    .foregroundStyle(ChallengeColor.gradient)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 8)
    }

    // MARK: - 최고기록 히어로

    private var startCard: some View {
        VStack(spacing: 16) {
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(String.loc("challenge_best"))
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Theme.textSecondary)
                    AnimatedValue(target: Double(progress.bestTimeAttack), duration: 1.0) { v in
                        Text("\(Int(v.rounded()))")
                            .font(.system(size: 52, weight: .heavy, design: .rounded))
                            .monospacedDigit()
                            .foregroundStyle(ChallengeColor.gradient)
                            .minimumScaleFactor(0.6)
                            .lineLimit(1)
                    }
                }
                Spacer()
                GlowPulse(color: ChallengeColor.amber, strength: 1.4) {
                    Image(systemName: "bolt.fill")
                        .font(.system(size: 44, weight: .bold))
                        .foregroundStyle(ChallengeColor.gradient)
                }
            }
            HStack(spacing: 6) {
                Image(systemName: "info.circle").font(.system(size: 12))
                Text(String.loc("challenge_mode_note"))
                    .font(.system(size: 12))
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .foregroundStyle(Theme.textSecondary)
            .padding(.horizontal, 12).padding(.vertical, 8)
            .background(Capsule(style: .continuous).fill(Color.white.opacity(0.06)))
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))

            ChallengeStartButton(title: String.loc("challenge_start")) { playing = true }
        }
        .padding(20)
        .neonCard(ChallengeColor.amber, strong: true)
    }

    // MARK: - 리더보드

    private var leaderboard: some View {
        VStack(alignment: .leading, spacing: 14) {
            SectionHeader(title: String.loc("ranking"))
            if loading && entries.isEmpty {
                skeleton
            } else if entries.isEmpty {
                emptyState
            } else {
                let top = Array(entries.prefix(3))
                let rest = Array(entries.dropFirst(3))
                let tied = tiedRanks
                myRankCard(tied: tied)
                Podium(entries: top, tiedRanks: tied)
                if !rest.isEmpty {
                    VStack(spacing: 10) {
                        ForEach(Array(rest.enumerated()), id: \.element.id) { i, e in
                            LeaderboardRow(entry: e, tied: tied.contains(e.rank), index: i)
                        }
                    }
                }
            }
        }
    }

    /// 같은 rank 를 가진 엔트리가 2개 이상인 rank 집합(경쟁 순위 동점).
    private var tiedRanks: Set<Int> {
        var counts: [Int: Int] = [:]
        for e in entries { counts[e.rank, default: 0] += 1 }
        return Set(counts.filter { $0.value > 1 }.keys)
    }

    @ViewBuilder
    private func myRankCard(tied: Set<Int>) -> some View {
        let best = progress.bestTimeAttack
        let name = progress.nickname.trimmingCharacters(in: .whitespaces)
        HStack(spacing: 12) {
            Text(String.loc("ranking_my_rank"))
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Theme.textSecondary)
            Spacer(minLength: 4)
            if best > 0 {
                let rank = entries.filter { $0.score > best }.count + 1
                let outside = entries.count >= 100 && best < (entries.last?.score ?? 0)
                VStack(alignment: .trailing, spacing: 4) {
                    HStack(spacing: 6) {
                        if !outside, entries.contains(where: { $0.score == best }) {
                            TiedChip()
                        }
                        Text(outside ? String.loc("ranking_out_of_top", 100) : String.loc("ranking_rank_n", rank))
                            .font(.system(size: 26, weight: .heavy, design: .rounded))
                            .foregroundStyle(Theme.accentGradient)
                            .lineLimit(1)
                            .minimumScaleFactor(0.6)
                    }
                    Text(name.isEmpty ? "\(best)" : "\(name) · \(best)")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Theme.textSecondary)
                        .lineLimit(1)
                }
            } else {
                Text(String.loc("ranking_no_record"))
                    .font(.system(size: 14))
                    .foregroundStyle(Theme.textTertiary)
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 14)
        .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(Theme.accent.opacity(0.12)))
        .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(Theme.accent, lineWidth: 1.5))
        .shadow(color: Theme.accent.opacity(0.3), radius: 10)
        .accessibilityElement(children: .combine)
    }

    private var skeleton: some View {
        VStack(spacing: 10) {
            ForEach(0..<5, id: \.self) { i in
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(Theme.card)
                    .frame(height: i == 0 ? 120 : 52)
                    .shimmer()
            }
        }
        .accessibilityHidden(true)
    }

    private var emptyState: some View {
        VStack(spacing: 10) {
            Image(systemName: failed ? "wifi.exclamationmark" : "trophy")
                .font(.system(size: 30))
                .foregroundStyle(Theme.textTertiary)
            Text(String.loc("ranking_empty"))
                .font(.system(size: 14))
                .foregroundStyle(Theme.textSecondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 28)
        .padding(.horizontal, 16)
        .card()
    }

    private func reload() {
        loading = true
        Task {
            do {
                let result = try await Ranking.service.leaderboard(mode: .timeAttack)
                let mine = progress.nickname
                entries = result.map { e in
                    var e = e
                    e.isMe = !mine.isEmpty && e.nickname == mine && e.score == progress.bestTimeAttack
                    return e
                }
                failed = false
            } catch {
                failed = true
            }
            loading = false
        }
    }
}

// MARK: - 시작 버튼(앰버→핑크, 샤인 + 펄스)

private struct ChallengeStartButton: View {
    let title: String
    let action: () -> Void
    @Environment(\.neonAnimating) private var active

    var body: some View {
        Button(action: action) {
            TimelineView(.animation(minimumInterval: 1.0 / 30, paused: !active)) { tl in
                label(t: tl.date.timeIntervalSinceReferenceDate)
            }
        }
        .buttonStyle(NeonPressStyle())
    }

    private func label(t: Double) -> some View {
        let pulse = active ? 0.5 + 0.5 * sin(t * .pi) : 0.5
        let sweep = active ? t.truncatingRemainder(dividingBy: 3) / 0.9 : 2
        return HStack(spacing: 8) {
            Image(systemName: "play.fill")
            Text(title).fontWeight(.heavy)
        }
        .font(.system(size: 17))
        .foregroundStyle(.white)
        .frame(maxWidth: .infinity)
        .padding(.vertical, 15)
        .background(ChallengeColor.gradient)
        .overlay {
            GeometryReader { geo in
                if sweep < 1 {
                    LinearGradient(colors: [.clear, .white.opacity(0.4), .clear], startPoint: .leading, endPoint: .trailing)
                        .frame(width: geo.size.width * 0.35, height: geo.size.height * 2)
                        .rotationEffect(.degrees(20))
                        .offset(x: geo.size.width * (-0.4 + 1.6 * sweep), y: -geo.size.height * 0.5)
                }
            }
        }
        .clipShape(Capsule())
        .shadow(color: ChallengeColor.pink.opacity(0.25 + 0.25 * pulse), radius: 8 + 8 * pulse, y: 2)
    }
}

// MARK: - 글로우 펄스

private struct GlowPulse<Content: View>: View {
    let color: Color
    var strength: Double = 1
    @ViewBuilder let content: Content
    @Environment(\.neonAnimating) private var active

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30, paused: !active)) { tl in
            let p = active ? 0.5 + 0.5 * sin(tl.date.timeIntervalSinceReferenceDate * 2 * .pi / 2.2) : 0.5
            content
                .shadow(color: color.opacity(0.35 + 0.35 * p), radius: (6 + 8 * p) * strength)
                .scaleEffect(1 + 0.04 * p)
        }
    }
}

// MARK: - 스켈레톤 시머

private struct Shimmer: ViewModifier {
    @Environment(\.neonAnimating) private var active

    func body(content: Content) -> some View {
        content.overlay {
            TimelineView(.animation(minimumInterval: 1.0 / 30, paused: !active)) { tl in
                GeometryReader { geo in
                    let p = active ? tl.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 1.4) / 1.4 : 0.3
                    LinearGradient(colors: [.clear, .white.opacity(0.10), .clear], startPoint: .leading, endPoint: .trailing)
                        .frame(width: geo.size.width * 0.45)
                        .offset(x: geo.size.width * (-0.45 + 1.45 * p))
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
    }
}

private extension View {
    func shimmer() -> some View { modifier(Shimmer()) }
}

// MARK: - 포디움

/// 서버 순서 상위 3개: 가운데=entries[0], 왼쪽=entries[1], 오른쪽=entries[2]. 각 카드는 자기 entry.rank 로 표시/스타일.
private struct Podium: View {
    let entries: [RankEntry]
    let tiedRanks: Set<Int>

    var body: some View {
        HStack(alignment: .bottom, spacing: 10) {
            slot(1)
            slot(0)
            slot(2)
        }
    }

    @ViewBuilder
    private func slot(_ i: Int) -> some View {
        if i < entries.count {
            PodiumCard(entry: entries[i], tied: tiedRanks.contains(entries[i].rank), order: i)
                .frame(maxWidth: .infinity)
        } else {
            Color.clear.frame(maxWidth: .infinity)
        }
    }
}

private struct TiedChip: View {
    var body: some View {
        Text(String.loc("ranking_tied"))
            .font(.system(size: 10, weight: .bold))
            .foregroundStyle(Theme.textSecondary)
            .padding(.horizontal, 6).padding(.vertical, 2)
            .background(Capsule().fill(Color.white.opacity(0.10)))
    }
}

private struct PodiumCard: View {
    let entry: RankEntry
    let tied: Bool
    let order: Int
    private var place: Int { entry.rank }

    private var color: Color {
        switch place {
        case 1: return ChallengeColor.gold
        case 2: return ChallengeColor.silver
        default: return ChallengeColor.bronze
        }
    }

    var body: some View {
        let first = place == 1
        VStack(spacing: 6) {
            if first {
                Image(systemName: "crown.fill")
                    .font(.system(size: 22))
                    .foregroundStyle(color)
                    .shadow(color: color.opacity(0.8), radius: 8)
            }
            HStack(spacing: 4) {
                Text("\(place)")
                    .font(.system(size: first ? 22 : 18, weight: .heavy, design: .rounded))
                    .foregroundStyle(color)
                if tied { TiedChip() }
            }
            if let flag = entry.flagEmoji {
                Text(flag).font(.system(size: first ? 24 : 20))
            }
            Text(entry.nickname)
                .font(.system(size: 14, weight: entry.isMe ? .heavy : .semibold))
                .foregroundStyle(entry.isMe ? Theme.accent2 : Theme.textPrimary)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            AnimatedValue(target: Double(entry.score), duration: 0.9, delay: 0.15 * Double(order)) { v in
                Text("\(Int(v.rounded()))")
                    .font(.system(size: first ? 22 : 17, weight: .heavy, design: .rounded))
                    .monospacedDigit()
                    .foregroundStyle(Theme.textPrimary)
            }
            if entry.isMe {
                Text(String.loc("me"))
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 6).padding(.vertical, 2)
                    .background(Theme.accent, in: Capsule())
            }
        }
        .padding(.horizontal, 6)
        .padding(.vertical, first ? 18 : 12)
        .padding(.bottom, first ? 10 : 0)
        .frame(maxWidth: .infinity)
        .neonCard(color, radius: 18, strong: first)
        .overlay {
            if entry.isMe {
                RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(Theme.accent, lineWidth: 2)
            }
        }
        .staggerOnAppear(order: order == 0 ? 0 : 1, step: 0.1, dy: 30)
        .accessibilityElement(children: .combine)
    }
}

// MARK: - 4위 이하 행

struct LeaderboardRow: View {
    let entry: RankEntry
    var tied = false
    var index = 0

    var body: some View {
        HStack(spacing: 12) {
            Text("\(entry.rank)")
                .font(.system(size: 13, weight: .heavy, design: .rounded))
                .monospacedDigit()
                .foregroundStyle(Theme.textSecondary)
                .frame(minWidth: 30, minHeight: 28)
                .background(Capsule().fill(Color.white.opacity(0.08)))

            if tied {
                Text(String.loc("ranking_tied"))
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(Theme.textSecondary)
            }
            if let flag = entry.flagEmoji {
                Text(flag).font(.system(size: 18))
            }
            Text(entry.nickname)
                .font(.system(size: 16, weight: entry.isMe ? .bold : .medium))
                .foregroundStyle(entry.isMe ? Theme.accent2 : Theme.textPrimary)
                .lineLimit(1)
            if entry.isMe {
                Text(String.loc("me"))
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 6).padding(.vertical, 2)
                    .background(Theme.accent, in: Capsule())
            }
            Spacer(minLength: 4)
            AnimatedValue(target: Double(entry.score), duration: 0.8) { v in
                Text("\(Int(v.rounded()))")
                    .font(.system(size: 16, weight: .bold, design: .rounded))
                    .monospacedDigit()
                    .foregroundStyle(Theme.textPrimary)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(entry.isMe ? Theme.accent.opacity(0.14) : Theme.card)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(entry.isMe ? Theme.accent : Theme.stroke, lineWidth: entry.isMe ? 1.5 : 1)
        )
        .shadow(color: entry.isMe ? Theme.accent.opacity(0.35) : .clear, radius: 10)
        .staggerIfEarly(index)
        .accessibilityElement(children: .combine)
    }
}

private extension View {
    /// 앞쪽 일부 행만 스태거, 나머지는 즉시 표시(성능).
    @ViewBuilder
    func staggerIfEarly(_ index: Int) -> some View {
        if index < 8 { staggerOnAppear(order: index, step: 0.04, dy: 16) } else { self }
    }
}
