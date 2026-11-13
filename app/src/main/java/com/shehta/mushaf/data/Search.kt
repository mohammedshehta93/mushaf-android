package com.shehta.mushaf.data

/** Pure-Kotlin Arabic search helpers (unit-tested in src/test). */
object ArabicText {
    private val marks = Regex("[ؐ-ًؚ-ٰٟۖ-ۭ࣓-ࣿـ]")
    private val digits = Regex("[٠-٩۰-۹0-9]")

    /** Removes tashkeel and Quranic marks and unifies letter forms, so users can type plain Arabic. */
    fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in marks.replace(digits.replace(s, ""), "")) {
            sb.append(
                when (ch) {
                    'ٱ', 'أ', 'إ', 'آ' -> 'ا'
                    'ى' -> 'ي'
                    'ؤ', 'ئ', 'ء' -> continue   // hamza seats differ between the Uthmani text and normal typing
                    'ة' -> 'ه'
                    'ۥ', 'ۦ' -> continue
                    else -> ch
                }
            )
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }
}

data class SearchHit(val s: Int, val a: Int, val text: String)

object QuranSearch {
    @Volatile private var index: List<Triple<Int, Int, String>>? = null

    private fun buildIndex(texts: Map<String, String>): List<Triple<Int, Int, String>> =
        texts.entries.map { (k, v) ->
            val (s, a) = k.split(":").map { it.toInt() }
            Triple(s, a, ArabicText.normalize(v))
        }.sortedWith(compareBy({ it.first }, { it.second }))

    fun search(texts: Map<String, String>, query: String, limit: Int = 300): List<SearchHit> {
        val q = ArabicText.normalize(query)
        if (q.length < 2) return emptyList()
        val idx = index ?: buildIndex(texts).also { index = it }
        val out = ArrayList<SearchHit>()
        for ((s, a, norm) in idx) {
            if (norm.contains(q)) {
                out += SearchHit(s, a, texts["$s:$a"] ?: "")
                if (out.size >= limit) break
            }
        }
        return out
    }
}
