package app.schoolday

import app.schoolday.ui.Czech
import app.schoolday.ui.S

/** One change, shown with an SF Symbol like Apple's own What's New screens. */
class ReleaseNote(val symbol: String, val title: String, val body: String)

/** What a version of the iPhone app brought. [version] is the app's marketing version ("1.4"). */
class ReleaseNotes(val version: String, val notes: List<ReleaseNote>)

/**
 * The iPhone app's changes, newest first, in the app's language. Shown once after an update and
 * any time from Settings › What's new. Add the new version's entry before raising the version.
 */
object IosChangelog {
    private class Entry(val version: String, val notes: List<Triple<String, Pair<String, String>, Pair<String, String>>>)

    private fun note(symbol: String, titleEn: String, titleCs: String, bodyEn: String, bodyCs: String) =
        Triple(symbol, titleEn to titleCs, bodyEn to bodyCs)

    private val entries = listOf(
        Entry("1.4", listOf(
            note("person.2.fill", "Your groups", "Tvoje skupiny",
                "Pick your group for each split subject at the top of Settings, or tap the ! on a lesson. Timetable, Today and the widgets then show only your lessons.",
                "Nahoře v Nastavení si u každého děleného předmětu vyber svou skupinu, nebo klepni na ! u hodiny. Rozvrh, Dnes i widgety pak ukážou jen tvoje hodiny."),
            note("fork.knife", "Breakfast and dinner", "Snídaně a večeře",
                "Turn them on in Settings and the Lunch tab becomes Menu.",
                "Zapni je v Nastavení a z karty Oběd se stane Jídelníček."),
            note("square.grid.2x2.fill", "Widget options", "Možnosti widgetu",
                "Hold the timetable widget and choose Edit Widget for a lunch button and four colour styles.",
                "Podrž widget rozvrhu a v Upravit widget najdeš tlačítko oběda a čtyři barevné styly."),
            note("rectangle.stack.fill", "Thicker widget rows", "Větší řádky widgetu",
                "Lessons get a bit more room, and the next one always peeks in at the bottom.",
                "Hodiny mají víc místa a ta další vždy vykukuje dole."),
            note("sparkles", "What's new", "Co je nového",
                "This screen, which you'll also find in Settings.",
                "Tahle obrazovka. Najdeš ji i v Nastavení."),
        )),
        Entry("1.3", listOf(
            note("lock.fill", "Lock Screen widgets", "Widgety na zamčenou obrazovku",
                "Countdown, Lunch, Now & next and Day.",
                "Odpočet, Oběd, Teď a potom a Den."),
            note("app.fill", "Refreshed look", "Svěžejší vzhled",
                "A softly shaded icon and a slightly darker Home Screen widget.",
                "Jemně stínovaná ikona a o něco tmavší widget na ploše."),
            note("arrow.triangle.2.circlepath", "Test builds", "Testovací verze",
                "Turn on Test builds in Settings to get new versions before they're released.",
                "Zapni Testovací verze v Nastavení a dostaneš nové verze ještě před vydáním."),
        )),
        Entry("1.2", listOf(
            note("rectangle.grid.1x2.fill", "Timetable widget", "Widget rozvrhu",
                "Your class's lessons on the Home Screen, in two sizes. Tap it to open that day.",
                "Hodiny tvé třídy na ploše, ve dvou velikostech. Klepnutím otevřeš ten den."),
            note("arrow.down.circle.fill", "Updates in the app", "Aktualizace v aplikaci",
                "New versions are offered in the app and install through SideStore.",
                "Nové verze se nabídnou v aplikaci a nainstalují se přes SideStore."),
            note("iphone", "Made for iPhone", "Udělaná pro iPhone",
                "Liquid Glass tabs, buttons and a native Settings screen.",
                "Panely, tlačítka a nastavení v Liquid Glass."),
        )),
    )

    fun all(): List<ReleaseNotes> {
        val cs = S === Czech
        return entries.map { e ->
            ReleaseNotes(e.version, e.notes.map { (symbol, title, body) ->
                ReleaseNote(symbol, if (cs) title.second else title.first, if (cs) body.second else body.first)
            })
        }
    }
}
