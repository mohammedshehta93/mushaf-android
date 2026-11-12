package com.shehta.mushaf

import com.shehta.mushaf.data.Methods
import com.shehta.mushaf.data.PrayerCalc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checked against published timetables (Umm al-Qura for Makkah, the Egyptian authority for Cairo). */
class PrayerCalcTest {
    private fun minutes(h: Double) = Math.round(h * 60).toInt()

    @Test fun makkahUmmAlQura() {
        val t = PrayerCalc.compute(2026, 11, 12, 21.4225, 39.8262, 3.0, Methods.byId("umm_alqura"), 1)
        assertEquals(5 * 60 + 11, minutes(t.fajr), 3)
        assertEquals(12 * 60 + 5, minutes(t.dhuhr), 2)
        assertEquals(minutes(t.maghrib) + 90, minutes(t.isha))
    }

    @Test fun cairoEgyptian() {
        val t = PrayerCalc.compute(2026, 11, 12, 30.0444, 31.2357, 2.0, Methods.byId("egypt"), 1)
        assertTrue(minutes(t.fajr) in (4 * 60 + 45)..(4 * 60 + 55))
        assertTrue(minutes(t.maghrib) in (16 * 60 + 58)..(17 * 60 + 4))
    }

    @Test fun qiblaDirections() {
        assertEquals(246.0, PrayerCalc.qibla(26.2235, 50.5876), 1.5)   // Manama
        assertEquals(136.0, PrayerCalc.qibla(30.0444, 31.2357), 1.5)   // Cairo
        assertEquals(119.0, PrayerCalc.qibla(51.5074, -0.1278), 1.5)   // London
    }

    @Test fun highLatitudeNeverNaN() {
        val t = PrayerCalc.compute(2026, 6, 21, 51.5074, -0.1278, 1.0, Methods.byId("mwl"), 1)
        assertTrue(t.list().none { it.isNaN() })
    }

    private fun assertEquals(expected: Int, actual: Int, tolerance: Int) =
        assertTrue("expected $expected±$tolerance but was $actual", kotlin.math.abs(expected - actual) <= tolerance)
}
