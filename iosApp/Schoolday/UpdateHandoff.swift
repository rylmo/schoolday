import CFNetwork
import Shared
import UIKit

/// Gets an update to SideStore, which can only install while LocalDevVPN is on: if no VPN is
/// connected, LocalDevVPN is asked to turn itself on and hand back (localdevvpn://enable, which
/// opens schoolday:// a second later), then SideStore gets the .ipa link. Coming back to the app
/// still on the old build afterwards offers the update again.
final class SideStoreInstaller: NSObject, UpdateInstaller {
    /// The .ipa waiting for LocalDevVPN to hand control back.
    private var pending: String?
    private var handedOffAt: Date?

    func install(url: String) {
        if Self.vpnConnected() {
            handOff(url)
            return
        }
        pending = url
        guard let enable = URL(string: "localdevvpn://enable?scheme=schoolday") else {
            handOff(url)
            return
        }
        UIApplication.shared.open(enable) { opened in
            // No LocalDevVPN on this phone: go ahead, SideStore says what is missing.
            if !opened {
                self.pending = nil
                self.handOff(url)
            }
        }
    }

    /// schoolday:// with nothing after it: LocalDevVPN is back from turning the VPN on.
    /// Returns false when no update was waiting for it.
    func resume() -> Bool {
        guard let url = pending else { return false }
        pending = nil
        // A moment for the tunnel to finish coming up.
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) { self.handOff(url) }
        return true
    }

    private func handOff(_ ipa: String) {
        var link = URLComponents()
        link.scheme = "sidestore"
        link.host = "install"
        link.queryItems = [URLQueryItem(name: "url", value: ipa)]
        handedOffAt = Date()
        guard let sideStore = link.url else { return }
        UIApplication.shared.open(sideStore) { opened in
            // No SideStore: download the .ipa in Safari instead.
            if !opened, let web = URL(string: ipa) { UIApplication.shared.open(web) }
        }
    }

    /// True once when the app comes back after a hand-off. Still running means still the old
    /// build (a successful install restarts the app), so the update is worth offering again.
    func consumeRecheck() -> Bool {
        guard let at = handedOffAt, Date().timeIntervalSince(at) > 2 else { return false }
        handedOffAt = nil
        return true
    }

    /// Whether any VPN is connected: iOS lists a VPN's tunnel among the scoped proxy settings.
    static func vpnConnected() -> Bool {
        guard let settings = CFNetworkCopySystemProxySettings()?.takeRetainedValue() as? [String: Any],
              let scoped = settings["__SCOPED__"] as? [String: Any] else { return false }
        return scoped.keys.contains { key in ["tap", "tun", "ppp", "ipsec"].contains { key.contains($0) } }
    }
}
