import Foundation

/// 타임어택 출제 덱. UI 와 분리된 순수 로직.
/// - 모드는 균등 확률로 고르되 같은 모드가 3번 연속 나오지 않게 보정
/// - 모드별 셔플 리스트를 순서대로 소진(다 쓰면 재셔플) → 한 판에서 중복 출제 없음(소진 시에만 재순환)
struct TimeAttackDeck {
    private var lists: [String: [Puzzle]]
    private var cursors: [String: Int] = [:]
    private var recent: [String] = []
    private var rng: any RandomNumberGenerator

    /// 모드별 쉬운 앞쪽 절반 챕터만: chapter <= ceil(최대 챕터 / 2)
    static func easyHalf(_ puzzles: [Puzzle]) -> [Puzzle] {
        let maxChapter = puzzles.map(\.chapter).max() ?? 0
        let limit = (maxChapter + 1) / 2
        return puzzles.filter { $0.chapter <= limit }
    }

    /// 해금 여부에 따른 모드 목록. 해금 전에는 numbers 하나뿐.
    static func tracks(unlocked: Bool) -> [String] {
        unlocked ? PuzzleStore.trackOrder : [PuzzleStore.defaultTrack]
    }

    /// pools: 모드 → 전체 퍼즐(필터 전). 빈 모드는 제외.
    init(pools: [String: [Puzzle]], rng: any RandomNumberGenerator = SystemRandomNumberGenerator()) {
        self.rng = rng
        var result: [String: [Puzzle]] = [:]
        for (track, list) in pools {
            let easy = Self.easyHalf(list)
            if !easy.isEmpty { result[track] = easy }
        }
        lists = result
        for track in result.keys {
            lists[track]!.shuffle(using: &self.rng)
            cursors[track] = 0
        }
    }

    init(unlocked: Bool, store: PuzzleStore = .shared) {
        var pools: [String: [Puzzle]] = [:]
        for t in Self.tracks(unlocked: unlocked) { pools[t] = store.puzzles(track: t) }
        self.init(pools: pools)
    }

    var isEmpty: Bool { lists.isEmpty }

    mutating func nextPuzzle() -> Puzzle? {
        guard !lists.isEmpty else { return nil }
        var candidates = lists.keys.sorted()
        if candidates.count > 1, recent.count >= 2, recent[recent.count - 1] == recent[recent.count - 2] {
            candidates.removeAll { $0 == recent[recent.count - 1] }
        }
        guard let track = candidates.randomElement(using: &rng) else { return nil }
        recent.append(track)
        if recent.count > 2 { recent.removeFirst() }

        var list = lists[track]!
        var cursor = cursors[track] ?? 0
        if cursor >= list.count {
            let last = list.last
            list.shuffle(using: &rng)
            // 재셔플 직후 바로 같은 문제가 나오지 않게
            if list.count > 1, list.first?.id == last?.id { list.swapAt(0, list.count - 1) }
            lists[track] = list
            cursor = 0
        }
        cursors[track] = cursor + 1
        return list[cursor]
    }
}
