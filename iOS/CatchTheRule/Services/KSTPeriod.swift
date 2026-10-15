import Foundation

/// 월간 랭킹 기간 계산. 서버와 동일하게 KST(UTC+9 고정, DST 없음) 달력 월 기준.
/// 기기 시간대와 무관하게 동작하도록 고정 오프셋 캘린더를 사용한다.
enum KSTPeriod {
    private static let calendar: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(secondsFromGMT: 9 * 3600)!
        return c
    }()

    /// `date` 가 속한 KST 월 "YYYY-MM".
    static func current(_ date: Date = Date()) -> String {
        let c = calendar.dateComponents([.year, .month], from: date)
        return String(format: "%04d-%02d", c.year ?? 1970, c.month ?? 1)
    }

    /// `date` 다음 달 1일 00:00 KST 시각(= 현 기간 종료 시점).
    static func resetDate(after date: Date = Date()) -> Date {
        let start = calendar.date(from: calendar.dateComponents([.year, .month], from: date)) ?? date
        return calendar.date(byAdding: .month, value: 1, to: start) ?? date
    }

    /// `resetsAt` 까지 남은 일수(올림, 최소 0). 0 이면 오늘(마지막 날) 초기화.
    static func daysUntil(_ resetsAt: Date, from now: Date = Date()) -> Int {
        let sec = resetsAt.timeIntervalSince(now)
        return max(0, Int((sec / 86_400).rounded(.up)))
    }
}
