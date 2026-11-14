package com.shehta.mushaf.ui

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.shehta.mushaf.data.Line
import com.shehta.mushaf.data.Page
import com.shehta.mushaf.data.Quran
import com.shehta.mushaf.data.TajweedRules
import kotlin.math.max
import kotlin.math.min

/** One drawn unit of a tajweed line: a word of the official text, or an ayah-number glyph of the page font. */
private class TUnit(val s: Int, val a: Int, val text: String, val spans: IntArray, val marker: Boolean) {
    var width = 0f
    var left = 0f
    var inkLeft = 0f          // where the word starts inside its (wide) layout
    var layout: StaticLayout? = null
}

private class TLine(val top: Float, val bottom: Float, val units: List<TUnit>)

private class TLaid(val lines: List<TLine>, val frames: List<Frame>, val fontPx: Float, val markerPx: Float, val lineH: Float)

/**
 * Coloured tajweed page with EXACTLY the lines of the printed Madinah Mushaf (QCF V2 layout): every line holds the
 * same words as the page view. The words are the official KFGQPC Hafs text drawn with its own font — the text is
 * never converted, so every haraka and sign is the Complex's own — and each tajweed rule is coloured inside the word
 * (colour does not change the shaping). The ayah numbers are the page font's own glyphs.
 */
@Composable
fun TajweedPage(
    page: Page,
    colors: MushafColors,
    @Suppress("UNUSED_PARAMETER") fontSp: Float,
    decor: PageDecor,
    onTap: (IntArray?) -> Unit,
    onLongPress: (IntArray?) -> Unit,
) {
    val tap by rememberUpdatedState(onTap)
    val longPress by rememberUpdatedState(onLongPress)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current.density
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val reserve = 24f * density
        val pageFont = remember(page.number) { Quran.pageFont(page.number) }
        val laid = remember(page.number, w, h, colors) { layoutTajweed(page, pageFont, w, h, reserve, colors) }

        fun ayahAt(p: Offset): IntArray? {
            val line = laid.lines.firstOrNull { p.y >= it.top && p.y <= it.bottom } ?: return null
            val u = line.units.minByOrNull { if (p.x < it.left) it.left - p.x else if (p.x > it.left + it.width) p.x - it.left - it.width else 0f } ?: return null
            return intArrayOf(u.s, u.a)
        }

        Canvas(Modifier.fillMaxSize().pointerInput(laid) {
            detectTapGestures(onTap = { tap(ayahAt(it)) }, onLongPress = { longPress(ayahAt(it)) })
        }) {
            drawIntoCanvas { cv ->
                val canvas = cv.nativeCanvas
                for (f in laid.frames) if (f.kind == 0) drawHeader(canvas, f, colors) else drawBasmala(canvas, f, colors)
                drawPageInfo(canvas, page.number, size.width, size.height, colors, density)
                val bg = Paint(Paint.ANTI_ALIAS_FLAG)
                val mk = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = pageFont; textSize = laid.markerPx; color = colors.marker.toArgb() }
                for (line in laid.lines) {
                    // ayah background (playing / selected / note colour)
                    for (u in line.units) {
                        val c = when {
                            decor.playing?.let { it[0] == u.s && it[1] == u.a } == true -> colors.playing
                            decor.selection?.let { it.s == u.s && it.a == u.a } == true -> colors.ayahHighlight
                            else -> decor.highlights["${u.s}:${u.a}"]
                        } ?: continue
                        bg.color = c.toArgb()
                        canvas.drawRoundRect(RectF(u.left - 3f, line.top + laid.lineH * 0.08f, u.left + u.width + 3f, line.bottom - laid.lineH * 0.06f), 12f, 12f, bg)
                    }
                    for (u in line.units) {
                        if (u.marker) {
                            val fm = mk.fontMetrics
                            canvas.drawText(u.text, u.left, (line.top + line.bottom) / 2 - (fm.ascent + fm.descent) / 2, mk)
                        } else {
                            val lay = u.layout ?: continue
                            canvas.save()
                            canvas.translate(u.left - u.inkLeft, (line.top + line.bottom) / 2 - lay.height / 2f)
                            lay.draw(canvas)
                            canvas.restore()
                        }
                    }
                }
            }
        }
    }
}

