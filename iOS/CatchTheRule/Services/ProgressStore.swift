import Foundation
import Observation

/// 진행도/설정의 단일 소스. UserDefaults 에 영속화. (로그인/서버 불필요)
@Observable
final class ProgressStore {

    /// 캠페인에서 다음에 풀 전역 퍼즐 인덱스.
    var currentIndex: Int { didSet { defaults.set(currentIndex, forKey: Keys.currentIndex) } }
    /// 퍼즐 id → 별 개수(0...3).
    var stars: [String: Int] { didSet { defaults.set(stars, forKey: Keys.stars) } }
    var hintsRemaining: Int { didSet { defaults.set(hintsRemaining, forKey: Keys.hints) } }
    var nickname: String { didSet { defaults.set(nickname, forKey: Keys.nickname) } }
    var soundOn: Bool { didSet { defaults.set(soundOn, forKey: Keys.sound) } }
    var hapticsOn: Bool { didSet { defaults.set(hapticsOn, forKey: Keys.haptics) } }
    var bestTimeAttack: Int { didSet { defaults.set(bestTimeAttack, forKey: Keys.best) } }

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        currentIndex = defaults.integer(forKey: Keys.currentIndex)
        stars = (defaults.dictionary(forKey: Keys.stars) as? [String: Int]) ?? [:]
        hintsRemaining = defaults.object(forKey: Keys.hints) as? Int ?? Self.dailyHintGrant
        nickname = defaults.string(forKey: Keys.nickname) ?? ""
        soundOn = defaults.object(forKey: Keys.sound) as? Bool ?? true
        hapticsOn = defaults.object(forKey: Keys.haptics) as? Bool ?? true
        bestTimeAttack = defaults.integer(forKey: Keys.best)
    }

    // MARK: - Derived

    var totalStars: Int { stars.values.reduce(0, +) }
    var maxStars: Int { PuzzleStore.shared.totalCount * 3 }
    var isCampaignFinished: Bool { currentIndex >= PuzzleStore.shared.totalCount }

    var progressFraction: Double {
        let total = PuzzleStore.shared.totalCount
        guard total > 0 else { return 0 }
        return min(1, Double(currentIndex) / Double(total))
    }

    func starCount(for puzzleID: String) -> Int { stars[puzzleID] ?? 0 }

    // MARK: - Mutations

    /// 캠페인에서 퍼즐을 맞혔을 때 호출. 별을 갱신하고 진행을 전진시킨다.
    func recordCampaignClear(puzzle: Puzzle, atGlobalIndex index: Int, earnedStars: Int) {
        let best = max(stars[puzzle.id] ?? 0, earnedStars)
        stars[puzzle.id] = best
        // 이미 풀었던 단계를 재도전한 경우 진행 인덱스는 건드리지 않는다.
        if index >= currentIndex {
            currentIndex = index + 1
        }
    }

    func spendHint() -> Bool {
        guard hintsRemaining > 0 else { return false }
        hintsRemaining -= 1
        return true
    }

    func resetProgress() {
        currentIndex = 0
        stars = [:]
        hintsRemaining = Self.dailyHintGrant
        bestTimeAttack = 0
    }

    static let dailyHintGrant = 5

    private enum Keys {
        static let currentIndex = "currentIndex"
        static let stars = "stars"
        static let hints = "hintsRemaining"
        static let nickname = "nickname"
        static let sound = "soundOn"
        static let haptics = "hapticsOn"
        static let best = "bestTimeAttack"
    }
}
