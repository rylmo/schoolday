import Foundation

/// Loading and reading the timetable and lunch, shared by every Schoolday widget.
enum WidgetData {
    // MARK: - class

    /// The class chosen in Edit Widget, or else the one set in the app (shared through the app group).
    static func chosenClass(_ configured: ClassEntity?) -> ClassEntity? {
        if let configured { return configured }
        guard let data = AppGroup.read(SharedFile.myClass),
              let mine = try? JSONDecoder().decode(WidgetClass.self, from: data), !mine.id.isEmpty
        else { return nil }
        return ClassEntity(id: mine.id, name: mine.name)
    }

    // MARK: - timetable

    /// The saved copy when it is fresh or the user just tapped an arrow (so taps redraw at once),
    /// otherwise both weeks from Bakaláři.
    static func days(for classId: String, now: Date) async -> (days: [WidgetDay], offline: Bool) {
        let loaded = await allGroupsDays(for: classId, now: now)
        return (YourGroups.apply(loaded.days, classId: classId), loaded.offline)
    }

    private static func allGroupsDays(for classId: String, now: Date) async -> (days: [WidgetDay], offline: Bool) {
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

    // MARK: - lunch

    struct Meal: Codable {
        var number: Int
        var name: String
        var ordered: Bool?
    }

    struct LunchDay: Codable {
        var date: String
        var soup: String?
        var options: [Meal]
        var breakfast: String?
        var dinner: String?
    }

    /// The whole menu: the app's (iCanteen, with orders) when it shares one, else the school website's.
    static func menuDays() async -> (days: [LunchDay], iCanteen: Bool) {
        if let data = AppGroup.read(SharedFile.lunch), let menu = try? JSONDecoder().decode(Menu.self, from: data), !menu.days.isEmpty {
            return (menu.days, menu.source == "ICanteen")
        }
        return (await SchoolMenu.days() ?? [], false)
    }

    /// Settings › Breakfast and dinner, shared by the app with your class.
    static func allMeals() -> Bool {
        struct Flags: Codable { var allMeals: Bool? }
        guard let data = AppGroup.read(SharedFile.myClass), let flags = try? JSONDecoder().decode(Flags.self, from: data) else { return false }
        return flags.allMeals ?? false
    }

    /// The app's lunch.json (its LunchMenu), or the school website's menu.
    struct Menu: Codable {
        var source: String
        var days: [LunchDay]
    }

    /// What to show for a day's lunch: your order from iCanteen when the app shares it, otherwise
    /// lunch 1 from the school website. `meal` is nil when iCanteen says nothing is ordered.
    struct LunchShown {
        var meal: Meal?
        var confirmed: Bool
    }

    static func lunch(on date: String) async -> LunchShown? {
        if let data = AppGroup.read(SharedFile.lunch), let menu = try? JSONDecoder().decode(Menu.self, from: data),
           let day = menu.days.first(where: { $0.date == date }) {
            if menu.source == "ICanteen" {
                return LunchShown(meal: day.options.first { $0.ordered == true }, confirmed: true)
            }
            return LunchShown(meal: day.options.first { $0.number == 1 }, confirmed: false)
        }
        guard let web = await SchoolMenu.days(), let day = web.first(where: { $0.date == date }) else { return nil }
        return LunchShown(meal: day.options.first { $0.number == 1 }, confirmed: false)
    }

    /// "Pečené kachní prso, červené zelí, ovoce, nápoje" → "Pečené kachní prso, červené zelí"
    static func shortDish(_ name: String) -> String {
        let noise: Set<String> = ["ovoce", "nápoje", "nápoj", "čaj", "šťáva", "voda"]
        let parts = name.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        return parts.enumerated().filter { $0.offset == 0 || !noise.contains($0.element.lowercased()) }.map(\.element)
            .joined(separator: ", ")
    }
}

/// The school website's public menu (no orders), read without an HTML library.
enum SchoolMenu {
    private static let url = URL(string: "https://www.roznovskastredni.cz/jidelnicek")!
    private static let cacheKey = "schoolMenu"

    static func days() async -> [WidgetData.LunchDay]? {
        let defaults = UserDefaults.standard
        if let at = defaults.object(forKey: cacheKey + "At") as? Date, Date().timeIntervalSince(at) < 6 * 3600,
           let data = defaults.data(forKey: cacheKey), let cached = try? JSONDecoder().decode([WidgetData.LunchDay].self, from: data) {
            return cached
        }
        do {
            var request = URLRequest(url: url, timeoutInterval: 20)
            request.setValue("Mozilla/5.0 Schoolday widget (unofficial student app)", forHTTPHeaderField: "User-Agent")
            let (data, _) = try await URLSession.shared.data(for: request)
            guard let html = String(data: data, encoding: .utf8) else { return nil }
            let days = parse(html)
            if !days.isEmpty, let encoded = try? JSONEncoder().encode(days) {
                defaults.set(encoded, forKey: cacheKey)
                defaults.set(Date(), forKey: cacheKey + "At")
            }
            return days
        } catch {
            guard let data = defaults.data(forKey: cacheKey) else { return nil }
            return try? JSONDecoder().decode([WidgetData.LunchDay].self, from: data)
        }
    }

