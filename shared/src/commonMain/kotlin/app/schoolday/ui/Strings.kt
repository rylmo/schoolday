package app.schoolday.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.schoolday.data.deviceLanguage
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number

/**
 * Every piece of text the app shows, in English and Czech. Kept in code (not Android
 * resources) so the language can be switched instantly inside the app on any Android version.
 */
open class Strings {
    // navigation
    open val today = "Today"; open val timetable = "Timetable"; open val lunch = "Lunch"; open val settings = "Settings"
    open val back = "Back"; open val more = "More"; open val selected = "Selected"; open val undo = "Undo"

    // Today
    open val classes = "Classes"; open val legendLunch = "lunch"; open val legendSame = "same as you"
    open val offlineData = "Offline – showing saved data"
    open val yourLunch = "Your lunch"; open val tomorrowsLunch = "Tomorrow's lunch"
    open fun lunchOn(day: LocalDate) = "${dayName(day)}'s lunch"
    open val afterSchoolCaps = "AFTER SCHOOL"; open val afterSchool = "after school"
    open val loading = "Loading…"; open val menuUnavailable = "Menu not available"; open val noLunchPlanned = "No lunch planned"
    open val nothingOrdered = "Nothing ordered"
    open fun notConfirmed(n: Int) = "Lunch $n · not confirmed"
    open val you = "You"
    open val noLessonsToday = "No lessons today"; open fun from(t: String) = "From $t"; open fun until(what: String, t: String) = "$what · until $t"
    open val statusLunch = "Lunch"; open val statusFree = "Free"; open val statusBreak = "Break"; open val doneForToday = "Done for today"

    // Timetable
    open val dayView = "Day view"; open val weekView = "Week view"; open val prevWeek = "Previous week"; open val nextWeek = "Next week"
    open val thisWeek = "This week"; open val permanent = "Permanent"; open val nextWeekLabel = "Next week"
    open val offlineTimetable = "Offline – showing saved timetable"; open val permanentTimetable = "Permanent timetable"
    open val dayOff = "Day off"; open val noLessons = "No lessons"
    open val weekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri")
    open val free = "Free"; open val lunchAfterSchool = "Lunch after school"; open fun fromLower(t: String) = "from $t"; open fun untilLower(t: String) = "until $t"
    open fun groups(n: Int) = "$n groups"
    open val group = "Group"; open val teacher = "Teacher"; open val room = "Room"; open val topic = "Topic"; open val bakalariNote = "Bakaláři note"; open val lesson = "Lesson"
    open val cancelledLower = "cancelled"; open fun movedLower(to: String) = "moved $to"
    // change labels
    open val chMovedHere = "Moved here"; open val chSwapped = "Swapped"; open val chSub = "Sub"; open val chMerged = "Merged"; open val chRoom = "Room"
    open val chOther = "Changed"; open val chMoved = "Moved"; open val chCancelled = "Cancelled"; open val chOff = "Off"
    open fun fromWhen(w: String) = "from $w"; open fun withClass(c: String) = "with $c"

    // Lunch
    open val lunchTitle = "Lunch"; open val schoolWebsite = "School website"; open val openICanteen = "Open iCanteen website"
    open val viewOnlyLogin = "View only. Log in to iCanteen in Settings to order."; open val viewOnlyDown = "iCanteen is unavailable – view only."
    open val offlineMenu = "Offline – showing saved menu"; open val locked = "Locked"; open val notOrdered = "Not ordered"
    open val noLunchInfo = "No lunch information for today"; open val soup = "Soup"; open fun lunchN(n: Int) = "Lunch $n"
    open fun ordered(n: Int, day: LocalDate) = "Lunch $n ordered for ${dayName(day)}"
    open fun cancelled(day: LocalDate) = "Lunch cancelled for ${dayName(day)}"

