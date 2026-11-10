import Shared
import SwiftUI
import UIKit

private let lunchOrange = UIColor(red: 0.949, green: 0.651, blue: 0.353, alpha: 1)

/// The shared Compose screens with native controls above them: the system tab bar (Liquid Glass
/// on iOS 26, including its selection lens) and a glass back button for Settings.
struct RootView: View {
    @ObservedObject var model: AppModel

    var body: some View {
        ZStack {
            ChromeHost(core: model.core, tab: model.tab, titles: model.titles, barVisible: model.barVisible)
                .ignoresSafeArea()
            if model.settingsOpen {
                VStack(spacing: 0) {
                    HStack {
                        GlassBackButton { model.core.chrome.back() }
                        Spacer()
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 6)
                    Spacer(minLength: 0)
                }
                .transition(.opacity.combined(with: .scale(scale: 0.8)))
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
