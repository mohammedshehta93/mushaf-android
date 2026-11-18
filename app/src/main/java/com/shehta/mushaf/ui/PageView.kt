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

class Laid(val glyphs: List<Placed>, val frames: List<Frame>, val size: Float, val height: Float, val lineH: Float, val paged: Boolean = false)

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


/**
 * Text size steps for the page view (QCF V2). Step 0 fits the widest line of the whole Mushaf exactly like the print
 * (Quran.maxLine, measured at build time); the larger steps narrow the few lines that would then be too long.
 */
val PAGE_FONT_STEPS = floatArrayOf(1f, 0.95f, 0.91f)
val PAGE_FONT_NAMES = listOf("كالمطبوع", "كبير", "أكبر")

/**
 * Lays out one Mushaf page.
 *  PAGE  mode: the 15 lines of the printed Madinah Mushaf, scaled to fill the screen exactly.
 *  LARGE mode: the same word glyphs re-flowed at a bigger size, scrolling vertically inside the page.
 */
fun layoutPage(page: Page, font: Typeface, w: Float, h: Float, mode: ViewMode, largePx: Float, bottomReserve: Float, fontStep: Int = 0): Laid {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font; textSize = REF }
    val glyphs = ArrayList<Placed>(200)
    val frames = ArrayList<Frame>(4)
    val special = page.number <= 2

    fun refWidth(t: Token) = paint.measureText(t.glyph)

    if (mode == ViewMode.PAGE) {
        val padX = w * 0.014f
        val padTop = max(bottomReserve, h * 0.012f)          // juz / surah line on top, like the print
        val innerW = w - 2 * padX
        val innerH = h - padTop - max(bottomReserve, h * 0.012f)
        val rows = page.lines.size
        val lineH = if (special) innerH / (rows + 5) else innerH / max(rows, 15)
        val contentW = if (special) innerW * 0.76f else innerW
        val left = padX + (innerW - contentW) / 2

        // One font size for every page (like the printed Mushaf), never enlarged per page: narrower pages are justified.
        val ref = Quran.maxLine * PAGE_FONT_STEPS[fontStep.coerceIn(0, PAGE_FONT_STEPS.size - 1)]
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
                    val justify = !special && total > contentW * 0.9f && ws.size > 1
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
        return Laid(glyphs, frames, size, h, lineH, paged = true)
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

    /**
     * Balanced line breaking (minimum raggedness): words are spread so every line is about equally full and no line
     * is left with two or three stranded words. All lines are justified, except the last line of a surah.
     */
    fun flush(justifyLast: Boolean) {
        if (pending.isEmpty()) return
        val n = pending.size
        val best = DoubleArray(n + 1) { Double.MAX_VALUE }; best[n] = 0.0
        val next = IntArray(n + 1) { n }
        for (i in n - 1 downTo 0) {
            var len = 0f
            for (j in i until n) {
                len += pending[j].width + if (j > i) space else 0f
                if (len > contentW && j > i) break
                val slack = (contentW - len).coerceAtLeast(0f).toDouble()
                val cost = if (j == n - 1 && !justifyLast) 0.0 else slack * slack
                if (best[j + 1] != Double.MAX_VALUE && cost + best[j + 1] < best[i]) { best[i] = cost + best[j + 1]; next[i] = j + 1 }
            }
        }
        var lineStart = 0
        while (lineStart < n) {
            val end = next[lineStart] - 1
            var wSum = 0f
            for (gi in lineStart..end) wSum += pending[gi].width
            val last = end == n - 1
            val k = end - lineStart
            val gap = if (k > 0 && (!last || justifyLast)) (contentW - wSum) / k else space
            var x = if (k == 0 && justifyLast) padX + contentW / 2 + wSum / 2 else padX + contentW
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
    // The page usually continues on the next one, so its last line is justified too — unless a surah ends here.
    val lastTok = pending.lastOrNull()?.toks?.lastOrNull { it.a > 0 }
    val surahEnds = lastTok != null && lastTok.a >= (Quran.surahs.getOrNull(lastTok.s - 1)?.ayahs ?: 0)
    flush(!surahEnds)
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
    fontStep: Int = 0,
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
        val reserve = if (showPageNumber) with(density) { 24f * this.density } else 0f
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
            color = c.muted.toArgb(); textSize = 13f * density; textAlign = Paint.Align.CENTER; isFakeBoldText = true
        }
        if (laid.paged) {
            drawPageInfo(canvas, pageNo, size.width, laid.height, c, density)
        } else canvas.drawText(pageNo.ar(), size.width / 2, laid.height - 7f * density, p)
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

/**
 * Word glyphs are drawn as outlines (paths), not through the GPU text cache: with 604 page fonts some phones mixed up
 * cached glyphs and drew broken pieces of other words. The outline of each word is computed once and kept.
 */
private object GlyphPaths {
    val cache = android.util.LruCache<String, android.graphics.Path>(3000)
    // a unique number per font object (identity hash codes can repeat, which would mix the pages' glyphs)
    private val ids = java.util.WeakHashMap<Typeface, Int>()
    private var next = 0
    @Synchronized private fun idOf(t: Typeface?): Int = if (t == null) -1 else ids.getOrPut(t) { next++ }
    fun of(glyph: String, paint: Paint): android.graphics.Path {
        val key = "${idOf(paint.typeface)}|${paint.textSize}|$glyph"
        cache.get(key)?.let { return it }
        val path = android.graphics.Path()
        paint.getTextPath(glyph, 0, glyph.length, 0f, 0f, path)
        cache.put(key, path)
        return path
    }
}

private fun drawGlyph(canvas: android.graphics.Canvas, g: Placed, paint: Paint) {
    val path = GlyphPaths.of(g.tok.glyph, paint)
    canvas.save()
    canvas.translate(g.drawX, g.baseline)
    if (g.sx != 1f) canvas.scale(g.sx, 1f)
    canvas.drawPath(path, paint)
    canvas.restore()
}

internal fun drawHeader(canvas: android.graphics.Canvas, f: Frame, c: MushafColors) {
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

internal fun drawBasmala(canvas: android.graphics.Canvas, f: Frame, c: MushafColors) {
    val r = f.rect
    // KFGQPC QCF V2 basmala font: three glyphs in reading order, drawn right-to-left
    val s = "\uFB51\uFB52\uFB53"
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Quran.basmalaFont; color = c.ink.toArgb(); textSize = 100f }
    val wRef = p.measureText(s)
    val size = min(100f * r.width() * 0.70f / wRef, r.height() * 1.05f)
    p.textSize = size
    p.textAlign = Paint.Align.CENTER
    val fm = p.fontMetrics
    canvas.drawText(s, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2, p)
}

val JUZ_NAMES = listOf("الأول", "الثاني", "الثالث", "الرابع", "الخامس", "السادس", "السابع", "الثامن", "التاسع", "العاشر",
    "الحادي عشر", "الثاني عشر", "الثالث عشر", "الرابع عشر", "الخامس عشر", "السادس عشر", "السابع عشر", "الثامن عشر", "التاسع عشر",
    "العشرون", "الحادي والعشرون", "الثاني والعشرون", "الثالث والعشرون", "الرابع والعشرون", "الخامس والعشرون", "السادس والعشرون",
    "السابع والعشرون", "الثامن والعشرون", "التاسع والعشرون", "الثلاثون")

/** «٣/٤ الحزب ٢٧» — the quarter of the hizb in which the page starts. */
fun hizbLabel(page: Int): String {
    val r = Quran.rub.lastOrNull { it.page <= page } ?: Quran.rub.first()
    val h = (r.n - 1) / 4 + 1; val q = (r.n - 1) % 4
    return (if (q > 0) listOf("", "١/٤", "١/٢", "٣/٤")[q] + " " else "") + "الحزب " + h.ar()
}

/** Juz and surah on top, hizb quarter and page number at the bottom — like the printed page. */
internal fun drawPageInfo(canvas: android.graphics.Canvas, pageNo: Int, width: Float, height: Float, c: MushafColors, density: Float) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = c.muted.toArgb(); textSize = 13f * density; isFakeBoldText = true
    }
    val top = 17f * density
    p.textAlign = Paint.Align.RIGHT
    canvas.drawText("الجزء " + JUZ_NAMES[Quran.juzOfPage(pageNo) - 1], width - 14f * density, top, p)
    p.textAlign = Paint.Align.LEFT
    canvas.drawText(Quran.surahs[Quran.firstAyah(pageNo)[0] - 1].name, 14f * density, top, p)
    val y = height - 8f * density
    val num = pageNo.ar()
    p.textAlign = Paint.Align.CENTER; p.color = c.ink.toArgb()
    val nw = p.measureText(num) + 18f * density
    val box = RectF(width / 2 - nw / 2, y - 14f * density, width / 2 + nw / 2, y + 5f * density)
    val fr = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f * density; color = c.frame.toArgb() }
    canvas.drawRoundRect(box, box.height() / 2, box.height() / 2, fr)
    canvas.drawText(num, width / 2, y, p)
    p.color = c.muted.toArgb(); p.textAlign = Paint.Align.RIGHT
    canvas.drawText(hizbLabel(pageNo), box.left - 8f * density, y, p)
}
