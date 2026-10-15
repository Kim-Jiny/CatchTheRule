import SwiftUI

/// 챕터 타임라인의 한 챕터 상태.
enum ChapterState {
    case completed, current, locked
}

/// 챕터 행 왼쪽의 세로 연결선 + 원형 노드. 행 `.background` 로 붙여 행 높이를 따라 늘어난다.
struct TimelineRail: View {
    let track: String
    let number: Int
    let state: ChapterState
    let prevCompleted: Bool
    let isFirst: Bool
    let isLast: Bool

    static let width: CGFloat = 32
    static let nodeY: CGFloat = 36   // 노드 중심 y(행 상단 기준)
    private let nodeSize: CGFloat = 28

    var body: some View {
        ZStack(alignment: .top) {
            VStack(spacing: 0) {
                segment(filled: prevCompleted, visible: !isFirst)
                    .frame(height: Self.nodeY)
                segment(filled: state == .completed, visible: !isLast)
                    .frame(maxHeight: .infinity)
            }
            node
                .offset(y: Self.nodeY - nodeSize / 2)
        }
        .frame(width: Self.width)
    }

    private func segment(filled: Bool, visible: Bool) -> some View {
        Rectangle()
            .fill(filled ? AnyShapeStyle(Theme.modeGradient(track)) : AnyShapeStyle(Color.white.opacity(0.10)))
            .frame(width: 3)
            .opacity(visible ? 1 : 0)
    }

    @ViewBuilder
    private var node: some View {
        switch state {
        case .completed:
            Circle()
                .fill(Theme.modeGradient(track))
                .frame(width: nodeSize, height: nodeSize)
                .overlay(Image(systemName: "checkmark").font(.system(size: 12, weight: .heavy)).foregroundStyle(.white))
                .shadow(color: Theme.modeGlow(track), radius: 8)
        case .current:
            CurrentNode(track: track, number: number, size: nodeSize)
        case .locked:
            Circle()
                .fill(Theme.bgElevated)
                .frame(width: nodeSize, height: nodeSize)
                .overlay(Circle().stroke(Color.white.opacity(0.12), lineWidth: 1))
                .overlay(Image(systemName: "lock.fill").font(.system(size: 11)).foregroundStyle(Theme.textTertiary))
        }
    }
}

/// 현재 챕터 노드: 1.6초 주기 펄스 링.
private struct CurrentNode: View {
    let track: String
    let number: Int
    let size: CGFloat
    @Environment(\.neonAnimating) private var active

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30, paused: !active)) { tl in
            let f = active ? tl.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 1.6) / 1.6 : 0
            ZStack {
                Circle()
                    .stroke(Theme.modeColor(track), lineWidth: 2)
                    .frame(width: size, height: size)
                    .scaleEffect(1 + 0.7 * f)
                    .opacity(active ? 1 - f : 0.4)
                Circle()
                    .fill(Theme.card)
                    .frame(width: size, height: size)
                    .overlay(Circle().stroke(Theme.modeColor(track), lineWidth: 2))
                    .shadow(color: Theme.modeGlow(track), radius: 8)
                Text("\(number)")
                    .font(.system(size: 12, weight: .heavy, design: .rounded))
                    .foregroundStyle(Theme.textPrimary)
            }
        }
        .frame(width: size, height: size)
    }
}
