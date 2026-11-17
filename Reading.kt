package com.shehta.mushaf.data

import org.json.JSONArray
import org.json.JSONObject

/** An ayah (kind 'a') or a hadith (kind 'h') with its source; hadiths may carry an explanation. */
class RItem(
    val isAyah: Boolean, val s: Int, val a: Int, val text: String, val source: String,
    val label: String, val hadith: DailyHadith?, val english: String,
)

/** Intentions before reading, tadabbur reminders and the virtues of the Quran (assets/quran/reading.json). */
object Reading {
    private val root: JSONObject by lazy { try { JSONObject(Quran.asset("reading.json")) } catch (e: Exception) { JSONObject() } }

    private fun item(o: JSONObject, label: String = ""): RItem {
        val isAyah = o.optString("k") == "a"
        return RItem(isAyah, o.optInt("s"), o.optInt("a"), o.getString("t"), o.optString("src"), label,
            if (isAyah) null else parseHadith(JSONObject(o.toString()).put("s", o.optString("src"))), o.optString("en"))
    }

    private fun list(key: String, label: String): List<RItem> {
        val a = root.optJSONArray(key) ?: JSONArray()
        return List(a.length()) { a.getJSONObject(it).let { o -> item(o, o.optString(label)) } }
    }

    val intentions: List<RItem> by lazy { list("intentions", "n") }

    /** «وقفة تدبّر»: the three questions, and the hadith of repeating one ayah until the morning. */
    val tadabburQuestions: List<String> by lazy {
        root.optJSONObject("tadabbur")?.optJSONArray("questions")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
    }
    val repeatHadith: RItem? by lazy { root.optJSONObject("tadabbur")?.optJSONObject("repeat")?.let { item(it) } }
    val reminders: List<RItem> by lazy { list("reminders", "h") }
    val virtues: List<Group<RItem>> by lazy {
        val a = root.optJSONArray("virtues") ?: JSONArray()
        List(a.length()) { i ->
            val g = a.getJSONObject(i); val its = g.getJSONArray("items")
            Group(g.getString("title"), List(its.length()) { its.getJSONObject(it).let { o -> item(o, o.optString("h")) } }, g.optString("note"))
        }
    }
}
