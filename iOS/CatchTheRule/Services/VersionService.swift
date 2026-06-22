import Foundation
import Observation

/// 앱 버전 확인. 내 버전(번들)과 App Store 최신 버전(iTunes lookup)을 비교한다.
/// 스토어 미등록/오프라인이면 storeVersion 은 nil 로 남고 안내는 표시하지 않는다.
@Observable
final class VersionService {
    /// 번들의 현재 버전(CFBundleShortVersionString).
    let current: String =
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"

    private(set) var store: String?       // App Store 최신 버전(확인되면 채워짐)
    private(set) var storeURL: String?    // App Store 제품 페이지
    private(set) var checked = false      // 1회 조회 완료 여부

    /// 스토어가 내 버전보다 최신이면 true → "새 문제 업데이트" 안내.
    var updateAvailable: Bool {
        guard let store else { return false }
        return Self.isNewer(store, than: current)
    }

    func checkOnce() async {
        guard !checked else { return }
        guard let bundleID = Bundle.main.bundleIdentifier else { checked = true; return }
        let region = Locale.current.region?.identifier ?? "US"
        guard let url = URL(string:
            "https://itunes.apple.com/lookup?bundleId=\(bundleID)&country=\(region)&t=\(Int(Date().timeIntervalSince1970))")
        else { checked = true; return }

        struct Lookup: Decodable {
            let results: [Item]
            struct Item: Decodable { let version: String; let trackViewUrl: String? }
        }
        if let (data, resp) = try? await URLSession.shared.data(from: url),
           (resp as? HTTPURLResponse)?.statusCode == 200,
           let decoded = try? JSONDecoder().decode(Lookup.self, from: data),
           let first = decoded.results.first {
            store = first.version
            storeURL = first.trackViewUrl
        }
        checked = true
    }

    /// "1.0.10" > "1.0.2" 처럼 각 자리를 숫자로 비교.
    static func isNewer(_ a: String, than b: String) -> Bool {
        let pa = a.split(separator: ".").map { Int($0) ?? 0 }
        let pb = b.split(separator: ".").map { Int($0) ?? 0 }
        for i in 0..<max(pa.count, pb.count) {
            let x = i < pa.count ? pa[i] : 0
            let y = i < pb.count ? pb[i] : 0
            if x != y { return x > y }
        }
        return false
    }
}
