import Foundation

/// Localizable.xcstrings 키로 현재 언어 문자열을 가져온다.
/// - `String.loc("home_continue")`            → "Continue" / "이어하기" ...
/// - `String.loc("ta_score", score)`          → 포맷 인자 적용 (%lld / %1$lld)
extension String {
    static func loc(_ key: String) -> String {
        String(localized: String.LocalizationValue(key))
    }

    static func loc(_ key: String, _ args: CVarArg...) -> String {
        String(format: String(localized: String.LocalizationValue(key)), arguments: args)
    }
}
