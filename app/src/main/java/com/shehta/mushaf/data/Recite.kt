package com.shehta.mushaf.data

import kotlin.math.max
import kotlin.math.min

/**
 * Comparing what the reciter said (speech recognition, no diacritics, normal spelling) with the Mushaf words
 * (Uthmani spelling). Pure Kotlin — unit-tested in src/test (ReciteMatcherTest).
 */
object ReciteText {
    /** Uthmani word → plain letters as people say/write it: dagger alif is an alif, «وٰ» is an alif (الصلوٰة → الصلاة). */
    fun norm(word: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < word.length) {
            val ch = word[i]
            val next = word.getOrNull(i + 1)
            when {
                ch == 'و' && next == 'ٰ' -> { sb.append('ا'); i += 2; continue }
                (ch == 'ى' || ch == 'ي') && next == 'ٰ' -> {        // «علىٰ» → على, but «أدرىٰك» → أدراك
                    val more = word.substring(i + 2).any { it in 'ء'..'ي' }
                    sb.append(if (more) 'ا' else 'ى'); i += 2; continue
                }
                ch == 'ٰ' -> sb.append('ا')
                ch == 'ۧ' -> sb.append('ي')            // small high yeh (إبراهـۧم)
                else -> sb.append(ch)
            }
            i++
        }
        return ArabicText.normalize(sb.toString()).replace(" ", "")
    }

    /** 0..1 similarity (1 - edit distance / longer length). */
    fun similarity(a: String, b: String): Float {
        if (a == b) return 1f
        if (a.isEmpty() || b.isEmpty()) return 0f
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]; d[0] = i
            for (j in 1..b.length) {
                val tmp = d[j]
                d[j] = min(min(d[j] + 1, d[j - 1] + 1), prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return 1f - d[b.length].toFloat() / max(a.length, b.length)
    }

    private val PREAMBLE = setOf("اعوذ", "بالله", "من", "الشيطان", "الرجيم", "بسم", "الله", "الرحمن", "الرحيم").map { norm(it) }.toSet()
    fun isPreamble(spoken: String) = norm(spoken) in PREAMBLE

    /** Disjoined letters (الٓمٓ، حمٓ…): said as letter names, so they are shown, not tested. */
    private val MUQATTAAT_SURAHS = setOf(2, 3, 7, 10, 11, 12, 13, 14, 15, 19, 20, 26, 27, 28, 29, 30, 31, 32, 36, 38, 40, 41, 42, 43, 44, 45, 46, 50, 68)
    fun isMuqattaat(s: Int, a: Int, w: Int) = w == 1 && ((a == 1 && s in MUQATTAAT_SURAHS) || (s == 42 && a == 2))
}

class ExpectedWord(val s: Int, val a: Int, val w: Int, val text: String, val key: String = ReciteText.norm(text),
                   val auto: Boolean = false)

/** What happened to a word: 0 hidden, 1 correct, 2 said wrongly (shown), 3 skipped (shown), 4 the starting words of a test, 5 prompted (the reciter was stuck). */
object WordState { const val HIDDEN = 0; const val OK = 1; const val WRONG = 2; const val SKIPPED = 3; const val GIVEN = 4; const val PROMPTED = 5 }

/**
 * Follows the recitation. Instead of consuming recognised words one by one (fragile: the recogniser revises its
 * partial results, repeats words between segments, and mishears), it re-aligns the WHOLE transcript heard since the
 * last checkpoint with the expected words every time anything new is heard (a small edit-distance alignment).
 *  - a word turns green as soon as it is said;
 *  - a word is marked wrong / skipped only after the reciter has gone on correctly past it (two words), or at the end
 *    — never while the reciter may still be saying it, and one misheard word never drags the following ones with it;
 *  - the isti'adha / basmala before a surah, repetitions and the letters of «الٓمٓ» cost nothing.
 * [threshold]: how close a spoken word must be to count as correct (lenient 0.55 · normal 0.7 · strict 0.85).
 */
class ReciteMatcher(val words: List<ExpectedWord>, var threshold: Float = 0.7f, private val start: Int = 0) {
    val state = IntArray(words.size)
    /** what was heard instead of a wrong word */
    val said = HashMap<Int, String>()
    /** the next word to recite */
    var pos = start; private set
    val done get() = pos >= words.size

    private var base = start                                   // first word not frozen yet
    private val committed = ArrayList<String>()                // final results heard since the checkpoint (normalised)
    private val committedRaw = ArrayList<String>()
    private val boundaries = HashSet<Int>()                    // token indices where a new recogniser result began
    private var partial: List<String> = emptyList()
    private var partialRaw: List<String> = emptyList()
    private var skipNext = 0                                   // tokens of the current utterance already used up
    private val okEver = BooleanArray(words.size)              // correct and followed by more: stays correct
    private val flagged = BooleanArray(words.size)             // a mistake once announced stays a mistake
    private var freeLead = false                               // after a correction/prompt: re-reading before the frontier is free

