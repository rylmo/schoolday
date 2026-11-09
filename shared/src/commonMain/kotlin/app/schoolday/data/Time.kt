package app.schoolday.data

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalTime::class)
private fun nowLocal(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

@OptIn(ExperimentalTime::class)
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

fun todayDate(): LocalDate = nowLocal().date

/** Minutes since midnight, local time. */
fun minutesNow(): Int = nowLocal().let { it.hour * 60 + it.minute }

/** Local date and time for the debug report, e.g. 2026-11-09T17:20:01. */
fun timestamp(): String = nowLocal().toString().substringBefore('.')
