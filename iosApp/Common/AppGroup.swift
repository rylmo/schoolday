import Foundation

/// The folder the app and its widgets share. SideStore registers the app group under a new name
/// (it appends the signing team's ID), so the real name is read from the signing profile, with
/// the plain name as a fallback. Every call returns nil when sharing isn't available, and callers
/// then work without it.
enum AppGroup {
    static let base = "group.app.schoolday.ios"

    static let id: String? = {
        let candidates = profileGroups() + [base]
        return candidates.first { FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: $0) != nil }
    }()

    static var container: URL? {
        id.flatMap { FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: $0) }
    }

    static func write(_ name: String, _ text: String) {
        guard let url = container?.appendingPathComponent(name) else { return }
        try? Data(text.utf8).write(to: url, options: .atomic)
    }

    static func read(_ name: String) -> Data? {
        guard let url = container?.appendingPathComponent(name) else { return nil }
        return try? Data(contentsOf: url)
    }

    /// App groups listed in this bundle's embedded.mobileprovision (the app's or the widget's).
    private static func profileGroups() -> [String] {
        guard let url = Bundle.main.url(forResource: "embedded", withExtension: "mobileprovision"),
              let data = try? Data(contentsOf: url),
              let start = data.range(of: Data("<?xml".utf8)),
              let end = data.range(of: Data("</plist>".utf8), in: start.lowerBound ..< data.endIndex),
              let plist = try? PropertyListSerialization.propertyList(from: data.subdata(in: start.lowerBound ..< end.upperBound), format: nil) as? [String: Any],
              let entitlements = plist["Entitlements"] as? [String: Any],
              let groups = entitlements["com.apple.security.application-groups"] as? [String]
        else { return [] }
        return groups.filter { $0.hasPrefix(base) }
    }
}

/// What the app shares with the widgets.
enum SharedFile {
    /// The app's lunch menu (iCanteen with what's ordered, or the school website).
    static let lunch = "lunch.json"
    /// {"id": "43", "name": "IT3B"}: the class chosen in the app's Settings.
    static let myClass = "myclass.json"
}
