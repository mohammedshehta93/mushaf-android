package com.shehta.mushaf.data

import android.content.Context
import android.graphics.Typeface
import android.util.LruCache
import org.json.JSONArray
import org.json.JSONObject

/** One surah from the index. */
data class Surah(val n: Int, val name: String, val ayahs: Int, val type: String, val page: Int)

/** A juz or a rub' al-hizb start. */
data class Mark(val n: Int, val s: Int, val a: Int, val page: Int)

/** A difficult word with its meaning (Al-Muyassar fi Gharib Al-Quran, KFGQPC). */
data class Gharib(val id: Int, val s: Int, val a: Int, val phrase: String, val meaning: String)

/** One block of Ibn Kathir's commentary, covering ayahs [from..to]. */
data class TafsirBlock(val from: Int, val to: Int, val text: String)

/**
 * One glyph of a page font. w = word index inside the ayah, 0 = the ayah-end marker.
 * g = gharib id or -1; colored = short "difficult word" entry (long phrase explanations are tappable but not coloured).
 */
class Token(val glyph: String, val s: Int, val a: Int, val w: Int, val g: Int, val colored: Boolean)

sealed class Line {
    class Header(val surah: Int) : Line()
    object Basmala : Line()
    class Text(val tokens: List<Token>) : Line()
}

class Page(val number: Int, val lines: List<Line>)

data class MorphWord(val root: String, val lemma: String, val description: String)

data class Reciter(val id: String, val name: String, val timed: Boolean) {
    fun file(s: Int, a: Int) = String.format(java.util.Locale.US, "%03d%03d.mp3", s, a)
    fun url(s: Int, a: Int) = "https://everyayah.com/data/$id/${file(s, a)}"
}

/** Tajweed rule names (same order as tools/tajweed_prep.py RULES) grouped into the colours of the coloured Mushaf. */
object TajweedRules {
    val RULES = listOf(
        "ghunnah", "idghaam_ghunnah", "idghaam_no_ghunnah", "idghaam_mutajanisayn", "idghaam_mutaqaribayn",
        "idghaam_shafawi", "ikhfa", "ikhfa_shafawi", "iqlab", "madd_2", "madd_246", "madd_muttasil",
        "madd_munfasil", "madd_6", "qalqalah", "hamzat_wasl", "lam_shamsiyyah", "silent",
    )
    /** 0 ghunnah/ikhfa/iqlab (green), 1 madd 2 (gold), 2 madd 2-4-6 (amber), 3 munfasil (orange), 4 muttasil (red), 5 lazim 6 (dark red), 6 qalqalah (blue), 7 not pronounced (grey) */
    fun group(rule: Int): Int = when (RULES.getOrNull(rule)) {
        "ghunnah", "idghaam_ghunnah", "ikhfa", "ikhfa_shafawi", "iqlab", "idghaam_shafawi" -> 0
        "madd_2" -> 1
        "madd_246" -> 2
        "madd_munfasil" -> 3
        "madd_muttasil" -> 4
        "madd_6" -> 5
        "qalqalah" -> 6
        else -> 7
    }
    val legend = listOf(
        "غنّة / إخفاء / إقلاب / إدغام بغنّة", "مدّ طبيعي (حركتان)", "مدّ عارض أو لين (٢–٤–٦)",
        "مدّ جائز منفصل (٤–٥)", "مدّ واجب متصل (٤–٥)", "مدّ لازم (٦)", "قلقلة", "لا يُلفظ (همزة وصل، لام شمسية، إدغام بلا غنّة)",
    )
}

/**
 * All Quran data, read from the offline assets that CI generates (see tools/prepare_data.py).
 * Small things load at startup; pages, fonts and tafsir load on demand and are cached.
 */
object Quran {
    const val PAGES = 604
    private lateinit var app: Context

    lateinit var surahs: List<Surah>; private set
    lateinit var juz: List<Mark>; private set
    lateinit var rub: List<Mark>; private set
    private lateinit var ayahPage: Array<IntArray>
    private lateinit var pageRanges: Array<IntArray>
    private var sajdas: Set<Long> = emptySet()

    private val pageCache = LruCache<Int, Page>(24)
    private val fontCache = LruCache<Int, Typeface>(10)
    private val tafsirCache = LruCache<Int, List<TafsirBlock>>(6)

    @Volatile private var gharib: List<Gharib>? = null
    @Volatile private var ayahText: Map<String, String>? = null

    val ready get() = this::surahs.isInitialized

