package com.shehta.mushaf.reminder

import android.Manifest
import android.app.AlarmManager
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.shehta.mushaf.MainActivity
import com.shehta.mushaf.R
import com.shehta.mushaf.data.Prefs
import com.shehta.mushaf.data.Quran
import com.shehta.mushaf.data.ar
import java.util.Calendar
import kotlin.math.ceil

/** Daily wird reminder (inexact alarm, so it costs no battery and needs no special permission). */
object Reminder {
    private const val CHANNEL = "wird"

    fun schedule(context: Context, prefs: Prefs) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(context)
        am.cancel(pi)
        if (!prefs.reminderOn) return
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, prefs.reminderMinutes / 60)
            set(Calendar.MINUTE, prefs.reminderMinutes % 60)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
    }

    private fun pending(context: Context) = PendingIntent.getBroadcast(
        context, 7, Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun show(context: Context) {
        Quran.init(context)
        val prefs = Prefs(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "تذكير الورد اليومي", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val page = prefs.lastPage
        val k = prefs.khatma
        val text = if (k != null) {
            val daily = ceil(604.0 / k.days).toInt()
            val done = prefs.pagesToday()
            if (done >= daily) "أتممت وردك اليوم، بارك الله فيك. (${done.ar()} صفحة)"
            else "وردك اليوم ${daily.ar()} صفحات — قرأت ${done.ar()}. تابع من سورة ${Quran.surahOfPage(page).name}، صفحة ${page.ar()}."
        } else "حان وقت وردك. تابع من سورة ${Quran.surahOfPage(page).name}، صفحة ${page.ar()}."
        val open = PendingIntent.getActivity(
            context, 8, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_PAGE, page),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_book)
            .setContentTitle("الورد اليومي")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (allowed) {
            try { NotificationManagerCompat.from(context).notify(1, n) } catch (e: SecurityException) { /* notifications not allowed */ }
        }
    }
}

/**
 * «آية اليوم» (the same ayah as the home-screen widget) and the follow-up of what the reader wrote he would do with an
 * ayah in the tadabbur notebook. Both are optional (Settings); one inexact daily alarm serves both.
 */
object TadabburReminder {
    private const val CHANNEL = "tadabbur"
    const val ACTION = "com.shehta.mushaf.TADABBUR"
    private const val FOLLOW_AFTER = 3L * 86_400_000L

    fun schedule(context: Context, prefs: Prefs) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(context)
        am.cancel(pi)
        if (!prefs.dailyAyahOn && !prefs.actFollowUp) return
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, prefs.tadabburMinutes / 60)
            set(Calendar.MINUTE, prefs.tadabburMinutes % 60)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
    }

    private fun pending(context: Context) = PendingIntent.getBroadcast(
        context, 11, Intent(context, ReminderReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun show(context: Context) {
        Quran.init(context)
        val prefs = Prefs(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL, "التدبّر: آية اليوم والتذكير بما نويت", NotificationManager.IMPORTANCE_DEFAULT))
        }
        if (prefs.dailyAyahOn) {
            val (s, a) = com.shehta.mushaf.widget.AyahWidget.ayahOfDay()      // the same «آية اليوم» as the home-screen widget
            val q = com.shehta.mushaf.data.Reading.tadabburQuestions.lastOrNull() ?: ""
            val text = "﴿${Quran.ayahText(s, a)}﴾\n$q"
            notify(context, 2, "آية اليوم · سورة ${Quran.surahs[s - 1].name} ${a.ar()}", text, "tadabbur:$s:$a")
        }
        if (prefs.actFollowUp) {
            val now = System.currentTimeMillis()
            prefs.notes.values.filter { it.act.isNotBlank() && !it.done && now - it.time > FOLLOW_AFTER && now - it.asked > FOLLOW_AFTER }
                .minByOrNull { it.time }?.let { n ->
                    notify(context, 3, "من دفتر التدبّر",
                        "نويتَ عند الآية ${n.a.ar()} من سورة ${Quran.surahs[n.s - 1].name}: «${n.act}». هل عملت بها؟", "notes")
                    prefs.markAsked(n.s, n.a)
                }
        }
    }

    private fun notify(context: Context, id: Int, title: String, text: String, screen: String) {
        val open = PendingIntent.getActivity(
            context, 20 + id, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_SCREEN, screen)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_book)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (allowed) {
            try { NotificationManagerCompat.from(context).notify(id, n) } catch (e: SecurityException) { /* notifications not allowed */ }
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) =
        if (intent.action == TadabburReminder.ACTION) TadabburReminder.show(context) else Reminder.show(context)
}

/** Alarms are cleared on reboot; set the reminder again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Reminder.schedule(context, Prefs(context))
            TadabburReminder.schedule(context, Prefs(context))
            PrayerAlarms.scheduleAll(context)
        }
    }
}
