import Foundation

/// 퍼즐 제공자.
/// - 기본 11챕터: 앱 번들 puzzles.json
/// - 추가 스테이지: 서버(/api/catchtherule/puzzles)에서 받아 캐시. 오프라인이면 캐시/번들로 폴백.
/// 서버 갱신은 캐시에 저장되어 **다음 실행**에 반영된다(세션 중 안전).
final class PuzzleStore {
    static let shared = PuzzleStore()

    private(set) var puzzles: [Puzzle]

    private init() {
        puzzles = Self.merge(base: Self.loadBundled(), extra: Self.loadCached())
    }

    // MARK: - Load

    private static func loadBundled() -> [Puzzle] {
        guard
            let url = Bundle.main.url(forResource: "puzzles", withExtension: "json"),
            let data = try? Data(contentsOf: url),
            let decoded = try? JSONDecoder().decode([Puzzle].self, from: data)
        else {
            assertionFailure("puzzles.json 을 로드하지 못했습니다.")
            return []
        }
        return decoded
    }

    private static var cacheURL: URL {
        let dir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        return dir.appendingPathComponent("ctr_server_puzzles.json")
    }

    private static func loadCached() -> [Puzzle] {
        guard
            let data = try? Data(contentsOf: cacheURL),
            let decoded = try? JSONDecoder().decode([Puzzle].self, from: data)
        else { return [] }
        return decoded
    }

    /// 번들 + 추가분 병합(번들 우선, id 중복 제외) 후 (챕터, 순서) 정렬.
    private static func merge(base: [Puzzle], extra: [Puzzle]) -> [Puzzle] {
        var seen = Set(base.map(\.id))
        var all = base
        for p in extra where !seen.contains(p.id) {
            all.append(p)
            seen.insert(p.id)
        }
        return all.sorted { ($0.chapter, $0.order) < ($1.chapter, $1.order) }
    }

    /// 서버에서 추가 스테이지를 받아 캐시에 저장. (다음 실행에 반영)
    static func refreshFromServer() async {
        let url = URL(string: "https://duo.jiny.shop/api/catchtherule/puzzles")!
        guard
            let (data, resp) = try? await URLSession.shared.data(from: url),
            (resp as? HTTPURLResponse)?.statusCode == 200
        else { return }
        struct Payload: Decodable { let puzzles: [Puzzle] }
        // 디코드 성공 = 스키마 유효. 실패 시 캐시 미변경(폴백 유지).
        guard let payload = try? JSONDecoder().decode(Payload.self, from: data),
              let encoded = try? JSONEncoder().encode(payload.puzzles)
        else { return }
        try? encoded.write(to: cacheURL, options: .atomic)
    }

    // MARK: - Access

    var totalCount: Int { puzzles.count }

    var chapters: [Int] { Array(Set(puzzles.map(\.chapter))).sorted() }

    func puzzles(in chapter: Int) -> [Puzzle] {
        puzzles.filter { $0.chapter == chapter }
    }

    func puzzle(at globalIndex: Int) -> Puzzle? {
        puzzles.indices.contains(globalIndex) ? puzzles[globalIndex] : nil
    }

    func position(of globalIndex: Int) -> (chapter: Int, stage: Int)? {
        guard let p = puzzle(at: globalIndex) else { return nil }
        let stage = puzzles(in: p.chapter).firstIndex(where: { $0.id == p.id }).map { $0 + 1 } ?? 1
        return (p.chapter, stage)
    }
}
