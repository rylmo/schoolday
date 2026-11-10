import Foundation

/// One lesson as the widget shows it. Times are minutes since midnight.
struct WidgetLesson: Codable, Hashable {
    var period: String
    var start: Int
    var end: Int
    var subject: String
    var abbrev: String
    var detail: String
    var room: String
    var changed: Bool
    var cancelled: Bool
}

struct WidgetDay: Codable, Hashable {
    /// yyyy-MM-dd
    var date: String
    var dayOff: Bool
    var dayOffName: String
    var lessons: [WidgetLesson]

    var realLessons: [WidgetLesson] { lessons.filter { !$0.cancelled } }
    var endTime: Int? { realLessons.map(\.end).max() }
}

struct WidgetClass: Codable, Hashable {
    var id: String
    var name: String
}

/// Reads the public Bakaláři timetable directly. The widget runs as its own process and can't see
/// the app's data (SideStore renames shared app groups when it re-signs), so it fetches by itself.
enum Bakalari {
    private static let base = "https://roznovskastredni.bakalari.cz/bakaweb/Timetable/Public"
    /// School period start times; index = period number (0 = 7:00, 1 = 8:00, …).
    private static let periodStarts = [420, 480, 530, 590, 645, 695, 745, 795, 845, 895, 945]

    private static func page(_ path: String) async throws -> String {
        var request = URLRequest(url: URL(string: base + path)!, timeoutInterval: 20)
        // The site follows Accept-Language; the parser expects the Czech page.
        request.setValue("cs-CZ,cs;q=0.9", forHTTPHeaderField: "Accept-Language")
        request.setValue("Mozilla/5.0 Schoolday widget (unofficial student app)", forHTTPHeaderField: "User-Agent")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard (response as? HTTPURLResponse)?.statusCode == 200, let html = String(data: data, encoding: .utf8) else {
            throw URLError(.badServerResponse)
        }
        return html
    }

    /// "Actual" (this week) or "Next".
    static func week(classId: String, next: Bool) async throws -> [WidgetDay] {
        let html = try await page("/\(next ? "Next" : "Actual")/Class/\(classId)")
        return try parse(html)
    }

