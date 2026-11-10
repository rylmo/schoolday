import AppIntents
import SwiftUI
import WidgetKit

// MARK: - texts

private enum W {
    static let czech = (Locale.preferredLanguages.first ?? "").hasPrefix("cs")
    static func t(_ en: String, _ cs: String) -> String { czech ? cs : en }

    static var today: String { t("TODAY", "DNES") }
    static var tomorrow: String { t("TOMORROW", "ZÍTRA") }
    static var weekdays: [String] {
        czech ? ["NEDĚLE", "PONDĚLÍ", "ÚTERÝ", "STŘEDA", "ČTVRTEK", "PÁTEK", "SOBOTA"]
            : ["SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"]
    }
    static var chooseClass: String { t("Hold the widget and choose Edit Widget to pick a class.", "Podrž widget a v Upravit widget vyber třídu.") }
    static var noLessons: String { t("No lessons", "Žádné hodiny") }
    static var cantLoad: String { t("Can't load the timetable", "Rozvrh se nepodařilo načíst") }
    static var offline: String { t("offline", "offline") }
}

// MARK: - layout numbers (shared by the provider, which plans the windows, and the views)

private enum Metrics {
    static let padding: CGFloat = 12
    static let headerHeight: CGFloat = 22
    static let headerGap: CGFloat = 6
    static let smallRowHeight: CGFloat = 26
    static let rowHeight: CGFloat = 30
    /// Each extra group in a merged row adds a detail line (medium only).
    static let extraLine: CGFloat = 13
    static let maxGroupLines = 3
    /// Small: two round buttons in the header. Medium: two bigger ones beside the rows.
    static let headerArrow: CGFloat = 22
    static let sideArrow: CGFloat = 30
    static let sideArrowGap: CGFloat = 10
    /// How far each arrow tap moves.
    static let step = 2

    static func gap(small: Bool) -> CGFloat { small ? 3 : 4 }

    static func height(_ row: LessonRow, small: Bool) -> CGFloat {
        small ? smallRowHeight : rowHeight + extraLine * CGFloat(min(row.parts.count, maxGroupLines) - 1)
    }
}

// MARK: - configuration

struct ClassEntity: AppEntity {
    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Class"
    static var defaultQuery = ClassQuery()

    var id: String
    var name: String

    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(name)") }
}

struct ClassQuery: EntityQuery {
    private static let cacheKey = "classes"

    private func all() async -> [ClassEntity] {
        if let fresh = try? await Bakalari.classes(), !fresh.isEmpty {
            if let data = try? JSONEncoder().encode(fresh) { UserDefaults.standard.set(data, forKey: Self.cacheKey) }
            return fresh.map { ClassEntity(id: $0.id, name: $0.name) }
        }
        guard let data = UserDefaults.standard.data(forKey: Self.cacheKey),
              let cached = try? JSONDecoder().decode([WidgetClass].self, from: data) else { return [] }
        return cached.map { ClassEntity(id: $0.id, name: $0.name) }
    }

    private func cached() -> [ClassEntity] {
        guard let data = UserDefaults.standard.data(forKey: Self.cacheKey),
              let list = try? JSONDecoder().decode([WidgetClass].self, from: data) else { return [] }
        return list.map { ClassEntity(id: $0.id, name: $0.name) }
    }

    /// Runs on every redraw (each arrow tap too), so it uses the saved class list and only
    /// downloads when a class isn't in it. Downloading here is what made taps slow.
    func entities(for identifiers: [String]) async throws -> [ClassEntity] {
        var known = cached()
        if identifiers.contains(where: { id in !known.contains { $0.id == id } }) { known = await all() }
        return identifiers.map { id in known.first { $0.id == id } ?? ClassEntity(id: id, name: id) }
    }

    func suggestedEntities() async throws -> [ClassEntity] { await all() }
}

struct TimetableWidgetIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Timetable"
    static var description = IntentDescription("Shows a class's lessons for the week.")

    @Parameter(title: "Class")
    var schoolClass: ClassEntity?
}

/// The ▲ ▼ buttons: move two lines up or down through the week.
struct ShiftPageIntent: AppIntent {
    static var title: LocalizedStringResource = "Show other lessons"
    static var isDiscoverable = false

    @Parameter(title: "Class") var classId: String
    @Parameter(title: "Step") var step: Int

