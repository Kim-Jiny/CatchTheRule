import Foundation

/// 서버 없이 동작하는 임시 랭킹. 실제 Minigame 서버 결합 전 UI/흐름 개발용.
/// 제출한 점수는 프로세스 메모리에만 유지된다.
final class MockRankingService: RankingService {
    static let shared = MockRankingService()

    private actor Board {
        var scores: [(nickname: String, score: Int)] = [
            ("규칙왕", 42),
            ("패턴마스터", 38),
            ("수열요정", 35),
            ("두뇌풀가동", 31),
            ("초보탈출", 24),
            ("느긋한고양이", 19),
            ("퍼즐러", 16),
        ]

        func submit(nickname: String, score: Int) -> Int {
            if let i = scores.firstIndex(where: { $0.nickname == nickname }) {
                scores[i].score = max(scores[i].score, score)
            } else {
                scores.append((nickname, score))
            }
            scores.sort { $0.score > $1.score }
            return (scores.firstIndex { $0.nickname == nickname } ?? 0) + 1
        }

        func snapshot() -> [(nickname: String, score: Int)] {
            scores.sorted { $0.score > $1.score }
        }
    }

    private let board = Board()

    func submit(score: Int, nickname: String, mode: GameMode) async throws -> Int {
        try? await Task.sleep(nanoseconds: 400_000_000) // 네트워크 흉내
        return await board.submit(nickname: nickname, score: score)
    }

    func leaderboard(mode: GameMode) async throws -> [RankEntry] {
        try? await Task.sleep(nanoseconds: 300_000_000)
        let snap = await board.snapshot()
        return snap.enumerated().map { idx, item in
            // 샘플 랭킹은 한국 국기로 표시.
            RankEntry(rank: idx + 1, nickname: item.nickname, score: item.score, country: "KR")
        }
    }
}
