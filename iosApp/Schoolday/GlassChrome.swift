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

struct GlassTabBar: View {
    let selected: Int
    let titles: [String]
    let onSelect: (Int) -> Void
    @Namespace private var namespace

    private let symbols = ["sun.max", "calendar", "fork.knife"]
    private let selectedSymbols = ["sun.max.fill", "calendar", "fork.knife"]

    var body: some View {
        if #available(iOS 26.0, *) {
            bar
                .padding(5)
                .glassEffect(.regular.interactive(), in: Capsule())
        } else {
            bar
                .padding(5)
                .background(.ultraThinMaterial, in: Capsule())
        }
    }

    private var bar: some View {
        HStack(spacing: 0) {
            ForEach(0 ..< symbols.count, id: \.self) { index in
                Button {
                    UISelectionFeedbackGenerator().selectionChanged()
                    onSelect(index)
                } label: {
                    VStack(spacing: 2) {
                        Image(systemName: selected == index ? selectedSymbols[index] : symbols[index])
                            .font(.system(size: 19, weight: .medium))
                            .symbolRenderingMode(.monochrome)
                        Text(index < titles.count ? titles[index] : "")
                            .font(.system(size: 11, weight: selected == index ? .semibold : .medium))
                            .lineLimit(1)
                    }
                    .foregroundStyle(selected == index ? lunchOrange : Color.primary)
                    .frame(maxWidth: .infinity, minHeight: 52)
                    .background {
                        if selected == index {
                            Capsule()
                                .fill(lunchOrange.opacity(0.16))
                                .matchedGeometryEffect(id: "selection", in: namespace)
                        }
                    }
                    .contentShape(Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selected == index ? .isSelected : [])
            }
        }
    }
}
