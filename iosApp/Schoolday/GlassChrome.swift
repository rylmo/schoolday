import Shared
import SwiftUI

private let lunchOrange = Color(red: 0.949, green: 0.651, blue: 0.353)

/// The shared Compose screens with native Liquid Glass controls floating above them.
struct RootView: View {
    @ObservedObject var model: AppModel

    var body: some View {
        ZStack {
            ComposeHost(core: model.core).ignoresSafeArea()
            VStack(spacing: 0) {
                if model.settingsOpen {
                    HStack {
                        GlassBackButton { model.core.chrome.back() }
                        Spacer()
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 6)
                    .transition(.opacity.combined(with: .scale(scale: 0.8)))
                }
                Spacer(minLength: 0)
                if model.barVisible {
                    GlassTabBar(
                        selected: model.tab,
                        titles: model.titles,
                        onSelect: { model.core.chrome.select(tab: Int32($0)) }
                    )
                    .padding(.horizontal, 24)
                    .padding(.bottom, 4)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
        }
    }
}

struct GlassBackButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "chevron.left")
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(.primary)
                .frame(width: 44, height: 44)
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Back")
        .modifier(GlassCircle())
    }
}

private struct GlassCircle: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content.glassEffect(.regular.interactive(), in: Circle())
        } else {
            content.background(.ultraThinMaterial, in: Circle())
        }
    }
}

private struct GlassBar: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content.glassEffect(.regular.interactive(), in: Capsule())
        } else {
            content.background(.ultraThinMaterial, in: Capsule())
        }
    }
}

/// The bubble behind the selected tab. It turns clearer while it's being dragged, like a lens.
private struct GlassSelector: View {
    let lifted: Bool

    var body: some View {
        if #available(iOS 26.0, *) {
            Color.clear.glassEffect(
                (lifted ? Glass.clear : Glass.regular).tint(lunchOrange.opacity(lifted ? 0.14 : 0.3)),
                in: Capsule()
            )
        } else {
            Capsule().fill(lunchOrange.opacity(0.16))
        }
    }
}

struct GlassTabBar: View {
    let selected: Int
    let titles: [String]
    let onSelect: (Int) -> Void

    /// Set the moment a tab is chosen, so the bubble moves before the shared screens respond.
    @State private var chosen: Int?
    @State private var dragX: CGFloat?

    private let symbols = ["sun.max", "calendar", "fork.knife"]
    private let selectedSymbols = ["sun.max.fill", "calendar", "fork.knife"]
    private let barHeight: CGFloat = 52

    private var active: Int { chosen ?? selected }

    var body: some View {
        GeometryReader { geometry in
            let slot = geometry.size.width / CGFloat(symbols.count)
            ZStack(alignment: .leading) {
                selector(slot: slot)
                HStack(spacing: 0) {
                    ForEach(0 ..< symbols.count, id: \.self) { index in
                        tabButton(index)
                    }
                }
            }
            .simultaneousGesture(dragGesture(slot: slot))
        }
        .frame(height: barHeight)
        .padding(5)
        .modifier(GlassBar())
        .onChange(of: selected) { newValue in
            chosen = newValue
        }
    }

    private func selector(slot: CGFloat) -> some View {
        let lifted = dragX != nil
        let lastX = slot * CGFloat(symbols.count - 1)
        let x = dragX.map { min(max($0 - slot / 2, 0), lastX) } ?? slot * CGFloat(active)
        return GlassSelector(lifted: lifted)
            .frame(width: slot, height: barHeight)
            .scaleEffect(lifted ? 1.1 : 1)
            .offset(x: x)
            .animation(lifted ? Animation.interactiveSpring(response: 0.2, dampingFraction: 0.86) : Animation.spring(response: 0.4, dampingFraction: 0.7), value: x)
            .animation(.spring(response: 0.3, dampingFraction: 0.65), value: lifted)
            .allowsHitTesting(false)
    }

    private func tabButton(_ index: Int) -> some View {
        let isActive = active == index
        return Button {
            choose(index)
        } label: {
            VStack(spacing: 2) {
                Image(systemName: isActive ? selectedSymbols[index] : symbols[index])
                    .font(.system(size: 19, weight: .medium))
                    .symbolRenderingMode(.monochrome)
                Text(index < titles.count ? titles[index] : "")
                    .font(.system(size: 11, weight: isActive ? .semibold : .medium))
                    .lineLimit(1)
            }
            .foregroundStyle(isActive ? lunchOrange : Color.primary)
            .frame(maxWidth: .infinity, minHeight: barHeight)
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isActive ? .isSelected : [])
    }

    private func dragGesture(slot: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 8, coordinateSpace: .local)
            .onChanged { value in
                dragX = value.location.x
            }
            .onEnded { value in
                let index = min(max(Int(value.location.x / slot), 0), symbols.count - 1)
                dragX = nil
                choose(index)
            }
    }

    private func choose(_ index: Int) {
        guard index != active else { return }
        UISelectionFeedbackGenerator().selectionChanged()
        chosen = index
        onSelect(index)
    }
}
