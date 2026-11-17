package com.shehta.mushaf.data

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * A calculation method. Fajr/Isha are sun depression angles; when [ishaMinutes] > 0, Isha is that many
 * minutes after Maghrib instead (Umm al-Qura, Gulf, Qatar).
 */
class CalcMethod(val id: String, val name: String, val fajr: Double, val isha: Double, val ishaMinutes: Int = 0, val ishaRamadanMinutes: Int = 0)

object Methods {
    val all = listOf(
        CalcMethod("umm_alqura", "أم القرى (مكة المكرمة)", 18.5, 0.0, 90, 120),
        CalcMethod("gulf", "منطقة الخليج", 19.5, 0.0, 90),
        CalcMethod("egypt", "الهيئة المصرية العامة للمساحة", 19.5, 17.5),
        CalcMethod("mwl", "رابطة العالم الإسلامي", 18.0, 17.0),
        CalcMethod("kuwait", "الكويت", 18.0, 17.5),
        CalcMethod("qatar", "قطر", 18.0, 0.0, 90),
        CalcMethod("dubai", "دبي", 18.2, 18.2),
        CalcMethod("karachi", "جامعة العلوم الإسلامية بكراتشي", 18.0, 18.0),
        CalcMethod("isna", "أمريكا الشمالية (ISNA)", 15.0, 15.0),
        CalcMethod("turkey", "تركيا (ديانت)", 18.0, 17.0),
    )
    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all[0]
}

/** Times of one day as hours from local midnight (e.g. 4.5 = 04:30). */
class DayTimes(val fajr: Double, val sunrise: Double, val dhuhr: Double, val asr: Double, val maghrib: Double, val isha: Double) {
    fun list() = listOf(fajr, sunrise, dhuhr, asr, maghrib, isha)
}

/**
 * Astronomical prayer-time calculation (the well-known PrayTimes algorithm), fully offline.
 * Pure Kotlin so it is unit-tested on the build machine.
 */
object PrayerCalc {
    val NAMES = listOf("الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء")

    private fun dsin(d: Double) = sin(Math.toRadians(d))
    private fun dcos(d: Double) = cos(Math.toRadians(d))
    private fun dtan(d: Double) = tan(Math.toRadians(d))
    private fun darcsin(x: Double) = Math.toDegrees(asin(x))
    private fun darccos(x: Double) = Math.toDegrees(acos(x))
    private fun darctan2(y: Double, x: Double) = Math.toDegrees(atan2(y, x))
    private fun darccot(x: Double) = Math.toDegrees(atan(1 / x))
    private fun fix(a: Double, b: Double): Double { val r = a - b * floor(a / b); return if (r < 0) r + b else r }

    fun julian(year: Int, month: Int, day: Int): Double {
        var y = year; var m = month
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    /** [declination, equation of time] for a julian date. */
    private fun sunPosition(jd: Double): DoubleArray {
        val d = jd - 2451545.0
        val g = fix(357.529 + 0.98560028 * d, 360.0)
        val q = fix(280.459 + 0.98564736 * d, 360.0)
        val l = fix(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g), 360.0)
        val e = 23.439 - 0.00000036 * d
        val ra = fix(darctan2(dcos(e) * dsin(l), dcos(l)) / 15.0, 24.0)
        val eqt = q / 15.0 - ra
        val decl = darcsin(dsin(e) * dsin(l))
        return doubleArrayOf(decl, eqt)
    }

    /**
     * @param tz hours from UTC for that date (e.g. 3.0 for Bahrain)
     * @param asrFactor 1 = standard (Shafi'i, Maliki, Hanbali), 2 = Hanafi
     * Returns NaN for a time that does not occur (extreme latitudes); the caller shows "—".
     */
    fun compute(year: Int, month: Int, day: Int, lat: Double, lng: Double, tz: Double, method: CalcMethod, asrFactor: Int, ramadan: Boolean = false): DayTimes {
        val jDate = julian(year, month, day) - lng / (15 * 24.0)

        fun midDay(t: Double): Double = fix(12 - sunPosition(jDate + t)[1], 24.0)
        fun sunAngleTime(angle: Double, t: Double, ccw: Boolean): Double {
            val decl = sunPosition(jDate + t)[0]
            val noon = midDay(t)
            val v = (-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat))
            if (v < -1 || v > 1) return Double.NaN
            val tt = darccos(v) / 15.0
            return noon + if (ccw) -tt else tt
        }
        fun asrTime(factor: Int, t: Double): Double {
            val decl = sunPosition(jDate + t)[0]
            val angle = -darccot(factor + dtan(abs(lat - decl)))
            return sunAngleTime(angle, t, false)
        }

