package com.shehta.mushaf.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.shehta.mushaf.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class Sheikh(val name: String, val query: String, val desc: String)
class Track(val title: String, val url: String)
class Poem(val name: String, val author: String, val desc: String, val query: String, val tracks: List<Track> = emptyList(), val source: String = "")
class Group<T>(val title: String, val items: List<T>)
class Topic(val name: String, val query: String, val desc: String = "")

class TajweedExample(val s: Int, val a: Int, val text: String, val from: Int, val to: Int, val rule: String)
class LessonSection(val heading: String, val text: String, val examples: List<String>)
class Lesson(val id: String, val title: String, val sections: List<LessonSection>, val quiz: List<String>)

/**
 * «طلب العلم»: the curated library (sheikhs, didactic poems, nasheeds — tools/content/library.json) and the
 * simplified tajweed course (tools/content/tajweed_lessons.json, examples extracted from the Mushaf text at build time).
 */
object Learn {
    private fun obj(path: String): JSONObject? = try { JSONObject(Quran.asset(path)) } catch (e: Exception) { null }

    /** Bundled copy (with audio tracks found at build time) — the remote file overrides lists and hide rules. */
    private val bundled by lazy { obj("learn/library.json") ?: JSONObject() }
    private var lib by mutableStateOf<JSONObject?>(null)
    private fun current(): JSONObject = lib ?: bundled

    /** The owner edits content/library.json on GitHub; every app picks it up (validated, cached, never breaks the app). */
    fun refresh(context: Context) {
        val cache = File(context.filesDir, "library_remote.json")
        if (lib == null && cache.exists()) valid(cache.readText())?.let { lib = it }
        Thread {
            try {
                val c = URL("https://raw.githubusercontent.com/${BuildConfig.UPDATE_REPO}/main/content/library.json").openConnection() as HttpURLConnection
                c.connectTimeout = 15000; c.readTimeout = 20000
                val txt = c.inputStream.bufferedReader().use { it.readText() }
                valid(txt)?.let { o -> cache.writeText(txt); android.os.Handler(android.os.Looper.getMainLooper()).post { lib = o } }
            } catch (e: Exception) { /* offline: keep cached / bundled */ }
        }.start()
    }

    private fun valid(t: String): JSONObject? = try {
        JSONObject(t).takeIf { it.optJSONArray("sheikhGroups") != null && it.optJSONArray("poemGroups") != null }
    } catch (e: Exception) { null }

    private fun hideList(key: String): List<String> {
        val a = current().optJSONObject("hide")?.optJSONArray(key) ?: return emptyList()
        return List(a.length()) { a.optString(it).trim() }.filter { it.isNotEmpty() }
    }
    private fun hidden(key: String, vararg texts: String): Boolean {
        val h = hideList(key); if (h.isEmpty()) return false
        val t = texts.joinToString(" ") { ArabicText.normalize(it) }
        return h.any { t.contains(ArabicText.normalize(it)) }
    }
    fun reciterHidden(name: String) = hidden("reciters", name)
    fun podcastHidden(title: String, author: String) = hidden("podcasts", title, author)
    fun radioHidden(name: String) = hidden("radios", name)

    private fun <T> groups(key: String, hideKey: String, item: (JSONObject) -> T, name: (JSONObject) -> String): List<Group<T>> {
        val arr = current().optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val g = arr.getJSONObject(i)
            val items = g.optJSONArray("items") ?: JSONArray()
            Group(g.optString("title"), (0 until items.length()).map { items.getJSONObject(it) }.filter { !hidden(hideKey, name(it)) }.map(item))
        }.filter { it.items.isNotEmpty() }
    }

    /** Audio found at build time, by poem name (the remote file has no build results). */
    private val bundledTracks: Map<String, Pair<List<Track>, String>> by lazy {
        val out = HashMap<String, Pair<List<Track>, String>>()
        val gs = bundled.optJSONArray("poemGroups") ?: JSONArray()
        for (i in 0 until gs.length()) {
            val items = gs.getJSONObject(i).optJSONArray("items") ?: continue
            for (j in 0 until items.length()) { val o = items.getJSONObject(j); tracksOf(o).takeIf { it.isNotEmpty() }?.let { out[o.getString("n")] = it to o.optString("srcTitle") } }
        }
        out
    }
    private fun tracksOf(o: JSONObject): List<Track> {
        o.optString("url").takeIf { it.startsWith("http") }?.let { return listOf(Track(o.optString("n"), it)) }
        val a = o.optJSONArray("tracks") ?: return emptyList()
        return (0 until a.length()).map { a.getJSONObject(it) }.map { Track(it.optString("t"), it.getString("u")) }
    }

    val sheikhs: List<Group<Sheikh>> get() = groups("sheikhGroups", "sheikhs", { Sheikh(it.getString("n"), it.optString("q", it.getString("n")), it.optString("d")) }, { it.getString("n") })
    val poems: List<Group<Poem>> get() = groups("poemGroups", "poems", { o ->
        val own = tracksOf(o)
        val b = bundledTracks[o.getString("n")]
        Poem(o.getString("n"), o.optString("a"), o.optString("d"), o.optString("q", o.getString("n")),
            own.ifEmpty { b?.first ?: emptyList() }, if (own.isNotEmpty()) "" else b?.second ?: "")
    }, { it.getString("n") + " " + it.optString("a") })
    private fun topicGroups(arr: JSONArray?): List<Group<Topic>> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map { g ->
            val items = g.optJSONArray("items") ?: JSONArray()
            Group(g.optString("title"), (0 until items.length()).map { items.getJSONObject(it) }
                .map { Topic(it.getString("n"), it.optString("q", it.getString("n")), it.optString("d")) }
                .filter { !hidden("podcasts", it.name, it.query) })
        }.filter { it.items.isNotEmpty() }
    }

    /** Podcast topics in sections, plus the sheikhs of the library as their own section. */
    val podcastGroups: List<Group<Topic>> get() =
        topicGroups(current().optJSONArray("podcastGroups")) +
            listOf(Group("المشايخ", sheikhs.flatMap { it.items }.map { Topic(it.name, it.query) })).filter { it.items.isNotEmpty() }

    // ------------------------------------------------------------------ English corner
    private val en: JSONObject get() = current().optJSONObject("english") ?: bundled.optJSONObject("english") ?: JSONObject()
    val enSpeakers: List<Sheikh> get() {
        val a = en.optJSONArray("speakers") ?: return emptyList()
        return (0 until a.length()).map { a.getJSONObject(it) }.map { Sheikh(it.getString("n"), it.optString("q", it.getString("n")), it.optString("d")) }
            .filter { !hidden("sheikhs", it.name) }
    }
    val enSeries: List<Group<Topic>> get() = topicGroups(en.optJSONArray("series"))
    val enPodcasts: List<Group<Topic>> get() = topicGroups(en.optJSONArray("podcastGroups"))

    /** English searches: no «بدون موسيقى» suffix, but "no music" for short clips. */
    fun enLectures(q: String) = yt("$q lecture")
    fun enShorts(q: String) = yt("$q #shorts no music")

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
