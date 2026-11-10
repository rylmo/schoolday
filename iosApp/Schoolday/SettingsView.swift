import Shared
import SwiftUI
import UIKit

private let lunchOrange = Color(red: 0.949, green: 0.651, blue: 0.353)

/// Mirrors the shared app's settings for the native Settings screen.
final class SettingsModel: NSObject, ObservableObject, SettingsListener {
    let bridge: SettingsBridge
    @Published var snapshot: SettingsSnapshot?

    init(bridge: SettingsBridge) {
        self.bridge = bridge
        super.init()
        bridge.watch(listener: self)
    }

    func changed(snapshot: SettingsSnapshot) {
        self.snapshot = snapshot
    }
}

/// Settings as a native iOS screen: system switches, menu pickers and Liquid Glass buttons.
/// The lunch notification and update check are left out because iOS doesn't have them.
struct SettingsView: View {
    @ObservedObject var model: SettingsModel
    let close: () -> Void

    @State private var user = ""
    @State private var password = ""
    @State private var busy = false
    @State private var loginError: String?
    @State private var copied = false

    private var text: Strings { model.bridge.text }
    private var bridge: SettingsBridge { model.bridge }

    var body: some View {
        NavigationStack {
            Group {
                if let snap = model.snapshot {
                    form(snap)
                } else {
                    ProgressView()
                }
            }
            .navigationTitle(text.settings)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    if #available(iOS 26.0, *) {
                        Button(role: .close) { close() }
                    } else {
                        Button(text.done) { close() }
                    }
                }
            }
        }
        .tint(lunchOrange)
    }

    private func form(_ snap: SettingsSnapshot) -> some View {
        Form {
            iCanteenSection(snap)
            classesSection(snap)
            appSection(snap)
            Section {
                Button {
                    UIPasteboard.general.string = bridge.debugInfo()
                    copied = true
                } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(text.copyDebug).foregroundStyle(.primary)
                        Text(copied ? text.copied : text.copyDebugSub)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
            } header: {
                Text(text.help)
            } footer: {
                Text(text.footer(v: snap.version))
            }
        }
    }

    @ViewBuilder
    private func iCanteenSection(_ snap: SettingsSnapshot) -> some View {
        if snap.loggedIn {
            Section(text.iCanteen) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(snap.icUser)
                        Text(text.loggedIn).font(.footnote).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Button(text.logOut, role: .destructive) {
                        password = ""
                        bridge.logOut()
                    }
                    .modifier(GlassButton(prominent: false))
                }
            }
        } else {
            Section {
                TextField(text.username, text: $user)
                    .textContentType(.username)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                SecureField(text.password, text: $password)
                    .textContentType(.password)
                    .onSubmit(logIn)
            } header: {
                Text(text.iCanteen)
            }
            .onAppear { if user.isEmpty { user = snap.icUser } }
            // The button gets a section of its own: as a see-through row inside the fields' card it
            // cut the card's rounded bottom off.
            Section {
                Button(action: logIn) {
                    HStack(spacing: 8) {
                        if busy { ProgressView() }
                        Text(text.logIn).fontWeight(.semibold)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 4)
                }
                .modifier(GlassButton(prominent: true))
                .disabled(busy || user.trimmingCharacters(in: .whitespaces).isEmpty || password.isEmpty)
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets(top: 8, leading: 0, bottom: 8, trailing: 0))
            } footer: {
                VStack(alignment: .leading, spacing: 6) {
                    if let loginError {
                        Text(loginError).foregroundStyle(.red)
                    }
                    Text(text.savedEncrypted)
                }
            }
        }
    }

    private func classesSection(_ snap: SettingsSnapshot) -> some View {
        let shown = snap.shownClasses
        let addable = snap.addableClasses
        let all = snap.allClasses
        return Section {
            ForEach(0 ..< shown.count, id: \.self) { i in
                Text(shown[i].name)
            }
            .onDelete { rows in
                rows.map { shown[$0].key }.forEach { bridge.removeClass(key: $0) }
            }
            Menu {
                ForEach(0 ..< addable.count, id: \.self) { i in
                    Button(addable[i].name) { bridge.addClass(key: addable[i].key) }
                }
            } label: {
                Label(text.addClass, systemImage: "plus.circle.fill")
            }
            .disabled(addable.isEmpty)
            Picker(selection: Binding(get: { snap.myClass }, set: { bridge.updateMyClass(key: $0) })) {
                ForEach(0 ..< all.count, id: \.self) { i in
                    Text(all[i].name).tag(all[i].key)
                }
            } label: {
                VStack(alignment: .leading, spacing: 2) {
                    Text(text.yourClass)
                    Text(text.yourClassSub).font(.footnote).foregroundStyle(.secondary)
                }
            }
            .pickerStyle(.menu)
            Toggle(text.showMyClass, isOn: Binding(get: { snap.showMyClass }, set: { bridge.updateShowMyClass(on: $0) }))
        } header: {
            Text(text.classes)
        }
    }

    private func appSection(_ snap: SettingsSnapshot) -> some View {
        let themes = snap.themes
        let languages = snap.languages
        return Section(text.app) {
            Picker(selection: Binding(get: { snap.theme }, set: { bridge.updateTheme(key: $0) })) {
                ForEach(["", "dark", "light"], id: \.self) { group in
                    let rows = (0 ..< themes.count).filter { themes[$0].group == group }
                    if group.isEmpty {
                        ForEach(rows, id: \.self) { i in themeRow(themes[i]) }
                    } else {
                        Section(group == "dark" ? text.dark : text.light) {
                            ForEach(rows, id: \.self) { i in themeRow(themes[i]) }
                        }
                    }
                }
            } label: {
                Text(text.colours)
            }
            .pickerStyle(.navigationLink)
            Picker(text.language, selection: Binding(get: { snap.language }, set: { bridge.updateLanguage(key: $0) })) {
                ForEach(0 ..< languages.count, id: \.self) { i in
                    Text(languages[i].name).tag(languages[i].key)
                }
            }
            .pickerStyle(.menu)
        }
    }

    private func themeRow(_ theme: SettingsOption) -> some View {
        HStack(spacing: 12) {
            Swatch(colors: theme.swatch)
            Text(theme.name)
        }
        .tag(theme.key)
    }

    private func logIn() {
        guard !busy, !user.isEmpty, !password.isEmpty else { return }
        busy = true
        loginError = nil
        bridge.logIn(user: user, password: password) { error in
            busy = false
            loginError = error
            if error == nil { password = "" }
        }
    }
}

/// Three overlapping dots: background, accent, lunch.
private struct Swatch: View {
    let colors: [String]

    var body: some View {
        HStack(spacing: -8) {
            ForEach(0 ..< colors.count, id: \.self) { i in
                Circle()
                    .fill(Color(hex: colors[i]))
                    .overlay(Circle().stroke(Color.secondary.opacity(0.4), lineWidth: 1))
                    .frame(width: 20, height: 20)
            }
        }
        .frame(width: 44, alignment: .leading)
    }
}

private struct GlassButton: ViewModifier {
    let prominent: Bool

    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            if prominent {
                content.buttonStyle(.glassProminent)
            } else {
                content.buttonStyle(.glass)
            }
        } else if prominent {
            content.buttonStyle(.borderedProminent)
        } else {
            content.buttonStyle(.bordered)
        }
    }
}

private extension Color {
    init(hex: String) {
        let value = UInt32(hex, radix: 16) ?? 0
        self.init(
            red: Double((value >> 16) & 0xFF) / 255,
            green: Double((value >> 8) & 0xFF) / 255,
            blue: Double(value & 0xFF) / 255
        )
    }
}
