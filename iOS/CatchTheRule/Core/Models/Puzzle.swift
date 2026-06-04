import Foundation

enum InputType: String, Codable {
    case keypad     // 숫자 직접 입력
    case choices    // 4지선다
}

/// 하나의 규칙찾기 퍼즐. 번들된 puzzles.json 으로부터 디코딩된다.
///
/// 토큰/정답을 문자열로 다뤄 숫자·문자(A,B,C)·모양(이모지) 퍼즐을
/// 하나의 스키마로 통합한다. (`type` 은 표시용 분류로만 사용)
struct Puzzle: Codable, Identifiable, Equatable {
    let id: String
    let type: String
    let chapter: Int
    let order: Int
    let tokens: [String?]?    // 단일 행. nil 인 항이 빈칸. (grid 사용 시 생략)
    let grid: [[String?]]?    // 다중 행/매트릭스/수식형. 한 칸이 nil(빈칸).
    let answer: String
    let inputType: InputType
    let choices: [String]?
    let hints: [String]
    let explanation: String

    /// 격자형 퍼즐 여부.
    var isGrid: Bool { (grid?.isEmpty == false) }

    func isCorrect(_ value: String) -> Bool {
        value.trimmingCharacters(in: .whitespaces) == answer
    }
}
