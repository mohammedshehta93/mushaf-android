package com.shehta.mushaf.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.shehta.mushaf.MainActivity
import com.shehta.mushaf.R
import com.shehta.mushaf.data.MuslimPrefs
import com.shehta.mushaf.data.PrayerCalc
import com.shehta.mushaf.data.Quran
import java.util.Calendar

/**
 * Prayer (adhan), Friday (Surat al-Kahf) and morning/evening azkar notifications.
 * Only the next alarm of each kind is set; every alarm schedules the following one when it fires.
 */
object PrayerAlarms {
    private const val CH_ADHAN = "adhan"
    private const val CH_REMIND = "reminders"
    const val KAHF_PAGE = 293

    fun scheduleAll(context: Context) {
        val mp = MuslimPrefs(context)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // next prayer
        cancel(context, am, 100)
        if (mp.hasLocation) {
            var t = System.currentTimeMillis()
            for (k in 0 until 6) {
                val n = mp.next(t) ?: break
                if (mp.adhanOn[n.first]) {
                    set(context, am, 100, n.second, Intent(context, AlarmReceiver::class.java).setAction("prayer").putExtra("i", n.first))
                    break
                }
                t = n.second + 1000
            }
        }
        // Friday reminder
        cancel(context, am, 101)
        if (mp.fridayOn) {
            val c = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY)
                set(Calendar.HOUR_OF_DAY, mp.fridayMinutes / 60); set(Calendar.MINUTE, mp.fridayMinutes % 60); set(Calendar.SECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.WEEK_OF_YEAR, 1)
            }
            set(context, am, 101, c.timeInMillis, Intent(context, AlarmReceiver::class.java).setAction("friday"))
        }
        // morning azkar (30 min after Fajr) and evening azkar (30 min after Asr)
        cancel(context, am, 102); cancel(context, am, 103)
        if (mp.azkarReminders && mp.hasLocation) {
            for ((code, idx, action) in listOf(Triple(102, 0, "azkar_morning"), Triple(103, 3, "azkar_evening"))) {
                val at = nextDaily(mp, idx, 30)
                if (at != null) set(context, am, code, at, Intent(context, AlarmReceiver::class.java).setAction(action))
            }
        }
    }

    private fun nextDaily(mp: MuslimPrefs, prayer: Int, plusMinutes: Int): Long? {
        val now = System.currentTimeMillis()
        for (d in 0..1) {
            val day = java.util.Date(now + d * 86_400_000L)
            val t = mp.times(day) ?: return null
            val h = t.list()[prayer]
            if (h.isNaN()) continue
            val c = Calendar.getInstance().apply { time = day; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
            val at = c.timeInMillis + (h * 3_600_000).toLong() + plusMinutes * 60_000L
            if (at > now) return at
        }
        return null
    }

    private fun pi(context: Context, code: Int, intent: Intent) =
        PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun cancel(context: Context, am: AlarmManager, code: Int) =
        am.cancel(pi(context, code, Intent(context, AlarmReceiver::class.java)))

    private fun set(context: Context, am: AlarmManager, code: Int, at: Long, intent: Intent) {
        val p = pi(context, code, intent)
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
    }

    fun channels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_ADHAN, "مواقيت الصلاة", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_REMIND, "الأذكار وتذكير الجمعة", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun canNotify(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun open(context: Context, code: Int, page: Int = 0, screen: String = ""): PendingIntent = PendingIntent.getActivity(
        context, code, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_PAGE, page).putExtra(MainActivity.EXTRA_SCREEN, screen)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun notify(context: Context, id: Int, channel: String, title: String, text: String, tap: PendingIntent) {
        if (!canNotify(context)) return
        channels(context)
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_book).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(tap).setAutoCancel(true)
            .setCategory(if (channel == CH_ADHAN) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .build()
        try { NotificationManagerCompat.from(context).notify(id, n) } catch (e: SecurityException) { }
    }

    fun onAlarm(context: Context, intent: Intent) {
        Quran.init(context)
        val mp = MuslimPrefs(context)
        when (intent.action) {
            "prayer" -> {
                val i = intent.getIntExtra("i", 0)
                val name = PrayerCalc.NAMES[i]
                notify(context, 10, CH_ADHAN, "حان الآن موعد صلاة $name", "${mp.cityName} · ${PrayerCalc.format(mp.times()?.list()?.get(i) ?: Double.NaN)}", open(context, 30, screen = "prayer"))
                if (mp.adhanSound.isNotEmpty()) {
                    val s = Intent(context, AdhanService::class.java).putExtra("uri", mp.adhanSound).putExtra("title", "أذان $name")
                    try { ContextCompat.startForegroundService(context, s) } catch (e: Exception) { }
                }
            }
            "friday" -> notify(context, 11, CH_REMIND, "يوم الجمعة", "اقرأ سورة الكهف، وأكثِر من الصلاة على النبي ﷺ.", open(context, 31, page = KAHF_PAGE))
            "azkar_morning" -> notify(context, 12, CH_REMIND, "أذكار الصباح", "«ألا بذكر الله تطمئن القلوب»", open(context, 32, screen = "azkar_morning"))
            "azkar_evening" -> notify(context, 13, CH_REMIND, "أذكار المساء", "«فاذكروني أذكركم»", open(context, 33, screen = "azkar_evening"))
        }
        scheduleAll(context)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = PrayerAlarms.onAlarm(context, intent)
}

/** Plays the adhan sound the user picked, with a "stop" button. */
class AdhanService : Service() {
    private var player: MediaPlayer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") { stopSelf(); return START_NOT_STICKY }
        PrayerAlarms.channels(this)
        val stop = PendingIntent.getService(this, 40, Intent(this, AdhanService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE)
        val n: Notification = NotificationCompat.Builder(this, "adhan")
            .setSmallIcon(R.drawable.ic_stat_book).setContentTitle(intent?.getStringExtra("title") ?: "الأذان")
            .setContentText("اضغط لإيقاف الأذان").setContentIntent(stop).addAction(0, "إيقاف", stop).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(9, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(9, n)
        player?.release()
        player = try {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                setDataSource(this@AdhanService, Uri.parse(intent?.getStringExtra("uri")))
                setOnCompletionListener { stopSelf() }
                setOnErrorListener { _, _, _ -> stopSelf(); true }
                prepare(); start()
            }
        } catch (e: Exception) { stopSelf(); null }
        return START_NOT_STICKY
    }

    override fun onDestroy() { player?.release(); player = null; super.onDestroy() }
}
