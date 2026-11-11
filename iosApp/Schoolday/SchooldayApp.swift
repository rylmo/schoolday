import Security
import Shared
import SwiftUI

/// Stores the iCanteen password in the iOS Keychain.
final class KeychainStore: NSObject, SecureStore {
    private func query(_ key: String) -> [String: Any] {
        [kSecClass as String: kSecClassGenericPassword,
         kSecAttrService as String: "schoolday",
         kSecAttrAccount as String: key]
    }

    func get(key: String) -> String? {
        var q = query(key)
        q[kSecReturnData as String] = true
        q[kSecMatchLimit as String] = kSecMatchLimitOne
        var out: AnyObject?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess, let data = out as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    func set(key: String, value: String?) {
        SecItemDelete(query(key) as CFDictionary)
        guard let value, let data = value.data(using: .utf8) else { return }
        var q = query(key)
        q[kSecValueData as String] = data
        q[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        SecItemAdd(q as CFDictionary, nil)
    }
}

/// Mirrors the shared app's navigation state so SwiftUI can draw the native controls.
final class AppModel: NSObject, ObservableObject, ChromeListener {
    let core: SchoolIos
    let settings: SettingsModel
    let installer: SideStoreInstaller
    @Published var tab = 0
    @Published var settingsOpen = false
    @Published var barVisible = false
    @Published var dark = true
    @Published var titles = ["Today", "Timetable", "Lunch"]
    @Published var weekView = false
    @Published var permanent = false
    /// The newer version on offer, shown as an alert; nil when there is none.
    @Published var updateVersion: String?

    func updateChanged(available: Bool, version: String) {
        updateVersion = available ? version : nil
    }
    /// Days until SideStore's signature runs out, once there are fewer than three.
    @Published var daysLeft: Int? = SigningExpiry.daysLeft()

    func checkExpiry() {
        let days = SigningExpiry.daysLeft()
        if days != daysLeft { withAnimation { daysLeft = days } }
    }

    override init() {
        let installer = SideStoreInstaller()
        self.installer = installer
        let core = SchoolIos(secure: KeychainStore(), installer: installer, shared: GroupFiles())
        self.core = core
        settings = SettingsModel(bridge: core.settingsBridge)
        super.init()
        core.chrome.listener = self
    }

    func timetableChanged(weekView: Bool, permanent: Bool) {
        self.weekView = weekView
        self.permanent = permanent
    }

    func changed(tab: Int32, settingsOpen: Bool, visible: Bool, dark: Bool, titles: [String]) {
        withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
            self.tab = Int(tab)
            self.settingsOpen = settingsOpen
            self.barVisible = visible
        }
        self.dark = dark
        self.titles = titles
    }
}

@main
struct SchooldayApp: App {
    @StateObject private var model = AppModel()
    @Environment(\.scenePhase) private var phase

    var body: some Scene {
        WindowGroup {
            RootView(model: model)
                .preferredColorScheme(model.dark ? .dark : .light)
                .onOpenURL { url in
                    // A bare schoolday:// is LocalDevVPN handing back during an update.
                    if (url.host ?? "").isEmpty, model.installer.resume() { return }
                    model.core.openLink(url: url.absoluteString)
                }
        }
        .onChange(of: phase) { newPhase in
            if newPhase == .active {
                model.core.onForeground()
                model.checkExpiry()
                if model.installer.consumeRecheck() { model.core.recheckUpdate() }
            }
        }
    }
}
