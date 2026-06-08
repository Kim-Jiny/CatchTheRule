import SwiftUI

/// 화면 내에서 드래그로 옮길 수 있는 플로팅 계산기. 계산 기록 포함.
struct CalculatorPanel: View {
    @Binding var isPresented: Bool

    @State private var expr: String = ""
    @State private var history: [String] = []     // "expr = result" (최신이 뒤)
    @State private var position: CGSize = .zero    // 누적 이동량
    @GestureState private var dragOffset: CGSize = .zero

    private let rows: [[String]] = [
        ["C", "⌫", "÷", "×"],
        ["7", "8", "9", "−"],
        ["4", "5", "6", "+"],
        ["1", "2", "3", "="],
    ]

    var body: some View {
        VStack(spacing: 0) {
            handle
            historyView
            display
            keypad
        }
        .frame(width: 250)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(Theme.bgElevated)
                .shadow(color: .black.opacity(0.5), radius: 20, y: 8)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 20, style: .continuous).stroke(Theme.stroke, lineWidth: 1)
        )
        .offset(x: clamp(position.width + dragOffset.width, maxOffset.width),
                y: clamp(position.height + dragOffset.height, maxOffset.height))
    }

    /// 화면 밖으로 끌려나가 사라지지 않도록 이동 범위 제한.
    private var maxOffset: CGSize {
        let s = UIScreen.main.bounds
        return CGSize(width: max(0, (s.width - 250) / 2 - 8),
                      height: max(0, (s.height - 430) / 2 - 8))
    }
    private func clamp(_ v: CGFloat, _ m: CGFloat) -> CGFloat { min(max(v, -m), m) }

    // 이동 핸들(드래그) + 제목 + 닫기
    private var handle: some View {
        HStack(spacing: 8) {
            Image(systemName: "arrow.up.and.down.and.arrow.left.and.right")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Theme.textTertiary)
            Text(String.loc("calc_title"))
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Theme.textSecondary)
            Spacer()
            Button { isPresented = false } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Theme.textTertiary)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(coordinateSpace: .global)   // 글로벌 좌표 → 이동 중 떨림 방지
                .updating($dragOffset) { v, s, _ in s = v.translation }
                .onEnded { v in
                    position.width = clamp(position.width + v.translation.width, maxOffset.width)
                    position.height = clamp(position.height + v.translation.height, maxOffset.height)
                }
        )
    }

    private var historyView: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .trailing, spacing: 4) {
                    if history.isEmpty {
                        Text(String.loc("calc_no_history"))
                            .font(.system(size: 12))
                            .foregroundStyle(Theme.textTertiary)
                            .frame(maxWidth: .infinity, alignment: .center)
                            .padding(.vertical, 6)
                    } else {
                        ForEach(Array(history.enumerated()), id: \.offset) { i, line in
                            Text(line)
                                .font(.system(size: 12, design: .rounded))
                                .foregroundStyle(Theme.textTertiary)
                                .frame(maxWidth: .infinity, alignment: .trailing)
                                .id(i)
                        }
                    }
                }
                .padding(.horizontal, 14)
            }
            .frame(height: 64)
            .onChange(of: history.count) { _, c in
                if c > 0 { withAnimation { proxy.scrollTo(c - 1, anchor: .bottom) } }
            }
        }
    }

    private var display: some View {
        Text(expr.isEmpty ? "0" : expr)
            .font(.system(size: 26, weight: .bold, design: .rounded))
            .foregroundStyle(Theme.textPrimary)
            .lineLimit(1)
            .minimumScaleFactor(0.4)
            .frame(maxWidth: .infinity, alignment: .trailing)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
    }

    private var keypad: some View {
        VStack(spacing: 6) {
            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                HStack(spacing: 6) {
                    ForEach(row, id: \.self) { key in keyButton(key) }
                }
            }
            HStack(spacing: 6) {
                keyButton("0").frame(maxWidth: .infinity)
                keyButton(".")
            }
        }
        .padding(10)
    }

    private func keyButton(_ key: String) -> some View {
        let isEquals = key == "="
        let isOp = ["÷", "×", "−", "+", "C", "⌫"].contains(key)
        return Button { tap(key) } label: {
            Text(key)
                .font(.system(size: 19, weight: .semibold, design: .rounded))
                .foregroundStyle(isEquals ? .white : (isOp ? Theme.accent2 : Theme.textPrimary))
                .frame(maxWidth: .infinity)
                .frame(height: 42)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(isEquals ? AnyShapeStyle(Theme.accentGradient) : AnyShapeStyle(Theme.card))
                )
        }
        .buttonStyle(.plain)
    }

    // MARK: - Logic

    private func tap(_ key: String) {
        switch key {
        case "C": expr = ""
        case "⌫": if !expr.isEmpty { expr.removeLast() }
        case "=":
            if let r = CalcEval.evaluate(expr) {
                let result = CalcEval.format(r)
                history.append("\(expr) = \(result)")
                if history.count > 20 { history.removeFirst() }
                expr = result
            }
        default:
            expr.append(key)
        }
    }
}

