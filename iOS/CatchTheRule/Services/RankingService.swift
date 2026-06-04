import Foundation

/// 랭킹 백엔드 추상화. 실제 Minigame 서버 스펙이 확정되면
/// `MinigameRankingService` 를 구현해 교체한다. 그 전까지는 Mock 사용.
protocol RankingService {
    /// 점수 제출 후 내 순위를 반환.
    func submit(score: Int, nickname: String, mode: GameMode) async throws -> Int
    /// 리더보드 조회.
    func leaderboard(mode: GameMode) async throws -> [RankEntry]
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
