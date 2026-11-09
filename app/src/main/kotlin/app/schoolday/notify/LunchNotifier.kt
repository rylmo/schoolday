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
import app.schoolday.logic.LunchWindow
import app.schoolday.logic.day
import app.schoolday.logic.lunchWindow
import app.schoolday.logic.myMeal
import app.schoolday.logic.nowMinutes
import app.schoolday.logic.shortDish
import app.schoolday.ui.MainActivity
import app.schoolday.work.SyncWorker
import java.time.LocalDate
import java.time.ZoneId

/**
 * Silent notification showing today's lunch during your lunch break.
 *  - lunch gap in your timetable → visible for the whole gap
 *  - lunch after the last lesson → from 10 min before the end until 5 min after
 */
object LunchNotifier {
    private const val CHANNEL = "lunch"
    private const val NOTIF_ID = 1001
    const val EXTRA_OPEN = "open"
    const val OPEN_LUNCH_TODAY = "lunch_today"

    fun createChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, app.schoolday.ui.S.channelName, NotificationManager.IMPORTANCE_LOW).apply {
            description = app.schoolday.ui.S.channelDesc
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun todayWindow(ctx: Context): LunchWindow? {
        val s = ctx.app.prefs.value
        val tt = ctx.app.repo.cachedTimetable(s.myClass, TimetableMode.Actual) ?: return null
        return tt.day(LocalDate.now())?.lunchWindow()
    }

    private fun millisAt(minutes: Int): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + minutes * 60_000L

    /** Called after every sync, on boot and when the app opens. */
    fun reschedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = alarmIntent(ctx)
        am.cancel(pi)
        if (!ctx.app.prefs.value.lunchNotification) {
            cancel(ctx); return
        }
        val w = todayWindow(ctx) ?: return
        val now = nowMinutes()
        when {
            now >= w.end -> Unit
            now >= w.start -> show(ctx, w)
            else -> am.setWindow(AlarmManager.RTC_WAKEUP, millisAt(w.start), 60_000L, pi)
        }
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
        val remainingMs = (millisAt(w.end) - System.currentTimeMillis()).coerceAtLeast(60_000L)
        post(ctx, title, remainingMs)
        markShown(ctx)
    }

    /** Text for the notification, or null when iCanteen says no lunch is ordered today. */
    private fun mealTitle(ctx: Context): String? {
        val menu = ctx.app.repo.cachedLunch() ?: return app.schoolday.ui.S.notifFallback
        val day = menu.day(LocalDate.now()) ?: return app.schoolday.ui.S.notifFallback
        val (meal, _) = menu.myMeal(day) ?: return null
        return shortDish(meal.name)
    }

    fun showTest(ctx: Context) {
        post(ctx, mealTitle(ctx) ?: "No lunch ordered today", 2 * 60_000L)
    }

    private fun post(ctx: Context, title: String, timeoutMs: Long) {
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
            .setColor(0xFFFFB86B.toInt())
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setTimeoutAfter(timeoutMs)
            .build()
        NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n)
    }

    fun cancel(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(NOTIF_ID)
}

class LunchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val s = context.app.prefs.value
        if (!s.lunchNotification) return
        val tt = context.app.repo.cachedTimetable(s.myClass, TimetableMode.Actual) ?: return
        val w = tt.day(LocalDate.now())?.lunchWindow() ?: return
        if (nowMinutes() < w.end) LunchNotifier.show(context, w)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        LunchNotifier.reschedule(context)
        SyncWorker.runNow(context)
    }
}
