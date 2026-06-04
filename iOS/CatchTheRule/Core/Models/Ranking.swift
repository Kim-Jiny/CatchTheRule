import Foundation

enum GameMode: String, Codable, CaseIterable {
    case timeAttack
    var serverKey: String { rawValue }
}

/// 랭킹 보드의 한 줄.
struct RankEntry: Identifiable, Codable, Equatable {
    let rank: Int
    let nickname: String
    let score: Int
    var country: String? = nil   // ISO 3166-1 alpha-2 (예: "KR")
    var isMe: Bool = false

    var id: String { "\(rank)-\(nickname)" }

    /// 국가코드 → 국기 이모지 (예: "KR" → 🇰🇷). 없거나 형식이 틀리면 nil.
    var flagEmoji: String? { Self.flag(from: country) }

    static func flag(from code: String?) -> String? {
        guard let code, code.count == 2 else { return nil }
        let base: UInt32 = 127397   // 0x1F1E6 - 'A'
        var result = ""
        for scalar in code.uppercased().unicodeScalars {
            guard scalar.value >= 65, scalar.value <= 90,
                  let flagScalar = UnicodeScalar(base + scalar.value) else { return nil }
            result.unicodeScalars.append(flagScalar)
        }
        return result
    }
}
