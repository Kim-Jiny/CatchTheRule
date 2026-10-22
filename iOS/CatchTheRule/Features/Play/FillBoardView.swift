import SwiftUI

// MARK: - 격자 채우기 상태

/// 격자 채우기 퍼즐의 현재 입력 상태. 규칙 평가는 즉각 위반 표시와 `확인` 전 검사에만 쓰고,
/// 정답 판정은 JSON 의 `answer` 와 비교한다.
struct FillState: Equatable {
    let n: Int
    let given: [[Int]]            // 0 = 빈칸
    let rules: Set<String>
    let lt: [[Int]]
    let cages: [[Int]]
    private(set) var values: [[Int]]
    var selected: Int?            // 선택한 빈칸(행*n+열)

    init?(puzzle: Puzzle) {
        guard let b = puzzle.board, !b.isEmpty else { return nil }
        n = b.count
        given = b
        values = b
        rules = Set(puzzle.rules ?? ["row", "col"])
        lt = puzzle.lt ?? []
        cages = puzzle.cages ?? []
    }

    /// 상자 크기(행×열). N=4 → 2×2, N=6 → 2×3, 그 외 상자 없음.
    var boxSize: (rows: Int, cols: Int)? {
        guard rules.contains("box") else { return nil }
        switch n {
        case 4: return (2, 2)
        case 6: return (2, 3)
        default: return nil
        }
    }

    func isGiven(_ r: Int, _ c: Int) -> Bool { given[r][c] != 0 }
    func value(_ r: Int, _ c: Int) -> Int { values[r][c] }

    var blankCount: Int { given.joined().filter { $0 == 0 }.count }
    var isComplete: Bool { !values.joined().contains(0) }
    var hasInput: Bool { selected != nil }

    mutating func tap(_ r: Int, _ c: Int) {
        guard !isGiven(r, c) else { return }
        let i = r * n + c
        selected = selected == i ? nil : i
    }

    mutating func enter(_ v: Int) {
        guard let s = selected else { return }
        values[s / n][s % n] = v
    }

    mutating func clearSelected() {
        guard let s = selected else { return }
        values[s / n][s % n] = 0
    }

    /// 빈칸 값을 행 우선으로 이어 붙인 문자열(JSON answer 형식).
    var answerString: String {
        var out = ""
        for r in 0..<n { for c in 0..<n where given[r][c] == 0 { out += "\(values[r][c])" } }
        return out
    }

    // MARK: 규칙 위반

    /// 규칙을 어기는 칸(행*n+열) 집합.
    var conflicts: Set<Int> {
        var bad = Set<Int>()
        func dup(_ cells: [(Int, Int)]) {
            var seen: [Int: Int] = [:]
            for (r, c) in cells {
                let v = values[r][c]
                guard v != 0 else { continue }
                if let first = seen[v] { bad.insert(first); bad.insert(r * n + c) } else { seen[v] = r * n + c }
            }
        }
        for k in 0..<n {
            dup((0..<n).map { (k, $0) })
            dup((0..<n).map { ($0, k) })
        }
        if let b = boxSize {
            for br in stride(from: 0, to: n, by: b.rows) {
                for bc in stride(from: 0, to: n, by: b.cols) {
                    dup((0..<b.rows).flatMap { dr in (0..<b.cols).map { (br + dr, bc + $0) } })
                }
            }
        }
        if rules.contains("diag") {
            dup((0..<n).map { ($0, $0) })
            dup((0..<n).map { ($0, n - 1 - $0) })
        }
        for e in lt where e.count == 4 {
            let a = values[e[0]][e[1]], b = values[e[2]][e[3]]
            if a != 0, b != 0, a >= b { bad.insert(e[0] * n + e[1]); bad.insert(e[2] * n + e[3]) }
        }
        for cage in cages where cage.count >= 3 {
            let cells = Self.cageCells(cage)
            let vals = cells.map { values[$0.0][$0.1] }
            let filled = vals.filter { $0 != 0 }
            let sum = filled.reduce(0, +)
            let over = sum > cage[0]
            let wrong = filled.count == vals.count && sum != cage[0]
            let repeated = Set(filled).count != filled.count
            if over || wrong || repeated { for (r, c) in cells { bad.insert(r * n + c) } }
        }
        return bad
    }

    var hasConflict: Bool { !conflicts.isEmpty }

    static func cageCells(_ cage: [Int]) -> [(Int, Int)] {
        stride(from: 1, to: cage.count - 1, by: 2).map { (cage[$0], cage[$0 + 1]) }
    }
}

// MARK: - 격자 보드

struct FillBoardView: View {
    @Binding var state: FillState
    var disabled: Bool = false
    var onTap: () -> Void = {}

