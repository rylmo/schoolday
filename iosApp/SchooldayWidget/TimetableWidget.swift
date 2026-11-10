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

    func entities(for identifiers: [String]) async throws -> [ClassEntity] {
        let known = await all()
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

/// The ◀ ▶ buttons: move three lessons back or forward through the week.
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
        WidgetCache.setOffset(classId, WidgetCache.offset(classId) + step)
        return .result()
    }
}

// MARK: - pages

/// A screenful of lessons: up to [pageSize] lessons of one day.
struct Page: Hashable {
    var day: WidgetDay
    var lessons: [WidgetLesson]
}

private let pageSize = 3

enum Pages {
    /// Every page of the two weeks, and the one to show by itself: the current lesson today,
    /// or the next school day once today's lessons are over.
    static func build(_ days: [WidgetDay], now: Date) -> (pages: [Page], auto: Int) {
        let todayKey = WidgetDates.key(now)
        let minute = WidgetDates.minutes(now)
        let sorted = days.sorted { $0.date < $1.date }

        // the day shown automatically, and the lesson it starts at
        var autoDay = sorted.firstIndex { $0.date > todayKey && !$0.realLessons.isEmpty } ?? max(sorted.count - 1, 0)
        var autoLesson = 0
        if let t = sorted.firstIndex(where: { $0.date == todayKey }),
           let end = sorted[t].endTime, minute < end {
            autoDay = t
            autoLesson = sorted[t].lessons.firstIndex { $0.end > minute } ?? 0
        }

        var pages: [Page] = []
        var auto = 0
        for (i, day) in sorted.enumerated() {
            if i == autoDay { auto = pages.count }
            guard !day.lessons.isEmpty else {
                pages.append(Page(day: day, lessons: []))
                continue
            }
            // On the automatic day, pages are aligned so one starts exactly at the current lesson.
            var start = 0
            if i == autoDay, autoLesson % pageSize != 0 {
                pages.append(Page(day: day, lessons: Array(day.lessons[0 ..< autoLesson % pageSize])))
                start = autoLesson % pageSize
            }
            while start < day.lessons.count {
                if i == autoDay, start == autoLesson { auto = pages.count }
                pages.append(Page(day: day, lessons: Array(day.lessons[start ..< min(start + pageSize, day.lessons.count)])))
                start += pageSize
            }
        }
        return (pages, auto)
    }
}

// MARK: - timeline

struct TimetableEntry: TimelineEntry {
    let date: Date
    let classId: String?
    let className: String?
    let page: Page?
    let canBack: Bool
    let canForward: Bool
    let offline: Bool
    let failed: Bool
}

struct TimetableProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> TimetableEntry {
        TimetableEntry(date: Date(), classId: nil, className: "IT3B", page: Self.sample, canBack: true, canForward: true, offline: false, failed: false)
    }

    func snapshot(for configuration: TimetableWidgetIntent, in context: Context) async -> TimetableEntry {
        if context.isPreview, configuration.schoolClass == nil { return placeholder(in: context) }
        return await entries(for: configuration, now: Date()).first ?? placeholder(in: context)
    }

    func timeline(for configuration: TimetableWidgetIntent, in context: Context) async -> Timeline<TimetableEntry> {
        let now = Date()
        let list = await entries(for: configuration, now: now)
        // Fetch again regularly; between fetches the extra entries roll the view to the current lesson.
        return Timeline(entries: list, policy: .after(now.addingTimeInterval(45 * 60)))
    }

    private func entries(for configuration: TimetableWidgetIntent, now: Date) async -> [TimetableEntry] {
        guard let cls = configuration.schoolClass else {
            return [TimetableEntry(date: now, classId: nil, className: nil, page: nil, canBack: false, canForward: false, offline: false, failed: false)]
        }
        var days: [WidgetDay] = []
        var offline = false
        do {
            async let thisWeek = Bakalari.week(classId: cls.id, next: false)
            async let nextWeek = try? Bakalari.week(classId: cls.id, next: true)
            let current = try await thisWeek
            let upcoming = await nextWeek ?? []
            days = current + upcoming
            WidgetCache.save(cls.id, days)
        } catch {
            days = WidgetCache.load(cls.id) ?? []
            offline = true
        }
        guard !days.isEmpty else {
            return [TimetableEntry(date: now, classId: cls.id, className: cls.name, page: nil, canBack: false, canForward: false, offline: offline, failed: true)]
        }

        // now, and each lesson end today, so the widget moves on as lessons finish
        let todayKey = WidgetDates.key(now)
        let startOfDay = Calendar.current.startOfDay(for: now)
        let ends = Set(days.first { $0.date == todayKey }?.lessons.map(\.end) ?? [])
        let times = [now] + ends.sorted()
            .map { startOfDay.addingTimeInterval(TimeInterval($0 * 60)) }
            .filter { $0 > now }
            .prefix(10)

        let offset = WidgetCache.offset(cls.id, now: now)
        return times.map { time in
            let built = Pages.build(days, now: time)
            let index = min(max(built.auto + offset, 0), max(built.pages.count - 1, 0))
            if time == now, index - built.auto != offset {
                WidgetCache.setOffset(cls.id, index - built.auto) // don't let taps pile up past either end
            }
            return TimetableEntry(
                date: time, classId: cls.id, className: cls.name,
                page: built.pages.isEmpty ? nil : built.pages[index],
                canBack: index > 0, canForward: index < built.pages.count - 1,
                offline: offline, failed: false
            )
        }
    }

    static let sample = Page(
        day: WidgetDay(date: WidgetDates.key(Date()), dayOff: false, dayOffName: "", lessons: []),
        lessons: [
            WidgetLesson(period: "1", start: 480, end: 525, subject: "Základy společenských věd", abbrev: "ZSV", detail: "V311 · Mgr. Dana Vejmelková", room: "V311", changed: true, cancelled: false),
            WidgetLesson(period: "2", start: 530, end: 575, subject: "Základy elektrotechniky", abbrev: "ZET", detail: "V311 · Ing. Evžen Žabčík", room: "V311", changed: false, cancelled: false),
            WidgetLesson(period: "3", start: 590, end: 635, subject: "Anglický jazyk", abbrev: "AJ", detail: "Aj1 · V311 · Mgr. Pavlína Chovancová", room: "V311", changed: true, cancelled: false),
        ]
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

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            header
            content
            Spacer(minLength: 0)
        }
        .containerBackground(for: .widget) { palette.background }
    }

    private var header: some View {
        HStack(spacing: 6) {
            Text(dayTitle)
                .font(.system(size: small ? 11 : 12, weight: .bold))
                .foregroundStyle(palette.text)
                .lineLimit(1)
                .minimumScaleFactor(0.65)
            Spacer(minLength: 0)
            if let id = entry.classId, entry.page != nil {
                arrow("chevron.left", step: -1, enabled: entry.canBack, classId: id)
                arrow("chevron.right", step: 1, enabled: entry.canForward, classId: id)
            }
        }
    }

    private func arrow(_ symbol: String, step: Int, enabled: Bool, classId: String) -> some View {
        Button(intent: ShiftPageIntent(classId: classId, step: step)) {
            Image(systemName: symbol)
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(palette.text)
                .frame(width: 22, height: 22)
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
        } else if let page = entry.page {
            if page.lessons.isEmpty {
                message(page.day.dayOff && !page.day.dayOffName.isEmpty ? page.day.dayOffName : W.noLessons)
            } else {
                VStack(spacing: 4) {
                    ForEach(page.lessons, id: \.self) { lesson in
                        if small { SmallRow(lesson: lesson, palette: palette) } else { MediumRow(lesson: lesson, palette: palette) }
                    }
                }
            }
        } else {
            message(W.cantLoad)
        }
    }

    private func message(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 13, weight: .medium))
            .foregroundStyle(palette.secondary)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
            .multilineTextAlignment(.center)
    }

    private var dayDate: Date? { entry.page.flatMap { WidgetDates.date(from: $0.day.date) } }

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
        if !small, let name = entry.className, entry.page != nil { title = "\(name) · " + title }
        if entry.offline { title += " · " + W.offline }
        return title
    }
}