    init { for (i in 0 until start) state[i] = WordState.GIVEN; skipAuto() }

    private fun skipAuto() {
        while (pos < words.size && words[pos].auto) { state[pos] = WordState.OK; pos++ }
        if (base < pos && committed.isEmpty() && partial.isEmpty()) base = pos
    }

    private fun clean(raw: List<String>): Pair<List<String>, List<String>> {
        val n = ArrayList<String>(); val r = ArrayList<String>()
        for (t in raw) { val k = ReciteText.norm(t); if (k.length >= 2 || k == "و") { n += k; r += t } }
        return n to r
    }

    /**
     * A recogniser result: [final] = this utterance/segment is complete (its words are kept), otherwise it is the
     * current, still-changing partial result of the utterance. Returns the indices that just became wrong/skipped.
     */
    fun onResult(tokens: List<String>, final: Boolean): List<Int> {
        var (n, r) = clean(tokens)
        if (skipNext > 0) { val k = minOf(skipNext, n.size); n = n.drop(k); r = r.drop(k) }
        // the utterance ended without a final text (error / timeout): keep what the last partial result had
        if (final && tokens.isEmpty()) { n = partial; r = partialRaw }
        if (final) {
            skipNext = 0
            if (n.isNotEmpty()) { boundaries += committed.size; committed += n; committedRaw += r }
            partial = emptyList(); partialRaw = emptyList()
        } else { partial = n; partialRaw = r }
        return align(false)
    }

    /** Session over: confirm the mistakes that were still waiting. */
    fun finish(): List<Int> { partial = emptyList(); return align(true) }

    /** The user is stuck: show the current word and move on. */
    fun giveCurrent(): Int? {
        if (done) return null
        val i = pos
        state[i] = WordState.PROMPTED
        // everything heard so far is accounted for
        skipNext += partial.size
        committed.clear(); committedRaw.clear(); boundaries.clear(); partial = emptyList(); partialRaw = emptyList()
        base = i + 1; pos = i + 1; skipAuto(); freeLead = true
        for (j in pos until words.size) if (state[j] != WordState.OK || !okEver[j]) state[j] = WordState.HIDDEN
        return i
    }

    /**
     * After the app has corrected the reciter aloud (the recogniser was stopped, a new session starts): everything
     * up to the frontier is settled, and he may re-read from the start of the ayah before going on.
     */
    fun resumeAfterCorrection() {
        skipNext = 0
        committed.clear(); committedRaw.clear(); boundaries.clear(); partial = emptyList(); partialRaw = emptyList()
        for (j in base until pos) if (state[j] == WordState.OK) okEver[j] = true
        base = pos; freeLead = true
        for (j in pos until words.size) if (!okEver[j] && !flagged[j]) state[j] = WordState.HIDDEN
    }

    private val anyOk get() = okEver.any { it }

