import SwiftUI

/// 성냥개비 퍼즐 식 영역: 막대를 직접 드래그해서 옮긴다.
struct MatchBoardView: View {
    @Binding var board: MatchBoard
    var disabled: Bool = false
    var onPick: () -> Void = {}
    var onDrop: (MatchBoard.DropResult) -> Void = { _ in }

    @State private var finger: CGPoint = .zero
    @State private var dragging = false

    private let pickRadius: CGFloat = 24
    private let dropRadius: CGFloat = 44
    private let spacing: CGFloat = 6

    // MARK: 기하

    private struct Layout {
        let cellW: CGFloat, cellH: CGFloat, thick: CGFloat
        let originX: CGFloat, originY: CGFloat, spacing: CGFloat
    }

    private func layout(in size: CGSize) -> Layout {
        let n = max(1, board.cells.count)
        let w = min(46, (size.width - spacing * CGFloat(n - 1)) / CGFloat(n))
        let h = w * 1.9
        let total = w * CGFloat(n) + spacing * CGFloat(n - 1)
        return Layout(cellW: w, cellH: h, thick: w * 0.17,
                      originX: (size.width - total) / 2, originY: (size.height - h) / 2, spacing: spacing)
    }

    private struct Seg { let center: CGPoint; let horizontal: Bool; let length: CGFloat }

    private func seg(_ s: MatchBoard.Slot, _ l: Layout) -> Seg {
        let ox = l.originX + CGFloat(s.cell) * (l.cellW + l.spacing)
        let oy = l.originY
        let w = l.cellW, h = l.cellH, t = l.thick
        switch board.kinds[s.cell] {
        case .digit:
            let xl = w * 0.12, xr = w * 0.88
            let yt = h * 0.1, ym = h * 0.5, yb = h * 0.9
            let hl = (xr - xl) - t * 1.2
            let vl = (ym - yt) - t * 1.2
            switch s.idx {
            case 0: return Seg(center: CGPoint(x: ox + w / 2, y: oy + yt), horizontal: true, length: hl)
            case 1: return Seg(center: CGPoint(x: ox + xr, y: oy + (yt + ym) / 2), horizontal: false, length: vl)
            case 2: return Seg(center: CGPoint(x: ox + xr, y: oy + (ym + yb) / 2), horizontal: false, length: vl)
            case 3: return Seg(center: CGPoint(x: ox + w / 2, y: oy + yb), horizontal: true, length: hl)
            case 4: return Seg(center: CGPoint(x: ox + xl, y: oy + (ym + yb) / 2), horizontal: false, length: vl)
            case 5: return Seg(center: CGPoint(x: ox + xl, y: oy + (yt + ym) / 2), horizontal: false, length: vl)
            default: return Seg(center: CGPoint(x: ox + w / 2, y: oy + ym), horizontal: true, length: hl)
            }
        case .op:
            // 그려지는 위치는 현재 글리프에 따라: + 는 십자(가로·세로 중앙 교차), - 는 중앙 하나, = 는 중앙 위아래.
            let len = w * 0.58
            let cx = ox + w / 2, cy = oy + h / 2
            let c = board.cells[s.cell]
            if s.idx == 2 { return Seg(center: CGPoint(x: cx, y: cy), horizontal: false, length: len) }
            let sign: CGFloat = s.idx == 0 ? -1 : 1
            let y: CGFloat
            if c[s.idx] {
                y = (c[0] && c[1]) ? sign * h * 0.07 : 0
            } else {
                y = sign * t * 1.7   // 고스트: 놓였을 때 그 쪽에 생길 자리 근처
            }
            return Seg(center: CGPoint(x: cx, y: cy + y), horizontal: true, length: len)
        }
    }

    private func allSlots() -> [MatchBoard.Slot] {
        board.cells.indices.flatMap { i in board.cells[i].indices.map { MatchBoard.Slot(cell: i, idx: $0) } }
    }

    /// 점과 선분(막대 중심선) 사이 거리.
    private func distance(_ p: CGPoint, _ s: Seg) -> CGFloat {
        let half = s.length / 2
        let dx = s.horizontal ? max(abs(p.x - s.center.x) - half, 0) : abs(p.x - s.center.x)
        let dy = s.horizontal ? abs(p.y - s.center.y) : max(abs(p.y - s.center.y) - half, 0)
        return hypot(dx, dy)
    }

