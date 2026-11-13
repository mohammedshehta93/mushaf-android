package com.shehta.mushaf.ui

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.shehta.mushaf.data.Line
import com.shehta.mushaf.data.Page
import com.shehta.mushaf.data.Quran
import com.shehta.mushaf.data.Token
import com.shehta.mushaf.data.ViewMode
import com.shehta.mushaf.data.ar
import kotlin.math.max
import kotlin.math.min

/** A glyph placed on screen. Coordinates in pixels, x from the left. */
class Placed(
    val tok: Token, val left: Float, val width: Float, val baseline: Float, val top: Float, val bottom: Float,
    /** Where the glyph is drawn (its origin) and its horizontal scale — differ from left/1 only on tightened lines. */
    val drawX: Float = left, val sx: Float = 1f,
)

/** A surah header (kind 0) or basmala (kind 1) box. */
class Frame(val kind: Int, val surah: Int, val rect: RectF)

class Laid(val glyphs: List<Placed>, val frames: List<Frame>, val size: Float, val height: Float, val lineH: Float)

/** What is currently selected on a page: an ayah (long press) and/or a gharib phrase (tap). w = pressed word. */
data class Selection(val s: Int, val a: Int, val gharib: Int = -1, val w: Int = 0, val wordOnly: Boolean = false)

/** Memorisation view: the first [revealed] words of the page are visible, the rest are hidden. */
class HifzView(val revealed: Int, val showFirstWords: Boolean, val mistakes: Map<String, Int>)

/** Everything drawn on top of the plain page. */
class PageDecor(
    val selection: Selection? = null,
    val playing: IntArray? = null,                 // [s, a, word]
    val highlights: Map<String, Color> = emptyMap(), // "s:a" -> note colour
    val hifz: HifzView? = null,
)

/** Word keys "s:a:w" of a page in reading order (ayah markers excluded). */
fun Page.wordKeys(): List<String> {
    val out = LinkedHashSet<String>()
    for (l in lines) if (l is Line.Text) for (t in l.tokens) if (t.w != 0) out += "${t.s}:${t.a}:${t.w}"
    return out.toList()
}

private const val REF = 100f

/** Widest natural line of all 604 QCF V1 page fonts at size 100 (16.2964 em, measured from the font files). */
private const val MAX_LINE_REF = 1630f

/**
 * Text size steps for the page view. Step 0 fits the widest line of the Mushaf (the printed proportions);
 * the larger steps fit a narrower reference width (median line = 1416); the minority of lines that are then too
 * long are narrowed horizontally (measured: 4.5% of lines at 1530, 11% at 1460). Line breaks never change.
 */
val PAGE_FONT_STEPS = floatArrayOf(MAX_LINE_REF, 1530f, 1460f)
val PAGE_FONT_NAMES = listOf("كالمطبوع", "كبير", "أكبر")

/**
 * Lays out one Mushaf page.
 *  PAGE  mode: the 15 lines of the printed Madinah Mushaf, scaled to fill the screen exactly.
 *  LARGE mode: the same word glyphs re-flowed at a bigger size, scrolling vertically inside the page.
 */
