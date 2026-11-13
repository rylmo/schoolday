import AppIntents
import SwiftUI
import WidgetKit

// Lock Screen widgets. iOS draws these in white over the wallpaper, so they don't mark changed
// lessons (pink on the Home Screen widget).

extension W {
    static var breakShort: String { t("BREAK", "PŘEST.") }
    static var lunchShort: String { t("LUNCH", "OBĚD") }
    static var breakTitle: String { t("Break", "Přestávka") }
    static var lunchTitle: String { t("Lunch", "Oběd") }
    static var nowWord: String { t("now", "teď") }
    static var nextWord: String { t("Next", "Další") }
    static var firstWord: String { t("First", "První") }
    static var lastLesson: String { t("Last lesson today", "Poslední hodina") }
    static var minutes: String { t("min", "min") }
    static var nothingOrdered: String { t("Nothing ordered", "Nic objednáno") }
    static var noMenu: String { t("Menu not available", "Jídelníček není k dispozici") }
    static var pickClass: String { t("Choose a class", "Vyber třídu") }
    static var shortDays: [String] {
        czech ? ["NE", "PO", "ÚT", "ST", "ČT", "PÁ", "SO"] : ["SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"]
    }
    static func lessonOf(_ n: Int, _ total: Int) -> String { t("\(n) of \(total)", "\(n). z \(total)") }
    static func lessons(_ n: Int) -> String { t("\(n) lessons", "\(n) hodin") }
    static func ends(_ time: String) -> String { t("ends \(time)", "do \(time)") }
}

// MARK: - timeline

struct LockEntry: TimelineEntry {
    let date: Date
    /// The day shown: today while it lasts, otherwise the next school day.
    let reading: DayReading?
    let isToday: Bool
    let lunch: WidgetData.LunchShown?
    let missingClass: Bool

    var minute: Int { WidgetDates.minutes(date) }

    /// "THU" for a day that isn't today.
    var dayShort: String? {
        guard !isToday, let reading, let day = WidgetDates.date(from: reading.day.date) else { return nil }
        return W.shortDays[Calendar.current.component(.weekday, from: day) - 1]
    }

    func time(_ minute: Int) -> Date {
        let base = reading.flatMap { WidgetDates.date(from: $0.day.date) } ?? Calendar.current.startOfDay(for: date)
        return base.addingTimeInterval(TimeInterval(minute * 60))
    }

    static let sample: LockEntry = {
        let today = WidgetDates.key(Date())
        func lesson(_ p: String, _ s: Int, _ e: Int, _ name: String, _ ab: String, changed: Bool = false) -> WidgetLesson {
            WidgetLesson(period: p, start: s, end: e, subject: name, abbrev: ab, detail: "V311", room: "V311", changed: changed, cancelled: false)
        }
        let day = WidgetDay(date: today, dayOff: false, dayOffName: "", lessons: [
            lesson("1", 480, 525, "Základy společenských věd", "ZSV"),
            lesson("2", 530, 575, "Základy elektrotechniky", "ZET"),
            lesson("3", 590, 635, "Anglický jazyk", "ANJ", changed: true),
            lesson("4", 645, 690, "Německý jazyk", "NEJ"),
            lesson("5", 695, 740, "Ekonomika", "EKN"),
        ])
        let at = Calendar.current.startOfDay(for: Date()).addingTimeInterval(TimeInterval(552 * 60))
        return LockEntry(date: at, reading: DayReading(day), isToday: true,
                         lunch: WidgetData.LunchShown(meal: WidgetData.Meal(number: 1, name: "Pečené kachní prso, červené zelí", ordered: true), confirmed: true),
                         missingClass: false)
    }()
}

struct LockProvider: AppIntentTimelineProvider {
    enum Mode { case countdown, lunch, nowNext, day }
    let mode: Mode

    func placeholder(in context: Context) -> LockEntry { .sample }

    func snapshot(for configuration: LockWidgetIntent, in context: Context) async -> LockEntry {
        if context.isPreview, WidgetData.chosenClass(configuration.schoolClass) == nil { return .sample }
        return await entries(for: configuration, now: Date()).first ?? .sample
    }

    func timeline(for configuration: LockWidgetIntent, in context: Context) async -> Timeline<LockEntry> {
        let list = await entries(for: configuration, now: Date())
        let last = list.last?.date ?? Date()
        return Timeline(entries: list, policy: .after(last.addingTimeInterval(60)))
    }