/// 안전한 사칙연산 평가기(× ÷ − + , 소수, 우선순위). NSExpression 미사용(크래시 방지).
enum CalcEval {
    static func evaluate(_ input: String) -> Double? {
        let s = input
            .replacingOccurrences(of: "×", with: "*")
            .replacingOccurrences(of: "÷", with: "/")
            .replacingOccurrences(of: "−", with: "-")
        guard let tokens = tokenize(s) else { return nil }
        return evalTokens(tokens)
    }

    private enum Token { case num(Double); case op(Character) }

    private static func tokenize(_ s: String) -> [Token]? {
        var tokens: [Token] = []
        var numBuf = ""
        let chars = Array(s)
        var i = 0
        while i < chars.count {
            let c = chars[i]
            if c.isNumber || c == "." {
                numBuf.append(c)
            } else if "+-*/".contains(c) {
                // 단항 마이너스: 시작이거나 직전이 연산자면 음수의 일부
                if c == "-" && (tokens.isEmpty && numBuf.isEmpty) {
                    numBuf.append(c)
                } else if numBuf.isEmpty, case .op = tokens.last ?? .num(0) {
                    if c == "-" { numBuf.append(c) } else { return nil }
                } else {
                    if numBuf.isEmpty { return nil }
                    tokens.append(.num(Double(numBuf) ?? 0)); numBuf = ""
                    tokens.append(.op(c))
                }
            } else if c == " " {
                // skip
            } else {
                return nil
            }
            i += 1
        }
        if !numBuf.isEmpty, let d = Double(numBuf) { tokens.append(.num(d)) }
        else if !numBuf.isEmpty { return nil }
        return tokens.isEmpty ? nil : tokens
    }

    private static func evalTokens(_ tokens: [Token]) -> Double? {
        // 두 패스: 먼저 * /, 그다음 + -
        var nums: [Double] = []
        var ops: [Character] = []
        var idx = 0
        // 첫 숫자
        guard case let .num(first)? = tokens.first else { return nil }
        nums.append(first)
        idx = 1
        while idx < tokens.count {
            guard case let .op(o) = tokens[idx], idx + 1 < tokens.count,
                  case let .num(n) = tokens[idx + 1] else { return nil }
            if o == "*" || o == "/" {
                let last = nums.removeLast()
                if o == "/" && n == 0 { return nil }
                nums.append(o == "*" ? last * n : last / n)
            } else {
                ops.append(o); nums.append(n)
            }
            idx += 2
        }
        var result = nums.first ?? 0
        var k = 1
        for o in ops {
            let n = nums[k]
            result = (o == "+") ? result + n : result - n
            k += 1
        }
        return result
    }

    static func format(_ d: Double) -> String {
        if d.isNaN || d.isInfinite { return "0" }
        if d == d.rounded() && abs(d) < 1e15 { return String(Int(d)) }
        return String(format: "%.10g", d)
    }
}