fun layoutPage(page: Page, font: Typeface, w: Float, h: Float, mode: ViewMode, largePx: Float, bottomReserve: Float, fontStep: Int = 2): Laid {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font; textSize = REF }
    val glyphs = ArrayList<Placed>(200)
    val frames = ArrayList<Frame>(4)
    val special = page.number <= 2

    fun refWidth(t: Token) = paint.measureText(t.glyph)

    if (mode == ViewMode.PAGE) {
        val padX = w * 0.014f
        val padTop = h * 0.012f
        val innerW = w - 2 * padX
        val innerH = h - padTop - max(bottomReserve, h * 0.012f)
        val rows = page.lines.size
        val lineH = if (special) innerH / (rows + 5) else innerH / max(rows, 15)
        val contentW = if (special) innerW * 0.76f else innerW
        val left = padX + (innerW - contentW) / 2

        // One font size for every page (like the printed Mushaf), never enlarged per page: narrower pages are justified.
        val ref = PAGE_FONT_STEPS[fontStep.coerceIn(0, PAGE_FONT_STEPS.size - 1)]
        val uniform = REF * innerW / ref
        val size = if (special) {
            var maxNat = 1f
            for (l in page.lines) if (l is Line.Text) maxNat = max(maxNat, l.tokens.sumOf { refWidth(it).toDouble() }.toFloat())
            min(min(uniform, REF * contentW / maxNat), lineH * 0.80f)
        } else min(uniform, lineH * 0.80f)
        paint.textSize = size
        val fm = paint.fontMetrics
        val startY = padTop + if (special) (innerH - rows * lineH) / 2 else 0f

        page.lines.forEachIndexed { i, l ->
            val top = startY + i * lineH
            when (l) {
                is Line.Header -> frames += Frame(0, l.surah, RectF(left, top + lineH * 0.10f, left + contentW, top + lineH * 0.90f))
                is Line.Basmala -> frames += Frame(1, 0, RectF(left, top, left + contentW, top + lineH))
                is Line.Text -> {
                    val ws = l.tokens.map { paint.measureText(it.glyph) }
                    val total = ws.sum()
                    val baseline0 = top + lineH / 2 - (fm.ascent + fm.descent) / 2
                    if (total > contentW) {
                        // Too long at this size: the word glyphs already touch, so the line is narrowed slightly
                        // as a whole (at most ~6% at «كبير», ~10% at «أكبر», on a minority of lines).
                        val sx = contentW / total
                        var x = left + contentW
                        l.tokens.forEachIndexed { j, t ->
                            x -= ws[j] * sx
                            glyphs += Placed(t, x, ws[j] * sx, baseline0, top, top + lineH, sx = sx)
                        }
                        return@forEachIndexed
                    }
                    val justify = !special && total > contentW * 0.72f && ws.size > 1
                    val gap = if (justify) (contentW - total) / (ws.size - 1) else 0f
                    var x = if (justify) left + contentW else left + contentW / 2 + total / 2
                    val baseline = top + lineH / 2 - (fm.ascent + fm.descent) / 2
                    l.tokens.forEachIndexed { j, t ->
                        x -= ws[j]
                        glyphs += Placed(t, x, ws[j], baseline, top, top + lineH)
                        x -= gap
                    }
                }
            }
        }
        return Laid(glyphs, frames, size, h, lineH)
    }

    // ---------------- LARGE (re-flow) mode
    val size = largePx
    paint.textSize = size
    val fm = paint.fontMetrics
    val padX = w * 0.04f
    val contentW = w - 2 * padX
    val lineH = size * 1.9f
    val space = size * 0.22f
    var y = h * 0.02f

    // group glyphs into words; the ayah marker sticks to the word before it
    class Group(val toks: MutableList<Token>, var width: Float)
    val pending = ArrayList<Group>()

    fun flush(justifyLast: Boolean) {
        if (pending.isEmpty()) return
        var lineStart = 0
        while (lineStart < pending.size) {
            var end = lineStart
            var wSum = pending[lineStart].width
            while (end + 1 < pending.size && wSum + space + pending[end + 1].width <= contentW) {
                end++; wSum += space + pending[end].width
            }
            val last = end == pending.size - 1
            val n = end - lineStart
            val gap = if (n > 0 && (!last || justifyLast)) (contentW - (wSum - n * space)) / n else space
            var x = padX + contentW
            val baseline = y + lineH / 2 - (fm.ascent + fm.descent) / 2
            for (gi in lineStart..end) {
                val g = pending[gi]
                for (t in g.toks) {
                    val tw = paint.measureText(t.glyph)
                    x -= tw
                    glyphs += Placed(t, x, tw, baseline, y, y + lineH)
                }
                x -= gap
            }
            y += lineH
            lineStart = end + 1
        }
        pending.clear()
    }

    for (l in page.lines) {
        when (l) {
            is Line.Header -> {
                flush(false)
                val hh = size * 1.7f
                frames += Frame(0, l.surah, RectF(padX, y + size * 0.2f, padX + contentW, y + hh))
                y += hh + size * 0.2f
            }
            is Line.Basmala -> {
                flush(false)
                val bh = size * 1.6f
                frames += Frame(1, 0, RectF(padX, y, padX + contentW, y + bh))
                y += bh
            }
            is Line.Text -> for (t in l.tokens) {
                val tw = paint.measureText(t.glyph)
                val prev = pending.lastOrNull()
                val sameWord = prev != null && prev.toks.last().let { it.s == t.s && it.a == t.a && it.w == t.w }
                if (t.w == 0 && prev != null || sameWord) { prev!!.toks += t; prev.width += tw }
                else pending += Group(mutableListOf(t), tw)
            }
        }
    }
    flush(false)
    y += lineH * 0.8f + bottomReserve
    return Laid(glyphs, frames, size, max(y, h), lineH)
}

fun Laid.hit(p: Offset): Placed? {
    var best: Placed? = null
    var bestD = Float.MAX_VALUE
    for (g in glyphs) {
        if (p.y < g.top || p.y > g.bottom) continue
        val d = when {
            p.x < g.left -> g.left - p.x
            p.x > g.left + g.width -> p.x - g.left - g.width
            else -> 0f
        }
        if (d < bestD) { bestD = d; best = g }
    }
    return if (bestD <= size * 0.35f) best else null
}

