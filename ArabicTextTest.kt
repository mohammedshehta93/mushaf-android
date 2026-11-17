package com.shehta.mushaf

import com.shehta.mushaf.data.ArabicText
import com.shehta.mushaf.data.QuranSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArabicTextTest {
    @Test fun removesTashkeelAndUnifiesAlef() {
        assertEquals("الرحمن", ArabicText.normalize("ٱلرَّحْمَـٰنِ"))
        assertEquals("الحمد لله", ArabicText.normalize("ٱلْحَمْدُ لِلَّهِ"))
    }

    @Test fun ignoresWaqfMarksAndNumbers() {
        assertEquals("لا ريب فيه", ArabicText.normalize("لَا رَيْبَ ۛ فِيهِ ۛ ٢"))
    }

    @Test fun searchFindsAyahWithoutDiacritics() {
        val texts = mapOf(
            "1:1" to "بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ",
            "1:2" to "ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَـٰلَمِينَ",
            "112:1" to "قُلْ هُوَ ٱللَّهُ أَحَدٌ",
        )
        val hits = QuranSearch.search(texts, "الله احد")
        assertEquals(1, hits.size)
        assertEquals(112, hits[0].s)
        assertTrue(QuranSearch.search(texts, "ا").isEmpty())
    }
}
