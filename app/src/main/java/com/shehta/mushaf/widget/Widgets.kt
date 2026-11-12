package com.shehta.mushaf.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.RemoteViews
import com.shehta.mushaf.MainActivity
import com.shehta.mushaf.R
import com.shehta.mushaf.data.Prefs
import com.shehta.mushaf.data.Quran
import com.shehta.mushaf.data.ar
import java.util.Calendar
import kotlin.math.ceil

/** Home-screen widget: continue reading + today's wird progress. */
class ReadingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Quran.init(context)
        val prefs = Prefs(context)
        val page = prefs.lastPage
        val surah = Quran.surahOfPage(page)
        val k = prefs.khatma
        val daily = if (k != null) ceil(604.0 / k.days).toInt() else 0
        val done = prefs.pagesToday()
        for (id in ids) {
            val v = RemoteViews(context.packageName, R.layout.widget_reading)
            v.setTextViewText(R.id.w_title, "سورة ${surah.name}")
            v.setTextViewText(R.id.w_sub, "صفحة ${page.ar()} · الجزء ${Quran.juzOfPage(page).ar()}")
            if (k != null) {
                v.setTextViewText(R.id.w_wird, "ورد اليوم: ${done.coerceAtMost(daily).ar()} / ${daily.ar()} · الختمة ${(prefs.khatmaPages.size * 100 / 604).ar()}٪")
                v.setProgressBar(R.id.w_progress, daily, done.coerceAtMost(daily), false)
            } else {
                v.setTextViewText(R.id.w_wird, if (done > 0) "قرأت اليوم ${done.ar()} صفحة" else "اضغط لمتابعة القراءة")
                v.setProgressBar(R.id.w_progress, 1, 0, false)
            }
            v.setOnClickPendingIntent(R.id.w_root, openPage(context, page, 20))
            manager.updateAppWidget(id, v)
        }
    }
}

/** Home-screen widget: an ayah a day, drawn in the Uthmani font. */
class AyahWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Quran.init(context)
        val (s, a) = ayahOfDay()
        val text = Quran.ayahText(s, a)
        val dm = context.resources.displayMetrics
        val night = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val bmp = render(text, (300 * dm.density).toInt(), 22f * dm.density, if (night) Color.rgb(230, 224, 210) else Color.rgb(27, 27, 27))
        for (id in ids) {
            val v = RemoteViews(context.packageName, R.layout.widget_ayah)
            v.setImageViewBitmap(R.id.w_ayah, bmp)
            v.setTextViewText(R.id.w_ref, "سورة ${Quran.surahs[s - 1].name} · ${a.ar()}")
            v.setOnClickPendingIntent(R.id.w_root, openPage(context, Quran.pageOf(s, a), 21))
            manager.updateAppWidget(id, v)
        }
    }

    private fun render(text: String, width: Int, size: Float, color: Int): Bitmap {
        val p = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { typeface = Quran.hafsFont; textSize = size; this.color = color }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, p, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(size * 0.35f, 1f).build()
        val h = layout.height.coerceAtMost((width * 0.9f).toInt())
        val bmp = Bitmap.createBitmap(width, h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        layout.draw(Canvas(bmp))
        return bmp
    }

    companion object {
        /** A short, complete ayah chosen by the date (the same all day). */
        fun ayahOfDay(): Pair<Int, Int> {
            val texts = Quran.texts()
            val keys = texts.entries.filter { it.value.length in 40..170 }.map { it.key }.sorted()
            val day = Calendar.getInstance().let { it.get(Calendar.YEAR) * 400 + it.get(Calendar.DAY_OF_YEAR) }
            val k = keys[(day * 7919L % keys.size).toInt()]
            val (s, a) = k.split(":").map { it.toInt() }
            return s to a
        }
    }
}

private fun openPage(context: Context, page: Int, req: Int): PendingIntent = PendingIntent.getActivity(
    context, req, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_PAGE, page)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

object WidgetUpdater {
    fun updateAll(context: Context) {
        val m = AppWidgetManager.getInstance(context)
        for (cls in listOf(ReadingWidget::class.java, AyahWidget::class.java)) {
            val ids = m.getAppWidgetIds(ComponentName(context, cls))
            if (ids.isNotEmpty()) {
                context.sendBroadcast(Intent(context, cls).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
            }
        }
    }
}
