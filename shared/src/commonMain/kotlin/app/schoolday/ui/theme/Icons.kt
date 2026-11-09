package app.schoolday.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Schoolday's own icon set: 24×24, 1.75 stroke, round caps – the same line language as the
 * app icon. Replaces Material icons so nothing looks borrowed.
 */
object Ic {
    private fun icon(name: String, vararg paths: String, filled: Boolean = false): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        paths.forEach { d ->
            val nodes = PathParser().parsePathString(d).toNodes()
            if (filled) b.addPath(nodes, fill = SolidColor(Color.Black))
            else b.addPath(
                nodes, fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx + r} ${cy}a$r $r 0 1 1 ${-2 * r} 0a$r $r 0 1 1 ${2 * r} 0Z"

    val Today = icon("today", circle(12f, 12f, 4f), "M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M5.6 18.4 7 17M17 7l1.4-1.4")
    val Timetable = icon("timetable", "M7 4v16M4 8.5h16M4 13h16M4 17.5h16", "M7 4h10a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3Z")
    val Lunch = icon("lunch", "M7 3v8M5 3v5a2 2 0 0 0 4 0V3M7 11v10M17 21V3c-2 1.5-3 4-3 7h3")
    val Settings = icon("settings", "M4 7h10M18 7h2M4 17h4M12 17h8", circle(16f, 7f, 2f), circle(10f, 17f, 2f))
    val Week = icon("week", "M6 5h12a3 3 0 0 1 3 3v9a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3Z", "M8 5v15M13 5v15M18 5v15")
    val Day = icon("day", "M6 4h12a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3Z", "M7 9h10M7 13h10M7 17h6")
    val Soup = icon("soup", "M3 11h18a9 9 0 0 1-18 0Z", "M9 7c0-1.5 1-1.5 1-3M14 7c0-1.5 1-1.5 1-3")
    val Lock = icon("lock", "M17 9V7A5 5 0 0 0 7 7v2a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-8a2 2 0 0 0-2-2ZM9 7a3 3 0 0 1 6 0v2H9V7Zm3 10a2 2 0 1 1 0-4 2 2 0 0 1 0 4Z", filled = true)
    val Check = icon("check", "M5 12.5l4.5 4.5L19 7.5")
    val Back = icon("back", "M15 5l-7 7 7 7")
    val Prev = icon("prev", "M14.5 6l-6 6 6 6")
    val Next = icon("next", "M9.5 6l6 6-6 6")
    val Close = icon("close", "M6 6l12 12M18 6 6 18")
    val Plus = icon("plus", "M12 5v14M5 12h14")
    val More = icon("more", circle(12f, 5.5f, 0.9f), circle(12f, 12f, 0.9f), circle(12f, 18.5f, 0.9f))
    val External = icon("external", "M14 4h6v6M20 4l-9 9", "M18 14v4a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4")
    val Eye = icon("eye", "M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12Z", circle(12f, 12f, 3f))
    val EyeOff = icon("eye-off", "M4 4l16 16", "M10.6 6.1A9.6 9.6 0 0 1 12 6c6 0 9.5 6 9.5 6a17 17 0 0 1-2.7 3.4M6.6 7.7C4 9.5 2.5 12 2.5 12S6 18 12 18a9 9 0 0 0 4.3-1.1")
    val ChevronDown = icon("chevron-down", "M6 9.5l6 6 6-6")
    val Copy = icon("copy", "M9 9h9a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2Z", "M5 15H4a1 1 0 0 1-1-1V5a2 2 0 0 1 2-2h9a1 1 0 0 1 1 1v1")
    val Offline = icon("offline", "M3 3l18 18", "M8.5 8.6A6 6 0 0 0 6 13.5a4.5 4.5 0 0 0 .5 9h10.8M17.6 13.6A4 4 0 0 1 20.5 17M10.5 6.2A6 6 0 0 1 17 9")
    val Info = icon("info", circle(12f, 12f, 9f), "M12 11v6M12 7.5v.1")
}