    fun init(context: Context) {
        if (ready) return
        app = context.applicationContext
        val m = JSONObject(asset("meta.json"))
        surahs = m.getJSONArray("surahs").map { o ->
            Surah(o.getInt("n"), o.getString("name"), o.getInt("ayahs"), o.getString("type"), o.getInt("page"))
        }
        juz = m.getJSONArray("juz").map { Mark(it.getInt("n"), it.getInt("s"), it.getInt("a"), it.getInt("page")) }
        rub = m.getJSONArray("rub").map { Mark(it.getInt("n"), it.getInt("s"), it.getInt("a"), it.getInt("page")) }
        val ap = m.getJSONArray("ap")
        ayahPage = Array(ap.length()) { i -> ap.getJSONArray(i).toIntArray() }
        val pr = m.getJSONArray("pageRanges")
        pageRanges = Array(pr.length()) { i -> pr.getJSONArray(i).toIntArray() }
        sajdas = m.getJSONArray("sajda").map { key(it.getInt("s"), it.getInt("a")) }.toSet()
    }

    private fun key(s: Int, a: Int) = s.toLong() * 1000 + a

    fun asset(path: String): String =
        app.assets.open("quran/$path").bufferedReader(Charsets.UTF_8).use { it.readText() }

    // ------------------------------------------------------------------ pages

    fun cachedPage(n: Int): Page? = pageCache.get(n)