    init() {}

    init(classId: String, step: Int) {
        self.classId = classId
        self.step = step
    }

    func perform() async throws -> some IntentResult {
        WidgetCache.noteTap(step)
        WidgetCache.setOffset(classId, WidgetCache.offset(classId) + step)
        return .result()
    }
}

// MARK: - rows and windows

/// Everything in one time slot. Groups of the class (ASW1 and ASW2, Aj1 and Nj…) become one row,
/// since the public timetable can't tell which group you're in.
struct LessonRow: Hashable {
    var period: String
    var start: Int
    var end: Int
    var parts: [WidgetLesson]

    var changed: Bool { parts.contains { $0.changed } }
    var cancelled: Bool { parts.allSatisfy { $0.cancelled } }

    private func distinct(_ values: [String]) -> [String] {
        var seen = Set<String>()
        return values.filter { !$0.isEmpty && seen.insert($0).inserted }
    }

    var subject: String { distinct(parts.map(\.subject)).joined(separator: " / ") }
    var abbrev: String { distinct(parts.map { $0.abbrev.isEmpty ? $0.subject : $0.abbrev }).joined(separator: "/") }
    var rooms: String { distinct(parts.map(\.room)).joined(separator: " · ") }
    var mixedSubjects: Bool { distinct(parts.map(\.subject)).count > 1 }

    static func of(_ day: WidgetDay) -> [LessonRow] {
        var rows: [LessonRow] = []
        for lesson in day.lessons {
            if let i = rows.firstIndex(where: { $0.start == lesson.start }) {
                rows[i].parts.append(lesson)
                rows[i].end = max(rows[i].end, lesson.end)
            } else {
                rows.append(LessonRow(period: lesson.period, start: lesson.start, end: lesson.end, parts: [lesson]))
            }
        }
        // A lesson moved away or cancelled only stays (struck through) when nothing replaces it,
        // so "ZSV moved here, MAT moved away" shows just ZSV, not two groups.
        for i in rows.indices where rows[i].parts.contains(where: { !$0.cancelled }) {
            rows[i].parts.removeAll { $0.cancelled }
        }
        return rows.sorted { $0.start < $1.start }
    }
}

/// What the widget shows at once: one day, scrolled so that row [first] is at the top.
/// All of the day's rows are kept, so moving within a day slides them instead of swapping them.
struct Window: Hashable {
    var day: WidgetDay
    var first: Int
    var rows: [LessonRow]
    /// How many rows from [first] fit; the rest fade out instead of showing half-cut at the edge.
    var count: Int = 0

    var key: String { "\(day.date)#\(first)" }

    /// How far the day's rows are moved up to put row [first] at the top.
    func offset(small: Bool) -> CGFloat {
        rows.prefix(first).reduce(0) { $0 + Metrics.height($1, small: small) + Metrics.gap(small: small) }
    }
}

enum Windows {
    /// Every window of the two weeks, moving two rows at a time, and the one shown by itself:
    /// starting at the current lesson today, or the next school day once today's lessons are over.
    static func build(_ days: [WidgetDay], now: Date, budget: CGFloat, small: Bool) -> (windows: [Window], auto: Int) {
        let todayKey = WidgetDates.key(now)
        let minute = WidgetDates.minutes(now)
        let sorted = days.sorted { $0.date < $1.date }

        var autoDay = sorted.firstIndex { $0.date > todayKey && !$0.realLessons.isEmpty } ?? max(sorted.count - 1, 0)
        var autoRow = 0
        if let t = sorted.firstIndex(where: { $0.date == todayKey }), let end = sorted[t].endTime, minute < end {
            autoDay = t
            autoRow = LessonRow.of(sorted[t]).firstIndex { $0.end > minute } ?? 0
        }

        var windows: [Window] = []
        var auto = 0
        for (i, day) in sorted.enumerated() {
            let rows = LessonRow.of(day)
            guard !rows.isEmpty else {
                if i == autoDay { auto = windows.count }
                windows.append(Window(day: day, first: 0, rows: []))
                continue
            }
            let begin = i == autoDay ? autoRow : 0
            var starts: [Int] = []
            // upwards from the starting row…
            var s = begin - Metrics.step
            while s > 0 { starts.insert(s, at: 0); s -= Metrics.step }
            if begin > 0 { starts.insert(0, at: 0) }
            // …and downwards until the last row has been shown
            s = begin
            while true {
                starts.append(s)
                let fits = fitting(rows, from: s, budget: budget, small: small)
                if s + fits >= rows.count { break }
                s += max(1, min(Metrics.step, fits))
            }
            for start in starts {
                if i == autoDay, start == begin { auto = windows.count }
                let fits = fitting(rows, from: start, budget: budget, small: small)
                windows.append(Window(day: day, first: start, rows: rows, count: fits))
            }
        }
        return (windows, auto)
    }

