import Foundation
import Observation

/// 진행도/설정의 단일 소스. UserDefaults 에 영속화. (로그인/서버 불필요)
@Observable
final class ProgressStore {

    /// 트랙별 "다음에 풀 인덱스"(인덱스는 해당 트랙 퍼즐 배열 기준). 키=트랙.
    var indices: [String: Int] { didSet { defaults.set(indices, forKey: Keys.indices) } }
    /// 퍼즐 id → 별 개수(0...3). (트랙 공유 — id가 트랙별로 고유)
    var stars: [String: Int] { didSet { defaults.set(stars, forKey: Keys.stars) } }
    var hintsRemaining: Int { didSet { defaults.set(hintsRemaining, forKey: Keys.hints) } }
    /// 도형규칙 해금 알림을 본 적 있는지(1회성).
    var shapesUnlockSeen: Bool { didSet { defaults.set(shapesUnlockSeen, forKey: Keys.shapesUnlockSeen) } }
    var nickname: String { didSet { defaults.set(nickname, forKey: Keys.nickname) } }
    var soundOn: Bool { didSet { defaults.set(soundOn, forKey: Keys.sound) } }
    var hapticsOn: Bool { didSet { defaults.set(hapticsOn, forKey: Keys.haptics) } }
    var bestTimeAttack: Int { didSet { defaults.set(bestTimeAttack, forKey: Keys.best) } }

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        // 트랙별 인덱스 로드. 없으면 구버전 단일 currentIndex 를 numbers 로 1회 마이그레이션.
        if let saved = defaults.dictionary(forKey: Keys.indices) as? [String: Int] {
            indices = saved
        } else {
            let legacy = defaults.integer(forKey: Keys.currentIndex)
            indices = legacy > 0 ? [PuzzleStore.defaultTrack: legacy] : [:]
        }
        stars = (defaults.dictionary(forKey: Keys.stars) as? [String: Int]) ?? [:]
        hintsRemaining = defaults.object(forKey: Keys.hints) as? Int ?? Self.initialHintGrant
        shapesUnlockSeen = defaults.bool(forKey: Keys.shapesUnlockSeen)
        nickname = defaults.string(forKey: Keys.nickname) ?? ""
        soundOn = defaults.object(forKey: Keys.sound) as? Bool ?? true
        hapticsOn = defaults.object(forKey: Keys.haptics) as? Bool ?? true
        bestTimeAttack = defaults.integer(forKey: Keys.best)
    }

    // MARK: - Derived

    var totalStars: Int { stars.values.reduce(0, +) }

    /// 트랙의 다음에 풀 인덱스.
    func currentIndex(track: String = PuzzleStore.defaultTrack) -> Int { indices[track] ?? 0 }

    /// 트랙의 최대 별 수.
    func maxStars(track: String = PuzzleStore.defaultTrack) -> Int {
        PuzzleStore.shared.totalCount(track: track) * 3
    }

    /// 트랙에서 획득한 별 수.
    func earnedStars(track: String = PuzzleStore.defaultTrack) -> Int {
        PuzzleStore.shared.puzzles(track: track).reduce(0) { $0 + (stars[$1.id] ?? 0) }
    }

    func isCampaignFinished(track: String = PuzzleStore.defaultTrack) -> Bool {
        currentIndex(track: track) >= PuzzleStore.shared.totalCount(track: track)
    }

    func progressFraction(track: String = PuzzleStore.defaultTrack) -> Double {
        let total = PuzzleStore.shared.totalCount(track: track)
        guard total > 0 else { return 0 }
        return min(1, Double(currentIndex(track: track)) / Double(total))
    }

    func starCount(for puzzleID: String) -> Int { stars[puzzleID] ?? 0 }

    /// 도형규칙 해금 여부 = 숫자규칙 챕터 1~SHAPES_UNLOCK_CHAPTERS 전부 클리어.
    var isShapesUnlocked: Bool {
        let needed = PuzzleStore.shared.puzzles(track: "numbers")
            .filter { $0.chapter <= Self.shapesUnlockChapters }.count
        return needed > 0 && currentIndex(track: "numbers") >= needed
    }

    // MARK: - Mutations

    /// 캠페인에서 퍼즐을 맞혔을 때 호출. 별을 갱신하고 해당 트랙 진행을 전진시킨다.
    func recordCampaignClear(puzzle: Puzzle, atIndex index: Int, earnedStars: Int) {
        let best = max(stars[puzzle.id] ?? 0, earnedStars)
        stars[puzzle.id] = best
        // 이미 풀었던 단계를 재도전한 경우 진행 인덱스는 건드리지 않는다.
        let track = puzzle.trackKey
        if index >= currentIndex(track: track) {
            indices[track] = index + 1
        }
    }

    func spendHint() -> Bool {
        guard hintsRemaining > 0 else { return false }
        hintsRemaining -= 1
        return true
    }

    func resetProgress() {
        indices = [:]
        stars = [:]
        hintsRemaining = Self.initialHintGrant
        bestTimeAttack = 0
        shapesUnlockSeen = false
        defaults.removeObject(forKey: Keys.currentIndex)   // 구버전 키도 정리
    }

    static let initialHintGrant = 3
    /// 도형규칙 해금에 필요한 숫자규칙 클리어 챕터 수.
    static let shapesUnlockChapters = 5

    private enum Keys {
        static let currentIndex = "currentIndex"   // 구버전(마이그레이션 소스)
        static let indices = "trackIndices"
        static let shapesUnlockSeen = "shapesUnlockSeen"
        static let stars = "stars"
        static let hints = "hintsRemaining"
        static let nickname = "nickname"
        static let sound = "soundOn"
        static let haptics = "hapticsOn"
        static let best = "bestTimeAttack"
    }
}