    // ---------------------------------------------------------------- alignment
    private fun align(final: Boolean): List<Int> {
        val tok = committed + partial
        val raw = committedRaw + partialRaw
        val bnd = HashSet(boundaries).apply { if (partial.isNotEmpty()) add(committed.size) }
        val n = tok.size
        val m = minOf(words.size - base, n + 15)
        val before = state.copyOf()
        if (n == 0 || m <= 0) return emptyList()
        val E = words.subList(base, base + m)
        val sim = Array(n) { i -> FloatArray(m) { j -> ReciteText.similarity(tok[i], E[j].key) } }
        val preambleFree = !anyOk
        // the recogniser often mishears a letter or two of a correctly recited word: unless strict, a near miss counts
        val near = if (threshold < 0.8f) threshold - 0.2f else threshold
        fun ok(s: Float) = s >= near
        val NEG = -1e9f
        val D = Array(n + 1) { FloatArray(m + 1) { NEG } }
        val B = Array(n + 1) { ByteArray(m + 1) }       // 1 match/sub · 2 deletion · 3 insertion · 4 split · 5 join
        fun del(j: Int) = if (E[j - 1].auto) 0f else -1f
        fun ins(i: Int, j: Int): Float {
            val t = tok[i - 1]
            if ((j < m && E[j].auto) || (j > 0 && E[j - 1].auto)) return 0f                       // letter names of «الٓمٓ»
            if (j == 0 && freeLead) return 0f                                                        // re-reading after a correction
            if (ReciteText.isPreamble(t) && ((j == 0 && preambleFree) || (j < m && E[j].a == 1 && E[j].w == 1))) return 0f
            if (j > 0 && sim[i - 1][j - 1] >= threshold) return -0.3f                               // repeated the last word
            if (j > 1 && sim[i - 1][j - 2] >= threshold) return -0.4f
            return -1f
        }
        D[0][0] = 0f
        for (j in 1..m) { D[0][j] = D[0][j - 1] + del(j); B[0][j] = 2 }
        for (i in 1..n) for (j in 0..m) {
            var best = NEG; var b: Byte = 0
            fun take(v: Float, k: Byte) { if (v > best) { best = v; b = k } }
            if (j > 0) {
                val s = sim[i - 1][j - 1]
                take(D[i - 1][j - 1] + if (s >= threshold) 1f + s else if (ok(s)) 0.5f else -1f, 1)
                take(D[i][j - 1] + del(j), 2)
            }
            take(D[i - 1][j] + ins(i, j), 3)
            if (i > 1 && j > 0 && ReciteText.similarity(tok[i - 2] + tok[i - 1], E[j - 1].key) >= threshold) take(D[i - 2][j - 1] + 2f, 4)
            if (j > 1 && ReciteText.similarity(tok[i - 1], E[j - 2].key + E[j - 1].key) >= maxOf(threshold, 0.85f)) take(D[i - 1][j - 2] + 3.5f, 5)
            D[i][j] = best; B[i][j] = b
        }
        var bj = 0
        for (j in 1..m) if (D[n][j] > D[n][bj] || (final && j == bj + 1 && D[n][j] == D[n][bj] && B[n][j].toInt() == 1)) bj = j
        // trace back: label per expected word (0 untouched, 1 ok, 2 wrong, 3 skipped) and the token matched to it
        val lab = IntArray(m); val at = IntArray(m) { -1 }; val gapAtPause = BooleanArray(m)
        var i = n; var j = bj
        while (i > 0 || j > 0) {
            when (B[i][j].toInt()) {
                1 -> { lab[j - 1] = if (ok(sim[i - 1][j - 1])) 1 else 2; at[j - 1] = i - 1; i--; j-- }
                2 -> { lab[j - 1] = if (E[j - 1].auto) 1 else 3; at[j - 1] = i; gapAtPause[j - 1] = i in bnd; j-- }
                3 -> i--
                4 -> { lab[j - 1] = 1; at[j - 1] = i - 1; i -= 2; j-- }
                5 -> { lab[j - 1] = 1; lab[j - 2] = 1; at[j - 1] = i - 1; at[j - 2] = i - 1; i--; j -= 2 }
                else -> { i = 0; j = 0 }
            }
        }
        // ONE word missing exactly where the recogniser started a new result is usually the recogniser's loss, not
        // the reciter's: benefit of the doubt. (A whole forgotten ayah at a pause is still a mistake.)
        for (k in 0 until m) if (lab[k] == 3 && gapAtPause[k] && lab.getOrNull(k - 1) != 3 && lab.getOrNull(k + 1) != 3) lab[k] = 1
        var lastOk = -1
        for (k in 0 until m) if (lab[k] == 1 || okEver[base + k]) lastOk = k
        for (k in 0 until m) {
            val g = base + k
            val okAfter = (k + 1..lastOk).count { lab[it] == 1 }
            state[g] = when {
                flagged[g] -> state[g]
                okEver[g] || lab[k] == 1 -> { if (k < lastOk) okEver[g] = true; WordState.OK }
                lab[k] == 2 && ((k < lastOk && okAfter >= 2) || (final && k <= lastOk + 1)) -> { said[g] = raw.getOrElse(at[k]) { "" }; WordState.WRONG }
                k < lastOk && (okAfter >= 2 || final) && lab[k] == 3 -> WordState.SKIPPED
                else -> WordState.HIDDEN
            }
        }
        pos = base + lastOk + 1
        skipAuto()
        // checkpoint: freeze an old part so the alignment stays small and fast
        if (partial.isEmpty() && committed.size > 24) {
            var cut = -1
            for (k in lastOk downTo 0) if (lab[k] == 1 && at[k] in 0..committed.size - 8 && (k + 1..lastOk).count { lab[it] == 1 } >= 2) { cut = k; break }
            if (cut >= 0) {
                val drop = at[cut] + 1
                repeat(drop) { committed.removeAt(0); committedRaw.removeAt(0) }
                val nb = HashSet<Int>(); for (x in boundaries) if (x - drop >= 0) nb += x - drop
                boundaries.clear(); boundaries += nb
                base += cut + 1; freeLead = false
            }
        }
        val news = (0 until words.size).filter { (state[it] == WordState.WRONG || state[it] == WordState.SKIPPED) && before[it] != state[it] }
        for (k in news) flagged[k] = true
        return news
    }

    /** Wrong, skipped and prompted words. */
    val mistakes: List<Int> get() = state.indices.filter { state[it] == WordState.WRONG || state[it] == WordState.SKIPPED || state[it] == WordState.PROMPTED }
    val tested: Int get() = state.indices.count { !words[it].auto && state[it] != WordState.GIVEN && state[it] != WordState.HIDDEN }
    val accuracy: Int get() = if (tested == 0) 0 else (tested - mistakes.size) * 100 / tested
}
