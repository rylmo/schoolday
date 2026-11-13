import AppIntents
import SwiftUI
import WidgetKit

// The timetable widget's Edit Widget options (colours, lunch button) and its menu view.

/// Colour sets for the Home Screen widget. Bakaláři is the Android widget's look.
enum WidgetTheme: String, AppEnum, CaseIterable {
    case bakalari, schoolday, graphite, ocean

    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Colours"
    static var caseDisplayRepresentations: [WidgetTheme: DisplayRepresentation] = [
        .bakalari: "Bakaláři",
        .schoolday: "Schoolday",
        .graphite: "Graphite",
        .ocean: "Ocean",
    ]
}

/// Lock Screen widgets only need the class (colours and the lunch button don't apply to them).
struct LockWidgetIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Class"
    static var description = IntentDescription("Which class the widget follows.")

    @Parameter(title: "Class")
    var schoolClass: ClassEntity?
}

/// The fork / calendar button: flips the widget between the timetable and the menu.
struct ToggleMenuIntent: AppIntent {
    static var title: LocalizedStringResource = "Show the menu"
    static var isDiscoverable = false

    @Parameter(title: "Class") var classId: String

    init() {}
    init(classId: String) { self.classId = classId }

    func perform() async throws -> some IntentResult {
        WidgetCache.noteTap(1)
        WidgetCache.setMenuMode(classId, !WidgetCache.menuMode(classId))
        return .result()
    }
}

/// ▲ ▼ while the menu is shown: one day back or forward.
struct ShiftMenuIntent: AppIntent {
    static var title: LocalizedStringResource = "Show another day's menu"
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
        WidgetCache.setMenuOffset(classId, WidgetCache.menuOffset(classId) + step)
        return .result()
    }
}

/// One day of the menu, as the widget lists it.
struct MenuPage: Hashable {
    struct Row: Hashable {
        var label: String
        var shortLabel: String
        var dish: String
        /// Your iCanteen order: drawn in the accent colour.
        var mine: Bool
    }

    var date: String
    var rows: [Row]
    var canUp: Bool
    var canDown: Bool

    /// Days from today on; [offset] moves through them.
    static func build(days: [WidgetData.LunchDay], iCanteen: Bool, allMeals: Bool, offset: Int, now: Date) -> (page: MenuPage, index: Int)? {
        let today = WidgetDates.key(now)
        let upcoming = days.filter { $0.date >= today }.sorted { $0.date < $1.date }
        guard !upcoming.isEmpty else { return nil }
        let index = min(max(offset, 0), upcoming.count - 1)
        let day = upcoming[index]
        var rows: [Row] = []
        if allMeals, let breakfast = day.breakfast {
            rows.append(Row(label: W.breakfast, shortLabel: W.breakfastShort, dish: WidgetData.shortDish(breakfast), mine: false))
        }
        for meal in day.options {
            rows.append(Row(label: "\(W.lunchWord) \(meal.number)", shortLabel: "\(meal.number)",
                            dish: WidgetData.shortDish(meal.name), mine: iCanteen && meal.ordered == true))
        }
        if allMeals, let dinner = day.dinner {
            rows.append(Row(label: W.dinner, shortLabel: W.dinnerShort, dish: WidgetData.shortDish(dinner), mine: false))
        }
        return (MenuPage(date: day.date, rows: rows, canUp: index > 0, canDown: index < upcoming.count - 1), index)
    }
}

extension W {
    static var lunchWord: String { t("Lunch", "Oběd") }
    static var menuWord: String { t("MENU", "JÍDELNÍČEK") }
    static var lunchHeader: String { t("LUNCH", "OBĚD") }
    static var breakfast: String { t("Breakfast", "Snídaně") }
    static var dinner: String { t("Dinner", "Večeře") }
    static var breakfastShort: String { t("Br.", "Sn.") }
    static var dinnerShort: String { t("Din.", "Več.") }
    static var noMenuDays: String { t("No menu yet", "Jídelníček zatím není") }
}

/// A meal in the widget's menu: label and dish on one line.
struct MenuLine: View {
    let row: MenuPage.Row
    let small: Bool
    let accent: Color
    let text: Color
    let secondary: Color
    let fill: Color
    let mineFill: Color

    var body: some View {
        HStack(spacing: 6) {
            Capsule()
                .fill(row.mine ? accent : Color.clear)
                .frame(width: 3)
                .padding(.vertical, 4)
                .widgetAccentable()
            Text(small ? row.shortLabel : row.label)
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(row.mine ? accent : secondary)
                .frame(width: small ? 26 : 62, alignment: .leading)
                .lineLimit(1)
                .widgetAccentable()
            Text(row.dish)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(text)
                .lineLimit(1)
            Spacer(minLength: 0)
        }
        .frame(height: small ? 24 : 26)
        .background(
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .fill(row.mine ? mineFill : fill)
        )
    }
}
