import Foundation

/// Minigame 서버(https://duo.jiny.shop)의 CatchTheRule 랭킹 API 결합.
///
///   POST /api/catchtherule/scores       { nickname, score, mode, deviceId } -> { rank }
///   GET  /api/catchtherule/leaderboard?mode=timeAttack&limit=100 -> { entries: [...] }
///
/// 로그인이 없으므로 인증 헤더 없이 호출하고, 기기당 1행 유지를 위해
/// 로컬에 영속화한 deviceId(UUID)를 함께 보낸다.
final class MinigameRankingService: RankingService {

    private let baseURL = URL(string: "https://duo.jiny.shop")!
    private let session: URLSession
    private let deviceID: String
    private let country: String?

    init(session: URLSession = .shared) {
        self.session = session
        self.deviceID = Self.resolveDeviceID()
        self.country = Self.resolveCountry()
    }

    // MARK: - RankingService

    func submit(score: Int, nickname: String, mode: GameMode) async throws -> Int {
        let url = baseURL.appendingPathComponent("api/catchtherule/scores")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(
            SubmitBody(nickname: nickname, score: score, mode: mode.serverKey,
                       deviceId: deviceID, country: country)
        )

        let (data, response) = try await session.data(for: request)
        try Self.check(response, data)
        return try JSONDecoder().decode(SubmitResponse.self, from: data).rank
    }

    func leaderboard(mode: GameMode) async throws -> [RankEntry] {
        var comps = URLComponents(
            url: baseURL.appendingPathComponent("api/catchtherule/leaderboard"),
            resolvingAgainstBaseURL: false
        )!
        comps.queryItems = [
            URLQueryItem(name: "mode", value: mode.serverKey),
            URLQueryItem(name: "limit", value: "100"),
        ]
        var request = URLRequest(url: comps.url!)
        request.httpMethod = "GET"

        let (data, response) = try await session.data(for: request)
        try Self.check(response, data)
        let decoded = try JSONDecoder().decode(LeaderboardResponse.self, from: data)
        return decoded.entries.map {
            RankEntry(rank: $0.rank, nickname: $0.nickname, score: $0.score, country: $0.country)
        }
    }

    // MARK: - Helpers

    private static func check(_ response: URLResponse, _ data: Data) throws {
        guard let http = response as? HTTPURLResponse else { return }
        guard (200..<300).contains(http.statusCode) else {
            let message = (try? JSONDecoder().decode(ErrorResponse.self, from: data))?.error
                ?? "HTTP \(http.statusCode)"
            throw RankingError.server(message)
        }
    }

    /// 기기 식별자(UUID). 문의 서비스와 동일한 값을 공유한다.
    private static func resolveDeviceID() -> String { CTRDevice.id }

    /// 기기 지역(ISO 3166-1 alpha-2, 예: "KR"). 랭킹 국기 표시용.
    private static func resolveCountry() -> String? {
        if #available(iOS 16, *) {
            return Locale.current.region?.identifier
        }
        return Locale.current.regionCode
    }

    // MARK: - DTO

    private struct SubmitBody: Encodable {
        let nickname: String
        let score: Int
        let mode: String
        let deviceId: String
        let country: String?
    }
    private struct SubmitResponse: Decodable {
        let rank: Int
    }
    private struct LeaderboardResponse: Decodable {
        let entries: [Entry]
        struct Entry: Decodable {
            let rank: Int
            let nickname: String
            let score: Int
            let country: String?
        }
    }
    private struct ErrorResponse: Decodable {
        let error: String
    }
}
