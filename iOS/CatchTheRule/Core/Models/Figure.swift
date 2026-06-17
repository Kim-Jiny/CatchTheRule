import Foundation

/// "도형에서 규칙찾기" 트랙에서 직접 그려 표현하는 도형 1개의 명세.
///
/// 두 가지 용도로 쓰인다:
/// - 숫자형(keypad): `slots` 에 꼭짓점/세그먼트/중앙 숫자를 담고, `nil` 인 칸이 빈칸(정답).
///   레이아웃은 `shape` + `slots.count` 로 결정한다. (예: triangle+3=세 꼭짓점, +4=중앙 포함)
/// - 시각형(choices): `rotation`/`filled`/`count` 로 순수 시각 규칙을 표현하고,
///   보기 식별은 `code`(== 퍼즐 `answer`) 로 채점한다.
struct Figure: Codable, Equatable {
    let shape: String          // "triangle" | "square" | "circle" | "arrow" | "dot"
    /// 숫자형 슬롯(시계방향, nil = 빈칸). 레이아웃은 shape + count 로 결정:
    /// triangle 3=세 꼭짓점, 4=꼭짓점+중앙 / square 4=네 모서리, 5=모서리+중앙 / circle N=N세그먼트.
    let slots: [String?]?
    let rotation: Int?         // 시각형: 회전(도)
    let filled: Bool?          // 시각형: 채움(true)/외곽선(false)
    let count: Int?            // 시각형: 같은 도형 N개 반복
    let code: String?          // 시각형 보기 식별자

    var rotationDegrees: Double { Double(rotation ?? 0) }
    var isFilled: Bool { filled ?? true }
    var repeatCount: Int { max(1, count ?? 1) }

    /// 숫자형 도형 여부(슬롯이 있으면 숫자형).
    var hasSlots: Bool { (slots?.isEmpty == false) }
}
