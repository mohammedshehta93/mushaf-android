package com.shehta.mushaf

import com.shehta.mushaf.data.HadithDb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** Runs on the hadith files built by tools/prepare_data.py (skipped when they are not there). */
class HadithDbTest {
    private val dir = File("src/main/assets/quran")

    @Before fun open() {
        assumeTrue(File(dir, "hadith/bukhari.txt").exists())
        HadithDb.opener = { File(dir, it).inputStream() }
    }

    @Test fun findsByText() {
        val r = HadithDb.search("إنما الأعمال بالنيات")
        assertEquals("bukhari", r.hits.first().book.id); assertEquals("1", r.hits.first().number)
    }

    @Test fun weakHadithShowsItsRuling() {
        val h = HadithDb.search("قلب القرآن يس").hits.first { it.book.id == "tirmidhi" && it.number == "2887" }
        assertEquals(3, h.verdict); assertTrue(h.rulings.contains("الألباني: موضوع"))
        assertTrue(HadithDb.plain(h.text.substring(h.start)).startsWith("ان لكل شي قلبا"))
    }

    @Test fun notInTheBooks() { assertEquals(0, HadithDb.search("اطلبوا العلم ولو بالصين").total) }

    @Test fun shortWordsAreWholeWords() {
        assertTrue(HadithDb.search("قلب القرآن يس").hits.none { it.book.id == "bukhari" })
    }
}
