package com.shehta.mushaf.data

import android.content.Context
import android.os.Build
import com.shehta.mushaf.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Problems & suggestions → the owner's Telegram (bot token and chat id come from GitHub secrets at build time).
 * Only what the user typed is sent, plus app version, Android version, phone model and the page being read.
 */
object Feedback {
    val enabled get() = BuildConfig.TELEGRAM_TOKEN.isNotEmpty() && BuildConfig.TELEGRAM_CHAT.isNotEmpty()

    private const val MIN_GAP_MS = 60_000L

    /** Blocking (background thread). Returns null on success, or an Arabic error message. */
    fun send(context: Context, kind: String, message: String, contact: String, page: Int): String? {
        if (!enabled) return "الإرسال غير مفعّل في هذه النسخة."
        val sp = context.getSharedPreferences("mushaf", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - sp.getLong("fbLast", 0) < MIN_GAP_MS) return "انتظر دقيقة قبل إرسال رسالة أخرى."
        val text = buildString {
            append("📩 ").append(kind).append("\n\n")
            append(message.trim().take(3000)).append("\n\n")
            if (contact.isNotBlank()) append("👤 ").append(contact.trim().take(200)).append("\n")
            append("— الإصدار ").append(BuildConfig.VERSION_NAME).append(" · أندرويد ").append(Build.VERSION.RELEASE)
            append(" · ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL)
            if (page > 0) append(" · صفحة ").append(page)
        }
        return try {
            val c = URL("https://api.telegram.org/bot${BuildConfig.TELEGRAM_TOKEN}/sendMessage").openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true
            c.connectTimeout = 15000; c.readTimeout = 20000
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.outputStream.use { it.write(JSONObject().put("chat_id", BuildConfig.TELEGRAM_CHAT).put("text", text).toString().toByteArray()) }
            if (c.responseCode in 200..299) { sp.edit().putLong("fbLast", now).apply(); null }
            else "تعذّر الإرسال (${c.responseCode}). حاول لاحقًا."
        } catch (e: Exception) {
            "تعذّر الإرسال. تأكد من الاتصال بالإنترنت."
        }
    }

    /** Blocking. Downloads of the app from GitHub: [latest release, all releases], or null. */
    fun downloads(): IntArray? = try {
        val c = URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases?per_page=100").openConnection() as HttpURLConnection
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.connectTimeout = 15000; c.readTimeout = 20000
        val arr = JSONArray(c.inputStream.bufferedReader().use { it.readText() })
        var total = 0; var latest = 0
        for (i in 0 until arr.length()) {
            val assets = arr.getJSONObject(i).getJSONArray("assets")
            var n = 0
            for (j in 0 until assets.length()) n += assets.getJSONObject(j).optInt("download_count")
            total += n
            if (i == 0) latest = n
        }
        intArrayOf(latest, total)
    } catch (e: Exception) { null }
}