    // Settings
    open val iCanteen = "iCanteen"; open fun remove(what: String) = "Remove $what"; open val addClass = "Add class"
    open val yourClass = "Your class"; open val yourClassSub = "Finds your lunch break"; open val showMyClass = "Show my class in Timetable"
    open val app = "App"; open val lunchNotification = "Lunch notification"; open val lunchNotificationSub = "During your lunch break"
    open val colours = "Colours"; open val dark = "Dark"; open val light = "Light"; open val language = "Language"
    open val help = "Help"; open val copyDebug = "Copy debug info"; open val copyDebugSub = "For reporting a problem"; open val copied = "Copied – paste it to whoever is helping you"
    open fun footer(v: String) = "Schoolday $v · unofficial student app, not affiliated with SŠIEŘ Rožnov p. R. " +
        "Timetables from the public Bakaláři timetable, lunches from iCanteen and roznovskastredni.cz. Font: Geologica (SIL Open Font License)."
    open val loggedIn = "Logged in"; open val logOut = "Log out"; open val logIn = "Log in"; open val username = "Username"; open val password = "Password"
    open val hide = "Hide"; open val show = "Show"; open val savedEncrypted = "Saved encrypted on this phone only."
    open val langSystem = "Phone language"
    open fun themeName(id: String) = when (id) {
        "system" -> "System (One UI)"; "midnight" -> "Midnight"; "black" -> "Pure black"; "forest" -> "Forest"; "ocean" -> "Ocean"
        "plum" -> "Plum"; "paper" -> "Paper"; "sand" -> "Sand"; "mint" -> "Mint"; else -> id
    }

    // Setup
    open val welcome = "Welcome to Schoolday"; open val whichClass = "Which class are you in?"; open val whichClassSub = "Used to find your lunch break and to compare with your friends."
    open val friends = "Friends"; open val whoseDay = "Whose day do you want to see?"; open val whoseDaySub = "Pick any classes. You can change this later in Settings."
    open val optional = "Optional"; open val orderHere = "Order lunch from here"
    open val orderHereSub = "Log in with your iCanteen account to see and change your lunches. Without it you still get the menu."
    open val next = "Next"; open val skip = "Skip"; open val done = "Done"; open val skipForNow = "Skip for now"

    // errors (data layer)
    open val errLoginFirst = "Log in to iCanteen in Settings first"; open val errGone = "That lunch isn't on iCanteen anymore"
    open val errLocked = "This lunch can't be changed anymore"; open val errNotAccepted = "iCanteen didn't accept the change"
    open val errRefused = "iCanteen refused the change"; open val errMenu = "Menu unavailable"
    open val errNoInternet = "No internet connection"; open val errTimeout = "The server didn't respond in time"; open val errConnect = "Couldn't reach the server"
    open val errBadLogin = "iCanteen rejected the username or password"; open val errSession = "iCanteen session could not be opened"; open val errExpired = "Session expired – try again"

    // app updates
    open val updateTitle = "Update available"; open val updateNow = "Update"; open val updateLater = "Later"
    open val updateDownloading = "Downloading…"; open val updateInstalling = "Installing…"
    open val updateCloses = "The app closes while it installs. Open it again afterwards."
    open val updateViaSideStore = "Turn on LocalDevVPN and stay on Wi-Fi."
    open fun updateReady(v: String) = "Version $v is available"
    open val updateAllow = "Allow Schoolday to install updates, then come back."; open val openSettings = "Open settings"
    open val updateFailed = "The update failed. Try again later."; open val updateCancelled = "The update was cancelled."
    open val updateBadFile = "The download was damaged. Try again."
    open val checkUpdates = "Check for updates"; open val checkingUpdates = "Checking…"; open val upToDate = "You're on the latest version"
    open fun versionLabel(v: String) = "Version $v"

    // iPhone: the 7-day SideStore signature
    open fun signingExpires(days: Int) = when (days) {
        0 -> "Schoolday stops working today"
        1 -> "Schoolday stops working tomorrow"
        else -> "Schoolday stops working in $days days"
    }
    open val renewInSideStore = "Open SideStore with LocalDevVPN on to renew it"