    /// How many rows from [start] fit in [budget] points (always at least one).
    private static func fitting(_ rows: [LessonRow], from start: Int, budget: CGFloat, small: Bool) -> Int {
        var used: CGFloat = 0
        var count = 0
        for row in rows[start...] {
            let h = Metrics.height(row, small: small) + (count > 0 ? Metrics.gap(small: small) : 0)
            if count > 0, used + h > budget { break }
            used += h
            count += 1
        }
        return max(count, 1)
    }
}

// MARK: - timeline

struct TimetableEntry: TimelineEntry {
    let date: Date
    let classId: String?
    let className: String?
    let window: Window?
    let canUp: Bool
    let canDown: Bool
    /// Which way the rows slide in: -1 from the top (going up), +1 from the bottom.
    let direction: Int
    let offline: Bool
}

struct TimetableProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> TimetableEntry {
        TimetableEntry(date: Date(), classId: nil, className: "IT3B", window: Self.sample, canUp: true, canDown: true, direction: 1, offline: false)
    }

    func snapshot(for configuration: TimetableWidgetIntent, in context: Context) async -> TimetableEntry {
        if context.isPreview, configuration.schoolClass == nil { return placeholder(in: context) }
        return await entries(for: configuration, in: context, now: Date()).first ?? placeholder(in: context)
    }

    func timeline(for configuration: TimetableWidgetIntent, in context: Context) async -> Timeline<TimetableEntry> {
        let now = Date()
        let list = await entries(for: configuration, in: context, now: now)
        return Timeline(entries: list, policy: .after(now.addingTimeInterval(45 * 60)))
    }

    /// The saved copy when it is fresh or the user just tapped an arrow (so taps redraw at once),
    /// otherwise both weeks from Bakaláři.
    private func loadDays(for classId: String, now: Date) async -> (days: [WidgetDay], offline: Bool) {
        let cached = WidgetCache.load(classId)
        if let cached, !cached.isEmpty, WidgetCache.age(classId, now: now) < 20 * 60 || WidgetCache.recentTap(now: now) != nil {
            return (cached, false)
        }
        do {
            async let thisWeek = Bakalari.week(classId: classId, next: false)
            async let nextWeek = try? Bakalari.week(classId: classId, next: true)
            let current = try await thisWeek
            let upcoming = await nextWeek ?? []
            let fresh = current + upcoming
            WidgetCache.save(classId, fresh)
            WidgetCache.lastError = nil
            return (fresh, false)
        } catch {
            WidgetCache.lastError = String(describing: error)
            return (cached ?? [], true)
        }
    }

    private func entries(for configuration: TimetableWidgetIntent, in context: Context, now: Date) async -> [TimetableEntry] {
        guard let cls = configuration.schoolClass else {
            return [TimetableEntry(date: now, classId: nil, className: nil, window: nil, canUp: false, canDown: false, direction: 1, offline: false)]
        }
        let (days, offline) = await loadDays(for: cls.id, now: now)
        guard !days.isEmpty else {
            return [TimetableEntry(date: now, classId: cls.id, className: cls.name, window: nil, canUp: false, canDown: false, direction: 1, offline: offline)]
        }

        let small = context.family == .systemSmall
        let height = context.displaySize.height > 0 ? context.displaySize.height : 169
        let budget = height - 2 * Metrics.padding - Metrics.headerHeight - Metrics.headerGap

        // now, and each lesson end today, so the widget moves on as lessons finish
        let todayKey = WidgetDates.key(now)
        let startOfDay = Calendar.current.startOfDay(for: now)
        let ends = Set(days.first { $0.date == todayKey }?.lessons.map(\.end) ?? [])
        let times = [now] + ends.sorted()
            .map { startOfDay.addingTimeInterval(TimeInterval($0 * 60)) }
            .filter { $0 > now }
            .prefix(10)

        let offset = WidgetCache.offset(cls.id, now: now)
        let direction = WidgetCache.recentTap(now: now) ?? 1
        return times.map { time in
            let built = Windows.build(days, now: time, budget: budget, small: small)
            let index = min(max(built.auto + offset, 0), max(built.windows.count - 1, 0))
            if time == now, index - built.auto != offset {
                WidgetCache.setOffset(cls.id, index - built.auto) // taps past either end don't pile up
            }
            return TimetableEntry(
                date: time, classId: cls.id, className: cls.name,
                window: built.windows.isEmpty ? nil : built.windows[index],
                canUp: index > 0, canDown: index < built.windows.count - 1,
                direction: time == now ? direction : 1, offline: offline
            )
        }
    }

    static let sample = Window(
        day: WidgetDay(date: WidgetDates.key(Date()), dayOff: false, dayOffName: "", lessons: []),
        first: 0,
        rows: [
            LessonRow(period: "1", start: 480, end: 525, parts: [
                WidgetLesson(period: "1", start: 480, end: 525, subject: "Aplikační software", abbrev: "ASW", detail: "ASW1 · P334 · Mgr. Václav Knápek", room: "P334", changed: false, cancelled: false),
                WidgetLesson(period: "1", start: 480, end: 525, subject: "Aplikační software", abbrev: "ASW", detail: "ASW2 · P204 · Ing. Jana Vaculínová", room: "P204", changed: false, cancelled: false),
            ]),
            LessonRow(period: "2", start: 530, end: 575, parts: [
                WidgetLesson(period: "2", start: 530, end: 575, subject: "Základy elektrotechniky", abbrev: "ZET", detail: "V311 · Ing. Evžen Žabčík", room: "V311", changed: true, cancelled: false),
            ]),
        ],
        count: 2
    )
}

