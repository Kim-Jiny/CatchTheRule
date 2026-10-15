import Foundation

/// 성냥개비 퍼즐 순수 로직(UI 무관). 명세: docs/match-mode-spec.md
///
/// 셀 종류는 고정: 숫자 셀 = 7슬롯(a b c d e f g → 0...6), 연산자 셀 = 3슬롯(hu hl v → 0...2).
struct MatchBoard: Equatable {
    struct Slot: Hashable { let cell: Int; let idx: Int }

    enum DropResult: Equatable { case placed, returned, rejectedLimit }

    enum Kind: Equatable { case digit, op }

    static let digitPatterns: [String: Character] = [
        "abcdef": "0", "bc": "1", "abdeg": "2", "abcdg": "3", "bcfg": "4",
        "acdfg": "5", "acdefg": "6", "abc": "7", "abcdefg": "8", "abcdfg": "9",
    ]
    static let digitLetters = Array("abcdefg")
    static let opLabels = ["hu", "hl", "v"]

    let kinds: [Kind]
    let moves: Int
    private(set) var cells: [[Bool]]
    private let initial: [[Bool]]
    private(set) var held: Slot?
    private var heldOrigin: Slot?
    private var pickSnapshot: [[Bool]] = []
    private var undoStack: [[[Bool]]] = []

    // MARK: 초기화

    init(equation: [String], moves: Int) {
        var kinds: [Kind] = []
        var cells: [[Bool]] = []
        for ch in equation {
            if let d = ch.first, d.isNumber, ch.count == 1 {
                kinds.append(.digit)
                let set = Self.digitPatterns.first { $0.value == d }?.key ?? ""
                cells.append(Self.digitLetters.map { set.contains($0) })
            } else {
                kinds.append(.op)
                switch ch {
                case "=": cells.append([true, true, false])
                case "+": cells.append([true, false, true])   // {hu,v}
                default:  cells.append([true, false, false])  // "-" → {hu}
                }
            }
        }
        self.kinds = kinds
        self.cells = cells
        self.initial = cells
        self.moves = moves
    }

    /// 테스트용: 슬롯 집합 문자열 → 셀(종류 지정).
    init(kinds: [Kind], sets: [String], moves: Int) {
        self.kinds = kinds
        self.cells = zip(kinds, sets).map { Self.slots(kind: $0, set: $1) }
        self.initial = self.cells
        self.moves = moves
    }

    static func slots(kind: Kind, set: String) -> [Bool] {
        switch kind {
        case .digit: return digitLetters.map { set.contains($0) }
        case .op:
            let parts = Set(set.split(separator: " ").map(String.init))
            return opLabels.map { parts.contains($0) }
        }
    }

    static func slotCount(_ kind: Kind) -> Int { kind == .digit ? 7 : 3 }

    // MARK: 글리프·식

    static func glyph(kind: Kind, slots: [Bool]) -> Character? {
        switch kind {
        case .digit:
            let key = String(zip(digitLetters, slots).filter { $0.1 }.map { $0.0 })
            return digitPatterns[key]
        case .op:
            let hu = slots[0], hl = slots[1], v = slots[2]
            switch (hu, hl, v) {
            case (true, true, false): return "="
            case (true, false, false), (false, true, false): return "-"
            case (true, false, true), (false, true, true): return "+"
            default: return nil
            }
        }
    }

    func glyph(at cell: Int) -> Character? { Self.glyph(kind: kinds[cell], slots: cells[cell]) }

    /// 모든 셀이 유효 글리프면 이어 붙인 문자열.
    var text: String? {
        var s = ""
        for i in cells.indices {
            guard let g = glyph(at: i) else { return nil }
            s.append(g)
        }
        return s
    }

    /// 무효 글리프 셀 인덱스(들고 있는 동안엔 비어 있다고 본다).
    var invalidCells: Set<Int> {
        guard held == nil else { return [] }
        return Set(cells.indices.filter { glyph(at: $0) == nil })
    }

    /// 식 문자열 판정. 문법 오류면 nil, 아니면 참/거짓.
    static func evaluate(_ text: String) -> Bool? {
        let sides = text.split(separator: "=", omittingEmptySubsequences: false)
        guard sides.count == 2, text.filter({ $0 == "=" }).count == 1,
              let l = value(of: sides[0]), let r = value(of: sides[1]) else { return nil }
        return l == r
    }

    private static func value(of side: Substring) -> Int? {
        guard !side.isEmpty else { return nil }
        var total = 0, sign = 1
        var run = ""
        var expectNumber = true
        func flush() -> Bool {
            guard !run.isEmpty else { return false }
            if run.count > 1 && run.first == "0" { return false }
            guard let n = Int(run) else { return false }
            total += sign * n
            run = ""
            return true
        }
        for ch in side {
            if ch.isNumber {
                run.append(ch); expectNumber = false
            } else if ch == "+" || ch == "-" {
                guard !expectNumber, flush() else { return nil }
                sign = ch == "+" ? 1 : -1
                expectNumber = true
            } else { return nil }
        }
        guard !expectNumber, flush() else { return nil }
        return total
    }

    /// 현재 식의 참/거짓. 무효 글리프·문법 오류면 nil.
    var truth: Bool? { text.flatMap(Self.evaluate) }

    // MARK: 순변위

    /// 현재 놓인 슬롯 중 초기에 없던 슬롯 수.
    var displacement: Int {
        var n = 0
        for i in cells.indices { for j in cells[i].indices where cells[i][j] && !initial[i][j] { n += 1 } }
        return n
    }

    var canCheck: Bool { held == nil && displacement >= 1 }
    var canUndo: Bool { held == nil && !undoStack.isEmpty }
    var canReset: Bool { held == nil && cells != initial }

    func isOccupied(_ s: Slot) -> Bool { cells[s.cell][s.idx] }

    /// 놓을 수 있는 빈 슬롯(들고 있는 막대의 원래 슬롯 포함).
    var emptySlots: [Slot] {
        var out: [Slot] = []
        for i in cells.indices { for j in cells[i].indices where !cells[i][j] { out.append(Slot(cell: i, idx: j)) } }
        return out
    }

    // MARK: 조작

    mutating func pick(_ s: Slot) -> Bool {
        guard held == nil, cells.indices.contains(s.cell), cells[s.cell].indices.contains(s.idx), cells[s.cell][s.idx] else { return false }
        pickSnapshot = cells
        cells[s.cell][s.idx] = false
        held = s
        heldOrigin = s
        return true
    }

    /// 들고 있는 막대를 놓는다. target nil 이거나 원래 자리면 원위치.
    @discardableResult
    mutating func drop(at target: Slot?) -> DropResult {
        guard let origin = heldOrigin, held != nil else { return .returned }
        held = nil; heldOrigin = nil
        guard let t = target, t != origin, !cells[t.cell][t.idx] else {
            cells[origin.cell][origin.idx] = true
            return .returned
        }
        cells[t.cell][t.idx] = true
        if displacement > moves {
            cells[t.cell][t.idx] = false
            cells[origin.cell][origin.idx] = true
            return .rejectedLimit
        }
        undoStack.append(pickSnapshot)
        return .placed
    }

    mutating func undo() {
        guard held == nil, let last = undoStack.popLast() else { return }
        cells = last
    }

    mutating func reset() {
        guard held == nil else { return }
        cells = initial
        undoStack.removeAll()
    }
}