    // notification
    open val channelName = "Lunch break"; open val channelDesc = "Shows today's lunch during your lunch break"; open val notifFallback = "Lunch"

    // dates
    protected open val fullDays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    protected open val shortDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    protected open val months = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    /** Full weekday name; [weekday] 0 = Monday. */
    fun weekdayFull(weekday: Int): String = fullDays[weekday]
    fun dayName(d: LocalDate): String = fullDays[d.dayOfWeek.isoDayNumber - 1]
    fun shortDay(d: DayOfWeek): String = shortDays[d.isoDayNumber - 1]
    open fun monthDay(d: LocalDate): String = "${d.day} ${months[d.month.number - 1]}"
}

object English : Strings()

object Czech : Strings() {
    override val today = "Dnes"; override val timetable = "Rozvrh"; override val lunch = "Oběd"; override val settings = "Nastavení"
    override val back = "Zpět"; override val more = "Další"; override val selected = "Vybráno"; override val undo = "Zpět"

    override val classes = "Třídy"; override val legendLunch = "oběd"; override val legendSame = "stejně jako ty"
    override val offlineData = "Offline – zobrazuji uložená data"
    override val yourLunch = "Tvůj oběd"; override val tomorrowsLunch = "Zítřejší oběd"
    override fun lunchOn(day: LocalDate) = "Oběd " + when (day.dayOfWeek) {
        DayOfWeek.MONDAY -> "v pondělí"; DayOfWeek.TUESDAY -> "v úterý"; DayOfWeek.WEDNESDAY -> "ve středu"
        DayOfWeek.THURSDAY -> "ve čtvrtek"; DayOfWeek.FRIDAY -> "v pátek"; DayOfWeek.SATURDAY -> "v sobotu"; else -> "v neděli"
    }
    override val afterSchoolCaps = "PO VYUČOVÁNÍ"; override val afterSchool = "po vyučování"
    override val loading = "Načítám…"; override val menuUnavailable = "Jídelníček není k dispozici"; override val noLunchPlanned = "Žádný oběd"
    override val nothingOrdered = "Nic objednáno"
    override fun notConfirmed(n: Int) = "Oběd $n · nepotvrzeno"
    override val you = "Ty"
    override val noLessonsToday = "Dnes bez výuky"; override fun from(t: String) = "Od $t"; override fun until(what: String, t: String) = "$what · do $t"
    override val statusLunch = "Oběd"; override val statusFree = "Volno"; override val statusBreak = "Přestávka"; override val doneForToday = "Hotovo"

    override val dayView = "Den"; override val weekView = "Týden"; override val prevWeek = "Předchozí týden"; override val nextWeek = "Další týden"
    override val thisWeek = "Tento týden"; override val permanent = "Stálý"; override val nextWeekLabel = "Příští týden"
    override val offlineTimetable = "Offline – zobrazuji uložený rozvrh"; override val permanentTimetable = "Stálý rozvrh"
    override val dayOff = "Volno"; override val noLessons = "Žádné hodiny"
    override val weekdays = listOf("Po", "Út", "St", "Čt", "Pá")
    override val free = "Volno"; override val lunchAfterSchool = "Oběd po vyučování"; override fun fromLower(t: String) = "od $t"; override fun untilLower(t: String) = "do $t"
    override fun groups(n: Int) = if (n in 2..4) "$n skupiny" else "$n skupin"
    override val group = "Skupina"; override val teacher = "Vyučující"; override val room = "Učebna"; override val topic = "Téma"; override val bakalariNote = "Poznámka z Bakalářů"; override val lesson = "Hodina"
    override val cancelledLower = "zrušeno"; override fun movedLower(to: String) = "přesunuto $to"
    override val chMovedHere = "Přesunuto sem"; override val chSwapped = "Výměna"; override val chSub = "Suplování"; override val chMerged = "Spojeno"; override val chRoom = "Učebna"
    override val chOther = "Změna"; override val chMoved = "Přesunuto"; override val chCancelled = "Zrušeno"; override val chOff = "Odpadá"
    override fun fromWhen(w: String) = "z $w"; override fun withClass(c: String) = "s $c"

