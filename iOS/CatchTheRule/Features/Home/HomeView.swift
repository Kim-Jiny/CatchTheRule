import SwiftUI

/// 홈. 도형규칙 해금 전엔 숫자규칙 단일 캠페인(튜토리얼), 해금 후엔 히어로 + 모드 그리드 허브.
struct HomeView: View {
    @Environment(ProgressStore.self) private var progress
    @State private var showUnlock = false
    @State private var path = NavigationPath()
    @State private var playing = false
    @State private var playTrack = PuzzleStore.defaultTrack
    @State private var startIndex = 0

    var body: some View {
        NavigationStack(path: $path) {
            ZStack {
                ScreenBackground(animated: true)
                ScrollView {
                    VStack(spacing: 22) {
                        HomeHeader()
                            .staggerOnAppear(order: 0, dy: -24)
                        if progress.isShapesUnlocked {
                            hub
                        } else {
                            // 잠금: 숫자규칙 캠페인을 홈에 직접 표시(튜토리얼)
                            BannerAd(unitID: BannerUnits.home)
                            CampaignTrackView(track: "numbers")
                                .staggerOnAppear(order: 1)
                        }
                    }
                    .padding(20)
                }
                .tabBarSafeArea()
            }
            .neonScreen()
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
        .fullScreenCover(isPresented: $playing) {
            CampaignSessionView(startIndex: startIndex, track: playTrack)
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

    // MARK: - 허브(해금 후)

    /// 히어로 대상: 마지막 플레이 모드(없으면 첫 모드). 이미 전부 클리어했으면 가장 진행이 덜 된 모드.
    private var heroTrack: String {
        let tracks = PuzzleStore.shared.tracks
        let candidate = progress.lastPlayedTrack.flatMap { tracks.contains($0) ? $0 : nil } ?? tracks.first ?? "numbers"
        guard progress.isCampaignFinished(track: candidate) else { return candidate }
        return tracks.filter { !progress.isCampaignFinished(track: $0) }
            .min { progress.progressFraction(track: $0) < progress.progressFraction(track: $1) } ?? candidate
    }

    private var hub: some View {
        let tracks = PuzzleStore.shared.tracks
        let hero = heroTrack
        return VStack(alignment: .leading, spacing: 18) {
            HeroCard(track: hero, caption: String.loc("home_last_played"),
                     onOpen: { path.append(hero) },
                     onContinue: {
                         playTrack = hero
                         startIndex = progress.currentIndex(track: hero)
                         progress.lastPlayedTrack = hero
                         playing = true
                     })
                .staggerOnAppear(order: 1)
            VStack(alignment: .leading, spacing: 14) {
                SectionHeader(title: String.loc("select_mode"))
                BannerAd(unitID: BannerUnits.home)
                grid(tracks)
            }
            .staggerOnAppear(order: 2)
        }
    }

    /// 2열 그리드. 개수가 홀수면 마지막 카드는 가로 꽉.
    private func grid(_ tracks: [String]) -> some View {
        let rows = stride(from: 0, to: tracks.count, by: 2).map { Array(tracks[$0..<min($0 + 2, tracks.count)]) }
        return VStack(spacing: 14) {
            ForEach(Array(rows.enumerated()), id: \.offset) { r, row in
                HStack(spacing: 14) {
                    ForEach(Array(row.enumerated()), id: \.element) { c, track in
                        NavigationLink(value: track) {
                            ModeGridCard(track: track)
                        }
                        .buttonStyle(NeonPressStyle())
                        .staggerOnAppear(order: 3 + r * 2 + c)
                    }
                }
            }
        }
    }
}

func modeTitle(_ track: String) -> String {
    switch track {
    case "shapes":        return String.loc("mode_shapes")
    case "logic":         return String.loc("mode_logic")
    case "contradiction": return String.loc("mode_contradiction")
    case "target":        return String.loc("mode_target")
    case "match":         return String.loc("mode_match")
    case "fragment":      return String.loc("mode_fragment")
    case "cipher":        return String.loc("mode_cipher")
    case "scale":         return String.loc("mode_scale")
    default:              return String.loc("mode_numbers")
    }
}

func modeIcon(_ track: String) -> String {
    switch track {
    case "shapes":        return "triangle.fill"
    case "logic":         return "brain.head.profile"
    case "contradiction": return "exclamationmark.triangle.fill"
    case "target":        return "plus.forwardslash.minus"
    case "match":         return "flame.fill"
    case "fragment":      return "square.stack.3d.up.fill"
    case "cipher":        return "lock.open.fill"
    case "scale":         return "scalemass.fill"
    default:              return "number"
    }
}

// MARK: - 모드 상세 페이지

struct ModeDetailView: View {
    @Environment(\.dismiss) private var dismiss
    let track: String

    var body: some View {
        ZStack {
            ScreenBackground(animated: true)
            header
            ScrollView {
                VStack(spacing: 16) {
                    CampaignTrackView(track: track)
                }
                .padding(20)
            }
            .tabBarSafeArea()
        }
        .neonScreen()
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
                    .font(.system(size: 17, weight: .heavy))
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

    /// 상단 모드색 글로우 + 큰 아이콘 워터마크.
    private var header: some View {
        ZStack(alignment: .topTrailing) {
            RadialGradient(colors: [Theme.modeColor(track).opacity(0.38), .clear],
                           center: .top, startRadius: 0, endRadius: 320)
            Image(systemName: modeIcon(track))
                .font(.system(size: 150, weight: .bold))
                .foregroundStyle(Theme.modeColor(track).opacity(0.10))
                .rotationEffect(.degrees(-12))
                .offset(x: 30, y: 40)
        }
        .frame(maxWidth: .infinity, maxHeight: 340, alignment: .top)
        .frame(maxHeight: .infinity, alignment: .top)
        .clipped()
        .ignoresSafeArea()
        .allowsHitTesting(false)
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

// MARK: - 트랙 캠페인 뷰 (이어하기 + 챕터 타임라인 + 플레이)

/// 한 트랙의 이어하기 카드 + 챕터 타임라인. 잠금 홈(numbers)·모드 상세 공용.
struct CampaignTrackView: View {
    @Environment(ProgressStore.self) private var progress
    let track: String

    /// 시작 위치를 cover 에 함께 실어 보낸다(별도 @State 로 두면 갱신 전 값(0)으로 세션이 열릴 수 있음).
    private struct PlaySession: Identifiable {
        let id = UUID()
        let startIndex: Int
    }

    @State private var session: PlaySession?
    @State private var expanded: Set<Int> = []

    private var color: Color { Theme.modeColor(track) }

    private func play(from index: Int) {
        progress.lastPlayedTrack = track
        session = PlaySession(startIndex: index)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HeroCard(track: track, caption: String.loc("home_current")) {
                play(from: progress.currentIndex(track: track))
            }
            chapterList
        }
        .fullScreenCover(item: $session) { s in
            CampaignSessionView(startIndex: s.startIndex, track: track)
        }
    }

    private var chapterList: some View {
        let chapters = PuzzleStore.shared.chapters(track: track)
        return VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(chapters.enumerated()), id: \.element) { offset, chapter in
                chapterBlock(chapter, isFirst: offset == 0, isLast: offset == chapters.count - 1)
                    .staggerOnAppear(order: min(offset, 8), step: 0.04)
            }
        }
    }

    @ViewBuilder
    private func chapterBlock(_ chapter: Int, isFirst: Bool, isLast: Bool) -> some View {
        let items = PuzzleStore.shared.puzzles(in: chapter, track: track)
        let trackPuzzles = PuzzleStore.shared.puzzles(track: track)
        let firstIndex = trackPuzzles.firstIndex { $0.chapter == chapter } ?? 0
        let lastIndex = firstIndex + items.count - 1
        let current = progress.currentIndex(track: track)
        let unlocked = current >= firstIndex
        let completed = current > lastIndex
        let isExpanded = expanded.contains(chapter)
        let state: ChapterState = completed ? .completed : (unlocked ? .current : .locked)

        HStack(alignment: .top, spacing: 12) {
            Color.clear.frame(width: TimelineRail.width, height: 1)
            VStack(spacing: 8) {
                Button {
                    guard completed else { return }
                    withAnimation(.spring(response: 0.4, dampingFraction: 0.85)) {
                        if isExpanded { expanded.remove(chapter) } else { expanded.insert(chapter) }
                    }
                } label: {
                    chapterRow(chapter, items: items, state: state, firstIndex: firstIndex, expanded: isExpanded)
                }
                .buttonStyle(NeonPressStyle())
                .disabled(!completed)

                if completed && isExpanded {
                    stageList(items: items, firstIndex: firstIndex)
                }
            }
            .padding(.bottom, isLast ? 0 : 14)
        }
        .background(alignment: .topLeading) {
            TimelineRail(track: track, number: chapter, state: state,
                         prevCompleted: current > firstIndex - 1 && !isFirst,
                         isFirst: isFirst, isLast: isLast)
        }
    }

    private func chapterRow(_ chapter: Int, items: [Puzzle], state: ChapterState, firstIndex: Int, expanded: Bool) -> some View {
        let earned = items.reduce(0) { $0 + progress.starCount(for: $1.id) }
        let maxStars = items.count * 3
        let solved = max(0, min(items.count, progress.currentIndex(track: track) - firstIndex))
        let fraction = items.isEmpty ? 0 : Double(solved) / Double(items.count)
        let rowColor = state == .locked ? Theme.lockedColor : color
        return VStack(spacing: 10) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(String.loc("chapter_label", chapter))
                        .font(.system(size: 11, weight: .heavy))
                        .foregroundStyle(state == .locked ? Theme.textTertiary : color)
                    Text(chapterTitle(chapter))
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(state == .locked ? Theme.textTertiary : Theme.textPrimary)
                        .lineLimit(2)
                        .minimumScaleFactor(0.8)
                        .multilineTextAlignment(.leading)
                    Text(String.loc("chapter_stages", items.count))
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.textTertiary)
                }
                Spacer(minLength: 4)
                if state != .locked {
                    VStack(alignment: .trailing, spacing: 4) {
                        StarRow(count: maxStars == 0 ? 0 : Int(round(Double(earned) / Double(maxStars) * 3)))
                        Text("\(earned)/\(maxStars)")
                            .font(.system(size: 12, weight: .medium))
                            .monospacedDigit()
                            .foregroundStyle(Theme.textTertiary)
                    }
                }
                if state == .completed {
                    Image(systemName: "chevron.down")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Theme.textTertiary)
                        .rotationEffect(.degrees(expanded ? 180 : 0))
                }
            }
            if state != .locked {
                NeonBar(track: track, fraction: fraction, height: 4)
            }
        }
        .padding(14)
        .neonCard(rowColor, radius: 18, strong: state == .current)
        .opacity(state == .locked ? 0.55 : 1)
    }

    private func stageList(items: [Puzzle], firstIndex: Int) -> some View {
        VStack(spacing: 8) {
            ForEach(Array(items.enumerated()), id: \.element.id) { offset, puzzle in
                Button {
                    play(from: firstIndex + offset)
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
                            .foregroundStyle(color)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Theme.modeTint(track)))
                    .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(color.opacity(0.35), lineWidth: 1))
                }
                .buttonStyle(NeonPressStyle())
                .staggerOnAppear(order: offset, step: 0.04, dy: 12)
            }
        }
        .transition(.opacity.combined(with: .move(edge: .top)))
    }

    private func chapterTitle(_ chapter: Int) -> String {
        if track == "target" {
            switch chapter {
            case 1: return String.loc("target_chapter_1")
            case 2: return String.loc("target_chapter_2")
            case 3: return String.loc("target_chapter_3")
            case 4: return String.loc("target_chapter_4")
            case 5: return String.loc("target_chapter_5")
            case 6: return String.loc("target_chapter_6")
            case 7: return String.loc("target_chapter_7")
            case 8: return String.loc("target_chapter_8")
            case 9: return String.loc("target_chapter_9")
            case 10: return String.loc("target_chapter_10")
            case 11: return String.loc("target_chapter_11")
            case 12: return String.loc("target_chapter_12")
            case 13: return String.loc("target_chapter_13")
            case 14: return String.loc("target_chapter_14")
            default: return String.loc("chapter_label", chapter)
            }
        }
        if track == "fragment" {
            switch chapter {
            case 1: return String.loc("fragment_chapter_1")
            case 2: return String.loc("fragment_chapter_2")
            case 3: return String.loc("fragment_chapter_3")
            case 4: return String.loc("fragment_chapter_4")
            case 5: return String.loc("fragment_chapter_5")
            case 6: return String.loc("fragment_chapter_6")
            case 7: return String.loc("fragment_chapter_7")
            case 8: return String.loc("fragment_chapter_8")
            case 9: return String.loc("fragment_chapter_9")
            case 10: return String.loc("fragment_chapter_10")
            case 11: return String.loc("fragment_chapter_11")
            case 12: return String.loc("fragment_chapter_12")
            case 13: return String.loc("fragment_chapter_13")
            case 14: return String.loc("fragment_chapter_14")
            default: return String.loc("chapter_label", chapter)
            }
        }
        if track == "cipher" {
            switch chapter {
            case 1: return String.loc("cipher_chapter_1")
            case 2: return String.loc("cipher_chapter_2")
            case 3: return String.loc("cipher_chapter_3")
            case 4: return String.loc("cipher_chapter_4")
            case 5: return String.loc("cipher_chapter_5")
            case 6: return String.loc("cipher_chapter_6")
            case 7: return String.loc("cipher_chapter_7")
            case 8: return String.loc("cipher_chapter_8")
            case 9: return String.loc("cipher_chapter_9")
            case 10: return String.loc("cipher_chapter_10")
            case 11: return String.loc("cipher_chapter_11")
            case 12: return String.loc("cipher_chapter_12")
            case 13: return String.loc("cipher_chapter_13")
            case 14: return String.loc("cipher_chapter_14")
            default: return String.loc("chapter_label", chapter)
            }
        }
        if track == "scale" {
            switch chapter {
            case 1: return String.loc("scale_chapter_1")
            case 2: return String.loc("scale_chapter_2")
            case 3: return String.loc("scale_chapter_3")
            case 4: return String.loc("scale_chapter_4")
            case 5: return String.loc("scale_chapter_5")
            case 6: return String.loc("scale_chapter_6")
            case 7: return String.loc("scale_chapter_7")
            case 8: return String.loc("scale_chapter_8")
            case 9: return String.loc("scale_chapter_9")
            case 10: return String.loc("scale_chapter_10")
            case 11: return String.loc("scale_chapter_11")
            case 12: return String.loc("scale_chapter_12")
            case 13: return String.loc("scale_chapter_13")
            case 14: return String.loc("scale_chapter_14")
            default: return String.loc("chapter_label", chapter)
            }
        }
        if track == "match" {
            switch chapter {
            case 1: return String.loc("match_chapter_1")
            case 2: return String.loc("match_chapter_2")
            case 3: return String.loc("match_chapter_3")
            case 4: return String.loc("match_chapter_4")
            case 5: return String.loc("match_chapter_5")
            case 6: return String.loc("match_chapter_6")
            case 7: return String.loc("match_chapter_7")
            case 8: return String.loc("match_chapter_8")
            case 9: return String.loc("match_chapter_9")
            case 10: return String.loc("match_chapter_10")
            case 11: return String.loc("match_chapter_11")
            case 12: return String.loc("match_chapter_12")
            case 13: return String.loc("match_chapter_13")
            case 14: return String.loc("match_chapter_14")
            default: return String.loc("chapter_label", chapter)
            }
        }
        if track == "contradiction" {
            switch chapter {
            case 1: return String.loc("contradiction_chapter_1")
            case 2: return String.loc("contradiction_chapter_2")
            case 3: return String.loc("contradiction_chapter_3")
            case 4: return String.loc("contradiction_chapter_4")
            case 5: return String.loc("contradiction_chapter_5")
            case 6: return String.loc("contradiction_chapter_6")
            case 7: return String.loc("contradiction_chapter_7")
            case 8: return String.loc("contradiction_chapter_8")
            case 9: return String.loc("contradiction_chapter_9")
            case 10: return String.loc("contradiction_chapter_10")
            case 11: return String.loc("contradiction_chapter_11")
            case 12: return String.loc("contradiction_chapter_12")
            case 13: return String.loc("contradiction_chapter_13")
            default: return String.loc("chapter_label", chapter)
            }
        }
        if track == "logic" {
            switch chapter {
            case 1: return String.loc("logic_chapter_1")
            case 2: return String.loc("logic_chapter_2")
            case 3: return String.loc("logic_chapter_3")
            case 4: return String.loc("logic_chapter_4")
            case 5: return String.loc("logic_chapter_5")
            case 6: return String.loc("logic_chapter_6")
            case 7: return String.loc("logic_chapter_7")
            case 8: return String.loc("logic_chapter_8")
            case 9: return String.loc("logic_chapter_9")
            case 10: return String.loc("logic_chapter_10")
            case 11: return String.loc("logic_chapter_11")
            case 12: return String.loc("logic_chapter_12")
            case 13: return String.loc("logic_chapter_13")
            case 14: return String.loc("logic_chapter_14")
            default: return String.loc("chapter_label", chapter)
            }
        }
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
