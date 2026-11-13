import Shared
import SwiftUI

private let accent = Color(red: 0.949, green: 0.651, blue: 0.353)

/// One change: a symbol, a title and a line about it.
private struct NoteRow: View {
    let note: ReleaseNote

    var body: some View {
        HStack(alignment: .top, spacing: 16) {
            Image(systemName: note.symbol)
                .font(.system(size: 26))
                .foregroundStyle(accent)
                .frame(width: 40)
            VStack(alignment: .leading, spacing: 3) {
                Text(note.title).font(.headline)
                Text(note.body)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
    }
}

/// Shown once after an update, like Apple's own What's New sheets.
struct WhatsNewSheet: View {
    let releases: [ReleaseNotes]
    let text: Strings
    let done: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 26) {
                    Text(text.changesHeading)
                        .font(.largeTitle.bold())
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                        .padding(.top, 44)
                        .padding(.bottom, 6)
                    ForEach(releases, id: \.version) { release in
                        // Several versions at once (skipped updates): say which brought what.
                        if releases.count > 1 {
                            Text("Schoolday \(release.version)")
                                .font(.title3.bold())
                                .foregroundStyle(.secondary)
                        }
                        ForEach(0 ..< release.notes.count, id: \.self) { i in
                            NoteRow(note: release.notes[i])
                        }
                    }
                }
                .padding(.horizontal, 32)
                .padding(.bottom, 24)
            }
            Button(action: done) {
                Text(text.continueLabel)
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
            }
            .modifier(GlassButton(prominent: true))
            .padding(.horizontal, 32)
            .padding(.bottom, 20)
        }
        .tint(accent)
    }
}

/// Settings › What's new: every version's changes, newest first.
struct ReleaseHistory: View {
    let notes: [ReleaseNotes]
    let title: String

    var body: some View {
        List {
            ForEach(notes, id: \.version) { release in
                Section("Schoolday \(release.version)") {
                    ForEach(0 ..< release.notes.count, id: \.self) { i in
                        NoteRow(note: release.notes[i]).padding(.vertical, 4)
                    }
                }
            }
        }
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// Decides when What's new shows: once per new version, never on a fresh install.
enum WhatsNewGate {
    private static let seenKey = "notesSeen"

    static var current: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
    }

    /// The versions to show now: those after the last one seen, up to this one.
    static func pending(_ all: [ReleaseNotes]) -> [ReleaseNotes] {
        let defaults = UserDefaults.standard
        guard let seen = defaults.string(forKey: seenKey) else {
            // Set up before this screen existed: show what this version brought.
            if defaults.object(forKey: "onboarded") != nil || defaults.object(forKey: "myClass") != nil {
                return all.filter { $0.version == current }
            }
            // A fresh install has nothing to catch up on.
            markSeen()
            return []
        }
        return all.filter { newer($0.version, than: seen) && !newer($0.version, than: current) }
    }

    static func markSeen() {
        UserDefaults.standard.set(current, forKey: seenKey)
    }

    private static func newer(_ a: String, than b: String) -> Bool {
        a.compare(b, options: .numeric) == .orderedDescending
    }
}