@Composable
fun PageCanvas(
    page: Page,
    mode: ViewMode,
    largeFontSp: Float,
    colors: MushafColors,
    fontStep: Int = 2,
    colorGharib: Boolean,
    showPageNumber: Boolean,
    bookmarked: Boolean,
    decor: PageDecor,
    onTap: (Placed?) -> Unit,
    onLongPress: (Placed?) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val largePx = with(density) { largeFontSp * fontScale * this.density }
        val reserve = if (showPageNumber) with(density) { 22f * this.density } else 0f
        val font = remember(page.number) { Quran.pageFont(page.number) }
        val laid = remember(page.number, w, h, mode, largePx, reserve, fontStep) {
            layoutPage(page, font, w, h, mode, largePx, reserve, fontStep)
        }
        val ribbonW = with(density) { 14f * this.density }
        val wordIndex = remember(page.number) { page.wordKeys().withIndex().associate { it.value to it.index } }
        val tap by rememberUpdatedState(onTap)
        val longPress by rememberUpdatedState(onLongPress)
        val gestures = Modifier.pointerInput(laid) {
            detectTapGestures(
                onTap = { tap(laid.hit(it)) },
                onLongPress = { longPress(laid.hit(it)) },
            )
        }
        val draw: DrawScope.() -> Unit = {
            drawPage(laid, font, page.number, colors, colorGharib, showPageNumber, bookmarked, decor, wordIndex, ribbonW)
        }
        if (mode == ViewMode.PAGE) {
            Canvas(Modifier.fillMaxSize().then(gestures), onDraw = draw)
        } else {
            val scroll = rememberScrollState()
            Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
                val hDp = with(density) { laid.height.toDp() }
                Canvas(Modifier.fillMaxWidth().height(hDp).then(gestures), onDraw = draw)
            }
        }
    }
}

private fun DrawScope.drawPage(
    laid: Laid, font: Typeface, pageNo: Int, c: MushafColors, colorGharib: Boolean,
    showPageNumber: Boolean, bookmarked: Boolean, decor: PageDecor, wordIndex: Map<String, Int>, ribbonW: Float,
) {
    val canvas = drawContext.canvas.nativeCanvas
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font; textSize = laid.size }
    val sel = decor.selection

    // 0) note highlights and the ayah being recited
    fun ayahBg(s: Int, a: Int, color: Int) {
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        laid.glyphs.filter { it.tok.s == s && it.tok.a == a }.groupBy { it.top }.forEach { (_, gs) ->
            val l = gs.minOf { it.left }; val r = gs.maxOf { it.left + it.width }
            canvas.drawRoundRect(RectF(l - 4f, gs.first().top + laid.lineH * 0.06f, r + 4f, gs.first().bottom - laid.lineH * 0.04f), 14f, 14f, bg)
        }
    }
    for ((k, col) in decor.highlights) {
        val (s, a) = k.split(":").map { it.toInt() }
        ayahBg(s, a, col.toArgb())
    }
    decor.playing?.let { p ->
        ayahBg(p[0], p[1], c.playing.toArgb())
        if (p[2] > 0) {
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c.playingWord.toArgb() }
            laid.glyphs.filter { it.tok.s == p[0] && it.tok.a == p[1] && it.tok.w == p[2] }.groupBy { it.top }.forEach { (_, gs) ->
                val l = gs.minOf { it.left }; val r = gs.maxOf { it.left + it.width }
                canvas.drawRoundRect(RectF(l - 3f, gs.first().top + laid.lineH * 0.12f, r + 3f, gs.first().bottom - laid.lineH * 0.08f), 12f, 12f, bg)
            }
        }
    }

    // 1) selection backgrounds, one rounded rect per line segment
    if (sel != null) {
        val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        // a tapped "difficult word" highlights only that word; a long press highlights the whole ayah
        val rows: Map<Float, List<Placed>> = if (sel.wordOnly) emptyMap() else laid.glyphs.filter { it.tok.s == sel.s && it.tok.a == sel.a }.groupBy { it.top }
        bg.color = c.ayahHighlight.toArgb()
        for ((_, gs) in rows) {
            val l = gs.minOf { it.left }; val r = gs.maxOf { it.left + it.width }
            val t = gs.first().top + laid.lineH * 0.06f; val b = gs.first().bottom - laid.lineH * 0.04f
            canvas.drawRoundRect(RectF(l - 4f, t, r + 4f, b), 14f, 14f, bg)
        }
        if (sel.gharib >= 0) {
            bg.color = c.wordHighlight.toArgb()
            laid.glyphs.filter { it.tok.g == sel.gharib && it.tok.s == sel.s && it.tok.a == sel.a }.groupBy { it.top }.forEach { (_, gs) ->
                val l = gs.minOf { it.left }; val r = gs.maxOf { it.left + it.width }
                canvas.drawRoundRect(RectF(l - 3f, gs.first().top + laid.lineH * 0.1f, r + 3f, gs.first().bottom - laid.lineH * 0.06f), 12f, 12f, bg)
            }
        }
    }

    // 2) headers and basmala
    for (f in laid.frames) {
        if (f.kind == 0) drawHeader(canvas, f, c) else drawBasmala(canvas, f, c)
    }

    // 3) the words (in memorisation mode, hidden words become soft placeholders)
    val ink = c.ink.toArgb(); val marker = c.marker.toArgb(); val gh = c.gharib.toArgb()
    val hz = decor.hifz
    val ph = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c.muted.copy(alpha = 0.22f).toArgb() }
    for (g in laid.glyphs) {
        val t = g.tok
        if (hz != null && t.w != 0) {
            val key = "${t.s}:${t.a}:${t.w}"
            val idx = wordIndex[key] ?: 0
            val visible = idx < hz.revealed || (hz.showFirstWords && t.w == 1)
            if (!visible) {
                val cy = g.baseline - laid.size * 0.18f
                canvas.drawRoundRect(RectF(g.left + 2f, cy - laid.size * 0.16f, g.left + g.width - 2f, cy + laid.size * 0.16f), 10f, 10f, ph)
                continue
            }
            paint.color = if (hz.mistakes.containsKey(key)) c.mistake.toArgb() else ink
            drawGlyph(canvas, g, paint)
            continue
        }
        paint.color = when {
            t.w == 0 -> marker
            colorGharib && t.colored -> gh
            else -> ink
        }
        drawGlyph(canvas, g, paint)
    }

    // 4) page number + stop-mark ribbon
    if (showPageNumber) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = c.muted.toArgb(); textSize = 13f * density; textAlign = Paint.Align.CENTER
        }
        canvas.drawText(pageNo.ar(), size.width / 2, laid.height - 7f * density, p)
    }
    if (bookmarked) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c.ribbon.toArgb() }
        val x = size.width * 0.06f; val hgt = ribbonW * 2.6f
        val path = android.graphics.Path().apply {
            moveTo(x, 0f); lineTo(x + ribbonW, 0f); lineTo(x + ribbonW, hgt)
            lineTo(x + ribbonW / 2, hgt - ribbonW * 0.5f); lineTo(x, hgt); close()
        }
        canvas.drawPath(path, p)
    }
}