    override val lunchTitle = "Obědy"; override val schoolWebsite = "Web školy"; override val openICanteen = "Otevřít iCanteen"
    override val viewOnlyLogin = "Jen prohlížení. Pro objednávání se přihlas do iCanteen v Nastavení."; override val viewOnlyDown = "iCanteen nefunguje – jen prohlížení."
    override val offlineMenu = "Offline – zobrazuji uložený jídelníček"; override val locked = "Uzamčeno"; override val notOrdered = "Neobjednáno"
    override val noLunchInfo = "Na dnešek nejsou informace o obědě"; override val soup = "Polévka"; override fun lunchN(n: Int) = "Oběd $n"
    private fun accusative(d: LocalDate) = when (d.dayOfWeek) {
        DayOfWeek.MONDAY -> "pondělí"; DayOfWeek.TUESDAY -> "úterý"; DayOfWeek.WEDNESDAY -> "středu"; DayOfWeek.THURSDAY -> "čtvrtek"
        DayOfWeek.FRIDAY -> "pátek"; DayOfWeek.SATURDAY -> "sobotu"; else -> "neděli"
    }
    override fun ordered(n: Int, day: LocalDate) = "Oběd $n objednán na ${accusative(day)}"
    override fun cancelled(day: LocalDate) = "Oběd na ${accusative(day)} zrušen"

    override fun remove(what: String) = "Odebrat $what"; override val addClass = "Přidat třídu"
    override val yourClass = "Tvoje třída"; override val yourClassSub = "Podle ní se hledá tvůj oběd"; override val showMyClass = "Zobrazit mou třídu v rozvrhu"
    override val app = "Aplikace"; override val lunchNotification = "Oznámení o obědě"; override val lunchNotificationSub = "Během tvé polední pauzy"
    override val colours = "Barvy"; override val dark = "Tmavé"; override val light = "Světlé"; override val language = "Jazyk"
    override val help = "Pomoc"; override val copyDebug = "Zkopírovat ladicí informace"; override val copyDebugSub = "Pro nahlášení problému"; override val copied = "Zkopírováno – pošli to tomu, kdo ti pomáhá"
    override fun footer(v: String) = "Schoolday $v · neoficiální studentská aplikace, není spojená se SŠIEŘ Rožnov p. R. " +
        "Rozvrhy z veřejného rozvrhu Bakalářů, obědy z iCanteen a roznovskastredni.cz. Písmo: Geologica (SIL Open Font License)."
    override val loggedIn = "Přihlášeno"; override val logOut = "Odhlásit"; override val logIn = "Přihlásit"; override val username = "Uživatelské jméno"; override val password = "Heslo"
    override val hide = "Skrýt"; override val show = "Zobrazit"; override val savedEncrypted = "Uloženo šifrovaně jen v tomto telefonu."
    override val langSystem = "Podle telefonu"
    override fun themeName(id: String) = when (id) {
        "system" -> "Systém (One UI)"; "midnight" -> "Půlnoc"; "black" -> "Čistě černá"; "forest" -> "Les"; "ocean" -> "Oceán"
        "plum" -> "Švestka"; "paper" -> "Papír"; "sand" -> "Písek"; "mint" -> "Máta"; else -> id
    }