private fun layoutTajweed(page: Page, pageFont: Typeface, w: Float, h: Float, reserve: Float, c: MushafColors): TLaid {
    val special = page.number <= 2
    val padX = w * 0.016f
    val padTop = max(reserve, h * 0.012f)
    val innerW = w - 2 * padX
    val innerH = h - padTop - max(reserve, h * 0.012f)
    val rows = page.lines.size
    val lineH = if (special) innerH / (rows + 5) else innerH / max(rows, 15)
    val contentW = if (special) innerW * 0.76f else innerW
    val left = padX + (innerW - contentW) / 2
    val startY = padTop + if (special) (innerH - rows * lineH) / 2 else 0f

    // which lines are full lines of the print (measured with the page font, like the page view)
    val qp = Paint().apply { typeface = pageFont; textSize = 100f }
    val ink = c.ink.toArgb()

    // units of every text line
    val texts = ArrayList<Pair<Int, MutableList<TUnit>>>()
    val full = HashMap<Int, Boolean>()
    page.lines.forEachIndexed { li, l ->
        if (l !is Line.Text) return@forEachIndexed
        val units = ArrayList<TUnit>()
        val byGlyph = l.words.groupBy { it.glyphIndex }
        l.tokens.forEachIndexed { gi, t ->
            byGlyph[gi]?.forEach { wd -> units += TUnit(t.s, t.a, wd.text, wd.spans, false) }
            if (t.w == 0) units += TUnit(t.s, t.a, t.glyph, IntArray(0), true)
        }
        texts += li to units
        val qw = l.tokens.sumOf { qp.measureText(it.glyph).toDouble() }.toFloat()
        full[li] = !special && qw > Quran.maxLine * 0.9f
    }

    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Quran.hafsFont; color = ink }
    val mkp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = pageFont }
    fun build(u: TUnit) {
        if (u.marker) { u.width = mkp.measureText(u.text); return }
        val sp = SpannableString(u.text)
        var i = 0
        while (i + 2 < u.spans.size) {
            val st = u.spans[i + 1].coerceIn(0, u.text.length); val en = u.spans[i + 2].coerceIn(st, u.text.length)
            if (en > st) sp.setSpan(ForegroundColorSpan(c.tajweed[TajweedRules.group(u.spans[i])].toArgb()), st, en, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            i += 3
        }
        // laid out on a very wide single line so a word can never be broken; its real extent is read back
        val lay = StaticLayout.Builder.obtain(sp, 0, sp.length, tp, WIDE)
            .setTextDirection(TextDirectionHeuristics.RTL).setIncludePad(true).setMaxLines(1)
            .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE).build()
        u.layout = lay
        u.inkLeft = lay.getLineLeft(0)
        u.width = lay.getLineRight(0) - lay.getLineLeft(0)
    }

    // one font size for the page: measured with the real layouts at size 100, the widest line must fit
    val base = 100f
    tp.textSize = base; mkp.textSize = base * MARKER_SCALE
    val gapEm = 0.18f
    var widest = 1f
    for ((_, units) in texts) {
        var sum = 0f
        for (u in units) { build(u); sum += u.width }
        widest = max(widest, sum + gapEm * base * (units.size - 1))
    }
    val fontPx = min(base * contentW / widest, lineH * 0.46f)
    val markerPx = fontPx * MARKER_SCALE
    tp.textSize = fontPx; mkp.textSize = markerPx

    val lines = ArrayList<TLine>()
    for ((li, units) in texts) {
        for (u in units) build(u)
        val total = units.sumOf { it.width.toDouble() }.toFloat()
        val n = units.size
        val gap = if (full[li] == true && n > 1) max(0f, (contentW - total) / (n - 1)) else gapEm * fontPx
        var x = if (full[li] == true && n > 1) left + contentW else left + contentW / 2 + (total + gap * (n - 1)) / 2
        for (u in units) { x -= u.width; u.left = x; x -= gap }
        val top = startY + li * lineH
        lines += TLine(top, top + lineH, units)
    }
    val frames = ArrayList<Frame>()
    page.lines.forEachIndexed { li, l ->
        val top = startY + li * lineH
        when (l) {
            is Line.Header -> frames += Frame(0, l.surah, RectF(left, top + lineH * 0.10f, left + contentW, top + lineH * 0.90f))
            is Line.Basmala -> frames += Frame(1, 0, RectF(left, top, left + contentW, top + lineH))
            else -> {}
        }
    }
    return TLaid(lines, frames, fontPx, markerPx, lineH)
}

/** The page-font ayah number next to text of the Hafs font: QCF glyphs are drawn smaller per em. */
private const val MARKER_SCALE = 1.25f
private const val WIDE = 100000
