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
                (ch == 'ى' || ch == 'ي') && next == 'ٰ' -> { sb.append('ى'); i += 2; continue }
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

class ReciteEvent(val index: Int, val state: Int, val said: String = "")

/**
 * Follows the recitation word by word. [threshold]: how close a spoken word must be to count as correct
 * (lenient 0.55 · normal 0.7 · strict 0.85).
 */
class ReciteMatcher(val words: List<ExpectedWord>, var threshold: Float = 0.7f, start: Int = 0) {
    val state = IntArray(words.size)
    var pos = start; private set
    val done get() = pos >= words.size
    private var heardAnything = false

    init { for (i in 0 until start) state[i] = WordState.GIVEN }

    private fun matches(spoken: String, i: Int): Boolean =
        i < words.size && ReciteText.similarity(spoken, words[i].key) >= threshold

    private fun skipAuto(out: MutableList<ReciteEvent>) {
        while (pos < words.size && words[pos].auto) { state[pos] = WordState.OK; out += ReciteEvent(pos, WordState.OK); pos++ }
    }

    /** The user is stuck: show the current word and move on. */
    fun giveCurrent(): ReciteEvent? {
        if (done) return null
        state[pos] = WordState.PROMPTED
        return ReciteEvent(pos++, WordState.PROMPTED)
    }

    fun feed(spokenRaw: List<String>): List<ReciteEvent> {
        val out = ArrayList<ReciteEvent>()
        val spoken = spokenRaw.map { ReciteText.norm(it) }.filter { it.length >= 2 || it == "و" }
        var k = 0
        while (k < spoken.size) {
            skipAuto(out)
            if (done) break
            val t = spoken[k]
            // isti'adha / basmala before starting
            if (!heardAnything && ReciteText.isPreamble(t) && !matches(t, pos)) { k++; continue }
            heardAnything = true
            // 1) this word
            if (matches(t, pos)) { state[pos] = WordState.OK; out += ReciteEvent(pos, WordState.OK); pos++; k++; continue }
            // 2) the recogniser split one Mushaf word in two («يا أيها» → «يٰٓأَيُّهَا»)
            if (k + 1 < spoken.size && matches(t + spoken[k + 1], pos)) {
                state[pos] = WordState.OK; out += ReciteEvent(pos, WordState.OK); pos++; k += 2; continue
            }
            // 3) the recogniser joined two Mushaf words in one
            if (pos + 1 < words.size && ReciteText.similarity(t, words[pos].key + words[pos + 1].key) >= threshold) {
                for (j in 0..1) { state[pos] = WordState.OK; out += ReciteEvent(pos, WordState.OK); pos++ }
                k++; continue
            }
            // 4) a repetition of the previous word (people often repeat to get going): ignore
            if (pos > 0 && ReciteText.similarity(t, words[pos - 1].key) >= threshold) { k++; continue }
            // 5) words were skipped: the spoken word is one of the next three
            val ahead = (1..3).firstOrNull { matches(t, pos + it) }
            if (ahead != null) {
                for (j in 0 until ahead) { state[pos + j] = WordState.SKIPPED; out += ReciteEvent(pos + j, WordState.SKIPPED) }
                pos += ahead
                state[pos] = WordState.OK; out += ReciteEvent(pos, WordState.OK); pos++; k++; continue
            }
            // 6) a different word
            state[pos] = WordState.WRONG; out += ReciteEvent(pos, WordState.WRONG, spokenRaw.getOrElse(k) { t }); pos++; k++
        }
        skipAuto(out)
        return out
    }

    /** Wrong, skipped and prompted words. */
    val mistakes: List<Int> get() = state.indices.filter { state[it] == WordState.WRONG || state[it] == WordState.SKIPPED || state[it] == WordState.PROMPTED }
    val tested: Int get() = state.indices.count { !words[it].auto && state[it] != WordState.GIVEN && state[it] != WordState.HIDDEN }
    val accuracy: Int get() = if (tested == 0) 0 else (tested - mistakes.size) * 100 / tested
}

