import Foundation

/// CatchTheRule 문의(로그인 없음, deviceId 기반).
struct Inquiry: Identifiable, Decodable, Equatable {
    let id: Int
    let content: String
    let status: String
    let reply: String?
    let repliedAt: String?
    let createdAt: String?

    enum CodingKeys: String, CodingKey {
        case id, content, status, reply
        case repliedAt = "replied_at"
        case createdAt = "created_at"
    }

    var isReplied: Bool { status == "replied" }
}

/// 랭킹·문의가 공유하는 기기 식별자(영속 UUID). 개인정보 비식별.
enum CTRDevice {
    private static let lock = NSLock()

    // 최초 실행 시 analytics·ranking·IAP 검증이 동시에 id 를 읽으면 서로 다른 UUID 를
    // 생성·저장할 수 있다(중복 기기 행). 락으로 read-then-write 를 원자화한다.
    static var id: String {
        lock.lock()
        defer { lock.unlock() }
        let key = "ctr_device_id"
        let defaults = UserDefaults.standard
        if let existing = defaults.string(forKey: key) { return existing }
        let id = UUID().uuidString
        defaults.set(id, forKey: key)
        return id
    }
}

final class InquiryService {
    static let shared = InquiryService()

    private let baseURL = URL(string: "https://duo.jiny.shop")!
    private let session = URLSession.shared

    func submit(content: String, nickname: String?) async throws {
        let url = baseURL.appendingPathComponent("api/catchtherule/inquiries")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(
            SubmitBody(deviceId: CTRDevice.id, nickname: nickname, content: content)
        )
        let (data, response) = try await session.data(for: request)
        try Self.check(response, data)
    }

    func myInquiries() async throws -> [Inquiry] {
        var comps = URLComponents(
            url: baseURL.appendingPathComponent("api/catchtherule/inquiries"),
            resolvingAgainstBaseURL: false
        )!
        comps.queryItems = [URLQueryItem(name: "deviceId", value: CTRDevice.id)]
        let (data, response) = try await session.data(from: comps.url!)
        try Self.check(response, data)
        return try JSONDecoder().decode(ListResponse.self, from: data).inquiries
    }

    private static func check(_ response: URLResponse, _ data: Data) throws {
        guard let http = response as? HTTPURLResponse else {
            throw RankingError.server("Invalid response")
        }
        guard (200..<300).contains(http.statusCode) else {
            let message = (try? JSONDecoder().decode(ErrorResponse.self, from: data))?.error
                ?? "HTTP \(http.statusCode)"
            throw RankingError.server(message)
        }
    }

    private struct SubmitBody: Encodable {
        let deviceId: String
        let nickname: String?
        let content: String
    }
    private struct ListResponse: Decodable { let inquiries: [Inquiry] }
    private struct ErrorResponse: Decodable { let error: String }
}
