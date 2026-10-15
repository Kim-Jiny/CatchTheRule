import SwiftUI

// MARK: - 순수 로직

enum TargetOp: String, CaseIterable, Equatable {
    case add = "+", sub = "−", mul = "×", div = "÷"
    var precedence: Int { (self == .add || self == .sub) ? 1 : 2 }
}

/// 정확한 유리수(분자/분모 Int64, 약분, |값| ≤ 10^9). 범위 초과 시 nil.
struct Rational: Equatable {
    static let limit: Int64 = 1_000_000_000
    let n: Int64, d: Int64   // d > 0

    static func make(_ n: Int64, _ d: Int64) -> Rational? {
        guard d != 0 else { return nil }
        var n = n, d = d
        if d < 0 { n = -n; d = -d }
        var a = abs(n), b = d
        while b != 0 { (a, b) = (b, a % b) }
        if a > 1 { n /= a; d /= a }
        guard abs(n) <= limit, d <= limit else { return nil }
        return Rational(n: n, d: d)
    }

    func apply(_ op: TargetOp, _ o: Rational) -> Rational? {
        switch op {
        case .add, .sub:
            let (x1, f1) = n.multipliedReportingOverflow(by: o.d)
            let (x2, f2) = o.n.multipliedReportingOverflow(by: d)
            let (dd, f3) = d.multipliedReportingOverflow(by: o.d)
            if f1 || f2 || f3 { return nil }
            let (s, f4) = op == .add ? x1.addingReportingOverflow(x2) : x1.subtractingReportingOverflow(x2)
            return f4 ? nil : Self.make(s, dd)
        case .mul:
            let (a, f1) = n.multipliedReportingOverflow(by: o.n)
            let (b, f2) = d.multipliedReportingOverflow(by: o.d)
            return (f1 || f2) ? nil : Self.make(a, b)
        case .div:
            guard o.n != 0 else { return nil }
            let (a, f1) = n.multipliedReportingOverflow(by: o.d)
            let (b, f2) = d.multipliedReportingOverflow(by: o.n)
            return (f1 || f2) ? nil : Self.make(a, b)
        }
    }

    var isInteger: Bool { d == 1 }
    var text: String { d == 1 ? "\(n)" : "\(n)/\(d)" }
}

enum TargetToken: Equatable {
    case num(tile: Int, value: Int)
    case op(TargetOp)
    case open, close

    var text: String {
        switch self {
        case .num(_, let v): return "\(v)"
        case .op(let o): return o.rawValue
        case .open: return "("
        case .close: return ")"
        }
    }
}

/// 숫자 만들기 수식 상태(토큰 열 + 문법 가드 + 평가).
struct TargetExpression: Equatable {
    let numbers: [Int]
    let target: Int
    private(set) var tokens: [TargetToken] = []

    init(numbers: [Int], target: Int) { self.numbers = numbers; self.target = target }

    // MARK: 상태 질의
    var usedTiles: Set<Int> {
        Set(tokens.compactMap { if case .num(let t, _) = $0 { return t } else { return nil } })
    }
    var openCount: Int { tokens.reduce(0) { $0 + ($1 == .open ? 1 : $1 == .close ? -1 : 0) } }
    private var last: TargetToken? { tokens.last }
    private var lastIsValueEnd: Bool {
        switch last { case .num?, .close?: return true; default: return false }
    }
    private var expectsOperand: Bool { !lastIsValueEnd }   // 시작, 연산자, ( 뒤

    var canAddNumber: Bool { expectsOperand }
    func canUseTile(_ i: Int) -> Bool { expectsOperand && !usedTiles.contains(i) }
    var canAddOp: Bool { lastIsValueEnd }
    var canOpen: Bool { expectsOperand }
    var canClose: Bool { openCount > 0 && lastIsValueEnd }
    var canDelete: Bool { !tokens.isEmpty }
    var isComplete: Bool { !tokens.isEmpty && lastIsValueEnd && openCount == 0 }
    var displayText: String { tokens.map(\.text).joined(separator: " ") }

    // MARK: 변경 (성공 여부 반환)
    @discardableResult mutating func addTile(_ i: Int) -> Bool {
        guard numbers.indices.contains(i), canUseTile(i) else { return false }
        tokens.append(.num(tile: i, value: numbers[i])); return true
    }
    @discardableResult mutating func addOp(_ o: TargetOp) -> Bool {
        guard canAddOp else { return false }; tokens.append(.op(o)); return true
    }
    @discardableResult mutating func addOpen() -> Bool {
        guard canOpen else { return false }; tokens.append(.open); return true
    }
    @discardableResult mutating func addClose() -> Bool {
        guard canClose else { return false }; tokens.append(.close); return true
    }
    mutating func deleteLast() { _ = tokens.popLast() }
    mutating func reset() { tokens = [] }

    // MARK: 평가
    /// 완성된 식의 값. 미완성/0 나누기/범위 초과면 nil.
    var value: Rational? {
        guard isComplete else { return nil }
        var vals: [Rational] = []
        var ops: [TargetToken] = []
        func reduce() -> Bool {
            guard case .op(let o)? = ops.popLast(), let b = vals.popLast(), let a = vals.popLast(),
                  let r = a.apply(o, b) else { return false }
            vals.append(r); return true
        }
        for t in tokens {
            switch t {
            case .num(_, let v):
                guard let r = Rational.make(Int64(v), 1) else { return nil }
                vals.append(r)
            case .open: ops.append(.open)
            case .close:
                while let top = ops.last, top != .open { if !reduce() { return nil } }
                _ = ops.popLast()
            case .op(let o):
                while case .op(let p)? = ops.last, p.precedence >= o.precedence { if !reduce() { return nil } }
                ops.append(t)
            }
        }
        while !ops.isEmpty { if !reduce() { return nil } }
        return vals.count == 1 ? vals[0] : nil
    }