// MARK: - look

/// Colours of the Android Bakaláři widget for iOS's Default look; Dark, Clear and Tinted follow iOS.
private struct Palette {
    let fullColor: Bool
    let dark: Bool

    var background: Color { dark ? Color(red: 0.165, green: 0.173, blue: 0.192) : Color(red: 0.95, green: 0.95, blue: 0.96) }
    var row: Color { fullColor ? (dark ? Color.black.opacity(0.24) : Color.black.opacity(0.05)) : Color.primary.opacity(0.1) }
    var pink: Color { dark ? Color(red: 0.94, green: 0.33, blue: 0.53) : Color(red: 0.85, green: 0.11, blue: 0.38) }
    var changedRow: Color { fullColor ? pink.opacity(dark ? 0.2 : 0.12) : Color.primary.opacity(0.16) }
    var text: Color { fullColor ? (dark ? Color(white: 0.95) : Color(white: 0.1)) : .primary }
    var secondary: Color { fullColor ? (dark ? Color(white: 0.72) : Color(white: 0.35)) : .secondary }
    var accent: Color { fullColor ? pink : .primary }
}

struct TimetableWidgetView: View {
    let entry: TimetableEntry
    @Environment(\.widgetFamily) private var family
    @Environment(\.widgetRenderingMode) private var renderingMode
    @Environment(\.colorScheme) private var scheme

    private var palette: Palette { Palette(fullColor: renderingMode == .fullColor, dark: scheme == .dark) }
    private var small: Bool { family == .systemSmall }

    private var arrowsShown: Bool { entry.classId != nil && entry.window != nil }

