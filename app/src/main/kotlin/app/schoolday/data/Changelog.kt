package app.schoolday.data

/** One release's notes, in English and Czech. [code] is the release's versionCode. */
data class ChangelogEntry(val code: Int, val name: String, val en: List<String>, val cs: List<String>)

/**
 * What changed in each version, newest first. Shown once after an update (What's new) and
 * any time from Settings. Add an entry with the new versionCode before publishing a release.
 */
object Changelog {
    val entries = listOf(
        ChangelogEntry(
            7, "1.3",
            en = listOf(
                "Home screen widgets: timetable with a menu switch, timetable only, and menu only. Set their background and transparency in Settings.",
                "Your groups: in Settings, pick your group in each split subject, and Timetable, Today, the widgets and the lunch notification show only your lessons.",
                "Breakfast and dinner can be shown too. The Lunch tab then becomes Menu.",
                "This What's new screen, also found in Settings.",
                "If an update fails, the reason is saved in Copy debug info, and you can download the update in your browser instead.",
            ),
            cs = listOf(
                "Widgety na plochu: rozvrh s přepínačem na jídelníček, jen rozvrh a jen jídelníček. Pozadí a průhlednost nastavíš v Nastavení.",
                "Tvoje skupiny: v Nastavení si u každého děleného předmětu vyber svou skupinu a Rozvrh, Dnes, widgety i oznámení o obědě ukážou jen tvoje hodiny.",
                "Můžeš si zobrazit i snídaně a večeře. Karta Oběd se pak změní na Jídelníček.",
                "Tahle obrazovka Co je nového, najdeš ji i v Nastavení.",
                "Když aktualizace selže, důvod se uloží do ladicích informací a aktualizaci můžeš stáhnout v prohlížeči.",
            ),
        ),
        ChangelogEntry(
            6, "1.2.5",
            en = listOf(
                "The lunch notification arrives on time, 10 minutes before lunch, and shows when lunch is.",
                "It's quiet: no sound and no vibration.",
            ),
            cs = listOf(
                "Oznámení o obědě chodí včas, 10 minut před obědem, a ukazuje, kdy oběd je.",
                "Je tiché: bez zvuku a vibrací.",
            ),
        ),
        ChangelogEntry(
            5, "1.21",
            en = listOf("Timetable opens on your own class. Tapping a class on Today still opens that class."),
            cs = listOf("Rozvrh se otevírá na tvé třídě. Klepnutí na třídu na stránce Dnes pořád otevře tu třídu."),
        ),
        ChangelogEntry(
            4, "1.2",
            en = listOf("Updates install from inside the app.", "The startup animation plays reliably on more phones."),
            cs = listOf("Aktualizace se instalují přímo z aplikace.", "Úvodní animace se spolehlivě přehraje na více telefonech."),
        ),
    )

    /** Entries newer than [seen], up to [current]. */
    fun since(seen: Int, current: Int): List<ChangelogEntry> = entries.filter { it.code in (seen + 1)..current }
}
