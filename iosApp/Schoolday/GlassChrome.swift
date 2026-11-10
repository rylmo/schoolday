import Shared
import SwiftUI
import UIKit

private let lunchOrange = UIColor(red: 0.949, green: 0.651, blue: 0.353, alpha: 1)

/// The shared Compose screens with native controls above them: the system tab bar (Liquid Glass
/// on iOS 26, including its selection lens), floating glass toolbar buttons for each tab, and
/// Settings as a native sheet.
struct RootView: View {
    @ObservedObject var model: AppModel

    private var settingsShown: Binding<Bool> {
        Binding(
            get: { model.settingsOpen },
            set: { open in if !open { model.core.chrome.back() } }
        )
    }

    var body: some View {
        ZStack(alignment: .topTrailing) {
            ChromeHost(core: model.core, tab: model.tab, titles: model.titles, barVisible: model.barVisible)
                .ignoresSafeArea()
            if model.barVisible {
                TopButtons(model: model)
                    .padding(.trailing, 16)
                    .padding(.top, 6)
                    .transition(.opacity.combined(with: .scale(scale: 0.85)))
            }
            if model.barVisible, let days = model.daysLeft {
                ExpiryBanner(days: days, text: model.core.chrome.text)
                    .padding(.horizontal, 16)
                    .padding(.bottom, 72)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .sheet(isPresented: settingsShown) {
            SettingsView(model: model.settings) { model.core.chrome.back() }
                .preferredColorScheme(model.dark ? .dark : .light)
        }
    }
}

/// The current tab's toolbar buttons, floating in the top corner like iOS 26 toolbar items.
private struct TopButtons: View {
    @ObservedObject var model: AppModel
    @Environment(\.openURL) private var openURL

    private var chrome: NativeChrome { model.core.chrome }

    var body: some View {
        let text = chrome.text
        switch model.tab {
        case 1:
            HStack(spacing: 0) {
                Button { chrome.toggleWeekView() } label: {
                    ToolbarIcon(symbol: model.weekView ? "rectangle.split.1x2" : "list.bullet")
                }
                .accessibilityLabel(model.weekView ? text.dayView : text.weekView)
                Menu {
                    Toggle(isOn: Binding(get: { model.permanent }, set: { _ in chrome.togglePermanent() })) {
                        Label(text.permanentTimetable, systemImage: "calendar.badge.clock")
                    }
                    Button { chrome.openSettings() } label: {
                        Label(text.settings, systemImage: "gearshape")
                    }
                } label: {
                    ToolbarIcon(symbol: "ellipsis")
                }
                .accessibilityLabel(text.more)
            }
            .buttonStyle(.plain)
            .modifier(GlassShape(capsule: true))
        case 2:
            Button {
                if let url = URL(string: chrome.iCanteenUrl) { openURL(url) }
            } label: {
                ToolbarIcon(symbol: "safari")
            }
            .buttonStyle(.plain)
            .accessibilityLabel(text.openICanteen)
            .modifier(GlassShape(capsule: false))
        default:
            Button { chrome.openSettings() } label: {
                ToolbarIcon(symbol: "gearshape")
            }
            .buttonStyle(.plain)
            .accessibilityLabel(text.settings)
            .modifier(GlassShape(capsule: false))
        }
    }
}

/// Shown a few days before SideStore's 7-day signature runs out, in case the refresh automation
/// didn't run. Tapping it opens SideStore.
private struct ExpiryBanner: View {
    let days: Int
    let text: Strings
    @Environment(\.openURL) private var openURL

    var body: some View {
        Button {
            if let url = URL(string: "sidestore://") { openURL(url) }
        } label: {
            HStack(spacing: 12) {
                Image(systemName: "exclamationmark.arrow.circlepath")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(Color(lunchOrange))
                VStack(alignment: .leading, spacing: 1) {
                    Text(text.signingExpires(days: Int32(days)))
                        .font(.subheadline.weight(.semibold))
                    Text(text.renewInSideStore)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
            }
            .foregroundStyle(.primary)
            .padding(.horizontal, 18)
            .padding(.vertical, 10)
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .modifier(GlassShape(capsule: true))
    }
}

/// When the signature SideStore put on this app expires. Nil in builds it hasn't signed.
enum SigningExpiry {
    static func date() -> Date? {
        guard let url = Bundle.main.url(forResource: "embedded", withExtension: "mobileprovision"),
              let data = try? Data(contentsOf: url),
              let start = data.range(of: Data("<?xml".utf8)),
              let end = data.range(of: Data("</plist>".utf8), in: start.lowerBound ..< data.endIndex)
        else { return nil }
        let plist = data.subdata(in: start.lowerBound ..< end.upperBound)
        let dict = try? PropertyListSerialization.propertyList(from: plist, format: nil) as? [String: Any]
        return dict?["ExpirationDate"] as? Date
    }

    /// Whole days left, or nil while there are still at least three.
    static func daysLeft(now: Date = Date()) -> Int? {
        guard let expiry = date() else { return nil }
        let left = expiry.timeIntervalSince(now)
        guard left < 3 * 86_400 else { return nil }
        return max(0, Int(left / 86_400))
    }
}

private struct ToolbarIcon: View {
    let symbol: String

    var body: some View {
        Image(systemName: symbol)
            .font(.system(size: 17, weight: .semibold))
            .foregroundStyle(.primary)
            .frame(width: 44, height: 44)
            .contentShape(Rectangle())
    }
}

private struct GlassShape: ViewModifier {
    let capsule: Bool

    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            if capsule {
                content.glassEffect(.regular.interactive(), in: Capsule())
            } else {
                content.glassEffect(.regular.interactive(), in: Circle())
            }
        } else if capsule {
            content.background(.ultraThinMaterial, in: Capsule())
        } else {
            content.background(.ultraThinMaterial, in: Circle())
        }
    }
}

struct ChromeHost: UIViewControllerRepresentable {
    let core: SchoolIos
    let tab: Int
    let titles: [String]
    let barVisible: Bool

    func makeUIViewController(context: Context) -> ChromeController { ChromeController(core: core) }

    func updateUIViewController(_ controller: ChromeController, context: Context) {
        controller.apply(tab: tab, titles: titles, barVisible: barVisible)
    }
}

/// Sends touches to the tab bar when they land on it and to the Compose screens otherwise.
/// The tab bar controller covers the whole screen, so without this it would swallow every touch.
final class ChromeView: UIView {
    weak var content: UIView?
    weak var tabs: UITabBarController?
    var barActive = false

    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        if barActive, let tabs,
           let hit = tabs.view.hitTest(convert(point, to: tabs.view), with: event),
           isBarPart(hit, of: tabs) {
            return hit
        }
        guard let content else { return nil }
        return content.hitTest(convert(point, to: content), with: event)
    }

    /// Anything the controller hit-tests that is not its empty page (or a container around it) is the bar.
    private func isBarPart(_ hit: UIView, of tabs: UITabBarController) -> Bool {
        if hit === tabs.view || hit === tabs.tabBar { return false }
        if let page = tabs.selectedViewController?.viewIfLoaded,
           page.isDescendant(of: hit) || hit.isDescendant(of: page) {
            return false
        }
        return true
    }
}

/// Hosts the Compose screens with a real UITabBarController floating over them. Its pages are
/// empty and see-through; it is there only for the system tab bar, which iOS 26 draws as
/// Liquid Glass with the draggable selection lens. A hand-built bar cannot get that lens:
/// glass placed inside glass renders as a flat tint.
final class ChromeController: UIViewController, UITabBarControllerDelegate {
    private let core: SchoolIos
    private let content: UIViewController
    private let tabs = UITabBarController()
    private var shown = false
    private var modelTab = 0

