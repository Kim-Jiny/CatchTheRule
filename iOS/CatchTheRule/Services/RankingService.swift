import Foundation

/// 랭킹 백엔드 추상화. 실제 Minigame 서버 스펙이 확정되면
/// `MinigameRankingService` 를 구현해 교체한다. 그 전까지는 Mock 사용.
protocol RankingService {
    /// 점수 제출 후 내 순위를 반환.
    func submit(score: Int, nickname: String, mode: GameMode) async throws -> Int
    /// 리더보드 조회.
    func leaderboard(mode: GameMode) async throws -> Leaderboard
}

/// 리더보드 조회 결과. 월간 랭킹 메타(`period`/`resetsAt`)는 구버전 서버에선 nil.
struct Leaderboard {
    var entries: [RankEntry]
    /// KST 달력 월 "YYYY-MM".
    var period: String?
    /// 다음 초기화 시각(다음 달 1일 00:00 KST).
    var resetsAt: Date?
}

enum RankingError: Error {
    case notConfigured
    case server(String)
}

/// 앱 전역에서 사용할 현재 랭킹 서비스.
/// 실서버: MinigameRankingService() · 오프라인 개발용: MockRankingService.shared
enum Ranking {
    static let service: RankingService = MinigameRankingService()
}
