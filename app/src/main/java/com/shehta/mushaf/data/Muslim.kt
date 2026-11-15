package com.shehta.mushaf.data

import android.content.Context
import android.content.SharedPreferences
import android.icu.util.IslamicCalendar
import android.icu.util.ULocale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class Zikr(val text: String, val virtue: String, val count: Int, val reference: String)
class ZikrCategory(val title: String, val items: List<Zikr>)
/** A hadith; explanation / lessons / word meanings come from HadeethEnc when available. */
class DailyHadith(
    val text: String, val source: String,
    val explanation: String = "", val hints: List<String> = emptyList(), val words: List<Pair<String, String>> = emptyList(),
    val title: String = "",
)

internal fun parseHadith(o: org.json.JSONObject): DailyHadith {
    val hl = o.optJSONArray("hl") ?: o.optJSONArray("h")
    val w = o.optJSONArray("w")
    return DailyHadith(
        o.getString("t"), o.optString("s") + (if (o.has("g") && !o.optString("s").contains(o.optString("g"))) " — " + o.optString("g") else ""),
        o.optString("x"),
        if (hl == null) emptyList() else List(hl.length()) { hl.getString(it) },
        if (w == null) emptyList() else List(w.length()) { w.getJSONArray(it).let { x -> x.getString(0) to x.getString(1) } },
        o.optString("title"),
    )
}
class GoodName(val name: String, val refs: List<IntArray>)

/** Hisn al-Muslim, hadith of the day, the 99 names — all offline. */
object MuslimData {
    @Volatile private var azkar: List<ZikrCategory>? = null
    @Volatile private var hadith: List<DailyHadith>? = null
    @Volatile private var names: List<GoodName>? = null

    fun azkar(): List<ZikrCategory> = azkar ?: synchronized(this) {
        azkar ?: JSONArray(Quran.asset("muslim/azkar.json")).let { arr ->
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i); val its = o.getJSONArray("i")
                ZikrCategory(o.getString("t"), List(its.length()) { j ->
                    val z = its.getJSONObject(j)
                    Zikr(z.getString("z"), z.optString("d"), z.optInt("c", 1), z.optString("r"))
                })
            }
        }.also { azkar = it }
    }

    fun category(title: String): ZikrCategory? = azkar().firstOrNull { it.title == title }

    val MORNING = "أذكار الصباح"
    val EVENING = "أذكار المساء"
    val SLEEP = "أذكار النوم"
    val AFTER_PRAYER = "الأذكار بعد السلام من الصلاة"
    val WAKING = "أذكار الاستيقاظ من النوم"

    fun hadiths(): List<DailyHadith> = hadith ?: synchronized(this) {
        hadith ?: JSONArray(Quran.asset("muslim/hadith_daily.json")).let { arr ->
            List(arr.length()) { i -> parseHadith(arr.getJSONObject(i)) }
        }.also { hadith = it }
    }

    /** The same hadith all day; [offset] lets the user see another one. */
    fun hadithOfDay(offset: Int = 0): DailyHadith {
        val list = hadiths()
        val c = Calendar.getInstance()
        val day = c.get(Calendar.YEAR) * 400 + c.get(Calendar.DAY_OF_YEAR)
        return list[((day.toLong() * 7919 + offset) % list.size).toInt()]
    }

    @Volatile private var explained: List<DailyHadith>? = null

    /** The library of explained hadiths (HadeethEnc); empty when the build could not fetch it. */
    fun explained(): List<DailyHadith> = explained ?: synchronized(this) {
        explained ?: try {
            JSONArray(Quran.asset("muslim/hadith_sharh.json")).let { arr -> List(arr.length()) { parseHadith(arr.getJSONObject(it)) } }
        } catch (e: Exception) { emptyList() }.also { explained = it }
    }

    fun names(): List<GoodName> = names ?: synchronized(this) {
        names ?: JSONArray(Quran.asset("muslim/names.json")).let { arr ->
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i); val r = o.getJSONArray("r")
                GoodName(o.getString("n"), List(r.length()) { j -> r.getJSONArray(j).let { intArrayOf(it.getInt(0), it.getInt(1)) } })
            }
        }.also { names = it }
    }
}

