import Foundation

/// 번들된 puzzles.json 을 로드해 제공한다. 서버 불필요.
final class PuzzleStore {
    static let shared = PuzzleStore()

    let puzzles: [Puzzle]

    private init() {
        guard
            let url = Bundle.main.url(forResource: "puzzles", withExtension: "json"),
            let data = try? Data(contentsOf: url),
            let decoded = try? JSONDecoder().decode([Puzzle].self, from: data)
        else {
            assertionFailure("puzzles.json 을 로드하지 못했습니다.")
            puzzles = []
            return
        }
        puzzles = decoded.sorted { ($0.chapter, $0.order) < ($1.chapter, $1.order) }
    }

    var totalCount: Int { puzzles.count }

    var chapters: [Int] {
        Array(Set(puzzles.map(\.chapter))).sorted()
    }

    func puzzles(in chapter: Int) -> [Puzzle] {
        puzzles.filter { $0.chapter == chapter }
    }

    /// 캠페인 진행용 전역 인덱스 접근.
    func puzzle(at globalIndex: Int) -> Puzzle? {
        puzzles.indices.contains(globalIndex) ? puzzles[globalIndex] : nil
    }

    /// 전역 인덱스 → (챕터, 챕터 내 순번) 표시용.
    func position(of globalIndex: Int) -> (chapter: Int, stage: Int)? {
        guard let p = puzzle(at: globalIndex) else { return nil }
        let stage = puzzles(in: p.chapter).firstIndex(where: { $0.id == p.id }).map { $0 + 1 } ?? 1
        return (p.chapter, stage)
    }
}
