import Foundation
import UIKit

/// 익명 디바이스 핑(유저 카운팅/통계용). 로그인 없이 공유 deviceId 로 집계.
/// 앱 실행 시 1회 fire-and-forget 호출. 실패해도 앱 동작에 영향 없음.
enum AnalyticsService {
    private static let baseURL = URL(string: "https://duo.jiny.shop")!

    static func ping() {
        let body = PingBody(
            deviceId: CTRDevice.id,
            platform: "ios",
            appVersion: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String,
            osVersion: UIDevice.current.systemVersion,
            country: country()
        )
        guard let data = try? JSONEncoder().encode(body) else { return }
        var request = URLRequest(url: baseURL.appendingPathComponent("api/catchtherule/devices/ping"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = data
        URLSession.shared.dataTask(with: request).resume()
    }

    private static func country() -> String? {
        if #available(iOS 16, *) { return Locale.current.region?.identifier }
        return Locale.current.regionCode
    }

    private struct PingBody: Encodable {
        let deviceId: String
        let platform: String
        let appVersion: String?
        let osVersion: String?
        let country: String?
    }
}