    static func classes() async throws -> [WidgetClass] {
        let html = try await page("")
        guard let start = html.range(of: "id=\"selectedClass\""),
              let end = html.range(of: "</select>", range: start.upperBound ..< html.endIndex)
        else { return [] }
        let select = String(html[start.upperBound ..< end.lowerBound])
        let regex = try NSRegularExpression(pattern: #"<option\s+value="([^"]+)"[^>]*>\s*([^<]+?)\s*</option>"#)
        let ns = select as NSString
        return regex.matches(in: select, range: NSRange(location: 0, length: ns.length)).map {
            WidgetClass(id: ns.substring(with: $0.range(at: 1)), name: ns.substring(with: $0.range(at: 2)))
        }
    }

    // MARK: - parsing

    private struct Root: Decodable { let Days: [Day]? }
    private struct Day: Decodable {
        let Date: String?
        let DayOff: Bool?
        let DayOffName: String?
        let Hours: [Hour]?
    }
    private struct Hour: Decodable {
        let BeginTimeValue: Int?
        let EndTimeValue: Int?
        let Atoms: [Atom]?
    }
    private struct Atom: Decodable {
        let SubjectText: String?
        let SubjectAbbrev: String?
        let TeacherFullname: String?
        let Teacher: String?
        let Room: String?
        let GroupsNames: String?
        let ChangeInfo: String?
        let HasChanged: Bool?
        /// "atom", or "removed" for a cancelled lesson.
        let kind: String?

        enum CodingKeys: String, CodingKey {
            case SubjectText, SubjectAbbrev, TeacherFullname, Teacher, Room, GroupsNames, ChangeInfo, HasChanged
            case kind = "Type"
        }
    }

    static func parse(_ html: String, today: Date = Date()) throws -> [WidgetDay] {
        let marker = "const timetableData = "
        guard let start = html.range(of: marker) else { throw URLError(.cannotParseResponse) }
        // The page ends lines with "\r\n", which Swift counts as one Character, so look for any
        // newline rather than "\n" itself.
        let lineEnd = html[start.upperBound...].firstIndex { $0.isNewline } ?? html.endIndex
        var json = html[start.upperBound ..< lineEnd].trimmingCharacters(in: .whitespacesAndNewlines)
        if json.hasSuffix(";") { json.removeLast() }
        let root = try JSONDecoder().decode(Root.self, from: Data(json.utf8))

        return (root.Days ?? []).compactMap { day in
            guard let date = resolveDate(day.Date ?? "", today: today) else { return nil }
            let lessons: [WidgetLesson] = (day.Hours ?? []).flatMap { hour -> [WidgetLesson] in
                guard let begin = hour.BeginTimeValue, let end = hour.EndTimeValue else { return [] }
                let period = periodStarts.firstIndex { abs($0 - begin) <= 5 }.map(String.init) ?? ""
                return (hour.Atoms ?? []).map { atom in
                    let info = (atom.ChangeInfo ?? "").trimmingCharacters(in: .whitespaces)
                    let subject = (atom.SubjectText ?? "").trimmingCharacters(in: .whitespaces)
                    let cancelled = atom.kind == "removed" || subject.isEmpty ||
                        ["Přesun na", "Zrušeno", "Vyjmuto"].contains { info.hasPrefix($0) }
                    let group = (atom.GroupsNames ?? "").trimmingCharacters(in: .whitespaces)
                    let room = (atom.Room ?? "").trimmingCharacters(in: .whitespaces)
                    let teacher = (atom.TeacherFullname ?? atom.Teacher ?? "").trimmingCharacters(in: .whitespaces)
                    let detail = [group.isEmpty ? nil : group, room.isEmpty ? nil : room, teacher.isEmpty ? nil : teacher]
                        .compactMap { $0 }.joined(separator: " · ")
                    let abbrev = (atom.SubjectAbbrev ?? "").trimmingCharacters(in: .whitespaces)
                    let name = subject.isEmpty ? cancelledName(info) : subject
                    return WidgetLesson(
                        period: period, start: begin, end: end,
                        subject: name,
                        abbrev: abbrev.isEmpty && subject.isEmpty ? name : abbrev,
                        // A lesson that isn't taking place says what happened to it instead.
                        detail: cancelled ? (changeNote(info, lessonDate: date, today: today) ?? detail) : detail,
                        room: room,
                        changed: (atom.HasChanged ?? false) || !info.isEmpty,
                        cancelled: cancelled
                    )
                }
            }
            .sorted { ($0.start, $0.cancelled ? 1 : 0) < ($1.start, $1.cancelled ? 1 : 0) }
            return WidgetDay(date: date, dayOff: day.DayOff ?? false, dayOffName: day.DayOffName ?? "", lessons: lessons)
        }
    }

    private static let czech = (Locale.preferredLanguages.first ?? "").hasPrefix("cs")

    /// "Přesun na 11.11., 8:00: …" → "Moved to period 1" (same day) or "Moved to 12. 11. 8:00";
    /// "Zrušeno (…)" → "Cancelled"; "Vyjmuto z rozvrhu (…)" → "Removed from the timetable".
    private static func changeNote(_ info: String, lessonDate: String, today: Date) -> String? {
        if info.hasPrefix("Zrušeno") { return czech ? "Zrušeno" : "Cancelled" }
        if info.hasPrefix("Vyjmuto") { return czech ? "Vyjmuto z rozvrhu" : "Removed from the timetable" }
        guard info.hasPrefix("Přesun na"),
              let regex = try? NSRegularExpression(pattern: #"(\d{1,2})\.\s*(\d{1,2})\.\s*,?\s*(\d{1,2}):(\d{2})"#),
              let m = regex.firstMatch(in: info, range: NSRange(info.startIndex..., in: info))
        else { return nil }
        let ns = info as NSString
        let part = { (i: Int) in Int(ns.substring(with: m.range(at: i))) ?? 0 }
        let (day, month, hour, minute) = (part(1), part(2), part(3), part(4))
        let time = String(format: "%d:%02d", hour, minute)
        if resolveDate("\(day).\(month).", today: today) == lessonDate,
           let period = periodStarts.firstIndex(where: { abs($0 - (hour * 60 + minute)) <= 5 }) {
            return czech ? "Přesunuto na \(period). hodinu" : "Moved to period \(period)"
        }
        return czech ? "Přesunuto na \(day). \(month). \(time)" : "Moved to \(day). \(month). \(time)"
    }

    /// "Zrušeno (ZET, Žabčík Evžen)" → "ZET"; "Přesun na 11.11., 8:00: ZSV, Vejmelková Dana, V311" → "ZSV"
    private static func cancelledName(_ info: String) -> String {
        if let open = info.firstIndex(of: "(") {
            let inside = info[info.index(after: open)...]
            return String(inside.prefix { $0 != "," && $0 != ")" })
        }
        if let colon = info.range(of: ": ", options: .backwards) {
            return String(info[colon.upperBound...].prefix { $0 != "," }).trimmingCharacters(in: .whitespaces)
        }
        return info
    }

    /// "10.11." → 2026-11-10, choosing the year that puts it nearest to today.
    private static func resolveDate(_ text: String, today: Date) -> String? {
        let parts = text.split(whereSeparator: { !$0.isNumber }).compactMap { Int($0) }
        guard parts.count >= 2 else { return nil }
        let calendar = Calendar(identifier: .gregorian)
        let year = calendar.component(.year, from: today)
        let candidates = (year - 1 ... year + 1).compactMap {
            calendar.date(from: DateComponents(year: $0, month: parts[1], day: parts[0]))
        }
        guard let best = candidates.min(by: { abs($0.timeIntervalSince(today)) < abs($1.timeIntervalSince(today)) })
        else { return nil }
        return WidgetDates.key(best)
    }
}

enum WidgetDates {
    static func key(_ date: Date) -> String {
        let c = Calendar(identifier: .gregorian).dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
    }

    static func minutes(_ date: Date) -> Int {
        let c = Calendar.current.dateComponents([.hour, .minute], from: date)
        return (c.hour ?? 0) * 60 + (c.minute ?? 0)
    }

    static func date(from key: String) -> Date? {
        let p = key.split(separator: "-").compactMap { Int($0) }
        guard p.count == 3 else { return nil }
        return Calendar.current.date(from: DateComponents(year: p[0], month: p[1], day: p[2]))
    }
}

/// Last good copy of each class's two weeks, so the widget still shows something offline.
/// Stored in the widget extension's own defaults; the app doesn't need to see it.
enum WidgetCache {
    private static let defaults = UserDefaults.standard

    static func load(_ classId: String) -> [WidgetDay]? {
        guard let data = defaults.data(forKey: "tt.\(classId)") else { return nil }
        return try? JSONDecoder().decode([WidgetDay].self, from: data)
    }

    static func save(_ classId: String, _ days: [WidgetDay]) {
        if let data = try? JSONEncoder().encode(days) {
            defaults.set(data, forKey: "tt.\(classId)")
            defaults.set(Date(), forKey: "fetchedAt.\(classId)")
        }
    }

    /// How old the saved copy is; infinite when there is none.
    static func age(_ classId: String, now: Date = Date()) -> TimeInterval {
        guard let at = defaults.object(forKey: "fetchedAt.\(classId)") as? Date else { return .infinity }
        return now.timeIntervalSince(at)
    }

    /// The last arrow tap: when, and which way (-1 up, +1 down). Taps redraw from the saved copy,
    /// and the direction decides which way the rows slide.
    static func noteTap(_ step: Int) {
        defaults.set(Date(), forKey: "tapAt")
        defaults.set(step, forKey: "tapStep")
    }

    static func recentTap(now: Date = Date()) -> Int? {
        guard let at = defaults.object(forKey: "tapAt") as? Date, now.timeIntervalSince(at) < 10 else { return nil }
        return defaults.integer(forKey: "tapStep")
    }

    /// Why the last fetch failed, shown under "Can't load the timetable".
    static var lastError: String? {
        get { defaults.string(forKey: "lastError") }
        set { defaults.set(newValue, forKey: "lastError") }
    }

    /// Days the user flipped to with the arrows. It falls back to automatic after a while.
    /// How long a scrolled position stays before the widget goes back to the current lesson.
    static let manualHold: TimeInterval = 20 * 60

    static func offset(_ classId: String, now: Date = Date()) -> Int {
        guard let set = defaults.object(forKey: "offsetAt.\(classId)") as? Date,
              now.timeIntervalSince(set) < manualHold else { return 0 }
        return defaults.integer(forKey: "offset.\(classId)")
    }

    /// When the current scrolled position expires (manualHold after the last tap).
    static func offsetExpiry(_ classId: String) -> Date? {
        (defaults.object(forKey: "offsetAt.\(classId)") as? Date)?.addingTimeInterval(manualHold)
    }

    /// A tap: the new position, and the hold starts again.
    static func setOffset(_ classId: String, _ value: Int) {
        defaults.set(value, forKey: "offset.\(classId)")
        defaults.set(Date(), forKey: "offsetAt.\(classId)")
    }

    /// Pulls a position back inside the week without restarting the hold.
    static func clampOffset(_ classId: String, _ value: Int) {
        defaults.set(value, forKey: "offset.\(classId)")
    }
}