    override val welcome = "Vítej v Schoolday"; override val whichClass = "Do které třídy chodíš?"; override val whichClassSub = "Podle ní najdeme tvůj oběd a porovnáme tvůj den s kamarády."
    override val friends = "Kamarádi"; override val whoseDay = "Čí den chceš vidět?"; override val whoseDaySub = "Vyber libovolné třídy. Později to můžeš změnit v Nastavení."
    override val optional = "Volitelné"; override val orderHere = "Objednávej obědy tady"
    override val orderHereSub = "Přihlas se účtem iCanteen a uvidíš i změníš své obědy. Bez přihlášení uvidíš aspoň jídelníček."
    override val next = "Další"; override val skip = "Přeskočit"; override val done = "Hotovo"; override val skipForNow = "Teď přeskočit"

    override val errLoginFirst = "Nejdřív se přihlas do iCanteen v Nastavení"; override val errGone = "Tenhle oběd už v iCanteen není"
    override val errLocked = "Tenhle oběd už nejde změnit"; override val errNotAccepted = "iCanteen změnu nepřijal"
    override val errRefused = "iCanteen změnu odmítl"; override val errMenu = "Jídelníček není k dispozici"
    override val errNoInternet = "Bez připojení k internetu"; override val errTimeout = "Server neodpověděl včas"; override val errConnect = "Nepodařilo se spojit se serverem"
    override val errBadLogin = "iCanteen odmítl jméno nebo heslo"; override val errSession = "Nepodařilo se přihlásit do iCanteen"; override val errExpired = "Přihlášení vypršelo – zkus to znovu"

    override val updateTitle = "Je dostupná aktualizace"; override val updateNow = "Aktualizovat"; override val updateLater = "Později"
    override val updateDownloading = "Stahuji…"; override val updateInstalling = "Instaluji…"
    override val updateCloses = "Během instalace se aplikace zavře. Potom ji znovu otevři."
    override val updateViaSideStore = "Zapni LocalDevVPN a zůstaň na Wi-Fi."
    override fun updateReady(v: String) = "Je dostupná verze $v"
    override val updateAllow = "Povol Schoolday instalovat aktualizace a pak se vrať."; override val openSettings = "Otevřít nastavení"
    override val updateFailed = "Aktualizace se nezdařila. Zkus to později."; override val updateCancelled = "Aktualizace byla zrušena."
    override val updateBadFile = "Stažený soubor je poškozený. Zkus to znovu."
    override val checkUpdates = "Zkontrolovat aktualizace"; override val checkingUpdates = "Kontroluji…"; override val upToDate = "Máš nejnovější verzi"
    override fun versionLabel(v: String) = "Verze $v"

    override fun signingExpires(days: Int) = when (days) {
        0 -> "Schoolday dnes přestane fungovat"
        1 -> "Schoolday zítra přestane fungovat"
        in 2..4 -> "Schoolday přestane fungovat za $days dny"
        else -> "Schoolday přestane fungovat za $days dní"
    }
    override val renewInSideStore = "Obnov ho v SideStoru se zapnutou LocalDevVPN"

    override val channelName = "Polední pauza"; override val channelDesc = "Ukazuje dnešní oběd během polední pauzy"; override val notifFallback = "Oběd"

    override val fullDays = listOf("Pondělí", "Úterý", "Středa", "Čtvrtek", "Pátek", "Sobota", "Neděle")
    override val shortDays = listOf("Po", "Út", "St", "Čt", "Pá", "So", "Ne")
    override val months = listOf("ledna", "února", "března", "dubna", "května", "června", "července", "srpna", "září", "října", "listopadu", "prosince")
    override fun monthDay(d: LocalDate): String = "${d.day}. ${months[d.month.number - 1]}"
}

/** The active language. Backed by Compose state, so switching it recomposes the whole UI. */
object L {
    var s: Strings by mutableStateOf(English)
        private set

    /** "system", "en" or "cs". */
    fun apply(lang: String) {
        s = when (lang) {
            "cs" -> Czech
            "en" -> English
            else -> if (deviceLanguage() == "cs") Czech else English
        }
    }
}

/** Shorthand used everywhere: `S.today`, `S.until(…)` … */
val S: Strings get() = L.s