        // first approximation, then refine once with the computed times (as PrayTimes does)
        var times = doubleArrayOf(5.0, 6.0, 12.0, 13.0, 18.0, 18.0, 18.0)
        repeat(2) {
            val p = times.map { it / 24.0 }
            times = doubleArrayOf(
                sunAngleTime(method.fajr, p[0], true),
                sunAngleTime(0.833, p[1], true),
                midDay(p[2]),
                asrTime(asrFactor, p[3]),
                sunAngleTime(0.833, p[4], false),
                sunAngleTime(0.833, p[5], false),
                if (method.ishaMinutes > 0) 0.0 else sunAngleTime(method.isha, p[6], false),
            )
        }
        val adj = tz - lng / 15.0
        val fajr = times[0] + adj
        val sunrise = times[1] + adj
        val dhuhr = times[2] + adj
        val asr = times[3] + adj
        val maghrib = times[5] + adj
        val ishaMin = if (ramadan && method.ishaRamadanMinutes > 0) method.ishaRamadanMinutes else method.ishaMinutes
        var isha = if (ishaMin > 0) maghrib + ishaMin / 60.0 else times[6] + adj
        var f = fajr
        // high latitudes: if fajr/isha do not occur, use the "middle of the night" rule
        val night = 24 - (maghrib - sunrise)
        if (f.isNaN() || sunrise - f > night / 2) f = sunrise - night / 2
        if (isha.isNaN() || isha - maghrib > night / 2) isha = maghrib + night / 2
        return DayTimes(f, sunrise, dhuhr, asr, maghrib, isha)
    }

    /** Qibla direction in degrees clockwise from true north. */
    fun qibla(lat: Double, lng: Double): Double {
        val kLat = 21.4225; val kLng = 39.8262
        val y = dsin(kLng - lng)
        val x = dcos(lat) * dtan(kLat) - dsin(lat) * dcos(kLng - lng)
        return fix(darctan2(y, x), 360.0)
    }

    /** Great-circle distance to the Kaaba in km. */
    fun distanceToKaaba(lat: Double, lng: Double): Double {
        val kLat = 21.4225; val kLng = 39.8262
        val a = dsin((kLat - lat) / 2) * dsin((kLat - lat) / 2) + dcos(lat) * dcos(kLat) * dsin((kLng - lng) / 2) * dsin((kLng - lng) / 2)
        return 6371 * 2 * atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    /** "04:32 ص" style text (12-hour, Arabic digits) for hours-from-midnight. */
    fun format(h: Double, twelve: Boolean = true): String {
        if (h.isNaN()) return "—"
        var mins = Math.round(fix(h, 24.0) * 60).toInt() % (24 * 60)
        val hh = mins / 60; val mm = mins % 60
        val d = "٠١٢٣٤٥٦٧٨٩"
        fun ar(n: Int, pad: Boolean) = (if (pad) String.format(java.util.Locale.US, "%02d", n) else n.toString()).map { d[it - '0'] }.joinToString("")
        return if (twelve) {
            val h12 = if (hh % 12 == 0) 12 else hh % 12
            "${ar(h12, false)}:${ar(mm, true)} ${if (hh < 12) "ص" else "م"}"
        } else "${ar(hh, true)}:${ar(mm, true)}"
    }
}

class City(val name: String, val lat: Double, val lng: Double)

object Cities {
    val all = listOf(
        City("المنامة", 26.2235, 50.5876), City("المحرق", 26.2572, 50.6119), City("الرفاع", 26.1300, 50.5550),
        City("مدينة عيسى", 26.1736, 50.5478), City("مدينة حمد", 26.1153, 50.5070), City("سترة", 26.1547, 50.6206),
        City("مكة المكرمة", 21.4225, 39.8262), City("المدينة المنورة", 24.4686, 39.6142), City("الرياض", 24.7136, 46.6753),
        City("جدة", 21.5433, 39.1728), City("الدمام", 26.4207, 50.0888), City("الخبر", 26.2172, 50.1971),
        City("الكويت", 29.3759, 47.9774), City("الدوحة", 25.2854, 51.5310), City("أبوظبي", 24.4539, 54.3773),
        City("دبي", 25.2048, 55.2708), City("الشارقة", 25.3463, 55.4209), City("مسقط", 23.5880, 58.3829),
        City("القاهرة", 30.0444, 31.2357), City("الجيزة", 30.0131, 31.2089), City("الإسكندرية", 31.2001, 29.9187),
        City("المنصورة", 31.0409, 31.3785), City("طنطا", 30.7865, 31.0004), City("الزقازيق", 30.5877, 31.5020),
        City("دمنهور", 31.0341, 30.4682), City("كفر الشيخ", 31.1107, 30.9388), City("بنها", 30.4660, 31.1848),
        City("شبين الكوم", 30.5594, 31.0076), City("دمياط", 31.4175, 31.8144), City("بورسعيد", 31.2653, 32.3019),
        City("الإسماعيلية", 30.5965, 32.2715), City("السويس", 29.9668, 32.5498), City("الفيوم", 29.3084, 30.8428),
        City("بني سويف", 29.0661, 31.0994), City("المنيا", 28.0871, 30.7618), City("أسيوط", 27.1810, 31.1837),
        City("سوهاج", 26.5591, 31.6957), City("قنا", 26.1551, 32.7160), City("الأقصر", 25.6872, 32.6396),
        City("أسوان", 24.0889, 32.8998), City("الغردقة", 27.2579, 33.8116), City("مرسى مطروح", 31.3543, 27.2373),
        City("عمّان", 31.9454, 35.9284), City("القدس", 31.7683, 35.2137), City("بيروت", 33.8938, 35.5018),
        City("دمشق", 33.5138, 36.2765), City("بغداد", 33.3152, 44.3661), City("صنعاء", 15.3694, 44.1910),
        City("الخرطوم", 15.5007, 32.5599), City("طرابلس", 32.8872, 13.1913), City("تونس", 36.8065, 10.1815),
        City("الجزائر", 36.7538, 3.0588), City("الرباط", 34.0209, -6.8416), City("الدار البيضاء", 33.5731, -7.5898),
        City("إسطنبول", 41.0082, 28.9784), City("لندن", 51.5074, -0.1278),
    )
}