    private func nearest(to p: CGPoint, among slots: [MatchBoard.Slot], radius: CGFloat, _ l: Layout) -> MatchBoard.Slot? {
        var best: (MatchBoard.Slot, CGFloat)?
        for s in slots {
            let g = seg(s, l)
            let d = distance(p, g)
            guard d <= radius else { continue }
            let key = d + 0.001 * hypot(p.x - g.center.x, p.y - g.center.y)
            if best == nil || key < best!.1 { best = (s, key) }
        }
        return best?.0
    }

    // MARK: 본문

    var body: some View {
        GeometryReader { geo in
            let l = layout(in: geo.size)
            let hover: MatchBoard.Slot? = (dragging && board.held != nil)
                ? nearest(to: finger, among: board.emptySlots, radius: dropRadius, l) : nil
            let invalid = board.invalidCells
            ZStack {
                // 셀 배경
                ForEach(board.cells.indices, id: \.self) { i in
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(invalid.contains(i) ? Theme.danger.opacity(0.16) : Color.white.opacity(0.035))
                        .frame(width: l.cellW, height: l.cellH)
                        .position(x: l.originX + CGFloat(i) * (l.cellW + l.spacing) + l.cellW / 2,
                                  y: l.originY + l.cellH / 2)
                }
                // 고스트(들고 있을 때 빈 슬롯)
                if board.held != nil {
                    ForEach(board.emptySlots, id: \.self) { s in
                        let g = seg(s, l)
                        stick(g, l, ghost: true, highlighted: s == hover)
                    }
                }
                // 놓인 막대
                ForEach(allSlots().filter { board.isOccupied($0) }, id: \.self) { s in
                    stick(seg(s, l), l, ghost: false, highlighted: false)
                }
                // 들고 있는 막대
                if dragging, board.held != nil {
                    let target = hover.map { seg($0, l) }
                    let horizontal = target?.horizontal ?? true
                    let len = target?.length ?? (l.cellW * 0.62)
                    stick(Seg(center: finger, horizontal: horizontal, length: len), l, ghost: false, highlighted: false, lifted: true)
                }
            }
            .frame(width: geo.size.width, height: geo.size.height)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { v in
                        guard !disabled else { return }
                        finger = v.location
                        if !dragging {
                            dragging = true
                            let occupied = allSlots().filter { board.isOccupied($0) }
                            if let s = nearest(to: v.startLocation, among: occupied, radius: pickRadius, l), board.pick(s) {
                                onPick()
                            }
                        }
                    }
                    .onEnded { v in
                        defer { dragging = false }
                        guard board.held != nil else { return }
                        let target = nearest(to: v.location, among: board.emptySlots, radius: dropRadius, l)
                        onDrop(board.drop(at: target))
                    }
            )
            .animation(.easeOut(duration: 0.12), value: board.cells)
        }
    }

    // MARK: 막대 그리기

    private func stick(_ g: Seg, _ l: Layout, ghost: Bool, highlighted: Bool, lifted: Bool = false) -> some View {
        let t = l.thick
        return ZStack {
            if ghost {
                Capsule()
                    .fill(highlighted ? Theme.accent2.opacity(0.85) : Color.white.opacity(0.14))
                    .overlay(Capsule().stroke(highlighted ? Theme.accent2 : Color.white.opacity(0.25), lineWidth: 1))
                    .frame(width: g.length, height: t)
            } else {
                Capsule()
                    .fill(LinearGradient(colors: [Color(hex: 0xF3D79B), Color(hex: 0xD9A95A)], startPoint: .top, endPoint: .bottom))
                    .frame(width: g.length, height: t)
                    .overlay(alignment: .leading) {
                        Capsule()
                            .fill(LinearGradient(colors: [Color(hex: 0xFF6B5B), Color(hex: 0xC8321F)], startPoint: .top, endPoint: .bottom))
                            .frame(width: min(g.length * 0.3, t * 1.7), height: t * 1.05)
                    }
                    .shadow(color: .black.opacity(lifted ? 0.5 : 0.25), radius: lifted ? 6 : 1.5, y: lifted ? 3 : 1)
            }
        }
        .rotationEffect(.degrees(g.horizontal ? 0 : 90))
        .scaleEffect(lifted ? 1.12 : 1)
        .position(g.center)
        .allowsHitTesting(false)
    }
}