    private let symbols = ["sun.max", "calendar", "fork.knife"]
    private let selectedSymbols = ["sun.max.fill", "calendar", "fork.knife"]

    init(core: SchoolIos) {
        self.core = core
        content = core.makeViewController()
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("not used") }

    override func loadView() {
        let root = ChromeView()
        root.tabs = tabs
        view = root
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        embed(content)
        (view as? ChromeView)?.content = content.view

        tabs.viewControllers = symbols.indices.map { index in
            let page = UIViewController()
            page.view.backgroundColor = .clear
            page.view.isUserInteractionEnabled = false
            page.tabBarItem = UITabBarItem(
                title: nil,
                image: UIImage(systemName: symbols[index]),
                selectedImage: UIImage(systemName: selectedSymbols[index])
            )
            return page
        }
        tabs.delegate = self
        tabs.view.backgroundColor = .clear
        tabs.tabBar.tintColor = lunchOrange
        if #unavailable(iOS 26.0) {
            // Before Liquid Glass a bar over non-scrolling content is fully transparent by default.
            let appearance = UITabBarAppearance()
            appearance.configureWithDefaultBackground()
            tabs.tabBar.standardAppearance = appearance
            tabs.tabBar.scrollEdgeAppearance = appearance
        }
        embed(tabs)
        tabs.view.alpha = 0
    }

    private func embed(_ child: UIViewController) {
        addChild(child)
        child.view.frame = view.bounds
        child.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(child.view)
        child.didMove(toParent: self)
    }

    override var childForStatusBarStyle: UIViewController? { content }
    override var childForHomeIndicatorAutoHidden: UIViewController? { content }

    func apply(tab: Int, titles: [String], barVisible: Bool) {
        loadViewIfNeeded()
        let pages = tabs.viewControllers ?? []
        for (index, page) in pages.enumerated() where index < titles.count && page.tabBarItem.title != titles[index] {
            page.tabBarItem.title = titles[index]
        }
        // Follow the shared app only when it actually moved, so a tap is not undone by an update
        // that was already on its way.
        if tab != modelTab {
            modelTab = tab
            if tab < pages.count, tabs.selectedIndex != tab { tabs.selectedIndex = tab }
        }
        guard barVisible != shown else { return }
        shown = barVisible
        (view as? ChromeView)?.barActive = barVisible
        UIView.animate(
            withDuration: 0.4, delay: 0, usingSpringWithDamping: 0.85, initialSpringVelocity: 0,
            options: [.beginFromCurrentState, .allowUserInteraction]
        ) {
            self.tabs.view.alpha = barVisible ? 1 : 0
            self.tabs.view.transform = barVisible ? .identity : CGAffineTransform(translationX: 0, y: 30)
        }
    }

    func tabBarController(_ tabBarController: UITabBarController, didSelect viewController: UIViewController) {
        let index = tabBarController.selectedIndex
        guard index != modelTab else { return }
        modelTab = index
        core.chrome.select(tab: Int32(index))
    }
}
