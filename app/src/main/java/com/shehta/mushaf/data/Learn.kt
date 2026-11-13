package com.shehta.mushaf.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class Sheikh(val name: String, val query: String, val desc: String)
class Poem(val name: String, val author: String, val desc: String, val query: String)
class Group<T>(val title: String, val items: List<T>)
class NamedQuery(val name: String, val query: String)

class TajweedExample(val s: Int, val a: Int, val text: String, val from: Int, val to: Int, val rule: String)
class LessonSection(val heading: String, val text: String, val examples: List<String>)
class Lesson(val id: String, val title: String, val sections: List<LessonSection>, val quiz: List<String>)

/**
 * «طلب العلم»: the curated library (sheikhs, didactic poems, nasheeds — tools/content/library.json) and the
 * simplified tajweed course (tools/content/tajweed_lessons.json, examples extracted from the Mushaf text at build time).
 */
object Learn {
    private fun obj(path: String): JSONObject? = try { JSONObject(Quran.asset(path)) } catch (e: Exception) { null }

    private val lib by lazy { obj("learn/library.json") ?: JSONObject() }

    private fun <T> groups(key: String, item: (JSONObject) -> T): List<Group<T>> {
        val arr = lib.optJSONArray(key) ?: return emptyList()
        return List(arr.length()) { i ->
            val g = arr.getJSONObject(i)
            val items = g.getJSONArray("items")
            Group(g.getString("title"), List(items.length()) { item(items.getJSONObject(it)) })
        }
    }

    val sheikhs: List<Group<Sheikh>> by lazy { groups("sheikhGroups") { Sheikh(it.getString("n"), it.getString("q"), it.optString("d")) } }
    val poems: List<Group<Poem>> by lazy { groups("poemGroups") { Poem(it.getString("n"), it.optString("a"), it.optString("d"), it.getString("q")) } }
    val nasheeds: List<NamedQuery> by lazy {
        val a = lib.optJSONArray("nasheeds") ?: JSONArray()
        List(a.length()) { a.getJSONObject(it).let { o -> NamedQuery(o.getString("n"), o.getString("q")) } }
    }
    val podcastTopics: List<String> by lazy {
        val a = lib.optJSONArray("podcastTopics") ?: JSONArray()
        List(a.length()) { a.getString(it) }
    }

    // ------------------------------------------------------------------ YouTube links (search only; nothing re-hosted)
    private const val NO_MUSIC = "بدون موسيقى"
    private fun enc(q: String) = URLEncoder.encode(q, "UTF-8")
    private fun yt(q: String, sp: String? = null) =
        "https://www.youtube.com/results?search_query=" + enc(q) + (if (sp != null) "&sp=$sp" else "")
    fun noMusic(q: String) = if (NO_MUSIC in q) q else "$q $NO_MUSIC"

    fun channel(q: String) = yt(q, "EgIQAg%3D%3D")                               // channels only
    fun playlists(q: String) = yt(q, "EgIQAw%3D%3D")                             // playlists only (organised series)
    fun lectures(q: String) = yt(noMusic("$q محاضرة"))
    fun shorts(q: String) = yt(noMusic("$q #shorts"))
    fun nasheed(q: String) = yt(noMusic(q))
    fun webSearch(q: String) = "https://www.google.com/search?q=" + enc(q)

    // ------------------------------------------------------------------ tajweed course
    private val tj by lazy { obj("learn/tajweed.json") ?: JSONObject() }

    val ruleNames: Map<String, String> by lazy {
        val o = tj.optJSONObject("ruleNames") ?: JSONObject()
        o.keys().asSequence().associateWith { o.getString(it) }
    }

    val lessons: List<Lesson> by lazy {
        val a = tj.optJSONArray("lessons") ?: JSONArray()
        List(a.length()) { i ->
            val l = a.getJSONObject(i)
            val secs = l.getJSONArray("sections")
            val quiz = l.optJSONArray("quiz")
            Lesson(
                l.getString("id"), l.getString("title"),
                List(secs.length()) { j ->
                    val s = secs.getJSONObject(j)
                    LessonSection(s.getString("h"), s.getString("t"), listOfNotNull(s.optString("ex").ifEmpty { null }, s.optString("ex2").ifEmpty { null }))
                },
                if (quiz == null) emptyList() else List(quiz.length()) { quiz.getString(it) },
            )
        }
    }

    fun examples(rule: String): List<TajweedExample> {
        val a = tj.optJSONObject("examples")?.optJSONArray(rule) ?: return emptyList()
        return List(a.length()) { a.getJSONArray(it).let { x -> TajweedExample(x.getInt(0), x.getInt(1), x.getString(2), x.getInt(3), x.getInt(4), rule) } }
    }
}