    /// The countdown changes every minute; the others every few minutes (the progress bar itself
    /// moves continuously).
    private func entries(for configuration: LockWidgetIntent, now: Date) async -> [LockEntry] {
        guard let cls = WidgetData.chosenClass(configuration.schoolClass) else {
            return [LockEntry(date: now, reading: nil, isToday: false, lunch: nil, missingClass: true)]
        }
        let (days, _) = await WidgetData.days(for: cls.id, now: now)
        let (step, count): (TimeInterval, Int) = switch mode {
        case .countdown: (60, 90)
        case .lunch: (15 * 60, 8)
        case .nowNext, .day: (5 * 60, 36)
        }
        let minuteStart = Date(timeIntervalSinceReferenceDate: (now.timeIntervalSinceReferenceDate / 60).rounded(.down) * 60)
        let times = [now] + (1 ..< count).map { minuteStart.addingTimeInterval(TimeInterval($0) * step) }

        var lunches: [String: WidgetData.LunchShown] = [:]
        var result: [LockEntry] = []
        for time in times {
            let picked = Self.pick(days, at: time, forLunch: mode == .lunch)
            var lunch: WidgetData.LunchShown?
            if mode == .lunch, let date = picked?.reading.day.date {
                if lunches[date] == nil, let found = await WidgetData.lunch(on: date) { lunches[date] = found }
                lunch = lunches[date]
            }
            result.append(LockEntry(date: time, reading: picked?.reading, isToday: picked?.isToday ?? false, lunch: lunch, missingClass: false))
        }
        return result
    }

    /// Today until its lessons (or, for lunch, its lunch) are over, then the next school day.
    static func pick(_ days: [WidgetDay], at time: Date, forLunch: Bool) -> (reading: DayReading, isToday: Bool)? {
        let key = WidgetDates.key(time)
        let minute = WidgetDates.minutes(time)
        let sorted = days.sorted { $0.date < $1.date }
        if let today = sorted.first(where: { $0.date == key }) {
            let reading = DayReading(today)
            if forLunch, let lunch = reading.lunchTime, minute < lunch + 45 { return (reading, true) }
            if !forLunch, let end = reading.end, minute < end { return (reading, true) }
        }
        guard let next = sorted.first(where: { $0.date > key && !DayReading($0).rows.isEmpty }) else { return nil }
        return (DayReading(next), false)
    }
}

// MARK: - views

private struct Ring: View {
    let progress: Double