    var body: some View {
        ZStack(alignment: .trailing) {
            VStack(alignment: .leading, spacing: Metrics.headerGap) {
                // Small: the buttons sit in the header, out of the lessons' way.
                HStack(spacing: 6) {
                    Text(dayTitle)
                        .font(.system(size: small ? 11 : 12, weight: .bold))
                        .foregroundStyle(palette.text)
                        .lineLimit(1)
                        .minimumScaleFactor(0.65)
                        .contentTransition(.opacity)
                    Spacer(minLength: 0)
                    if small, arrowsShown, let id = entry.classId {
                        arrow("chevron.up", step: -Metrics.step, enabled: entry.canUp, classId: id, size: Metrics.headerArrow)
                        arrow("chevron.down", step: Metrics.step, enabled: entry.canDown, classId: id, size: Metrics.headerArrow)
                    }
                }
                .frame(height: Metrics.headerHeight)
                HStack(spacing: 8) {
                    content
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                        .clipped()
                    // Medium: room for the side buttons, which rows rarely reach anyway.
                    if !small, arrowsShown { Color.clear.frame(width: Metrics.sideArrow) }
                }
            }
            // Medium: bigger buttons, centred on the whole widget.
            if !small, arrowsShown, let id = entry.classId {
                VStack(spacing: Metrics.sideArrowGap) {
                    arrow("chevron.up", step: -Metrics.step, enabled: entry.canUp, classId: id, size: Metrics.sideArrow)
                    arrow("chevron.down", step: Metrics.step, enabled: entry.canDown, classId: id, size: Metrics.sideArrow)
                }
            }
        }
        .padding(Metrics.padding)
        .containerBackground(for: .widget) { palette.background }
        .widgetURL(link)
    }

    private func arrow(_ symbol: String, step: Int, enabled: Bool, classId: String, size: CGFloat) -> some View {
        Button(intent: ShiftPageIntent(classId: classId, step: step)) {
            Image(systemName: symbol)
                .font(.system(size: size * 0.45, weight: .bold))
                .foregroundStyle(palette.text)
                .frame(width: size, height: size)
                .background(Circle().fill(palette.row))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.3)
    }

    @ViewBuilder
    private var content: some View {
        if entry.classId == nil {
            message(W.chooseClass)
        } else if let window = entry.window {
            Group {
                if window.rows.isEmpty {
                    message(window.day.dayOff && !window.day.dayOffName.isEmpty ? window.day.dayOffName : W.noLessons)
                } else {
                    // The whole day, moved up so row [first] is at the top. Within a day the rows keep
                    // their identity, so a tap glides them two lines instead of swapping them. They hang
                    // in an overlay so the day's full height never resizes the widget's layout.
                    Color.clear
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .overlay(alignment: .top) {
                            VStack(spacing: Metrics.gap(small: small)) {
                                ForEach(Array(window.rows.enumerated()), id: \.element.start) { index, row in
                                    Group {
                                        if small { SmallRow(row: row, palette: palette) } else { MediumRow(row: row, palette: palette) }
                                    }
                                    .opacity(index >= window.first && index < window.first + window.count ? 1 : 0)
                                }
                            }
                            .fixedSize(horizontal: false, vertical: true)
                            .offset(y: -window.offset(small: small))
                            // `.smooth` is a critically damped spring: it eases like one but never overshoots.
                            .animation(.smooth(duration: 0.4), value: window.first)
                        }
                        .clipped()
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            // A different day pushes in from the side the tap moves towards.
            .id(window.day.date)
            .transition(.push(from: entry.direction < 0 ? .top : .bottom))
            .animation(.smooth(duration: 0.4), value: window.day.date)
        } else {
            VStack(spacing: 4) {
                message(W.cantLoad)
                if let error = WidgetCache.lastError {
                    Text(error)
                        .font(.system(size: 9))
                        .foregroundStyle(palette.secondary)
                        .lineLimit(3)
                        .multilineTextAlignment(.center)
                }
            }
        }
    }

    private func message(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 13, weight: .medium))
            .foregroundStyle(palette.secondary)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
            .multilineTextAlignment(.center)
    }

    /// Opens the app on this class and the day shown.
    private var link: URL? {
        guard let id = entry.classId else { return nil }
        let day = entry.window.map { "?date=\($0.day.date)" } ?? ""
        return URL(string: "schoolday://timetable/\(id)\(day)")
    }

    private var dayDate: Date? { entry.window.flatMap { WidgetDates.date(from: $0.day.date) } }

    private var dayLabel: String {
        guard let date = dayDate else { return entry.className ?? "Schoolday" }
        let calendar = Calendar.current
        if calendar.isDate(date, inSameDayAs: entry.date) { return W.today }
        if let next = calendar.date(byAdding: .day, value: 1, to: entry.date), calendar.isDate(date, inSameDayAs: next) { return W.tomorrow }
        return W.weekdays[calendar.component(.weekday, from: date) - 1]
    }

