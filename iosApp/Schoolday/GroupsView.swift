import Shared
import SwiftUI

private let accent = Color(red: 0.949, green: 0.651, blue: 0.353)

/// Settings › Your groups: each split subject of your class with a row of glass chips, "All" first.
struct GroupsView: View {
    @ObservedObject var model: SettingsModel

    private var text: Strings { model.bridge.text }

    var body: some View {
        List {
            if let snap = model.snapshot {
                let rows = snap.groups
                if rows.isEmpty {
                    Section {
                        HStack(spacing: 10) {
                            if snap.groupsLoading { ProgressView() }
                            Text(snap.groupsLoading ? text.loading : text.noSplits).foregroundStyle(.secondary)
                        }
                    } footer: {
                        Text(text.praxeNote)
                    }
                }
                ForEach(0 ..< rows.count, id: \.self) { i in
                    let row = rows[i]
                    Section {
                        chips(row)
                            .listRowInsets(EdgeInsets(top: 10, leading: 10, bottom: 10, trailing: 10))
                    } header: {
                        HStack(alignment: .firstTextBaseline) {
                            Text(row.title)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(.primary)
                                .lineLimit(1)
                            Spacer(minLength: 8)
                            Text(row.kind).font(.caption).foregroundStyle(.secondary)
                        }
                        .textCase(nil)
                    } footer: {
                        if i == rows.count - 1 { Text(text.praxeNote) }
                    }
                }
            }
        }
        .navigationTitle(text.yourGroups)
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { model.bridge.loadGroups() }
        .tint(accent)
    }

    private func chips(_ row: GroupChoiceRow) -> some View {
        let options = row.options
        return HStack(spacing: 6) {
            ForEach(0 ..< options.count, id: \.self) { i in
                let option = options[i]
                Button {
                    model.bridge.pickGroup(key: row.key, code: option.key)
                } label: {
                    VStack(spacing: 1) {
                        Text(option.name).font(.system(size: 14, weight: .bold))
                        Text(option.group)
                            .font(.system(size: 11))
                            .opacity(0.85)
                            .lineLimit(1)
                            .truncationMode(.tail)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 3)
                }
                // Each chip needs its own style, or a tap in the row fires every button in it.
                .modifier(GlassButton(prominent: option.key == row.picked))
            }
        }
    }
}
