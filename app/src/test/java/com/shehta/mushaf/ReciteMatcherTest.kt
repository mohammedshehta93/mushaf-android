package com.shehta.mushaf

import com.shehta.mushaf.data.ExpectedWord
import com.shehta.mushaf.data.ReciteMatcher
import com.shehta.mushaf.data.ReciteText
import com.shehta.mushaf.data.WordState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReciteMatcherTest {
    // Al-Fatiha 1:2-4 in the Uthmani spelling of the Mushaf
    private val words = listOf("ٱلۡحَمۡدُ", "لِلَّهِ", "رَبِّ", "ٱلۡعَٰلَمِينَ", "ٱلرَّحۡمَٰنِ", "ٱلرَّحِيمِ", "مَٰلِكِ", "يَوۡمِ", "ٱلدِّينِ")
        .mapIndexed { i, t -> ExpectedWord(1, 2, i + 1, t) }
    // At-Tariq: the surah of the user's report
    private val tariq = ("وَٱلسَّمَآءِ وَٱلطَّارِقِ وَمَآ أَدۡرَىٰكَ مَا ٱلطَّارِقُ ٱلنَّجۡمُ ٱلثَّاقِبُ إِن كُلُّ نَفۡسٖ لَّمَّا عَلَيۡهَا حَافِظٞ " +
        "فَلۡيَنظُرِ ٱلۡإِنسَٰنُ مِمَّ خُلِقَ خُلِقَ مِن مَّآءٖ دَافِقٖ").split(" ").mapIndexed { i, t -> ExpectedWord(86, 1, i + 1, t) }
    private val tariqSaid = "والسماء والطارق وما ادراك ما الطارق النجم الثاقب ان كل نفس لما عليها حافظ فلينظر الانسان مم خلق خلق من ماء دافق".split(" ")

    private fun say(m: ReciteMatcher, s: String, final: Boolean = true) = m.onResult(s.split(" "), final)

    @Test fun uthmaniSpellingMatchesNormalSpelling() {
        assertEquals(ReciteText.norm("العالمين"), ReciteText.norm("ٱلۡعَٰلَمِينَ"))
        assertEquals(ReciteText.norm("الصلاة"), ReciteText.norm("ٱلصَّلَوٰةَ"))
        assertEquals(ReciteText.norm("الكتاب"), ReciteText.norm("ٱلۡكِتَٰبُ"))
        assertEquals(ReciteText.norm("ادراك"), ReciteText.norm("أَدۡرَىٰكَ"))
        assertEquals(ReciteText.norm("على"), ReciteText.norm("عَلَىٰ"))
        assertTrue(ReciteText.similarity(ReciteText.norm("ابراهيم"), ReciteText.norm("إِبۡرَٰهِـۧمَ")) >= 0.85f)
    }

    @Test fun correctRecitationRevealsEveryWord() {
        val m = ReciteMatcher(words)
        say(m, "الحمد لله رب العالمين"); say(m, "الرحمن الرحيم مالك يوم الدين"); m.finish()
        assertTrue(m.done); assertEquals(100, m.accuracy)
    }

    @Test fun streamingPartialsNeverLagBehind() {
        val m = ReciteMatcher(tariq)
        for (seg in tariqSaid.chunked(5)) {
            for (k in 1..seg.size) {
                m.onResult(seg.subList(0, k - 1) + seg[k - 1].take(3), false)    // half-heard, then revised
                m.onResult(seg.subList(0, k), false)
            }
            m.onResult(seg, true)
        }
        m.finish()
        assertTrue(m.done); assertTrue(m.mistakes.isEmpty())
    }

    @Test fun istiadhaAndBasmalaAreIgnored() {
        val m = ReciteMatcher(words)
        say(m, "اعوذ بالله من الشيطان الرجيم بسم الله الرحمن الرحيم الحمد لله")
        assertEquals(WordState.OK, m.state[0]); assertEquals(WordState.OK, m.state[1]); assertEquals(2, m.pos)
        assertTrue(m.mistakes.isEmpty())
    }

    @Test fun oneMisheardWordDoesNotDragTheRest() {
        val m = ReciteMatcher(tariq)
        val h = tariqSaid.toMutableList(); h[3] = "ادرى"; h[9] = "كلو"
        m.onResult(h, true); m.finish()
        assertTrue(m.done); assertTrue(m.mistakes.isEmpty())
    }

    @Test fun wrongWordIsMarkedOnlyAfterTheReciterGoesOn() {
        val m = ReciteMatcher(words)
        say(m, "الحمد لله رب الناس", final = false)
        assertEquals(WordState.HIDDEN, m.state[3])
        say(m, "الحمد لله رب الناس الرحمن الرحيم", final = false)
        assertEquals(WordState.WRONG, m.state[3]); assertEquals("الناس", m.said[3])
    }

    @Test fun skippedWordsAreMarked() {
        val m = ReciteMatcher(words)
        say(m, "الحمد لله رب العالمين مالك يوم الدين"); m.finish()
        assertEquals(WordState.SKIPPED, m.state[4]); assertEquals(WordState.SKIPPED, m.state[5])
        assertEquals(WordState.OK, m.state[6]); assertTrue(m.done)
    }

    @Test fun forgottenAyahAtAPauseIsStillAMistake() {
        val m = ReciteMatcher(tariq)
        m.onResult(tariqSaid.subList(0, 8), true); m.onResult(tariqSaid.subList(13, 20), true); m.finish()
        assertEquals((8..12).toList(), m.mistakes)
    }

    @Test fun oneWordLostByTheRecogniserAtAPauseIsForgiven() {
        val m = ReciteMatcher(tariq)
        m.onResult(tariqSaid.subList(0, 6), true); m.onResult(tariqSaid.subList(7, 14), true); m.finish()
        assertTrue(m.mistakes.isEmpty()); assertEquals(14, m.pos)
    }

    @Test fun repeatingIsNotAMistake() {
        val m = ReciteMatcher(words)
        say(m, "الحمد لله لله رب")
        assertEquals(3, m.pos); assertTrue(m.mistakes.isEmpty())
    }

    @Test fun promptThenContinue() {
        val m = ReciteMatcher(tariq)
        m.onResult(tariqSaid.subList(0, 5), false)
        assertEquals(5, m.giveCurrent())
        m.onResult(tariqSaid.subList(0, 5) + tariqSaid.subList(6, 12), true); m.finish()
        assertEquals(12, m.pos); assertEquals(listOf(5), m.mistakes); assertEquals(WordState.PROMPTED, m.state[5])
    }

    @Test fun splitWordsAreJoined() {
        val w = listOf(ExpectedWord(2, 21, 1, "يَٰٓأَيُّهَا"), ExpectedWord(2, 21, 2, "ٱلنَّاسُ"))
        val m = ReciteMatcher(w)
        say(m, "يا ايها الناس")
        assertTrue(m.done); assertTrue(m.mistakes.isEmpty())
    }
}