    /// Each `div.jidelnicekDen` holds `id="day-2026-11-11"` and `div.container`s of a label
    /// ("Polévka", "Oběd1", "Snídaně"…) and the dish text before its allergen `<span>`.
    static func parse(_ html: String) -> [WidgetData.LunchDay] {
        html.components(separatedBy: "class=\"jidelnicekDen\"").dropFirst().compactMap { block in
            guard let date = first(#"id="day-(\d{4}-\d{2}-\d{2})""#, in: block) else { return nil }
            var soup: String?
            var breakfast: String?
            var dinner: String?
            var options: [WidgetData.Meal] = []
            for container in block.components(separatedBy: "class=\"container\"").dropFirst() {
                guard let label = first(#"shrinkedColumn[^>]*>\s*<span[^>]*>([^<]+)</span>"#, in: container),
                      let raw = first(#"class="column jidelnicekItem">([^<]+)"#, in: container) else { continue }
                let name = raw.split(whereSeparator: \.isWhitespace).joined(separator: " ")
                guard !name.isEmpty else { continue }
                if label.lowercased().hasPrefix("polévka") {
                    soup = name
                } else if label.lowercased().hasPrefix("snídaně") {
                    breakfast = name
                } else if label.lowercased().hasPrefix("večeře") {
                    dinner = name
                } else if label.lowercased().hasPrefix("oběd") {
                    let number = Int(label.filter(\.isNumber)) ?? options.count + 1
                    options.append(WidgetData.Meal(number: number, name: name, ordered: nil))
                }
            }
            return options.isEmpty ? nil : WidgetData.LunchDay(date: date, soup: soup, options: options.sorted { $0.number < $1.number },
                                                               breakfast: breakfast, dinner: dinner)
        }
    }

    private static func first(_ pattern: String, in text: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)),
              let range = Range(match.range(at: 1), in: text) else { return nil }
        return String(text[range]).trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

/// Where the day stands, the same rules as the app's Today screen.
struct DayReading {
    let day: WidgetDay
    /// Lessons that take place, one per time slot.
    let rows: [LessonRow]

    init(_ day: WidgetDay) {
        self.day = day
        rows = LessonRow.of(day).filter { !$0.cancelled }
    }

    var start: Int? { rows.first?.start }
    var end: Int? { rows.last?.end }

    /// Lunch: the first gap of 40+ minutes that reaches period 4 (10:45) or later; with none,
    /// after the last lesson.
    var lunchTime: Int? {
        guard !day.dayOff, let end else { return nil }
        let periodStarts = [420, 480, 530, 590, 645, 695, 745, 795, 845, 895, 945]
        for (a, b) in zip(rows, rows.dropFirst()) where b.start - a.end >= 40 && b.start > periodStarts[4] {
            let inside = periodStarts.indices.filter { periodStarts[$0] >= a.end && periodStarts[$0] < b.start }
            if inside.isEmpty || inside.last! >= 4 { return a.end }
        }
        return end
    }

    enum Status {
        case lesson(LessonRow, index: Int)
        case gap(from: Int, to: Int, next: LessonRow)
        case before(LessonRow)
        case finished
        case noLessons
    }

    func status(at minute: Int) -> Status {
        guard let first = rows.first, let last = rows.last else { return .noLessons }
        if minute < first.start { return .before(first) }
        if minute >= last.end { return .finished }
        if let i = rows.firstIndex(where: { minute >= $0.start && minute < $0.end }) { return .lesson(rows[i], index: i) }
        let next = rows.first { $0.start > minute }!
        let previousEnd = rows.last { $0.end <= minute }?.end ?? minute
        return .gap(from: previousEnd, to: next.start, next: next)
    }

    /// The row after [row], if any.
    func after(_ row: LessonRow) -> LessonRow? {
        rows.first { $0.start > row.start }
    }
}

extension LessonRow {
    /// Short subject name for small spaces ("ANJ").
    var shortName: String { abbrev }
}

/// Settings › Your groups, shared by the app: your class's widgets leave out the other groups' lessons.
enum YourGroups {
    private struct Mine: Codable {
        var id: String
        var hiddenGroups: [String]?
    }

    /// The groups to leave out of [classId]'s lessons: only your own class has groups set.
    static func hidden(for classId: String) -> Set<String> {
        guard let data = AppGroup.read(SharedFile.myClass),
              let mine = try? JSONDecoder().decode(Mine.self, from: data), mine.id == classId
        else { return [] }
        return Set(mine.hiddenGroups ?? [])
    }

    private static func codes(_ group: String?) -> [String] {
        (group ?? "").split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
    }

    /// Drops lessons that belong only to hidden groups. A lesson left on its own in its slot loses
    /// its group name ("ASW1 · P334 · Knápek" → "P334 · Knápek"), since there's nothing to tell apart.
    static func apply(_ days: [WidgetDay], classId: String) -> [WidgetDay] {
        let hidden = hidden(for: classId)
        guard !hidden.isEmpty else { return days }
        return days.map { day in
            var day = day
            let all = day.lessons
            let kept = all.filter { lesson in
                let c = codes(lesson.group)
                return c.isEmpty || c.contains { !hidden.contains($0) }
            }
            day.lessons = kept.map { lesson in
                var lesson = lesson
                let before = all.filter { $0.start == lesson.start }.count
                let after = kept.filter { $0.start == lesson.start }.count
                if let group = lesson.group, !group.isEmpty, after < before, lesson.detail.hasPrefix(group + " · ") {
                    lesson.detail = String(lesson.detail.dropFirst(group.count + 3))
                }
                return lesson
            }
            return day
        }
    }
}
