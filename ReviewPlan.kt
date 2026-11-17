package com.shehta.mushaf.data

import android.content.Context
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

/** Spaced review per page: a well-recited page comes back later, a weak one sooner. */
class ReviewPlan(context: Context) {
    private val sp = context.getSharedPreferences("mushaf", Context.MODE_PRIVATE)
    private fun load(): JSONObject = try { JSONObject(sp.getString("reviewPlan", "{}")!!) } catch (e: Exception) { JSONObject() }
    private fun today() = (System.currentTimeMillis() / 86_400_000L).toInt()

    fun record(page: Int, accuracy: Int) {
        val o = load()
        val prev = o.optJSONArray(page.toString())?.optInt(0) ?: 0
        val interval = when {
            accuracy >= 95 -> if (prev <= 0) 3 else min(60, (prev * 2.5).toInt().coerceAtLeast(prev + 1))
            accuracy >= 80 -> max(1, prev)
            else -> 1
        }
        o.put(page.toString(), org.json.JSONArray().put(interval).put(today() + interval).put(accuracy))
        sp.edit().putString("reviewPlan", o.toString()).apply()
    }

    /** Pages due today or earlier, most overdue first. */
    fun due(): List<Int> {
        val o = load(); val t = today()
        return o.keys().asSequence().mapNotNull { k -> o.optJSONArray(k)?.let { k.toInt() to it.optInt(1) } }
            .filter { it.second <= t }.sortedBy { it.second }.map { it.first }.toList()
    }

    fun lastAccuracy(page: Int): Int? = load().optJSONArray(page.toString())?.optInt(2)
    val count: Int get() = load().length()
}