    var body: some View {
        ZStack {
            Circle().stroke(Color.primary.opacity(0.25), lineWidth: 4)
            Circle()
                .trim(from: 0, to: max(0.001, min(progress, 1)))
                .stroke(Color.primary, style: StrokeStyle(lineWidth: 4, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .widgetAccentable()
        }
        .padding(3)
    }
}

/// C1: minutes left in the lesson or break, with a ring that fills as it goes.
struct CountdownView: View {
    let entry: LockEntry

    var body: some View {
        ZStack {
            AccessoryWidgetBackground()
            content
        }
    }

    @ViewBuilder
    private var content: some View {
        if entry.missingClass {
            small(W.pickClass)
        } else if let reading = entry.reading {
            switch entry.isToday ? reading.status(at: entry.minute) : .noLessons {
            case let .lesson(row, _):
                timer(top: row.shortName, from: row.start, to: row.end)
            case let .gap(from, to, _):
                timer(top: reading.lunchTime == from ? W.lunchShort : W.breakShort, from: from, to: to)
            case let .before(first):
                start(top: nil, row: first)
            case .finished, .noLessons:
                if let first = reading.rows.first { start(top: entry.dayShort, row: first) } else { small("—") }
            }
        } else {
            small("—")
        }
    }

    private func timer(top: String, from: Int, to: Int) -> some View {
        ZStack {
            Ring(progress: Double(entry.minute - from) / Double(max(to - from, 1)))
            VStack(spacing: -1) {
                Text(top).font(.system(size: 9, weight: .bold)).opacity(0.75).lineLimit(1).minimumScaleFactor(0.6)
                Text("\(max(to - entry.minute, 0))").font(.system(size: 22, weight: .heavy, design: .rounded)).minimumScaleFactor(0.6)
                Text(W.minutes).font(.system(size: 9, weight: .bold)).opacity(0.75)
            }
            .padding(.horizontal, 8)
        }
    }

    private func start(top: String?, row: LessonRow) -> some View {
        VStack(spacing: 0) {
            if let top { Text(top).font(.system(size: 9, weight: .bold)).opacity(0.75) }
            Text(hm(row.start)).font(.system(size: 15, weight: .heavy, design: .rounded))
            Text(row.shortName).font(.system(size: 10, weight: .bold)).lineLimit(1).minimumScaleFactor(0.6)
        }
        .padding(.horizontal, 6)
    }

    private func small(_ text: String) -> some View {
        Text(text).font(.system(size: 10, weight: .semibold)).multilineTextAlignment(.center).padding(6)
    }
}

/// C4 (circle): when lunch is. R3 (rectangle): today's meal and when.
struct LunchView: View {
    let entry: LockEntry
    @Environment(\.widgetFamily) private var family

    private var time: String? { entry.reading?.lunchTime.map(hm) }

    var body: some View {
        if family == .accessoryCircular { circle } else { rectangle }
    }

    private var circle: some View {
        ZStack {
            AccessoryWidgetBackground()
            VStack(spacing: 1) {
                if let short = entry.dayShort { Text(short).font(.system(size: 9, weight: .bold)).opacity(0.75) }
                Image(systemName: "fork.knife").font(.system(size: 13, weight: .semibold))
                Text(entry.missingClass ? "—" : (time ?? "—")).font(.system(size: 15, weight: .heavy, design: .rounded))
            }
        }
    }

    private var rectangle: some View {
        VStack(alignment: .leading, spacing: 1) {
            HStack(spacing: 4) {
                Image(systemName: "fork.knife")
                Text(header).lineLimit(1)
            }
            .font(.system(size: 11, weight: .heavy))
            .opacity(0.8)
            Text(dish)
                .font(.system(size: 14, weight: .semibold))
                .lineLimit(2)
                .minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var header: String {
        var parts: [String] = []
        if let short = entry.dayShort { parts.append(short) }
        if let meal = entry.lunch?.meal { parts.append("\(W.lunchShort) \(meal.number)") } else { parts.append(W.lunchShort) }
        if let time { parts.append(time) }
        return parts.joined(separator: " · ")
    }

    private var dish: String {
        if entry.missingClass { return W.pickClass }
        guard let lunch = entry.lunch else { return W.noMenu }
        guard let meal = lunch.meal else { return W.nothingOrdered }
        return WidgetData.shortDish(meal.name)
    }
}

/// R1: the lesson now with its progress bar, then the next one.
struct NowNextView: View {
    let entry: LockEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            if entry.missingClass {
                Text(W.pickClass).font(.system(size: 13, weight: .semibold))
            } else if let reading = entry.reading {
                switch entry.isToday ? reading.status(at: entry.minute) : .noLessons {
                case let .lesson(row, _):
                    line(left: "\(row.shortName) \(W.nowWord)", right: hm(row.end))
                    bar(from: row.start, to: row.end)
                    nextLine(reading.after(row))
                case let .gap(from, to, next):
                    line(left: reading.lunchTime == from ? W.lunchTitle : W.breakTitle, right: hm(to))
                    bar(from: from, to: to)
                    nextLine(next)
                case let .before(first):
                    upcoming(reading, first)
                case .finished, .noLessons:
                    if let first = reading.rows.first { upcoming(reading, first) }
                }
            } else {
                Text("—").font(.system(size: 13, weight: .semibold))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func line(left: String, right: String) -> some View {
        HStack {
            Text(left).lineLimit(1)
            Spacer(minLength: 4)
            Text(right)
        }
        .font(.system(size: 15, weight: .heavy))
    }

    /// Moves by itself between timeline entries.
    private func bar(from: Int, to: Int) -> some View {
        ProgressView(timerInterval: entry.time(from) ... entry.time(max(to, from + 1)), countsDown: false) {
            EmptyView()
        } currentValueLabel: {
            EmptyView()
        }
        .progressViewStyle(.linear)
        .widgetAccentable()
    }

    private func nextLine(_ next: LessonRow?) -> some View {
        Group {
            if let next {
                Text("\(W.nextWord) \(hm(next.start)) \(next.shortName)" + (next.rooms.isEmpty ? "" : " · \(next.rooms)"))
            } else {
                Text(W.lastLesson)
            }
        }
        .font(.system(size: 12, weight: .semibold))
        .opacity(0.8)
        .lineLimit(1)
    }

    private func upcoming(_ reading: DayReading, _ first: LessonRow) -> some View {
        Group {
            line(left: [entry.dayShort, "\(W.firstWord) \(hm(first.start))"].compactMap { $0 }.joined(separator: " · "), right: "")
            Text("\(first.shortName)" + (first.rooms.isEmpty ? "" : " · \(first.rooms)"))
                .font(.system(size: 13, weight: .semibold))
                .lineLimit(1)
            if let end = reading.end {
                Text(W.ends(hm(end))).font(.system(size: 12, weight: .semibold)).opacity(0.8)
            }
        }
    }
}

/// R2: the day's lessons as small pills, with a line for now.
struct DayView: View {
    let entry: LockEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            if entry.missingClass {
                Text(W.pickClass).font(.system(size: 13, weight: .semibold))
            } else if let reading = entry.reading, let start = reading.start, let end = reading.end {
                Text(title(reading, end: end))
                    .font(.system(size: 12, weight: .heavy))
                    .lineLimit(1)
                strip(reading, start: start, end: end)
                Text(footer(reading))
                    .font(.system(size: 12, weight: .semibold))
                    .opacity(0.8)
                    .lineLimit(1)
            } else {
                Text("—").font(.system(size: 13, weight: .semibold))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func current(_ reading: DayReading) -> Int? {
        guard entry.isToday, case let .lesson(_, index) = reading.status(at: entry.minute) else { return nil }
        return index
    }

    private func title(_ reading: DayReading, end: Int) -> String {
        if let index = current(reading) { return "\(W.lessonOf(index + 1, reading.rows.count)) · \(W.ends(hm(end)))" }
        return [entry.dayShort, W.lessons(reading.rows.count), W.ends(hm(end))].compactMap { $0 }.joined(separator: " · ")
    }

    private func footer(_ reading: DayReading) -> String {
        guard entry.isToday else {
            guard let first = reading.rows.first else { return "" }
            return "\(W.firstWord) \(first.shortName) \(hm(first.start))"
        }
        switch reading.status(at: entry.minute) {
        case let .lesson(row, _):
            if let next = reading.after(row) { return "\(row.shortName) \(W.nowWord) · \(next.shortName) \(hm(next.start))" }
            return "\(row.shortName) \(W.nowWord)"
        case let .gap(_, _, next):
            return "\(W.nextWord) \(next.shortName) \(hm(next.start))"
        case let .before(first):
            return "\(W.firstWord) \(first.shortName) \(hm(first.start))"
        case .finished, .noLessons:
            return ""
        }
    }

    private func strip(_ reading: DayReading, start: Int, end: Int) -> some View {
        let now = current(reading)
        let span = Double(max(end - start, 1))
        let minute = entry.minute
        return GeometryReader { geo in
            let w = geo.size.width
            ZStack(alignment: .leading) {
                Capsule().fill(Color.primary.opacity(0.3)).frame(height: 2)
                ForEach(Array(reading.rows.enumerated()), id: \.element.start) { index, row in
                    RoundedRectangle(cornerRadius: 3, style: .continuous)
                        .fill(Color.primary.opacity(index == now ? 1 : 0.5))
                        .frame(width: max(CGFloat(Double(row.end - row.start) / span) * w - 2, 3), height: 10)
                        .offset(x: CGFloat(Double(row.start - start) / span) * w)
                }
                if entry.isToday, minute >= start, minute < end {
                    Capsule()
                        .fill(Color.primary)
                        .frame(width: 2, height: 16)
                        .offset(x: CGFloat(Double(minute - start) / span) * w - 1)
                        .widgetAccentable()
                }
            }
            .frame(height: 16)
        }
        .frame(height: 16)
    }
}

// MARK: - widgets

private struct LockContainer<Content: View>: View {
    let content: Content
    var body: some View {
        content.containerBackground(for: .widget) { Color.clear }
    }
}

struct CountdownWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "SchooldayCountdown", intent: LockWidgetIntent.self, provider: LockProvider(mode: .countdown)) { entry in
            LockContainer(content: CountdownView(entry: entry))
        }
        .configurationDisplayName(W.t("Countdown", "Odpočet"))
        .description(W.t("Minutes left in the lesson or break.", "Minuty do konce hodiny nebo přestávky."))
        .supportedFamilies([.accessoryCircular])
    }
}

struct LunchWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "SchooldayLunch", intent: LockWidgetIntent.self, provider: LockProvider(mode: .lunch)) { entry in
            LockContainer(content: LunchView(entry: entry))
        }
        .configurationDisplayName(W.t("Lunch", "Oběd"))
        .description(W.t("When your lunch break is, and what you're having.", "Kdy máš oběd a co jíš."))
        .supportedFamilies([.accessoryCircular, .accessoryRectangular])
    }
}

struct NowNextWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "SchooldayNowNext", intent: LockWidgetIntent.self, provider: LockProvider(mode: .nowNext)) { entry in
            LockContainer(content: NowNextView(entry: entry))
        }
        .configurationDisplayName(W.t("Now & next", "Teď a potom"))
        .description(W.t("The lesson now, how far through it you are, and what's next.", "Hodina teď, kolik z ní uběhlo a co bude dál."))
        .supportedFamilies([.accessoryRectangular])
    }
}

struct DayWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "SchooldayDay", intent: LockWidgetIntent.self, provider: LockProvider(mode: .day)) { entry in
            LockContainer(content: DayView(entry: entry))
        }
        .configurationDisplayName(W.t("Day", "Den"))
        .description(W.t("Your day's lessons at a glance.", "Hodiny dne na první pohled."))
        .supportedFamilies([.accessoryRectangular])
    }
}
