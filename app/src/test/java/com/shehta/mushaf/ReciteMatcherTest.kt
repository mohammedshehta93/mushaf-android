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

    @Test fun uthmaniSpellingMatchesNormalSpelling() {
        assertEquals(ReciteText.norm("العالمين"), ReciteText.norm("ٱلۡعَٰلَمِينَ"))
        assertEquals(ReciteText.norm("الصلاة"), ReciteText.norm("ٱلصَّلَوٰةَ"))
        assertEquals(ReciteText.norm("الكتاب"), ReciteText.norm("ٱلۡكِتَٰبُ"))
        assertTrue(ReciteText.similarity(ReciteText.norm("ابراهيم"), ReciteText.norm("إِبۡرَٰهِـۧمَ")) >= 0.85f)
    }

    @Test fun correctRecitationRevealsEveryWord() {
        val m = ReciteMatcher(words)
        m.feed("الحمد لله رب العالمين".split(" "))
        m.feed("الرحمن الرحيم مالك يوم الدين".split(" "))
        assertTrue(m.done)
        assertEquals(100, m.accuracy)
    }

    @Test fun istiadhaAndBasmalaAreIgnored() {
        val m = ReciteMatcher(words)
        m.feed("اعوذ بالله من الشيطان الرجيم الحمد لله".split(" "))
        assertEquals(WordState.OK, m.state[0]); assertEquals(WordState.OK, m.state[1]); assertEquals(2, m.pos)
    }

    @Test fun wrongWordIsMarked() {
        val m = ReciteMatcher(words)
        m.feed("الحمد لله رب الناس".split(" "))
        assertEquals(WordState.WRONG, m.state[3])
    }

    @Test fun skippedWordsAreMarked() {
        val m = ReciteMatcher(words)
        m.feed("الحمد لله رب العالمين مالك يوم الدين".split(" "))
        assertEquals(WordState.SKIPPED, m.state[4]); assertEquals(WordState.SKIPPED, m.state[5])
        assertEquals(WordState.OK, m.state[6]); assertTrue(m.done)
    }

    @Test fun repeatingTheLastWordIsNotAMistake() {
        val m = ReciteMatcher(words)
        m.feed("الحمد لله لله رب".split(" "))
        assertEquals(3, m.pos); assertTrue(m.mistakes.isEmpty())
    }

    @Test fun splitWordsAreJoined() {
        val w = listOf(ExpectedWord(2, 21, 1, "يَٰٓأَيُّهَا"), ExpectedWord(2, 21, 2, "ٱلنَّاسُ"))
        val m = ReciteMatcher(w)
        m.feed("يا ايها الناس".split(" "))
        assertTrue(m.done); assertTrue(m.mistakes.isEmpty())
    }
}
