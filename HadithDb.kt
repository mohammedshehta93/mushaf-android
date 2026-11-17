package com.shehta.mushaf.data

import java.io.InputStream

/**
 * Hadith search / authenticity check over the six books and al-Muwatta (assets/quran/hadith/<book>.txt, built by
 * tools/hadith_books.py: one hadith per line — number, verdict, rulings, text). Pure Kotlin (tested on the JVM with
 * the real files). The plain-letter index is built once, in the background, the first time the screen is opened;
 * the full texts are read from the file only for the hits shown.
 */
object HadithDb {
    class Book(val id: String, val name: String)

    val books = listOf(
        Book("bukhari", "صحيح البخاري"), Book("muslim", "صحيح مسلم"), Book("abudawud", "سنن أبي داود"),
        Book("tirmidhi", "جامع الترمذي"), Book("nasai", "سنن النسائي"), Book("ibnmajah", "سنن ابن ماجه"),
        Book("malik", "موطأ مالك"),
    )

    /** verdict: 0 sahih/hasan · 1 weak · 2 very weak / munkar / shadh · 3 fabricated · -1 no ruling in the data */
    class Hit(val book: Book, val line: Int, val number: String, val verdict: Int) {
        var rulings: String = ""; var text: String = ""
        /** where the searched words are in [text] (the start of the shown part; the chain of narrators comes before) */
        var start: Int = 0
    }

    /** Opens a file under assets/quran/ (set by the app; the tests open the built files directly). */
    @Volatile var opener: ((String) -> InputStream)? = null

    private class Idx(val numbers: Array<String>, val verdicts: ByteArray, val plain: Array<String>)
    @Volatile private var index: Map<String, Idx>? = null
    val ready get() = index != null

    /** Plain letters for searching: no diacritics, one form of alif/ya/ha, no hamza seats, no punctuation. */
    fun plain(s: CharSequence): String = plainMap(s, null)

    /** [plain], and for each of its characters the index in [s] it came from (into [map], if given). */
    fun plainMap(s: CharSequence, map: IntArray?): String {
        val sb = StringBuilder(s.length)
        var space = true
        for ((i, ch) in s.withIndex()) {
            val c = when (ch) {
                'ٱ', 'أ', 'إ', 'آ' -> 'ا'
                'ى' -> 'ي'
                'ة' -> 'ه'
                else -> ch
            }
            when {
                c.code in 0x0610..0x061A || c.code in 0x064B..0x065F || c.code == 0x0670 || c.code in 0x06D6..0x06ED || c == 'ـ' -> {}
                c == 'ؤ' || c == 'ئ' || c == 'ء' || c == 'ۥ' || c == 'ۦ' -> {}
                c in 'ا'..'ي' -> { map?.set(sb.length, i); sb.append(c); space = false }
                else -> if (!space) { map?.set(sb.length, i); sb.append(' '); space = true }
            }
        }
        if (sb.isNotEmpty() && sb[sb.length - 1] == ' ') sb.setLength(sb.length - 1)
        return sb.toString()
    }

    @Synchronized fun load() {
        if (index != null) return
        val open = opener ?: return
        val m = HashMap<String, Idx>()
        for (b in books) {
            val nums = ArrayList<String>(8000); val ver = ArrayList<Byte>(8000); val pl = ArrayList<String>(8000)
            open("hadith/${b.id}.txt").bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    val p = line.split('\t', limit = 4)
                    if (p.size < 4) continue
                    nums += p[0]; ver += (p[1].toIntOrNull() ?: -1).toByte(); pl += plain(p[3])
                }
            }
            m[b.id] = Idx(nums.toTypedArray(), ver.toByteArray(), pl.toTypedArray())
        }
        index = m
    }

    class Result(val hits: List<Hit>, val total: Int)

    /**
     * All the words of [query] (in any order); the exact phrase first, then the books in order.
     * [only]: a book id, or null for all. At most [limit] hits are returned (with their texts).
     */
    fun search(query: String, only: String? = null, limit: Int = 60): Result {
        load()
        val idx = index ?: return Result(emptyList(), 0)
        val q = plain(query)
        val words = q.split(' ').filter { it.length >= 2 }
        if (words.isEmpty()) return Result(emptyList(), 0)
        // very short words (يس، لا، من…) must be whole words, or they would match inside longer ones
        fun has(t: String, w: String) = if (w.length > 2) t.contains(w) else {
            var k = t.indexOf(w); var ok = false
            while (k >= 0 && !ok) { ok = (k == 0 || t[k - 1] == ' ') && (k + w.length == t.length || t[k + w.length] == ' '); k = t.indexOf(w, k + 1) }
            ok
        }
        val exact = ArrayList<Hit>(); val loose = ArrayList<Hit>()
        for (b in books) {
            if (only != null && b.id != only) continue
            val x = idx[b.id] ?: continue
            for (i in x.plain.indices) {
                val t = x.plain[i]
                if (words.all { has(t, it) }) (if (words.size > 1 && t.contains(q)) exact else loose) += Hit(b, i, x.numbers[i], x.verdicts[i].toInt())
            }
        }
        val all = if (words.size > 1) exact + loose else loose
        val shown = all.take(limit)
        fillTexts(shown)
        for (h in shown) h.start = startOf(h.text, q, words)
        return Result(shown, all.size)
    }

    /** One hadith by book and number (e.g. to check a reference someone sent). */
    fun byNumber(bookId: String, number: String): Hit? {
        load()
        val x = index?.get(bookId) ?: return null
        val i = x.numbers.indexOf(number.trim()).takeIf { it >= 0 } ?: return null
        return Hit(books.first { it.id == bookId }, i, x.numbers[i], x.verdicts[i].toInt()).also { fillTexts(listOf(it)) }
    }

    private fun fillTexts(hits: List<Hit>) {
        val open = opener ?: return
        for ((book, hs) in hits.groupBy { it.book }) {
            val want = hs.associateBy { it.line }
            var n = 0
            open("hadith/${book.id}.txt").bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    val p = line.split('\t', limit = 4)
                    if (p.size < 4) continue
                    want[n]?.let { it.rulings = p[2]; it.text = p[3] }
                    n++
                    if (n > (want.keys.maxOrNull() ?: 0)) break
                }
            }
        }
    }

    /** Index in [text] a little before the searched words (at the start of a clause), 0 when they are near the start. */
    fun startOf(text: String, q: String, words: List<String>): Int {
        val map = IntArray(text.length + 1)
        val p = plainMap(text, map)
        var k = p.indexOf(q)
        if (k < 0) k = words.mapNotNull { w -> p.indexOf(w).takeIf { it >= 0 } }.minOrNull() ?: return 0
        var i = map[k]
        if (i < 120) return 0
        val stop = maxOf(0, i - 90)
        while (i > stop && text[i - 1] != '،' && text[i - 1] != '.' && text[i - 1] != '"') i--
        if (i == stop) while (i < text.length && text[i] != ' ') i++     // at a word boundary
        return i
    }

    val verdictNames = arrayOf("صحيح أو حسن", "ضعيف", "ضعيف جدًا / منكر", "موضوع")
}
