#if DEBUG
import Foundation

/// App Store 스크린샷 촬영용 DEBUG 전용 훅. 릴리스 빌드에는 포함되지 않는다.
/// 사용: `-ctr_scene home | challenge | play:<퍼즐 id>`
/// 켜지면 광고/UMP/ATT/애널리틱스/서버 갱신을 끄고, 진행도를 UserDefaults 에 시드하며, 가짜 랭킹을 쓴다.
enum ScreenshotMode {
    /// 원본 인자 값(없으면 nil = 일반 실행).
    static let scene: String? = {
        let args = CommandLine.arguments
        guard let i = args.firstIndex(of: "-ctr_scene"), args.indices.contains(i + 1) else { return nil }
        return args[i + 1]
    }()

    static var active: Bool { scene != nil }
    static var startsOnChallenge: Bool { scene == "challenge" }

    /// `play:<id>` 이면 (트랙, 시작 인덱스).
    static let play: (track: String, index: Int)? = {
        guard let scene, scene.hasPrefix("play:") else { return nil }
        let id = String(scene.dropFirst(5))
        guard let p = PuzzleStore.shared.puzzles.first(where: { $0.id == id }) else { return nil }
        let track = p.trackKey
        guard let idx = PuzzleStore.shared.puzzles(track: track).firstIndex(where: { $0.id == id }) else { return nil }
        return (track, idx)
    }()

    // MARK: - 시드

    /// 가짜 프로필.
    static let fakeNickname = "Player"
    static let fakeBest = 14

    /// 모드별 진행률(숫자는 100% — 모든 모드 해금).
    private static let fractions: [String: Double] = [
        "numbers": 1.0, "shapes": 0.42, "logic": 0.28, "contradiction": 0.55, "target": 0.35, "match": 0.18, "fragment": 0.1,
    ]
    private static let heroTrack = "target"

    /// ProgressStore 생성 전에 호출. 기존 키 이름은 ProgressStore.Keys 와 동일.
    static func seed() {
        guard active else { return }
        let d = UserDefaults.standard
        var indices: [String: Int] = [:]
        var stars: [String: Int] = [:]
        for track in PuzzleStore.shared.tracks {
            let list = PuzzleStore.shared.puzzles(track: track)
            let frac = fractions[track] ?? 0.3
            let n = Int((Double(list.count) * frac).rounded())
            indices[track] = n
            for (i, p) in list.prefix(n).enumerated() {
                stars[p.id] = i % 5 == 0 ? 2 : 3
            }
        }
        d.set(indices, forKey: "trackIndices")
        d.set(stars, forKey: "stars")
        d.set(30, forKey: "hintsRemaining")
        d.set(true, forKey: "shapesUnlockSeen")
        d.set(fakeNickname, forKey: "nickname")
        d.set(true, forKey: "soundOn")
        d.set(false, forKey: "hapticsOn")
        d.set(heroTrack, forKey: "lastPlayedTrack")
        d.set(fakeBest, forKey: "bestTimeAttack")
        d.set(KSTPeriod.current(), forKey: "bestTimeAttackPeriod")
    }

    // MARK: - 가짜 랭킹

    /// 실서버/실닉네임이 화면에 나오지 않도록 하는 고정 데이터. 동점 한 쌍(31) 포함, "Player"(14) 포함.
    final class FakeRanking: RankingService {
        private let rows: [(String, Int, String)] = [
            ("NeonFox", 42, "US"), ("QuietOwl", 38, "KR"), ("PixelPanda", 35, "JP"),
            ("RuleHunter", 31, "DE"), ("MintCat", 31, "FR"), ("Zenith", 27, "ES"),
            ("BluePine", 24, "BR"), ("Orbit", 21, "CN"), ("Tango", 19, "KR"),
            ("Lumi", 17, "US"), ("Player", 14, "KR"), ("Quokka", 12, "JP"),
            ("Pebble", 9, "DE"), ("Nova", 7, "FR"),
        ]

        func submit(score: Int, nickname: String, mode: GameMode) async throws -> Int { 11 }

        func leaderboard(mode: GameMode) async throws -> Leaderboard {
            var entries: [RankEntry] = []
            for (i, r) in rows.enumerated() {
                // 경쟁 순위: 동점은 같은 순위.
                let rank = (i > 0 && rows[i - 1].1 == r.1) ? entries[i - 1].rank : i + 1
                entries.append(RankEntry(rank: rank, nickname: r.0, score: r.1, country: r.2))
            }
            return Leaderboard(entries: entries, period: KSTPeriod.current(), resetsAt: KSTPeriod.resetDate())
        }
    }
}
#endif
