package com.shehta.mushaf.data

import android.content.Context
import android.util.Xml
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class Radio(val name: String, val url: String)
class Podcast(val title: String, val author: String, val feed: String, val art: String)
class Episode(val title: String, val url: String, val date: String, val duration: String, val description: String)
class NasheedList(val title: String, val note: String, val query: String)

/**
 * The «استمع» tab: live Quran radios (official mp3quran.net streams, checked at build time), podcasts
 * (Apple's public podcast directory + each show's own RSS feed — audio streams from the publisher),
 * and ibtihal / nasheed collections that open on YouTube. Nothing is copied or re-hosted.
 */
object Listen {
    fun radios(): List<Radio> = try {
        val arr = JSONArray(Quran.asset("listen/radios.json"))
        List(arr.length()) { arr.getJSONObject(it).let { o -> Radio(o.getString("n"), o.getString("u")) } }
    } catch (e: Exception) { emptyList() }

    /** Search terms offered as chips; results come from the public podcast directory. */
    val SUGGESTED: List<String> get() = Learn.podcastTopics

    /** Curated in tools/content/library.json; every YouTube search gets «بدون موسيقى». */
    val NASHEEDS: List<NasheedList> get() = Learn.nasheeds.map { NasheedList(it.name, "", it.query) }

    fun youtubeSearch(q: String) = Learn.nasheed(q)

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 30000
        c.setRequestProperty("User-Agent", "Mushaf-Android")
        c.instanceFollowRedirects = true
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    /** Blocking (background thread). Apple's public iTunes Search API, podcast directory only. */
    fun searchPodcasts(term: String): List<Podcast> {
        val o = JSONObject(get("https://itunes.apple.com/search?media=podcast&limit=40&term=" + URLEncoder.encode(term, "UTF-8")))
        val arr = o.getJSONArray("results")
        return (0 until arr.length()).map { arr.getJSONObject(it) }.filter { it.optString("feedUrl").isNotEmpty() }.map {
            Podcast(it.optString("collectionName"), it.optString("artistName"), it.getString("feedUrl"), it.optString("artworkUrl600", it.optString("artworkUrl100")))
        }
    }

    /** Blocking. Reads the show's RSS feed: newest episodes first, as published. */
    fun episodes(feed: String): List<Episode> {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(get(feed).reader())
        val out = ArrayList<Episode>()
        var inItem = false
        var title = ""; var url = ""; var date = ""; var dur = ""; var desc = ""
        var tag = ""
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> {
                    tag = p.name
                    if (tag == "item") { inItem = true; title = ""; url = ""; date = ""; dur = ""; desc = "" }
                    if (inItem && tag == "enclosure") url = p.getAttributeValue(null, "url") ?: url
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (inItem) {
                    val t = p.text ?: ""
                    when (tag) {
                        "title" -> title += t
                        "pubDate" -> date += t
                        "itunes:duration" -> dur += t
                        "description", "itunes:summary" -> if (desc.length < 400) desc += t
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (p.name == "item") {
                        inItem = false
                        if (url.isNotEmpty()) out += Episode(title.trim(), url, date.trim().take(16), dur.trim(), desc.replace(Regex("<[^>]+>"), "").trim().take(300))
                    }
                    tag = ""
                }
            }
        }
        return out
    }
}

/** Podcast subscriptions and where each episode was stopped. */
class ListenPrefs(context: Context) {
    private val sp = context.getSharedPreferences("mushaf", Context.MODE_PRIVATE)

    var subscriptions by mutableStateOf(load()); private set

    private fun load(): List<Podcast> = try {
        val a = JSONArray(sp.getString("podcasts", "[]"))
        List(a.length()) { a.getJSONObject(it).let { o -> Podcast(o.getString("t"), o.optString("a"), o.getString("f"), o.optString("i")) } }
    } catch (e: Exception) { emptyList() }

    fun isSubscribed(feed: String) = subscriptions.any { it.feed == feed }

    fun toggle(p: Podcast) {
        subscriptions = if (isSubscribed(p.feed)) subscriptions.filter { it.feed != p.feed } else subscriptions + p
        val a = JSONArray()
        subscriptions.forEach { a.put(JSONObject().put("t", it.title).put("a", it.author).put("f", it.feed).put("i", it.art)) }
        sp.edit().putString("podcasts", a.toString()).apply()
    }

    fun position(url: String): Long = sp.getLong("pos:" + url.hashCode(), 0L)
    fun savePosition(url: String, ms: Long) { sp.edit().putLong("pos:" + url.hashCode(), ms).apply() }

    var savedLinks by mutableStateOf(loadLinks()); private set
    private fun loadLinks(): List<Pair<String, String>> = try {
        val a = JSONArray(sp.getString("ytLinks", "[]")); List(a.length()) { a.getJSONArray(it).let { x -> x.getString(0) to x.getString(1) } }
    } catch (e: Exception) { emptyList() }
    fun addLink(title: String, url: String) { savedLinks = savedLinks + (title to url); saveLinks() }
    fun removeLink(url: String) { savedLinks = savedLinks.filter { it.second != url }; saveLinks() }
    private fun saveLinks() {
        val a = JSONArray(); savedLinks.forEach { a.put(JSONArray().put(it.first).put(it.second)) }
        sp.edit().putString("ytLinks", a.toString()).apply()
    }
}
