package com.shehta.mushaf.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shehta.mushaf.data.Line
import com.shehta.mushaf.data.Page
import com.shehta.mushaf.data.Quran
import com.shehta.mushaf.data.TajweedRules
import com.shehta.mushaf.data.ar

private sealed class TBlock {
    class Header(val s: Int) : TBlock()
    object Basmala : TBlock()
    class Ayahs(val list: MutableList<IntArray>) : TBlock()
}

private class Para(val text: AnnotatedString, val ranges: List<Triple<Int, Int, IntArray>>)   // start, end, [s, a]

/**
 * Tajweed view of a page: the ayahs that begin on this page, in the KFGQPC Uthmanic font, with every
 * tajweed rule coloured. Text reflows to any size, so it doubles as a large-print reading mode.
 */
@Composable
fun TajweedPage(
    page: Page,
    colors: MushafColors,
    fontSp: Float,
    decor: PageDecor,
    onTap: (IntArray?) -> Unit,
    onLongPress: (IntArray?) -> Unit,
) {
    val blocks = remember(page.number) {
        val out = ArrayList<TBlock>()
        val seen = HashSet<String>()
        for (l in page.lines) {
            when (l) {
                is Line.Header -> out += TBlock.Header(l.surah)
                is Line.Basmala -> out += TBlock.Basmala
                is Line.Text -> for (t in l.tokens) {
                    val k = "${t.s}:${t.a}"
                    if (k in seen || Quran.pageOf(t.s, t.a) != page.number) continue
                    seen += k
                    val last = out.lastOrNull()
                    if (last is TBlock.Ayahs) last.list += intArrayOf(t.s, t.a) else out += TBlock.Ayahs(mutableListOf(intArrayOf(t.s, t.a)))
                }
            }
        }
        out
    }
    val tap by rememberUpdatedState(onTap)
    val longPress by rememberUpdatedState(onLongPress)

    // The whole page must be visible at once: find the largest font size whose layout fits the screen.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val padH = with(density) { 14.dp.roundToPx() }
        val padV = with(density) { 8.dp.roundToPx() }
        val wPx = (constraints.maxWidth - 2 * padH).coerceAtLeast(100)
        val hPx = (constraints.maxHeight - 2 * padV).coerceAtLeast(100)
        val fs = remember(page.number, wPx, hPx, fontSp) {
            val headerExtra = with(density) { 22.dp.roundToPx() }
            val basmalaExtra = with(density) { 6.dp.roundToPx() }
            fun total(size: Float): Int {
                var h = 0
                for (b in blocks) {
                    h += when (b) {
                        is TBlock.Header -> measurer.measure(
                            "سورة ${Quran.surahs[b.s - 1].name}",
                            TextStyle(fontFamily = hafsFamily, fontSize = (size * 0.85f).sp),
                            constraints = Constraints(maxWidth = wPx),
                        ).size.height + headerExtra
                        is TBlock.Basmala -> measurer.measure(
                            BASMALA, TextStyle(fontFamily = hafsFamily, fontSize = size.sp), constraints = Constraints(maxWidth = wPx),
                        ).size.height + basmalaExtra
                        is TBlock.Ayahs -> measurer.measure(
                            buildPara(b.list, colors, PageDecor()).text,
                            TextStyle(fontFamily = hafsFamily, fontSize = size.sp, lineHeight = (size * LINE).sp, textAlign = TextAlign.Justify),
                            constraints = Constraints(maxWidth = wPx),
                        ).size.height
                    }
                }
                return h
            }
            var lo = 10f; var hi = fontSp.coerceIn(20f, 44f)
            if (total(hi) <= hPx) hi else {
                repeat(10) { val mid = (lo + hi) / 2; if (total(mid) <= hPx) lo = mid else hi = mid }
                lo
            }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            blocks.forEachIndexed { bi, b -> key(bi) {
                when (b) {
                    is TBlock.Header -> Box(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .border(2.dp, colors.frame, RoundedCornerShape(50))
                            .padding(vertical = 3.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("سورة ${Quran.surahs[b.s - 1].name}", fontFamily = hafsFamily, fontSize = (fs * 0.85f).sp, color = colors.ink) }
                    is TBlock.Basmala -> Text(
                        BASMALA, fontFamily = hafsFamily, fontSize = fs.sp, color = colors.ink,
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                    is TBlock.Ayahs -> {
                        val para = buildPara(b.list, colors, decor)
                        var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
                        fun ayahAt(o: androidx.compose.ui.geometry.Offset): IntArray? {
                            val off = layout?.getOffsetForPosition(o) ?: return null
                            return para.ranges.firstOrNull { off >= it.first && off < it.second }?.third
                        }
                        Text(
                            para.text,
                            fontFamily = hafsFamily, fontSize = fs.sp, lineHeight = (fs * LINE).sp,
                            color = colors.ink, textAlign = TextAlign.Justify,
                            onTextLayout = { layout = it },
                            modifier = Modifier.fillMaxWidth().pointerInput(para) {
                                detectTapGestures(onTap = { tap(ayahAt(it)) }, onLongPress = { longPress(ayahAt(it)) })
                            },
                        )
                    }
                }
            } }
        }
    }
}

private const val LINE = 1.8f
private const val BASMALA = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ"

private fun buildPara(list: List<IntArray>, c: MushafColors, decor: PageDecor): Para {
    val ranges = ArrayList<Triple<Int, Int, IntArray>>()
    val text = buildAnnotatedString {
        for (x in list) {
            val s = x[0]; val a = x[1]
            val t = Quran.ayahText(s, a)
            val start = length
            val bg = when {
                decor.playing?.let { it[0] == s && it[1] == a } == true -> c.playing
                decor.selection?.let { it.s == s && it.a == a } == true -> c.ayahHighlight
                else -> decor.highlights["$s:$a"]
            }
            val spans = Quran.tajweed(s, a)
            val colorAt = arrayOfNulls<androidx.compose.ui.graphics.Color>(t.length)
            var i = 0
            while (i + 2 < spans.size) {
                val col = c.tajweed[TajweedRules.group(spans[i])]
                for (k in spans[i + 1] until spans[i + 2].coerceAtMost(t.length)) colorAt[k] = col
                i += 3
            }
            var k = 0
            while (k < t.length) {
                val col = colorAt[k]
                var e = k + 1
                while (e < t.length && colorAt[e] == col) e++
                withStyle(SpanStyle(color = col ?: c.ink, background = bg ?: androidx.compose.ui.graphics.Color.Transparent)) { append(t.substring(k, e)) }
                k = e
            }
            withStyle(SpanStyle(color = c.marker, background = bg ?: androidx.compose.ui.graphics.Color.Transparent)) { append(" ${a.ar()} ") }
            ranges += Triple(start, length, intArrayOf(s, a))
        }
    }
    return Para(text, ranges)
}