    private var mode: Color { Theme.modeColor("fill") }

    var body: some View {
        GeometryReader { geo in
            let side = min(geo.size.width, geo.size.height)
            let cell = side / CGFloat(state.n)
            let bad = state.conflicts
            ZStack(alignment: .topLeading) {
                // 대각선 옅은 배경
                if state.rules.contains("diag") {
                    ForEach(0..<state.n, id: \.self) { k in
                        diagTint(k, k, cell)
                        if k != state.n - 1 - k { diagTint(k, state.n - 1 - k, cell) }
                    }
                }
                ForEach(0..<state.n, id: \.self) { r in
                    ForEach(0..<state.n, id: \.self) { c in
                        cellView(r, c, size: cell, isBad: bad.contains(r * state.n + c))
                            .frame(width: cell, height: cell)
                            .offset(x: CGFloat(c) * cell, y: CGFloat(r) * cell)
                    }
                }
                gridLines(cell: cell)
                cageOutlines(cell: cell)
                ForEach(Array(state.lt.enumerated()), id: \.offset) { _, e in
                    if e.count == 4 { ltMark(e, cell: cell) }
                }
            }
            .frame(width: side, height: side)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .aspectRatio(1, contentMode: .fit)
        .frame(maxWidth: 380)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(state.n)x\(state.n), \(state.blankCount)")
    }

    private func diagTint(_ r: Int, _ c: Int, _ cell: CGFloat) -> some View {
        Rectangle().fill(mode.opacity(0.10))
            .frame(width: cell, height: cell)
            .offset(x: CGFloat(c) * cell, y: CGFloat(r) * cell)
    }

    private func cellView(_ r: Int, _ c: Int, size: CGFloat, isBad: Bool) -> some View {
        let given = state.isGiven(r, c)
        let v = state.value(r, c)
        let selected = state.selected == r * state.n + c
        return ZStack {
            Rectangle().fill(given ? Color.white.opacity(0.07) : Color.black.opacity(0.25))
            if isBad { Rectangle().fill(Theme.danger.opacity(0.22)) }
            if selected {
                Rectangle().fill(mode.opacity(0.18))
                Rectangle().strokeBorder(mode, lineWidth: 2.5).shadow(color: Theme.modeGlow("fill"), radius: 6)
            }
            if v != 0 {
                Text("\(v)")
                    .font(.system(size: size * 0.5, weight: given ? .semibold : .bold, design: .rounded))
                    .foregroundStyle(given ? Theme.textPrimary : (isBad ? Theme.danger : mode))
            }
        }
        .contentShape(Rectangle())
        .onTapGesture {
            guard !disabled, !given else { return }
            state.tap(r, c)
            onTap()
        }
    }

    /// 얇은 칸 선 + 상자 경계 굵은 선 + 바깥 테두리.
    private func gridLines(cell: CGFloat) -> some View {
        let n = state.n
        let side = cell * CGFloat(n)
        let b = state.boxSize
        return ZStack {
            Path { p in
                for k in 1..<n {
                    let x = CGFloat(k) * cell
                    p.move(to: CGPoint(x: x, y: 0)); p.addLine(to: CGPoint(x: x, y: side))
                    p.move(to: CGPoint(x: 0, y: x)); p.addLine(to: CGPoint(x: side, y: x))
                }
            }
            .stroke(Color.white.opacity(0.14), lineWidth: 1)
            Path { p in
                if let b {
                    for k in stride(from: b.cols, to: n, by: b.cols) {
                        let x = CGFloat(k) * cell
                        p.move(to: CGPoint(x: x, y: 0)); p.addLine(to: CGPoint(x: x, y: side))
                    }
                    for k in stride(from: b.rows, to: n, by: b.rows) {
                        let y = CGFloat(k) * cell
                        p.move(to: CGPoint(x: 0, y: y)); p.addLine(to: CGPoint(x: side, y: y))
                    }
                }
            }
            .stroke(Color.white.opacity(0.55), lineWidth: 2.5)
            Rectangle().strokeBorder(Color.white.opacity(0.55), lineWidth: 2.5)
        }
        .allowsHitTesting(false)
    }

    /// 케이지: 같은 케이지 칸과 맞닿지 않는 변에 안쪽으로 들인 점선, 좌상단 칸에 합.
    private func cageOutlines(cell: CGFloat) -> some View {
        let inset: CGFloat = 3.5
        return ZStack(alignment: .topLeading) {
            ForEach(Array(state.cages.enumerated()), id: \.offset) { _, cage in
                let cells = FillState.cageCells(cage)
                let set = Set(cells.map { $0.0 * 100 + $0.1 })
                Path { p in
                    for (r, c) in cells {
                        let x = CGFloat(c) * cell, y = CGFloat(r) * cell
                        if !set.contains((r - 1) * 100 + c) {
                            p.move(to: CGPoint(x: x + inset, y: y + inset)); p.addLine(to: CGPoint(x: x + cell - inset, y: y + inset))
                        }
                        if !set.contains((r + 1) * 100 + c) {
                            p.move(to: CGPoint(x: x + inset, y: y + cell - inset)); p.addLine(to: CGPoint(x: x + cell - inset, y: y + cell - inset))
                        }
                        if !set.contains(r * 100 + c - 1) {
                            p.move(to: CGPoint(x: x + inset, y: y + inset)); p.addLine(to: CGPoint(x: x + inset, y: y + cell - inset))
                        }
                        if !set.contains(r * 100 + c + 1) {
                            p.move(to: CGPoint(x: x + cell - inset, y: y + inset)); p.addLine(to: CGPoint(x: x + cell - inset, y: y + cell - inset))
                        }
                    }
                }
                .stroke(Theme.textSecondary.opacity(0.9), style: StrokeStyle(lineWidth: 1.4, lineCap: .round, dash: [3, 3]))
                if let first = cells.min(by: { ($0.0, $0.1) < ($1.0, $1.1) }) {
                    Text("\(cage[0])")
                        .font(.system(size: max(9, cell * 0.2), weight: .bold, design: .rounded))
                        .foregroundStyle(Theme.textSecondary)
                        .offset(x: CGFloat(first.1) * cell + inset + 2, y: CGFloat(first.0) * cell + inset)
                }
            }
        }
        .allowsHitTesting(false)
    }

    /// 두 칸 경계에 작은 쪽을 가리키는 기호(‹ › ˄ ˅).
    private func ltMark(_ e: [Int], cell: CGFloat) -> some View {
        let (r1, c1, r2, c2) = (e[0], e[1], e[2], e[3])
        let cx = (CGFloat(c1) + CGFloat(c2) + 1) / 2 * cell
        let cy = (CGFloat(r1) + CGFloat(r2) + 1) / 2 * cell
        let sym: String
        if r1 == r2 { sym = c2 > c1 ? "‹" : "›" } else { sym = r2 > r1 ? "˄" : "˅" }
        let d = max(14, cell * 0.3)
        return Text(sym)
            .font(.system(size: d * 0.95, weight: .heavy, design: .rounded))
            .foregroundStyle(mode)
            .frame(width: d, height: d)
            .background(Circle().fill(Color(white: 0.1).opacity(0.9)))
            .position(x: cx, y: cy)
            .allowsHitTesting(false)
    }
}

// MARK: - 숫자 패드 + 확인

struct FillControlsView: View {
    let state: FillState
    var disabled: Bool = false
    let onDigit: (Int) -> Void
    let onClear: () -> Void
    let onCheck: () -> Void