private fun drawGlyph(canvas: android.graphics.Canvas, g: Placed, paint: Paint) {
    if (g.sx == 1f) { canvas.drawText(g.tok.glyph, g.drawX, g.baseline, paint); return }
    canvas.save()
    canvas.scale(g.sx, 1f, g.drawX, g.baseline)
    canvas.drawText(g.tok.glyph, g.drawX, g.baseline, paint)
    canvas.restore()
}

private fun drawHeader(canvas: android.graphics.Canvas, f: Frame, c: MushafColors) {
    val r = f.rect
    val h = r.height()
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = c.frame.toArgb(); strokeWidth = max(2f, h * 0.05f) }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c.frameFill.toArgb() }
    val inset = RectF(r.left + h * 0.08f, r.top + h * 0.08f, r.right - h * 0.08f, r.bottom - h * 0.08f)
    canvas.drawRoundRect(r, h * 0.5f, h * 0.5f, fill)
    canvas.drawRoundRect(r, h * 0.5f, h * 0.5f, stroke)
    stroke.strokeWidth = max(1f, h * 0.025f)
    canvas.drawRoundRect(inset, h * 0.42f, h * 0.42f, stroke)
    // small ornaments at both ends
    val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c.frame.toArgb() }
    for (cx in floatArrayOf(r.left + h * 0.55f, r.right - h * 0.55f)) {
        val rr = h * 0.13f
        val path = android.graphics.Path().apply {
            moveTo(cx, r.centerY() - rr * 1.6f); lineTo(cx + rr, r.centerY()); lineTo(cx, r.centerY() + rr * 1.6f); lineTo(cx - rr, r.centerY()); close()
        }
        canvas.drawPath(path, dot)
    }
    val name = Quran.surahs.getOrNull(f.surah - 1)?.name ?: ""
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Quran.hafsFont; color = c.ink.toArgb(); textAlign = Paint.Align.CENTER; textSize = h * 0.52f
    }
    val fm = text.fontMetrics
    canvas.drawText("سورة $name", r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2, text)
}

private fun drawBasmala(canvas: android.graphics.Canvas, f: Frame, c: MushafColors) {
    val r = f.rect
    // KFGQPC basmala font: drawn left-to-right as #"! so that «بسم» lands on the right.
    val s = "#\"!"
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Quran.basmalaFont; color = c.ink.toArgb(); textSize = 100f }
    val wRef = p.measureText(s)
    val size = min(100f * r.width() * 0.70f / wRef, r.height() * 1.05f)
    p.textSize = size
    p.textAlign = Paint.Align.CENTER
    val fm = p.fontMetrics
    canvas.drawText(s, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2, p)
}