    private var shortDate: String? {
        guard let date = dayDate else { return nil }
        let c = Calendar.current.dateComponents([.day, .month], from: date)
        return "\(c.day ?? 0). \(c.month ?? 0)."
    }

    private var dayTitle: String {
        var title = dayLabel
        if let short = shortDate { title += small ? " \(short)" : " (\(short))" }
        if !small, let name = entry.className, entry.window != nil { title = "\(name) · " + title }
        if entry.offline { title += " · " + W.offline }
        return title
    }
}

private func hm(_ minutes: Int) -> String { String(format: "%d:%02d", minutes / 60, minutes % 60) }

/// Medium: period, start and end, subject, then one "group · room · teacher" line per group.
/// Changed lessons get the pink bar.
private struct MediumRow: View {
    let row: LessonRow
    let palette: Palette

    var body: some View {
        let shown = Array(row.parts.prefix(Metrics.maxGroupLines))
        HStack(spacing: 8) {
            Capsule()
                .fill(row.changed ? palette.accent : Color.clear)
                .frame(width: 3)
                .padding(.vertical, 4)
                .widgetAccentable()
            Text(row.period)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(row.changed ? palette.accent : palette.text)
                .frame(width: 14)
                .widgetAccentable()
            VStack(alignment: .leading, spacing: 0) {
                Text(hm(row.start))
                Text(hm(row.end))
            }
            .font(.system(size: 11, weight: .medium).monospacedDigit())
            .foregroundStyle(row.changed ? palette.accent : palette.secondary)
            .widgetAccentable()
            VStack(alignment: .leading, spacing: 0) {
                Text(row.subject)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(palette.text)
                    .strikethrough(row.cancelled)
                    .lineLimit(1)
                ForEach(0 ..< shown.count, id: \.self) { i in
                    let part = shown[i]
                    Text(row.mixedSubjects && !part.abbrev.isEmpty ? "\(part.abbrev) · \(part.detail)" : part.detail)
                        .font(.system(size: 11))
                        .foregroundStyle(palette.secondary)
                        .strikethrough(part.cancelled && !row.cancelled)
                        .lineLimit(1)
                        .frame(height: Metrics.extraLine)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.trailing, 8)
        .frame(height: Metrics.height(row, small: false))
        .background(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(row.changed ? palette.changedRow : palette.row)
        )
        .opacity(row.cancelled ? 0.6 : 1)
    }
}

/// Small: period and start time, short subject name and the rooms of all groups on one line.
private struct SmallRow: View {
    let row: LessonRow
    let palette: Palette

    var body: some View {
        HStack(spacing: 6) {
            Capsule()
                .fill(row.changed ? palette.accent : Color.clear)
                .frame(width: 3)
                .padding(.vertical, 4)
                .widgetAccentable()
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 4) {
                    Text(row.period).font(.system(size: 10, weight: .bold))
                    Text(hm(row.start)).font(.system(size: 10, weight: .medium).monospacedDigit())
                }
                .foregroundStyle(row.changed ? palette.accent : palette.secondary)
                .widgetAccentable()
                HStack(spacing: 4) {
                    Text(row.abbrev)
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(palette.text)
                        .strikethrough(row.cancelled)
                        .layoutPriority(1)
                    Text(row.rooms)
                        .font(.system(size: 10))
                        .foregroundStyle(palette.secondary)
                }
                .lineLimit(1)
            }
            Spacer(minLength: 0)
        }
        .frame(height: Metrics.smallRowHeight)
        .background(
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .fill(row.changed ? palette.changedRow : palette.row)
        )
        .opacity(row.cancelled ? 0.6 : 1)
    }
}

// MARK: - widget

struct TimetableWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "SchooldayTimetable", intent: TimetableWidgetIntent.self, provider: TimetableProvider()) { entry in
            TimetableWidgetView(entry: entry)
        }
        .configurationDisplayName("Timetable")
        .description(W.t("A class's lessons. Use the arrows to go through the week.", "Hodiny třídy. Šipkami projdeš celý týden."))
        .supportedFamilies([.systemSmall, .systemMedium])
        // The widget sets its own, narrower margins.
        .contentMarginsDisabled()
    }
}

@main
struct SchooldayWidgets: WidgetBundle {
    var body: some Widget {
        TimetableWidget()
    }
}