    private var mode: Color { Theme.modeColor("fill") }

    var body: some View {
        let canInput = state.hasInput && !disabled
        let canCheck = state.isComplete && !disabled
        VStack(spacing: 8) {
            HStack(spacing: 6) {
                ForEach(1...state.n, id: \.self) { d in
                    Button { onDigit(d) } label: {
                        Text("\(d)")
                            .font(.system(size: 22, weight: .bold, design: .rounded))
                            .foregroundStyle(canInput ? Theme.textPrimary : Theme.textTertiary)
                            .frame(maxWidth: .infinity, minHeight: 46)
                            .card(cornerRadius: 12)
                    }
                    .buttonStyle(.plain)
                    .disabled(!canInput)
                }
            }
            HStack(spacing: 8) {
                Button(action: onClear) {
                    HStack(spacing: 6) {
                        Image(systemName: "delete.left")
                        Text(String.loc("fill_clear")).lineLimit(1).minimumScaleFactor(0.7)
                    }
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(canInput ? Theme.textSecondary : Theme.textTertiary)
                    .frame(width: 110, height: 50)
                    .card(cornerRadius: 14)
                }
                .buttonStyle(.plain)
                .disabled(!canInput)
                Button(action: onCheck) {
                    Text(String.loc("fill_check"))
                        .font(.system(size: 17, weight: .bold))
                        .foregroundStyle(canCheck ? Color.black.opacity(0.8) : Color.white)  // 밝은 옐로 위 가독성
                        .frame(maxWidth: .infinity, minHeight: 50)
                        .background(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .fill(canCheck ? AnyShapeStyle(Theme.modeGradient("fill")) : AnyShapeStyle(mode.opacity(0.25)))
                        )
                }
                .buttonStyle(.plain)
                .disabled(!canCheck)
                .opacity(canCheck ? 1 : 0.6)
            }
        }
    }
}
