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
    let track: String?        // "numbers"(기본/nil) | "shapes". 모드 구분.
    let chapter: Int
    let order: Int
    let tokens: [String?]?    // 단일 행. nil 인 항이 빈칸. (grid 사용 시 생략)
    let grid: [[String?]]?    // 다중 행/매트릭스/수식형. 한 칸이 nil(빈칸).
    let figures: [Figure]?           // 숫자형 도형(예시 도형 여러 개를 한 줄에, 마지막에 빈칸 슬롯)
    let figureTokens: [Figure?]?     // 시각형 시퀀스. nil = 빈칸 셀.
    let figureChoices: [Figure]?     // 시각형 보기(도형 보기)
    let prompt: [String: String]?    // 논리형 질문 문단(로케일코드 → 문장). 있으면 수열 대신 질문을 표시.
    let statements: [String: [String]]?  // 모순찾기 문장 목록(로케일코드 → 문장 배열). 번호 매긴 카드로 표시, answer=모순 문장 번호(1-based).
    let answer: String
    let inputType: InputType
    let choices: [String]?
    let hints: [String: [String]]        // 로케일코드 → 힌트 3개
    let explanation: [String: String]    // 로케일코드 → 해설

    /// 소속 트랙(없으면 기본 캠페인 "numbers").
    var trackKey: String { track ?? "numbers" }

    /// 격자형 퍼즐 여부.
    var isGrid: Bool { (grid?.isEmpty == false) }

    /// 숫자형 도형(여러 예시) 퍼즐 여부.
    var isFigure: Bool { (figures?.isEmpty == false) }

    /// 도형 시퀀스(시각형) 퍼즐 여부.
    var isFigureSequence: Bool { (figureTokens?.isEmpty == false) }

    /// 논리형(질문 문단) 퍼즐 여부.
    var isPrompt: Bool { (prompt?.isEmpty == false) }

    /// 모순찾기(문장 목록) 퍼즐 여부.
    var isContradiction: Bool { (statements?.isEmpty == false) }

    /// 현재 언어의 문장 목록(없으면 영어 → 임의 폴백).
    var localizedStatements: [String] {
        let code = Locale.current.language.languageCode?.identifier ?? "en"
        return statements?[code] ?? statements?["en"] ?? statements?.values.first ?? []
    }

    /// 현재 언어의 질문 문단(없으면 영어 → 임의 폴백).
    var localizedPrompt: String {
        let code = Locale.current.language.languageCode?.identifier ?? "en"
        return prompt?[code] ?? prompt?["en"] ?? prompt?.values.first ?? ""
    }

    /// 현재 언어의 힌트(없으면 영어 → 임의 폴백).
    var localizedHints: [String] {
        let code = Locale.current.language.languageCode?.identifier ?? "en"
        return hints[code] ?? hints["en"] ?? hints.values.first ?? []
    }

    /// 현재 언어의 해설.
    var localizedExplanation: String {
        let code = Locale.current.language.languageCode?.identifier ?? "en"
        return explanation[code] ?? explanation["en"] ?? ""
    }

    func isCorrect(_ value: String) -> Bool {
        value.trimmingCharacters(in: .whitespaces) == answer
    }
}