private func hm(_ minutes: Int) -> String { String(format: "%d:%02d", minutes / 60, minutes % 60) }

/// Medium: period, start and end, subject, then group · room · teacher. Changed lessons get the pink bar.
private struct MediumRow: View {
    let lesson: WidgetLesson
    let palette: Palette

    var body: some View {
        HStack(spacing: 8) {
            Capsule()
                .fill(lesson.changed ? palette.accent : Color.clear)
                .frame(width: 3)
                .padding(.vertical, 4)
                .widgetAccentable()
            Text(lesson.period)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(lesson.changed ? palette.accent : palette.text)
                .frame(width: 14)
                .widgetAccentable()
            VStack(alignment: .leading, spacing: 0) {
                Text(hm(lesson.start))
                Text(hm(lesson.end))
            }
            .font(.system(size: 11, weight: .medium).monospacedDigit())
            .foregroundStyle(lesson.changed ? palette.accent : palette.secondary)
            .widgetAccentable()
            VStack(alignment: .leading, spacing: 1) {
                Text(lesson.subject)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(palette.text)
                    .strikethrough(lesson.cancelled)
                    .lineLimit(1)
                Text(lesson.detail)
                    .font(.system(size: 11))
                    .foregroundStyle(palette.secondary)
                    .lineLimit(1)
            }
            Spacer(minLength: 0)
        }
        .padding(.trailing, 8)
        .frame(height: 30)
        .background(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(lesson.changed ? palette.changedRow : palette.row)
        )
        .opacity(lesson.cancelled ? 0.6 : 1)
    }
}

/// Small: period and start time, short subject name and room on one line.
private struct SmallRow: View {
    let lesson: WidgetLesson
    let palette: Palette

    var body: some View {
        HStack(spacing: 6) {
            Capsule()
                .fill(lesson.changed ? palette.accent : Color.clear)
                .frame(width: 3)
                .padding(.vertical, 5)
                .widgetAccentable()
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 4) {
                    Text(lesson.period).font(.system(size: 11, weight: .bold))
                    Text(hm(lesson.start)).font(.system(size: 11, weight: .medium).monospacedDigit())
                }
                .foregroundStyle(lesson.changed ? palette.accent : palette.secondary)
                .widgetAccentable()
                HStack(spacing: 4) {
                    Text(lesson.abbrev.isEmpty ? lesson.subject : lesson.abbrev)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(palette.text)
                        .strikethrough(lesson.cancelled)
                    Text(lesson.room)
                        .font(.system(size: 11))
                        .foregroundStyle(palette.secondary)
                }
                .lineLimit(1)
            }
            Spacer(minLength: 0)
        }
        .frame(height: 30)
        .background(
            RoundedRectangle(cornerRadius: 9, style: .continuous)
                .fill(lesson.changed ? palette.changedRow : palette.row)
        )
        .opacity(lesson.cancelled ? 0.6 : 1)
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
    }
}

@main
struct SchooldayWidgets: WidgetBundle {
    var body: some Widget {
        TimetableWidget()
    }
}