    var isCorrect: Bool { value.map { $0.isInteger && Int($0.n) == target } ?? false }
}

// MARK: - 뷰

struct TargetBoardView: View {
    let expr: TargetExpression
    var incompleteShown: Bool = false
    let onTapTile: (Int) -> Void

    var body: some View {
        let value = expr.value
        let hit = expr.isCorrect
        VStack(spacing: 12) {
            VStack(spacing: 2) {
                Text(String.loc("target_label"))
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Theme.textSecondary)
                Text("\(expr.target)")
                    .font(.system(size: 38, weight: .heavy, design: .rounded))
                    .foregroundStyle(hit ? Theme.success : Theme.textPrimary)
                    .lineLimit(1).minimumScaleFactor(0.5)
            }
            .padding(.vertical, 10)
            .frame(maxWidth: .infinity)
            .card()
            .overlay(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .stroke(hit ? Theme.success : .clear, lineWidth: 2)
            )

            VStack(spacing: 6) {
                Group {
                    if expr.tokens.isEmpty {
                        Text(String.loc("target_expression_hint")).foregroundStyle(Theme.textTertiary)
                    } else {
                        Text(expr.displayText).foregroundStyle(Theme.textPrimary)
                    }
                }
                .font(.system(size: 26, weight: .bold, design: .rounded))
                .multilineTextAlignment(.center)
                .minimumScaleFactor(0.6)
                .frame(maxWidth: .infinity, minHeight: 40)

                Text(incompleteShown ? String.loc("target_incomplete")
                     : (value.map { "= \($0.text)" } ?? (expr.tokens.isEmpty ? " " : "= ?")))
                    .font(.system(size: 17, weight: .semibold, design: .rounded))
                    .foregroundStyle(incompleteShown ? Theme.danger : (hit ? Theme.success : Theme.textSecondary))
                    .lineLimit(1).minimumScaleFactor(0.6)
            }
            .padding(14)
            .frame(maxWidth: .infinity)
            .card()

            LazyVGrid(columns: [GridItem(.adaptive(minimum: 76), spacing: 8)], spacing: 8) {
                ForEach(Array(expr.numbers.enumerated()), id: \.offset) { i, n in
                    let used = expr.usedTiles.contains(i)
                    Button { onTapTile(i) } label: {
                        Text("\(n)")
                            .font(.system(size: 22, weight: .bold, design: .rounded))
                            .foregroundStyle(Theme.textPrimary)
                            .lineLimit(1).minimumScaleFactor(0.4)
                            .padding(.horizontal, 6)
                            .frame(maxWidth: .infinity, minHeight: 54)
                            .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Theme.card))
                            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .stroke(used ? Theme.stroke : Theme.accent2.opacity(0.6), lineWidth: 1))
                            .opacity(used ? 0.25 : 1)
                    }
                    .buttonStyle(.plain)
                    .disabled(used)
                    .accessibilityLabel("\(n)")
                }
            }
        }
    }
}

struct TargetControlsView: View {
    let expr: TargetExpression
    let disabled: Bool
    let onOp: (TargetOp) -> Void
    let onOpen: () -> Void
    let onClose: () -> Void
    let onDelete: () -> Void
    let onReset: () -> Void
    let onCheck: () -> Void

    var body: some View {
        let hit = expr.isCorrect
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                ForEach(TargetOp.allCases, id: \.self) { o in
                    key(o.rawValue, big: true, on: expr.canAddOp) { onOp(o) }
                }
                key("(", big: true, on: expr.canOpen, action: onOpen)
                key(")", big: true, on: expr.canClose, action: onClose)
            }
            HStack(spacing: 8) {
                labelKey("delete.left", String.loc("target_delete"), on: expr.canDelete, action: onDelete)
                labelKey("arrow.counterclockwise", String.loc("target_reset"), on: expr.canDelete, action: onReset)
            }
            Button(action: onCheck) {
                Text(String.loc("target_check"))
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity, minHeight: 50)
                    .background(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .fill(hit ? AnyShapeStyle(Theme.success)
                                  : (expr.isComplete ? AnyShapeStyle(Theme.accentGradient) : AnyShapeStyle(Theme.accent.opacity(0.3))))
                    )
            }
            .buttonStyle(.plain)
            .opacity(expr.isComplete ? 1 : 0.6)
        }
        .disabled(disabled)
    }

    private func key(_ t: String, big: Bool, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(t)
                .font(.system(size: 22, weight: .bold, design: .rounded))
                .foregroundStyle(on ? Theme.textPrimary : Theme.textTertiary)
                .frame(maxWidth: .infinity, minHeight: 46)
                .card(cornerRadius: 12)
        }
        .buttonStyle(.plain)
    }

    private func labelKey(_ icon: String, _ title: String, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: icon)
                Text(title).lineLimit(1).minimumScaleFactor(0.7)
            }
            .font(.system(size: 14, weight: .semibold))
            .foregroundStyle(on ? Theme.textSecondary : Theme.textTertiary)
            .frame(maxWidth: .infinity, minHeight: 40)
            .card(cornerRadius: 12)
        }
        .buttonStyle(.plain)
        .disabled(!on)
    }
}
