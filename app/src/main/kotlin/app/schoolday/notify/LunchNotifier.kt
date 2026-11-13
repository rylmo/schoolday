package app.schoolday.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.schoolday.R
import app.schoolday.app
import app.schoolday.data.TimetableMode
import app.schoolday.logic.AFTER_SCHOOL_LEAD
import app.schoolday.logic.LunchWindow
import app.schoolday.logic.day
import app.schoolday.logic.hm
import app.schoolday.logic.lunchWindow
import app.schoolday.logic.myMeal
import app.schoolday.logic.nowMinutes
import app.schoolday.logic.shortDish
import app.schoolday.ui.MainActivity
import app.schoolday.ui.S
import app.schoolday.work.SyncWorker
import java.time.LocalDate
import java.time.ZoneId

/**
 * Quiet notification with today's lunch, posted 10 minutes before lunch so you see it in time.
 *  - lunch gap in your timetable → from 10 min before the gap until it ends
 *  - lunch after the last lesson → from 10 min before school ends until 5 min after
 * It never makes a sound or vibrates, can be swiped away, and disappears when lunch is over.
 */
object LunchNotifier {
    /** Version 1.21 and older used "lunch" (low importance, so Samsung filed it under Silent). */
    private const val OLD_CHANNEL = "lunch"
    private const val CHANNEL = "lunch_quiet"
    private const val NOTIF_ID = 1001
    private const val LEAD_MINUTES = 10
    const val EXTRA_OPEN = "open"
    const val OPEN_LUNCH_TODAY = "lunch_today"

    fun createChannel(ctx: Context) {
        // Normal importance so it's listed with your other notifications and shows in the status
        // bar, but with no sound and no vibration. Below "high", so it never pops up over the screen.
        val ch = NotificationChannel(CHANNEL, S.channelName, NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = S.channelDesc
            setSound(null, null)
            enableVibration(false)
            vibrationPattern = null
            enableLights(false)
            setShowBadge(false)
        }
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(ch)
        nm.deleteNotificationChannel(OLD_CHANNEL)
    }

    /** When lunch itself begins: the start of the break, or the end of school. */
    private val LunchWindow.lunchStart: Int get() = if (afterSchool) start + AFTER_SCHOOL_LEAD else start

    /** When the notification appears. */
    private val LunchWindow.notifyAt: Int get() = lunchStart - LEAD_MINUTES

    private fun LunchWindow.text(): String =
        if (afterSchool) S.lunchAfterSchoolFrom(lunchStart.hm()) else S.lunchSpan(start.hm(), end.hm())

    /** Your lunch window on [date], from the saved timetables (this week's, then next week's). */
    private fun windowOn(ctx: Context, date: LocalDate): LunchWindow? {
        return listOf(TimetableMode.Actual, TimetableMode.Next)
            .firstNotNullOfOrNull { ctx.app.repo.myTimetable(it)?.day(date) }
            ?.lunchWindow()
    }

    private fun millisAt(date: LocalDate, minutes: Int): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + minutes * 60_000L

    /**
     * Shows the notification if it's due now, and sets the alarm for the next one (later today or
     * on a coming school day). Called after every sync, on boot, when the app opens and when the
     * alarm fires.
     */
    fun reschedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = alarmIntent(ctx)
        am.cancel(pi)
        if (!ctx.app.prefs.value.lunchNotification) {
            cancel(ctx); return
        }
        val today = LocalDate.now()
        val now = nowMinutes()
        windowOn(ctx, today)?.let { w -> if (now >= w.notifyAt && now < w.end) show(ctx, w) }

        val next = (0L..7L).asSequence()
            .map { today.plusDays(it) }
            .firstNotNullOfOrNull { d -> windowOn(ctx, d)?.takeIf { d != today || now < it.notifyAt }?.let { d to it } }
            ?: return
        val at = millisAt(next.first, next.second.notifyAt)
        // Exact and allowed while idle: an ordinary alarm can arrive half an hour late on a phone
        // lying still in a bag, after lunch is already over.
        if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    private fun alarmIntent(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 0, Intent(ctx, LunchReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun shownToday(ctx: Context): Boolean =
        ctx.getSharedPreferences("notif", Context.MODE_PRIVATE).getString("shown", null) == LocalDate.now().toString()

    private fun markShown(ctx: Context) =
        ctx.getSharedPreferences("notif", Context.MODE_PRIVATE).edit().putString("shown", LocalDate.now().toString()).apply()

    fun show(ctx: Context, w: LunchWindow, force: Boolean = false) {
        if (!force && shownToday(ctx)) return
        val title = mealTitle(ctx) ?: return
        post(ctx, title, w.text(), until = millisAt(LocalDate.now(), w.end))
        markShown(ctx)
    }

    /** Text for the notification, or null when iCanteen says no lunch is ordered today. */
    private fun mealTitle(ctx: Context): String? {
        val menu = ctx.app.repo.cachedLunch() ?: return S.notifFallback
        val day = menu.day(LocalDate.now()) ?: return S.notifFallback
        val (meal, _) = menu.myMeal(day) ?: return null
        return shortDish(meal.name)
    }

    /** A 2-minute preview (long-press "Lunch notification" in Settings), with today's lunch times if any. */
    fun showTest(ctx: Context) {
        post(
            ctx, mealTitle(ctx) ?: S.notifFallback, windowOn(ctx, LocalDate.now())?.text(),
            until = System.currentTimeMillis() + 2 * 60_000L,
        )
    }

    /** Posts the notification; it removes itself at [until]. */
    private fun post(ctx: Context, title: String, text: String?, until: Long) {
        // Android 13+ needs the runtime permission; on 12 notifications are on unless the user blocked them.
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            ctx, 1,
            Intent(ctx, MainActivity::class.java)
                .putExtra(EXTRA_OPEN, OPEN_LUNCH_TODAY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setColor(0xFFFFB86B.toInt())
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setDefaults(0)
            .setVibrate(null)
            .setSound(null)
            .setTimeoutAfter((until - System.currentTimeMillis()).coerceAtLeast(60_000L))
            .build()
        NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n)
    }

    fun cancel(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(NOTIF_ID)
}

class LunchReceiver : BroadcastReceiver() {
    /** Shows today's notification and sets the alarm for the next lunch. */
    override fun onReceive(context: Context, intent: Intent) = LunchNotifier.reschedule(context)
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        LunchNotifier.reschedule(context)
        SyncWorker.runNow(context)
        app.schoolday.widget.Widgets.refresh(context)
    }
}