    fun page(n: Int): Page {
        pageCache.get(n)?.let { return it }
        val arr = JSONArray(asset("pages/$n.json"))
        val lines = ArrayList<Line>(15)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            when (o.getString("t")) {
                "h" -> lines += Line.Header(o.getInt("s"))
                "b" -> lines += Line.Basmala
                else -> {
                    val g = o.getString("g")
                    val k = o.getJSONArray("k")
                    val mm = o.getJSONArray("m")
                    val toks = ArrayList<Token>(g.length)
                    var ci = 0
                    for (t in 0 until k.length()) {
                        val kk = k.getJSONArray(t)
                        // every glyph is a single UTF-16 unit (Arabic presentation forms)
                        val cp = g.codePointAt(ci)
                        val len = Character.charCount(cp)
                        val m = mm.getInt(t)
                        val gid = if (m >= 0) m else if (m <= -2) -(m + 2) else -1
                        toks += Token(g.substring(ci, ci + len), kk.getInt(0), kk.getInt(1), kk.getInt(2), gid, m >= 0)
                        ci += len
                    }
                    lines += Line.Text(toks)
                }
            }
        }
        return Page(n, lines).also { pageCache.put(n, it) }
    }

    fun pageFont(n: Int): Typeface {
        fontCache.get(n)?.let { return it }
        return Typeface.createFromAsset(app.assets, "quran/fonts/p$n.ttf").also { fontCache.put(n, it) }
    }

    val basmalaFont: Typeface by lazy { Typeface.createFromAsset(app.assets, "quran/fonts/bsml.ttf") }
    val hafsFont: Typeface by lazy { Typeface.createFromAsset(app.assets, "quran/fonts/hafs.otf") }

    // ------------------------------------------------------------------ navigation helpers

    fun pageOf(s: Int, a: Int): Int = ayahPage.getOrNull(s - 1)?.getOrNull(a - 1) ?: 1

    /** [surah, ayah] of the first ayah on a page. */
    fun firstAyah(page: Int): IntArray = pageRanges[page - 1].let { intArrayOf(it[0], it[1]) }

    fun surahOfPage(page: Int): Surah = surahs[firstAyah(page)[0] - 1]

    fun juzOfPage(page: Int): Int = juz.lastOrNull { it.page <= page }?.n ?: 1

    fun rubOfPage(page: Int): Mark = rub.lastOrNull { it.page <= page } ?: rub[0]

    fun isSajda(s: Int, a: Int) = key(s, a) in sajdas

    // ------------------------------------------------------------------ gharib / text / tafsir

    fun gharibList(): List<Gharib> {
        gharib?.let { return it }
        synchronized(this) {
            gharib?.let { return it }
            val arr = JSONArray(asset("gharib.json"))
            val list = List(arr.length()) { i ->
                val o = arr.getJSONArray(i)
                Gharib(i, o.getInt(0), o.getInt(1), o.getString(2), o.getString(3))
            }
            gharib = list
            return list
        }
    }

    fun gharib(id: Int): Gharib? = if (id < 0) null else gharibList().getOrNull(id)

    fun gharibOfAyah(s: Int, a: Int): List<Gharib> = gharibList().filter { it.s == s && it.a == a }

    fun texts(): Map<String, String> {
        ayahText?.let { return it }
        synchronized(this) {
            ayahText?.let { return it }
            val o = JSONObject(asset("text.json"))
            val map = HashMap<String, String>(6300)
            val it = o.keys()
            while (it.hasNext()) { val k = it.next(); map[k] = o.getString(k) }
            ayahText = map
            return map
        }
    }

    fun ayahText(s: Int, a: Int): String = texts()["$s:$a"] ?: ""

    fun tafsir(s: Int): List<TafsirBlock> {
        tafsirCache.get(s)?.let { return it }
        val arr = JSONArray(asset("tafsir/$s.json"))
        val list = List(arr.length()) { i ->
            val o = arr.getJSONArray(i)
            TafsirBlock(o.getInt(0), o.getInt(1), o.getString(2))
        }
        tafsirCache.put(s, list)
        return list
    }

    fun tafsirFor(s: Int, a: Int): TafsirBlock? = tafsir(s).firstOrNull { a in it.from..it.to }

    // ------------------------------------------------------------------ tajweed (display-text character spans)

    @Volatile private var tajweedMap: JSONObject? = null

    /** Flat [rule, start, end, rule, start, end, …] over [ayahText]. Rule indices follow [TajweedRules.RULES]. */
    fun tajweed(s: Int, a: Int): IntArray {
        val m = tajweedMap ?: synchronized(this) { tajweedMap ?: JSONObject(asset("tajweed.json")).also { tajweedMap = it } }
        val arr = m.optJSONArray("$s:$a") ?: return IntArray(0)
        return IntArray(arr.length()) { arr.getInt(it) }
    }

    // ------------------------------------------------------------------ morphology (roots)

    @Volatile private var morphMap: JSONObject? = null

    private fun morphJson(): JSONObject =
        morphMap ?: synchronized(this) { morphMap ?: JSONObject(asset("morph.json")).also { morphMap = it } }

    /** Word w (1-based) of an ayah: root, lemma and an Arabic description, or null. */
    fun morph(s: Int, a: Int, w: Int): MorphWord? {
        val arr = morphJson().optJSONArray("$s:$a") ?: return null
        if (w < 1 || w > arr.length()) return null
        val o = arr.getJSONArray(w - 1)
        return MorphWord(o.getString(0), o.getString(1), o.getString(2))
    }

    @Volatile private var rootIndex: Map<String, List<IntArray>>? = null

    /** Every occurrence (s, a, w) of a root. Built once, on first use (call from a background thread). */
    fun rootOccurrences(root: String): List<IntArray> {
        val idx = rootIndex ?: synchronized(this) {
            rootIndex ?: run {
                val map = HashMap<String, MutableList<IntArray>>()
                val m = morphJson()
                val keys = m.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val (s, a) = k.split(":").map { it.toInt() }
                    val arr = m.getJSONArray(k)
                    for (i in 0 until arr.length()) {
                        val r = arr.getJSONArray(i).getString(0)
                        if (r.isNotEmpty()) map.getOrPut(r) { ArrayList() }.add(intArrayOf(s, a, i + 1))
                    }
                }
                map.values.forEach { l -> l.sortWith(compareBy({ it[0] }, { it[1] }, { it[2] })) }
                map.also { rootIndex = it }
            }
        }
        return idx[root] ?: emptyList()
    }

    // ------------------------------------------------------------------ reciters / word timings

    val reciters: List<Reciter> by lazy {
        val arr = JSONArray(asset("reciters.json"))
        List(arr.length()) { i -> arr.getJSONObject(i).let { Reciter(it.getString("id"), it.getString("name"), it.getBoolean("timed")) } }
    }

    private val timingCache = HashMap<String, JSONObject?>()

    /** Word timings of one ayah for a reciter: flat [word(1-based), startMs, endMs, …], or null if unavailable. */
    fun timings(reciter: String, s: Int, a: Int): IntArray? {
        val m = synchronized(timingCache) {
            timingCache.getOrPut(reciter) {
                try { JSONObject(asset("timings/$reciter.json")) } catch (e: Exception) { null }
            }
        } ?: return null
        val arr = m.optJSONArray("$s:$a") ?: return null
        return IntArray(arr.length()) { arr.getInt(it) }
    }

    /** Warm-up on a background thread so the first tap is instant. */
    fun preload() {
        gharibList()
        texts()
    }
}

private inline fun <T> JSONArray.map(f: (JSONObject) -> T): List<T> = List(length()) { f(getJSONObject(it)) }
private fun JSONArray.toIntArray() = IntArray(length()) { getInt(it) }

/** Arabic numerals for display. */
fun Int.ar(): String {
    val d = "٠١٢٣٤٥٦٧٨٩"
    return toString().map { if (it.isDigit()) d[it - '0'] else it }.joinToString("")
}