/** Hijri date (Umm al-Qura calendar, built into Android) with a user correction of ±2 days. */
data class HijriDate(val year: Int, val month: Int, val day: Int) {
    val monthName get() = Hijri.MONTHS[month - 1]
    override fun toString() = "${day.ar()} $monthName ${year.ar()}هـ"
}

object Hijri {
    val MONTHS = listOf("محرّم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوّال", "ذو القعدة", "ذو الحجة")

    fun of(date: Date, adjust: Int): HijriDate {
        val cal = IslamicCalendar(ULocale("ar@calendar=islamic-umalqura"))
        cal.calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        cal.time = Date(date.time + adjust * 86_400_000L)
        return HijriDate(cal.get(android.icu.util.Calendar.YEAR), cal.get(android.icu.util.Calendar.MONTH) + 1, cal.get(android.icu.util.Calendar.DAY_OF_MONTH))
    }

    fun today(adjust: Int) = of(Date(), adjust)

    /** Gregorian date of a Hijri day (searching ±400 days around today). */
    fun toGregorian(h: HijriDate, adjust: Int): Date? {
        val start = System.currentTimeMillis() - 400L * 86_400_000L
        for (i in 0..800) {
            val d = Date(start + i * 86_400_000L)
            if (of(d, adjust) == h) return d
        }
        return null
    }

    class Occasion(val title: String, val note: String, val month: Int, val day: Int)

    val OCCASIONS = listOf(
        Occasion("رأس السنة الهجرية", "", 1, 1),
        Occasion("يوم تاسوعاء", "يُستحب صيامه مع عاشوراء", 1, 9),
        Occasion("يوم عاشوراء", "صيامه يكفّر السنة الماضية", 1, 10),
        Occasion("بداية شهر رمضان", "", 9, 1),
        Occasion("العشر الأواخر من رمضان", "التماس ليلة القدر", 9, 21),
        Occasion("عيد الفطر", "", 10, 1),
        Occasion("بداية عشر ذي الحجة", "أفضل أيام الدنيا", 12, 1),
        Occasion("يوم التروية", "", 12, 8),
        Occasion("يوم عرفة", "صيامه يكفّر سنتين لغير الحاج", 12, 9),
        Occasion("عيد الأضحى", "", 12, 10),
        Occasion("أيام التشريق", "١١–١٣ ذو الحجة", 12, 11),
    )

    /** White days (13–15) of the current or next Hijri month. */
    fun isWhiteDay(h: HijriDate) = h.day in 13..15
}

