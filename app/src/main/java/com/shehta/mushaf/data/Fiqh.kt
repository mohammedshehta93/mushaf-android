package com.shehta.mushaf.data

import org.json.JSONObject
import kotlin.random.Random

/** One hadith of Bulugh al-Maram: matn (with the Prophet's words in { }), takhrij, editor's grade and notes. */
class Hadith(
    val book: Int, val number: Int, val matn: String, val takhrij: String, val grade: String,
    val narrator: String, val prophetWords: String, val notes: List<String>,
) {
    val key get() = "$book:$number"
}

class FiqhBook(val index: Int, val title: String, val hadiths: List<Hadith>)

class QuizQuestion(val prompt: String, val context: String, val options: List<String>, val answer: Int, val explain: String)

/**
 * Fiqh study from «بلوغ المرام من أدلة الأحكام» (Ibn Hajar), the classic collection of the hadiths that
 * rulings are built on, arranged by the chapters of fiqh. Quizzes are generated from the text itself
 * (narrator, completing the Prophet's words, grading) — nothing is invented.
 */
object Fiqh {
    @Volatile private var data: List<FiqhBook>? = null
    var title = ""; private set
    var author = ""; private set

    fun books(): List<FiqhBook> {
        data?.let { return it }
        synchronized(this) {
            data?.let { return it }
            val o = JSONObject(Quran.asset("fiqh/bulugh.json"))
            title = o.getString("title"); author = o.getString("author")
            val arr = o.getJSONArray("books")
            val list = List(arr.length()) { bi ->
                val b = arr.getJSONObject(bi)
                val hs = b.getJSONArray("h")
                FiqhBook(bi, b.getString("title"), List(hs.length()) { hi ->
                    val h = hs.getJSONObject(hi)
                    val n = h.getJSONArray("n")
                    Hadith(bi, h.getInt("i"), h.getString("m"), h.getString("t"), h.getString("g"),
                        h.getString("r"), h.getString("q"), List(n.length()) { n.getString(it) })
                })
            }
            data = list
            return list
        }
    }

    fun all(): List<Hadith> = books().flatMap { it.hadiths }

    fun byKey(key: String): Hadith? {
        val (b, n) = key.split(":").map { it.toInt() }
        return books().getOrNull(b)?.hadiths?.firstOrNull { it.number == n }
    }

    /** Today's lesson: the next [count] hadiths not yet studied, in the book's order. */
    fun nextLesson(read: Set<String>, count: Int): List<Hadith> = all().filter { it.key !in read }.take(count)

    fun search(query: String): List<Hadith> {
        val q = ArabicText.normalize(query)
        if (q.length < 2) return emptyList()
        return all().filter { ArabicText.normalize(it.matn).contains(q) }.take(200)
    }

    // ------------------------------------------------------------------ quiz

    fun quiz(book: Int, size: Int = 10, seed: Long = System.currentTimeMillis()): List<QuizQuestion> {
        val rnd = Random(seed)
        val pool = books()[book].hadiths
        val narrators = pool.map { it.narrator }.filter { it.isNotBlank() }.distinct()
        val allWords = all().map { it.prophetWords }.filter { it.split(" ").size >= 6 }
        val out = ArrayList<QuizQuestion>()
        for (h in pool.shuffled(rnd)) {
            if (out.size >= size) break
            when (rnd.nextInt(3)) {
                0 -> if (h.narrator.isNotBlank() && narrators.size >= 4 && h.prophetWords.isNotBlank()) {
                    val opts = (narrators.filter { it != h.narrator }.shuffled(rnd).take(3) + h.narrator).shuffled(rnd)
                    out += QuizQuestion("من راوي هذا الحديث؟", "«${h.prophetWords.take(220)}»", opts, opts.indexOf(h.narrator),
                        "رواه ${h.narrator} — ${h.takhrij}")
                }
                1 -> {
                    val words = h.prophetWords.split(Regex("\\s+")).filter { it.isNotBlank() }
                    if (words.size >= 8) {
                        val cut = (words.size * 0.6).toInt().coerceIn(4, words.size - 3)
                        val head = words.take(cut).joinToString(" ")
                        val tail = words.drop(cut).take(6).joinToString(" ")
                        val wrong = allWords.filter { it != h.prophetWords }.shuffled(rnd).take(3).map { w ->
                            val ws = w.split(Regex("\\s+")); ws.drop(ws.size / 2).take(6).joinToString(" ")
                        }
                        val opts = (wrong + tail).shuffled(rnd)
                        out += QuizQuestion("أكمل الحديث:", "«$head …»", opts, opts.indexOf(tail), "«${h.prophetWords}» ${h.takhrij}")
                    }
                }
                else -> if (h.grade in listOf("صحيح", "حسن", "ضعيف")) {
                    val opts = listOf("صحيح", "حسن", "ضعيف")
                    out += QuizQuestion("ما حكم المحقق على هذا الحديث؟", "«${h.prophetWords.ifBlank { h.matn }.take(220)}»", opts,
                        opts.indexOf(h.grade), h.notes.firstOrNull()?.take(300) ?: h.grade)
                }
            }
        }
        return out
    }
}
