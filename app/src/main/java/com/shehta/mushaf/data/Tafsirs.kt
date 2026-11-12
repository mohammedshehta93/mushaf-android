package com.shehta.mushaf.data

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class TafsirSource(val id: String, val name: String, val slug: String?)

/**
 * Tafsir books. Ibn Kathir is inside the app; the others download per surah on first use
 * (from github.com/spa5k/tafsir_api) and are then kept on the phone for offline reading.
 */
object Tafsirs {
    val sources = listOf(
        TafsirSource("ibnkathir", "ابن كثير", null),
        TafsirSource("saadi", "السعدي", "ar-tafseer-al-saddi"),
        TafsirSource("muyassar", "الميسّر", "ar-tafsir-muyassar"),
        TafsirSource("tabari", "الطبري", "ar-tafsir-al-tabari"),
        TafsirSource("qurtubi", "القرطبي", "ar-tafseer-al-qurtubi"),
        TafsirSource("irab", "إعراب القرآن", "al-i-rab-al-muyassar"),
    )

    private lateinit var dir: File
    private val cache = HashMap<String, List<TafsirBlock>>()

    fun init(context: Context) { dir = File(context.filesDir, "tafsir").apply { mkdirs() } }

    fun source(id: String) = sources.firstOrNull { it.id == id } ?: sources[0]

    fun isDownloaded(id: String, s: Int): Boolean = source(id).slug == null || File(dir, "$id/$s.json").exists()

    fun downloadedCount(id: String): Int = if (source(id).slug == null) 114 else (File(dir, id).list()?.size ?: 0)

    /** Blocking: call from a background thread. Throws if the surah is not on the phone and there is no internet. */
    fun blocks(id: String, s: Int): List<TafsirBlock> {
        val src = source(id)
        if (src.slug == null) return Quran.tafsir(s)
        synchronized(cache) { cache["$id:$s"]?.let { return it } }
        val f = File(dir, "$id/$s.json")
        if (!f.exists()) download(src, s, f)
        val arr = JSONArray(f.readText())
        val groups = ArrayList<TafsirBlock>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val a = o.getInt("ayah"); val t = o.getString("text")
            val last = groups.lastOrNull()
            if (last != null && last.text == t) groups[groups.size - 1] = TafsirBlock(last.from, a, t)
            else groups += TafsirBlock(a, a, t)
        }
        synchronized(cache) { cache["$id:$s"] = groups }
        return groups
    }

    fun blockFor(id: String, s: Int, a: Int): TafsirBlock? = blocks(id, s).firstOrNull { a in it.from..it.to }

    private fun download(src: TafsirSource, s: Int, f: File) {
        val url = URL("https://raw.githubusercontent.com/spa5k/tafsir_api/main/tafsir/${src.slug}/$s.json")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15000; conn.readTimeout = 30000
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        JSONArray(body)                                   // validate before saving
        f.parentFile?.mkdirs()
        val tmp = File(f.path + ".part"); tmp.writeText(body); tmp.renameTo(f)
    }

    /** Downloads every surah of a tafsir; reports progress 0..114. */
    fun downloadAll(id: String, progress: (Int) -> Unit) {
        val src = source(id)
        if (src.slug == null) return
        for (s in 1..114) {
            val f = File(dir, "$id/$s.json")
            if (!f.exists()) download(src, s, f)
            progress(s)
        }
    }

    fun delete(id: String) { File(dir, id).deleteRecursively(); synchronized(cache) { cache.keys.removeAll { it.startsWith("$id:") } } }
}