/** Settings and records of the daily-life tools. Same preferences file as [Prefs], so backups include them. */
class MuslimPrefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("mushaf", Context.MODE_PRIVATE)

    // location & prayer-time method
    var cityName by mutableStateOf(sp.getString("p_city", "") ?: ""); private set
    var lat by mutableStateOf(sp.getFloat("p_lat", 0f).toDouble()); private set
    var lng by mutableStateOf(sp.getFloat("p_lng", 0f).toDouble()); private set
    var method by mutableStateOf(sp.getString("p_method", "umm_alqura") ?: "umm_alqura"); private set
    var hanafiAsr by mutableStateOf(sp.getBoolean("p_hanafi", false)); private set
    var adjustments by mutableStateOf(IntArray(6) { sp.getInt("p_adj$it", 0) }); private set
    var hijriAdjust by mutableStateOf(sp.getInt("hijriAdj", 0)); private set
    val hasLocation get() = cityName.isNotEmpty()

    fun setLocation(name: String, la: Double, lo: Double) {
        cityName = name; lat = la; lng = lo
        sp.edit().putString("p_city", name).putFloat("p_lat", la.toFloat()).putFloat("p_lng", lo.toFloat()).apply()
    }
    fun updateMethod(id: String) { method = id; sp.edit().putString("p_method", id).apply() }
    fun updateHanafi(v: Boolean) { hanafiAsr = v; sp.edit().putBoolean("p_hanafi", v).apply() }
    fun updateAdjustment(i: Int, minutes: Int) {
        adjustments = adjustments.copyOf().also { it[i] = minutes }
        sp.edit().putInt("p_adj$i", minutes).apply()
    }
    fun updateHijriAdjust(v: Int) { hijriAdjust = v; sp.edit().putInt("hijriAdj", v).apply() }

    // notifications
    var adhanOn by mutableStateOf(BooleanArray(6) { i -> sp.getBoolean("adhan$i", i != 1) }); private set   // no adhan for sunrise
    var adhanSound by mutableStateOf(sp.getString("adhanUri", "") ?: ""); private set
    var fridayOn by mutableStateOf(sp.getBoolean("fridayOn", true)); private set
    var fridayMinutes by mutableStateOf(sp.getInt("fridayAt", 9 * 60)); private set
    var azkarReminders by mutableStateOf(sp.getBoolean("azkarRem", true)); private set
    fun updateAdhan(i: Int, on: Boolean) {
        adhanOn = adhanOn.copyOf().also { it[i] = on }
        sp.edit().putBoolean("adhan$i", on).apply()
    }
    fun updateAdhanSound(uri: String) { adhanSound = uri; sp.edit().putString("adhanUri", uri).apply() }
    fun updateFriday(on: Boolean, minutes: Int) { fridayOn = on; fridayMinutes = minutes; sp.edit().putBoolean("fridayOn", on).putInt("fridayAt", minutes).apply() }
    fun updateAzkarReminders(v: Boolean) { azkarReminders = v; sp.edit().putBoolean("azkarRem", v).apply() }

    /** Today's (or another day's) times, with the user's minute corrections applied. */
    fun times(date: Date = Date()): DayTimes? {
        if (!hasLocation) return null
        val c = Calendar.getInstance().apply { time = date }
        val tz = TimeZone.getDefault().getOffset(date.time) / 3_600_000.0
        val ramadan = Hijri.of(date, hijriAdjust).month == 9
        val t = PrayerCalc.compute(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), lat, lng, tz,
            Methods.byId(method), if (hanafiAsr) 2 else 1, ramadan)
        val a = adjustments
        return DayTimes(t.fajr + a[0] / 60.0, t.sunrise + a[1] / 60.0, t.dhuhr + a[2] / 60.0, t.asr + a[3] / 60.0, t.maghrib + a[4] / 60.0, t.isha + a[5] / 60.0)
    }

    /** Next prayer (index into PrayerCalc.NAMES, sunrise excluded) and its time in millis. */
    fun next(now: Long = System.currentTimeMillis()): Pair<Int, Long>? {
        for (dayOffset in 0..1) {
            val day = Date(now + dayOffset * 86_400_000L)
            val t = times(day) ?: return null
            val c = Calendar.getInstance().apply { time = day; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
            t.list().forEachIndexed { i, h ->
                if (i == 1 || h.isNaN()) return@forEachIndexed
                val at = c.timeInMillis + (h * 3_600_000).toLong()
                if (at > now) return i to at
            }
        }
        return null
    }

    // tasbih
    var tasbihCount by mutableStateOf(sp.getInt("tasbih", 0)); private set
    var tasbihTarget by mutableStateOf(sp.getInt("tasbihTarget", 33)); private set
    var tasbihPhrase by mutableStateOf(sp.getInt("tasbihPhrase", 0)); private set
    var tasbihToday by mutableStateOf(if (sp.getString("tasbihDay", "") == today()) sp.getInt("tasbihTodayN", 0) else 0); private set
    var tasbihTotal by mutableStateOf(sp.getLong("tasbihTotal", 0)); private set
    fun tasbihTap() {
        tasbihCount++; tasbihToday++; tasbihTotal++
        sp.edit().putInt("tasbih", tasbihCount).putInt("tasbihTodayN", tasbihToday).putString("tasbihDay", today()).putLong("tasbihTotal", tasbihTotal).apply()
    }
    fun tasbihReset() { tasbihCount = 0; sp.edit().putInt("tasbih", 0).apply() }
    fun updateTasbih(target: Int, phrase: Int) {
        tasbihTarget = target; tasbihPhrase = phrase; tasbihCount = 0
        sp.edit().putInt("tasbihTarget", target).putInt("tasbihPhrase", phrase).putInt("tasbih", 0).apply()
    }

    // azkar progress of today: "category#index" -> remaining count
    private var azkarDay = sp.getString("azkarDay", "") ?: ""
    var azkarDone by mutableStateOf(if (azkarDay == today()) readMap("azkarDone") else emptyMap()); private set
    fun azkarCount(cat: String, i: Int, total: Int): Int = azkarDone["$cat#$i"] ?: 0
    fun azkarTap(cat: String, i: Int) {
        val k = "$cat#$i"
        azkarDone = azkarDone + (k to (azkarDone[k] ?: 0) + 1)
        azkarDay = today()
        sp.edit().putString("azkarDay", azkarDay).putString("azkarDone", JSONObject(azkarDone as Map<*, *>).toString()).apply()
    }
    fun azkarResetCategory(cat: String) {
        azkarDone = azkarDone.filterKeys { !it.startsWith("$cat#") }
        sp.edit().putString("azkarDone", JSONObject(azkarDone as Map<*, *>).toString()).apply()
    }

    // prayer tracker: "yyyy-MM-dd" -> bitmask (bit0 fajr, 1 dhuhr, 2 asr, 3 maghrib, 4 isha)
    var prayerLog by mutableStateOf(readMap("prayerLog")); private set
    fun prayed(day: String, i: Int) = ((prayerLog[day] ?: 0) shr i) and 1 == 1
    fun togglePrayed(day: String, i: Int) {
        prayerLog = prayerLog + (day to ((prayerLog[day] ?: 0) xor (1 shl i)))
        writeMap("prayerLog", prayerLog.entries.sortedBy { it.key }.takeLast(500).associate { it.key to it.value })
    }
    var qada by mutableStateOf(IntArray(5) { sp.getInt("qada$it", 0) }); private set
    fun updateQada(i: Int, v: Int) { qada = qada.copyOf().also { it[i] = v.coerceAtLeast(0) }; sp.edit().putInt("qada$i", qada[i]).apply() }

    // Ramadan: "hijriYear" -> bitmask of fasted days / taraweeh nights (bits 1..30)
    var fasting by mutableStateOf(readLongMap("fasting")); private set
    var taraweeh by mutableStateOf(readLongMap("taraweeh")); private set
    fun toggleFast(year: Int, day: Int) { fasting = toggleBit(fasting, year, day); writeLongMap("fasting", fasting) }
    fun toggleTaraweeh(year: Int, day: Int) { taraweeh = toggleBit(taraweeh, year, day); writeLongMap("taraweeh", taraweeh) }
    private fun toggleBit(m: Map<String, Long>, year: Int, day: Int): Map<String, Long> =
        m + ("$year" to ((m["$year"] ?: 0L) xor (1L shl day)))

    // zakat prices (per gram, in the user's currency)
    var goldPrice by mutableStateOf(sp.getFloat("goldPrice", 0f).toDouble()); private set
    var silverPrice by mutableStateOf(sp.getFloat("silverPrice", 0f).toDouble()); private set
    var currency by mutableStateOf(sp.getString("currency", "د.ب") ?: "د.ب"); private set
    fun updatePrices(g: Double, s: Double, cur: String) {
        goldPrice = g; silverPrice = s; currency = cur
        sp.edit().putFloat("goldPrice", g.toFloat()).putFloat("silverPrice", s.toFloat()).putString("currency", cur).apply()
    }

    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun readMap(key: String): Map<String, Int> = try {
        val o = JSONObject(sp.getString(key, "{}") ?: "{}"); o.keys().asSequence().associateWith { o.getInt(it) }
    } catch (e: Exception) { emptyMap() }
    private fun writeMap(key: String, m: Map<String, Int>) {
        val o = JSONObject(); m.forEach { (k, v) -> o.put(k, v) }; sp.edit().putString(key, o.toString()).apply()
    }
    private fun readLongMap(key: String): Map<String, Long> = try {
        val o = JSONObject(sp.getString(key, "{}") ?: "{}"); o.keys().asSequence().associateWith { o.getLong(it) }
    } catch (e: Exception) { emptyMap() }
    private fun writeLongMap(key: String, m: Map<String, Long>) {
        val o = JSONObject(); m.forEach { (k, v) -> o.put(k, v) }; sp.edit().putString(key, o.toString()).apply()
    }
}
