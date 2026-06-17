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

    static let defaultTrack = "numbers"

    /// 트랙별 퍼즐(이미 (챕터,순서) 정렬됨). 캠페인 인덱스는 이 배열 기준.
    func puzzles(track: String = defaultTrack) -> [Puzzle] {
        puzzles.filter { $0.trackKey == track }
    }

    /// 존재하는 트랙 목록(numbers 우선, 등장 순).
    var tracks: [String] {
        var seen = Set<String>()
        var result: [String] = []
        for p in puzzles where seen.insert(p.trackKey).inserted { result.append(p.trackKey) }
        return result
    }

    func totalCount(track: String = defaultTrack) -> Int { puzzles(track: track).count }

    func chapters(track: String = defaultTrack) -> [Int] {
        Array(Set(puzzles(track: track).map(\.chapter))).sorted()
    }

    func puzzles(in chapter: Int, track: String = defaultTrack) -> [Puzzle] {
        puzzles(track: track).filter { $0.chapter == chapter }
    }

    func puzzle(at index: Int, track: String = defaultTrack) -> Puzzle? {
        let list = puzzles(track: track)
        return list.indices.contains(index) ? list[index] : nil
    }

    func position(of index: Int, track: String = defaultTrack) -> (chapter: Int, stage: Int)? {
        guard let p = puzzle(at: index, track: track) else { return nil }
        let stage = puzzles(in: p.chapter, track: track).firstIndex(where: { $0.id == p.id }).map { $0 + 1 } ?? 1
        return (p.chapter, stage)
    }
}
