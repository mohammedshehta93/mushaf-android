package com.shehta.mushaf.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.shehta.mushaf.KeyBridge
import com.shehta.mushaf.Launch
import com.shehta.mushaf.audio.AudioEngine
import com.shehta.mushaf.audio.PlayRequest
import com.shehta.mushaf.data.Gharib
import com.shehta.mushaf.data.Line
import com.shehta.mushaf.data.Page
import com.shehta.mushaf.data.PaperTheme
import com.shehta.mushaf.data.Prefs
import com.shehta.mushaf.data.MuslimData
import com.shehta.mushaf.data.MuslimPrefs
import com.shehta.mushaf.data.PrayerCalc
import com.shehta.mushaf.reminder.PrayerAlarms
import com.shehta.mushaf.data.Quran
import com.shehta.mushaf.data.TajweedRules
import com.shehta.mushaf.data.ViewMode
import com.shehta.mushaf.data.ar
import com.shehta.mushaf.update.UpdateDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Screens shown above the reader. The reader itself always stays alive underneath, so its position is never lost. */
sealed class Overlay {
    object None : Overlay()
    object Index : Overlay()
    object Search : Overlay()
    object Hifz : Overlay()
    object Khatma : Overlay()
    object Fiqh : Overlay()
    object Home : Overlay()
    object Qibla : Overlay()
    object Tasbih : Overlay()
    object Hijri : Overlay()
    object Ramadan : Overlay()
    object Zakat : Overlay()
    object Names : Overlay()
    class Listen(val tab: Int = 0, val query: String? = null, val back: Overlay = Home) : Overlay()
    object Sheikhs : Overlay()
    object Poems : Overlay()
    object TajweedCourse : Overlay()
    object Feedback : Overlay()
    object Intentions : Overlay()
    object Virtues : Overlay()
    object HadithLibrary : Overlay()
    object HadithCheck : Overlay()
    class SurahInfo(val start: Int = 0, val back: Overlay = Home) : Overlay()
    object English : Overlay()
    class Recite(val review: Boolean = false) : Overlay()
    class Prayer(val tab: Int = 0) : Overlay()
    class Azkar(val start: String? = null) : Overlay()
    class Tafsir(val s: Int, val a: Int) : Overlay()
    class Root(val root: String) : Overlay()
}

val hafsFamily: FontFamily by lazy { FontFamily(Quran.hafsFont) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(prefs: Prefs, mp: MuslimPrefs) {
    val colors = mushafColors(prefs.theme)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val config = LocalConfiguration.current
    val spread = config.screenWidthDp > config.screenHeightDp && prefs.twoPagesLandscape && prefs.mode == ViewMode.PAGE

    // «تِبيان» opens on its home (the Mushaf is one of its parts) unless the reader chose to open on the Mushaf
    var overlay by remember { mutableStateOf<Overlay>(if (prefs.homeFirst && Launch.page == 0) Overlay.Home else Overlay.None) }
    // where the reader came from: «back» returns there, with its scroll position (see SaveableStateProvider below)
    val navStack = remember { mutableStateListOf<Overlay>() }
    fun open(o: Overlay) {
        if (o == Overlay.Home) navStack.clear()              // the home is the root
        else if (overlay != o) navStack.add(overlay)          // None = the Mushaf page: «back» returns to it
        overlay = o
    }
    fun back() { overlay = navStack.removeLastOrNull() ?: if (prefs.homeFirst && overlay != Overlay.Home) Overlay.Home else Overlay.None }
    val screenStates = rememberSaveableStateHolder()
    var tadabburFor by remember { mutableStateOf<Selection?>(null) }
    val listenPrefs = remember { com.shehta.mushaf.data.ListenPrefs(context) }
    val readingPrefs = remember { ReadingPrefs(context) }
    var reminder by remember { mutableStateOf<com.shehta.mushaf.data.RItem?>(null) }
    var pagesTurned by remember { mutableStateOf(0) }
    var readingStarted by remember { mutableStateOf(false) }
    // «what's new» once after every update (not on a fresh install)
    var whatsNew by remember {
        val sp = context.getSharedPreferences("mushaf", android.content.Context.MODE_PRIVATE)
        val seen = sp.getInt("seenVersion", if (sp.contains("lastPage") || sp.contains("theme")) 1 else 0)   // users from before this feature count as updated
        sp.edit().putInt("seenVersion", com.shehta.mushaf.BuildConfig.VERSION_CODE).apply()
        mutableStateOf(seen in 1 until com.shehta.mushaf.BuildConfig.VERSION_CODE)
    }
    fun nextReminder() {
        val list = com.shehta.mushaf.data.Reading.reminders
        if (!readingPrefs.tadabburOnStart || list.isEmpty()) return
        val i = readingPrefs.reminderIndex
        reminder = list[i % list.size]; readingPrefs.reminderIndex = i + 1
    }
    /** When the user starts reading (first page turn, or opening a surah/page): intentions, then one tadabbur reminder. */
    fun startReading() {
        if (readingStarted) return
        readingStarted = true
        if (readingPrefs.shouldShowIntentions()) open(Overlay.Intentions) else nextReminder()
    }
    var barsVisible by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<Selection?>(null) }
    var meaning by remember { mutableStateOf<Gharib?>(null) }
    var actionFor by remember { mutableStateOf<Selection?>(null) }
    var meaningsOf by remember { mutableStateOf<Selection?>(null) }
    var noteFor by remember { mutableStateOf<Selection?>(null) }
    var playerFor by remember { mutableStateOf<IntArray?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showView by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var hifzOn by remember { mutableStateOf(false) }
    val revealed = remember { mutableStateMapOf<Int, Int>() }

    // one pager per layout (single pages / two-page spreads), both starting from the saved page
    val pager = key(spread) {
        rememberPagerState(initialPage = if (spread) (prefs.lastPage - 1) / 2 else prefs.lastPage - 1) { if (spread) Quran.PAGES / 2 else Quran.PAGES }
    }
    fun pageOfIndex(i: Int) = if (spread) i * 2 + 1 else i + 1
    fun indexOfPage(p: Int) = if (spread) (p - 1) / 2 else p - 1
    val current = pageOfIndex(pager.currentPage).coerceIn(1, Quran.PAGES)
    LaunchedEffect(current) {
        pagesTurned++
        if (pagesTurned > 1) startReading()
    }

    fun goTo(page: Int, sel: Selection? = null) {
        scope.launch { pager.scrollToPage(indexOfPage(page.coerceIn(1, Quran.PAGES))) }
        selection = sel; meaning = null; overlay = Overlay.None; navStack.clear()
        startReading()
    }

    // a page or screen requested by a widget or a notification
    LaunchedEffect(Launch.page) { if (Launch.page > 0) { goTo(Launch.page); Launch.page = 0 } }
    LaunchedEffect(Launch.screen) {
        when (Launch.screen) {
            "prayer" -> open(Overlay.Prayer())
            "azkar_morning" -> open(Overlay.Azkar(MuslimData.MORNING))
            "azkar_evening" -> open(Overlay.Azkar(MuslimData.EVENING))
            "notes" -> { IndexTabs.request = 4; open(Overlay.Index) }
            else -> Regex("tadabbur:(\\d+):(\\d+)").matchEntire(Launch.screen)?.let { m ->
                val sel = Selection(m.groupValues[1].toInt(), m.groupValues[2].toInt())
                goTo(Quran.pageOf(sel.s, sel.a), sel); if (prefs.tadabburSheet) tadabburFor = sel
            }
        }
        Launch.screen = ""
    }

    // remember the reading position as soon as a page settles; count it as read after 20 seconds
    val settled = pageOfIndex(pager.settledPage)
    LaunchedEffect(settled) {
        prefs.updateLastPage(settled)
        val sel = selection
        if (sel != null && Quran.pageOf(sel.s, sel.a).let { it != settled && !(spread && it == settled + 1) }) { selection = null; meaning = null }
        delay(20_000)
        prefs.markPageRead(settled)
        if (spread && settled < Quran.PAGES) prefs.markPageRead(settled + 1)
    }

    // follow the recitation
    val audio = AudioEngine.state
    LaunchedEffect(audio.s, audio.a, audio.active) {
        if (audio.active && prefs.followAudio && audio.s > 0) {
            val p = Quran.pageOf(audio.s, audio.a)
            val idx = indexOfPage(p)
            if (idx != pager.currentPage && overlay == Overlay.None) pager.animateScrollToPage(idx)
        }
    }

    // full-screen: system bars only appear together with our own bars
    val view = LocalView.current
    LaunchedEffect(barsVisible, overlay, prefs.theme) {
        val win = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val ctl = WindowCompat.getInsetsController(win, view)
        ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        val show = barsVisible || overlay != Overlay.None
        if (show) ctl.show(WindowInsetsCompat.Type.systemBars()) else ctl.hide(WindowInsetsCompat.Type.systemBars())
        val lightIcons = prefs.theme == PaperTheme.NIGHT || (barsVisible && overlay == Overlay.None)
        ctl.isAppearanceLightStatusBars = !lightIcons
        ctl.isAppearanceLightNavigationBars = !lightIcons
    }
    DisposableEffect(prefs.keepScreenOn) {
        view.keepScreenOn = prefs.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // volume keys turn pages (only while the reader is in front)
    KeyBridge.active = prefs.volumeKeys && overlay == Overlay.None
    LaunchedEffect(spread) {
        KeyBridge.events.collect { dir ->
            pager.animateScrollToPage((pager.currentPage + dir).coerceIn(0, pager.pageCount - 1))
        }
    }

    // on the Mushaf with nothing open: «back» goes to the home of تِبيان
    BackHandler(enabled = prefs.homeFirst && overlay == Overlay.None && !(barsVisible || meaning != null || selection != null || hifzOn)) {
        open(Overlay.Home)
    }
    BackHandler(enabled = overlay == Overlay.None && (barsVisible || meaning != null || selection != null || hifzOn)) {
        when {
            meaning != null || selection != null -> { meaning = null; selection = null }
            barsVisible -> barsVisible = false
            else -> hifzOn = false
        }
    }

    val highlights = remember(prefs.notes) {
        prefs.notes.values.filter { it.color in NoteColors.indices }.associate { "${it.s}:${it.a}" to NoteColors[it.color] }
    }

    // ------------------------------------------------------------------ one page of the reader
    @Composable
    fun ReaderPage(n: Int, modifier: Modifier) {
        val page by produceState<Page?>(Quran.cachedPage(n), n) {
            value = withContext(Dispatchers.IO) { Quran.page(n).also { Quran.pageFont(n) } }
        }
        val p = page ?: return
        val playing = if (audio.active && !audio.basmala && audio.s > 0) intArrayOf(audio.s, audio.a, audio.word) else null
        val onPage = { s: Int, a: Int -> p.lines.any { l -> l is Line.Text && l.tokens.any { t -> t.s == s && t.a == a } } }
        val decor = PageDecor(
            selection = selection?.takeIf { onPage(it.s, it.a) },
            playing = playing?.takeIf { onPage(it[0], it[1]) },
            highlights = highlights,
            hifz = if (hifzOn) HifzView(revealed[n] ?: 0, prefs.hifzHideFirstWord, prefs.mistakes) else null,
        )
        fun longPressAt(s: Int, a: Int, w: Int, g: Int) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            meaning = null
            selection = Selection(s, a, g, w)
            actionFor = selection
        }
        if (prefs.mode == ViewMode.TAJWEED) {
            TajweedPage(
                page = p, colors = colors, fontSp = prefs.tajweedFontSize, decor = decor,
                onTap = { if (selection != null) selection = null else barsVisible = !barsVisible },
                onLongPress = { x -> if (x != null) longPressAt(x[0], x[1], 0, -1) },
            )
            return
        }
        PageCanvas(
            page = p,
            mode = prefs.mode,
            largeFontSp = prefs.largeFontSize,
            colors = colors,
            fontStep = prefs.pageFontStep,
            colorGharib = prefs.colorGharib && !hifzOn,
            showPageNumber = prefs.showPageNumber,
            bookmarked = prefs.isBookmarked(n),
            decor = decor,
            modifier = modifier,
            onTap = { placed ->
                val t = placed?.tok
                when {
                    hifzOn -> revealed[n] = ((revealed[n] ?: 0) + 1).coerceAtMost(p.wordKeys().size)
                    t != null && t.g >= 0 && t.colored && prefs.colorGharib && t.w != 0 -> {
                        meaning = Quran.gharib(t.g)
                        selection = Selection(t.s, t.a, t.g, t.w, wordOnly = true)
                        barsVisible = false
                    }
                    meaning != null || selection != null -> { meaning = null; selection = null }
                    else -> barsVisible = !barsVisible
                }
            },
            onLongPress = { placed ->
                val t = placed?.tok ?: return@PageCanvas
                longPressAt(t.s, t.a, t.w, if (t.w != 0) t.g else -1)
            },
        )
    }

    Box(Modifier.fillMaxSize().background(colors.paper)) {
        // ------------------------------------------------------------------ the Mushaf
        HorizontalPager(
            state = pager,
            beyondViewportPageCount = 1,
            key = { it },
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout),
        ) { index ->
            if (spread) {
                Row(Modifier.fillMaxSize()) {
                    ReaderPage(index * 2 + 1, Modifier.weight(1f).fillMaxHeight())
                    Box(Modifier.width(1.dp).fillMaxHeight().background(colors.frame.copy(alpha = 0.35f)))
                    ReaderPage(index * 2 + 2, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Box(Modifier.fillMaxSize()) { ReaderPage(index + 1, Modifier.fillMaxSize()) }
            }
        }

        // ------------------------------------------------------------------ top bar
        AnimatedVisibility(
            visible = barsVisible && overlay == Overlay.None,
            enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Surface(color = colors.bar, contentColor = colors.onBar) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val surah = Quran.surahOfPage(current)
                    val rub = Quran.rubOfPage(current)
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text("سورة ${surah.name}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("الجزء ${Quran.juzOfPage(current).ar()} · الحزب ${((rub.n - 1) / 4 + 1).ar()}${quarterLabel(rub.n)}", fontSize = 13.sp, color = colors.onBar.copy(alpha = 0.8f))
                    }
                    mp.next()?.let { (i, at) ->
                        Text("${PrayerCalc.NAMES[i]} ${PrayerCalc.format(mp.times()?.list()?.get(i) ?: Double.NaN).substringBefore(" ")}",
                            fontSize = 12.sp, modifier = Modifier.background(colors.onBar.copy(alpha = 0.15f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    if (pageHasSajda(current)) {
                        Text("۩ سجدة", fontSize = 13.sp, modifier = Modifier.background(colors.onBar.copy(alpha = 0.15f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    if (current in prefs.memorized) Icon(Icons.Filled.CheckCircle, "محفوظة", Modifier.size(18.dp))
                    Text("صفحة ${current.ar()}", fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
                    val marked = prefs.isBookmarked(current)
                    IconButton(onClick = {
                        if (marked) prefs.removeBookmark(current) else {
                            val fa = Quran.firstAyah(current); prefs.addBookmark(current, fa[0], fa[1])
                        }
                    }) { Icon(if (marked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, "علامة توقف") }
                }
            }
        }

        // ------------------------------------------------------------------ bottom area: player, hifz bar, menu bar
        Column(Modifier.align(Alignment.BottomCenter)) {
            AnimatedVisibility(visible = reminder != null && overlay == Overlay.None && !hifzOn) {
                reminder?.let { r -> TadabburCard(colors, r, onDismiss = { reminder = null }) }
            }
            AnimatedVisibility(visible = audio.active && overlay == Overlay.None && !hifzOn) {
                MiniPlayer(colors, onOpen = { if (audio.stream) open(Overlay.Listen()) else playerFor = intArrayOf(audio.s, audio.a) },
                    modifier = if (barsVisible) Modifier else Modifier.navigationBarsPadding())
            }
            AnimatedVisibility(visible = hifzOn && overlay == Overlay.None) {
                val total = remember(current) { Quran.page(current).wordKeys().size }
                HifzBar(
                    colors, current, prefs, revealed[current] ?: 0, total,
                    onWord = { revealed[current] = ((revealed[current] ?: 0) + 1).coerceAtMost(total) },
                    onAyah = {
                        val keys = Quran.page(current).wordKeys()
                        var r = revealed[current] ?: 0
                        if (r < keys.size) {
                            val ayah = keys[r].substringBeforeLast(":")
                            while (r < keys.size && keys[r].substringBeforeLast(":") == ayah) r++
                            revealed[current] = r
                        }
                    },
                    onHide = { revealed[current] = 0 },
                    onAll = { revealed[current] = total },
                    onMistake = {
                        val keys = Quran.page(current).wordKeys()
                        val r = (revealed[current] ?: 0)
                        val k = keys.getOrNull((r - 1).coerceAtLeast(0))
                        if (k != null) {
                            val (s, a, w) = k.split(":").map { it.toInt() }
                            prefs.addMistake(s, a, w)
                            Toast.makeText(context, "سُجّلت «${wordText(s, a, w)}» في أخطائك", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onClose = { hifzOn = false },
                )
            }
            AnimatedVisibility(
                visible = barsVisible && overlay == Overlay.None && !hifzOn,
                enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it },
            ) {
                Surface(color = colors.bar, contentColor = colors.onBar) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)) {
                        var dragging by remember { mutableStateOf(false) }
                        var sliderValue by remember { mutableFloatStateOf(current.toFloat()) }
                        if (!dragging) sliderValue = current.toFloat()
                        val preview = sliderValue.toInt().coerceIn(1, Quran.PAGES)
                        Text(
                            "صفحة ${preview.ar()} — سورة ${Quran.surahOfPage(preview).name}",
                            fontSize = 13.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                            color = colors.onBar.copy(alpha = if (dragging) 1f else 0.75f),
                        )
                        Box {
                            // stop marks as small ribbons above the slider track
                            Canvas(Modifier.fillMaxWidth().height(8.dp).padding(horizontal = 10.dp)) {
                                for (b in prefs.bookmarks) {
                                    val f = (b.page - 1) / 603f
                                    val x = size.width - f * size.width          // RTL: page 1 on the right
                                    drawRect(colors.ribbon, topLeft = androidx.compose.ui.geometry.Offset(x - 2f, 0f), size = androidx.compose.ui.geometry.Size(4f, size.height))
                                }
                            }
                            Slider(
                                value = sliderValue,
                                onValueChange = { dragging = true; sliderValue = it },
                                onValueChangeFinished = { dragging = false; goTo(sliderValue.toInt()) },
                                valueRange = 1f..Quran.PAGES.toFloat(),
                                colors = SliderDefaults.colors(thumbColor = colors.onBar, activeTrackColor = colors.onBar, inactiveTrackColor = colors.onBar.copy(alpha = 0.3f)),
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            BarButton(Icons.AutoMirrored.Filled.MenuBook, "الفهرس") { open(Overlay.Index) }
                            BarButton(Icons.Filled.Headphones, "استماع") {
                                val fa = Quran.firstAyah(current); playerFor = intArrayOf(fa[0], fa[1])
                            }
                            BarButton(Icons.Filled.Psychology, "حفظ") {
                                hifzOn = true; barsVisible = false; meaning = null; selection = null
                                if (prefs.mode == ViewMode.TAJWEED) prefs.updateMode(ViewMode.PAGE)
                            }
                            BarButton(Icons.Filled.ViewDay, "العرض") { showView = true }
                            BarButton(Icons.Filled.Home, "الرئيسية") { open(Overlay.Home); barsVisible = false }
                        }
                    }
                }
            }
        }

        // ------------------------------------------------------------------ word meaning card (tap on a coloured word)
        AnimatedVisibility(
            visible = meaning != null && overlay == Overlay.None,
            enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val g = meaning
            if (g != null) MeaningCard(g, colors,
                onTafsir = { open(Overlay.Tafsir(g.s, g.a)); meaning = null },
                onClose = { meaning = null; selection = null })
        }

        // ------------------------------------------------------------------ overlays
        // each screen keeps its own saved state (lists keep their scroll position) while the reader goes deeper and back
        val shown = overlay
        if (shown != Overlay.None) screenStates.SaveableStateProvider(navKey(shown)) { when (val o = shown) {
            Overlay.Index -> IndexScreen(prefs, current, onBack = { back() }, onGo = { p, s -> goTo(p, s) })
            Overlay.Search -> SearchScreen(onBack = { back() }, onGo = { s, a -> goTo(Quran.pageOf(s, a), Selection(s, a)) })
            Overlay.Hifz -> HifzScreen(prefs, onBack = { back() }, onGo = { p, s -> goTo(p, s) },
                onStartHifz = { overlay = Overlay.None; hifzOn = true })
            Overlay.Khatma -> KhatmaScreen(prefs, current, onBack = { back() }, onGo = { goTo(it) })
            Overlay.Fiqh -> FiqhScreen(prefs, onBack = { back() })
            is Overlay.Tafsir -> TafsirScreen(o.s, o.a, prefs, onBack = { back() }, onGo = { s, a -> goTo(Quran.pageOf(s, a), Selection(s, a)) })
            is Overlay.Root -> RootScreen(o.root, prefs, onBack = { back() }, onGo = { p, s -> goTo(p, s) })
            Overlay.Home -> HomeScreen(mp, colors, prefs.lastPage, onBack = if (prefs.homeFirst) null else ({ back() }), onPick = { id ->
                when {
                    id == "mushaf" -> goTo(prefs.lastPage)
                    id.startsWith("azkar:") -> open(Overlay.Azkar(id.removePrefix("azkar:")))
                    else -> when (id) {
                        "prayer" -> open(Overlay.Prayer())
                        "tracker" -> open(Overlay.Prayer(1))
                        "qibla" -> open(Overlay.Qibla)
                        "azkar" -> open(Overlay.Azkar())
                        "tasbih" -> open(Overlay.Tasbih)
                        "hijri" -> open(Overlay.Hijri)
                        "ramadan" -> open(Overlay.Ramadan)
                        "zakat" -> open(Overlay.Zakat)
                        "names" -> open(Overlay.Names)
                        "listen", "radio" -> open(Overlay.Listen(0))
                        "podcast" -> open(Overlay.Listen(1))
                        "nasheed" -> open(Overlay.Listen(2))
                        "sheikhs" -> open(Overlay.Sheikhs)
                        "poems" -> open(Overlay.Poems)
                        "tajweed" -> open(Overlay.TajweedCourse)
                        "feedback" -> open(Overlay.Feedback)
                        "virtues" -> open(Overlay.Virtues)
                        "intentions" -> open(Overlay.Intentions)
                        "hadiths" -> open(Overlay.HadithLibrary)
                        "hadithcheck" -> open(Overlay.HadithCheck)
                        "surahinfo" -> open(Overlay.SurahInfo())
                        "english" -> open(Overlay.English)
                        "recite" -> open(Overlay.Recite())
                        "review" -> open(Overlay.Recite(review = true))
                        "kahf" -> goTo(PrayerAlarms.KAHF_PAGE)
                        "search" -> open(Overlay.Search)
                        "marks" -> { IndexTabs.request = 3; open(Overlay.Index) }
                        "notes" -> { IndexTabs.request = 4; open(Overlay.Index) }
                        "khatma" -> open(Overlay.Khatma)
                        "hifz" -> open(Overlay.Hifz)
                        "fiqh" -> open(Overlay.Fiqh)
                        "tafsir" -> { val fa = Quran.firstAyah(current); open(Overlay.Tafsir(fa[0], fa[1])) }
                        "share" -> showShare = true
                        "settings" -> showSettings = true
                    }
                }
            })
            is Overlay.Prayer -> PrayerScreen(mp, colors, onBack = { back() }, startTab = o.tab)
            Overlay.Qibla -> QiblaScreen(mp, colors, onBack = { back() })
            is Overlay.Azkar -> AzkarScreen(mp, colors, o.start, onBack = { back() })
            Overlay.Tasbih -> TasbihScreen(mp, colors, onBack = { back() })
            Overlay.Hijri -> HijriScreen(mp, colors, onBack = { back() })
            Overlay.Ramadan -> RamadanScreen(mp, prefs, colors, onBack = { back() }, onKhatma = { open(Overlay.Khatma) })
            Overlay.Zakat -> ZakatScreen(mp, colors, onBack = { back() })
            Overlay.Names -> NamesScreen(colors, onBack = { back() }, onGo = { s, a -> goTo(Quran.pageOf(s, a), Selection(s, a)) })
            is Overlay.Listen -> androidx.compose.runtime.key(o.tab, o.query) { ListenScreen(colors, listenPrefs, onBack = { back() }, startTab = o.tab, podcastQuery = o.query) }
            Overlay.Sheikhs -> SheikhsScreen(colors, onBack = { back() }, onPodcast = { q -> open(Overlay.Listen(1, q, Overlay.Sheikhs)) })
            Overlay.Feedback -> FeedbackScreen(colors, current, onBack = { back() })
            Overlay.Intentions -> IntentionsScreen(colors, readingPrefs, onDone = { overlay = Overlay.None; nextReminder() })
            Overlay.Virtues -> VirtuesScreen(colors, onBack = { back() }, onGo = { s, a -> goTo(Quran.pageOf(s, a), Selection(s, a)) },
                onIntentions = { open(Overlay.Intentions) })
            Overlay.HadithLibrary -> HadithLibraryScreen(colors, onBack = { back() })
            Overlay.HadithCheck -> HadithCheckScreen(colors, onBack = { back() })
            is Overlay.SurahInfo -> SurahInfoScreen(colors, o.start, onBack = { back() }, onGo = { s, a -> goTo(Quran.pageOf(s, a), Selection(s, a)) })
            is Overlay.Recite -> ReciteScreen(colors, prefs, current, o.review, onBack = { back() })
            Overlay.English -> EnglishScreen(colors, listenPrefs, onBack = { back() },
                onGo = { s, a -> goTo(Quran.pageOf(s, a), Selection(s, a)) })
            Overlay.Poems -> PoemsScreen(colors, listenPrefs, onBack = { back() })
            Overlay.TajweedCourse -> TajweedCourseScreen(colors, onBack = { back() }, onGo = { s, a -> goTo(Quran.pageOf(s, a), Selection(s, a)) })
            Overlay.None -> {}
        } }
    }

    // ------------------------------------------------------------------ sheets & dialogs
    if (whatsNew) WhatsNewDialog(onDismiss = { whatsNew = false })
    actionFor?.let { sel ->
        AyahActions(
            sel = sel, prefs = prefs, colors = colors,
            onDismiss = { actionFor = null },
            onTafsir = { actionFor = null; open(Overlay.Tafsir(sel.s, sel.a)) },
            onTadabbur = { actionFor = null; tadabburFor = sel },
            onMeanings = { actionFor = null; meaningsOf = sel },
            onNote = { actionFor = null; noteFor = sel },
            onPlayFrom = {
                actionFor = null
                AudioEngine.play(PlayRequest(prefs.reciter, sel.s, sel.a, sel.s, Quran.surahs[sel.s - 1].ayahs, prefs.repeatAyah, prefs.repeatRange, prefs.speed))
            },
            onRepeat = { actionFor = null; playerFor = intArrayOf(sel.s, sel.a) },
            onWordAudio = { AudioEngine.playWord(sel.s, sel.a, sel.w) { Toast.makeText(context, "تعذّر تشغيل نطق الكلمة (يحتاج إنترنت)", Toast.LENGTH_SHORT).show() } },
            onRoot = { r -> actionFor = null; open(Overlay.Root(r)) },
            onBookmark = {
                actionFor = null
                prefs.addBookmark(Quran.pageOf(sel.s, sel.a), sel.s, sel.a)
                Toast.makeText(context, "تم حفظ علامة التوقف", Toast.LENGTH_SHORT).show()
            },
            onCopy = { actionFor = null; copyAyah(context, sel.s, sel.a) },
            onShare = { actionFor = null; shareAyah(context, sel.s, sel.a) },
        )
    }
    meaningsOf?.let { sel -> MeaningsSheet(sel, colors, onDismiss = { meaningsOf = null }) }
    tadabburFor?.let { sel ->
        TadabburSheet(sel.s, sel.a, prefs, colors, onDismiss = { tadabburFor = null },
            onTafsir = { tadabburFor = null; open(Overlay.Tafsir(sel.s, sel.a)) },
            onSurahInfo = { tadabburFor = null; open(Overlay.SurahInfo(sel.s, back = Overlay.None)) },
            onNote = { noteFor = sel },
            onGo = { s, a -> tadabburFor = null; goTo(Quran.pageOf(s, a), Selection(s, a)) })
    }
    noteFor?.let { sel -> NoteDialog(sel.s, sel.a, prefs, onDismiss = { noteFor = null }) }
    playerFor?.let { x -> PlayerSheet(prefs, x[0], x[1], onDismiss = { playerFor = null }) }
    if (showSettings) SettingsSheet(prefs, mp, onDismiss = { showSettings = false })
    if (showView) ViewSheet(prefs, colors, onDismiss = { showView = false })
    if (showShare) ShareAppSheet(onDismiss = { showShare = false })
    UpdateDialog(prefs)
}

/** A key per screen (and its arguments), so each one keeps its own saved state. */
private fun navKey(o: Overlay): String = when (o) {
    is Overlay.Listen -> "Listen:${o.tab}:${o.query}"
    is Overlay.Prayer -> "Prayer:${o.tab}"
    is Overlay.Azkar -> "Azkar:${o.start}"
    is Overlay.Tafsir -> "Tafsir:${o.s}:${o.a}"
    is Overlay.Root -> "Root:${o.root}"
    is Overlay.SurahInfo -> "SurahInfo:${o.start}"
    is Overlay.Recite -> "Recite:${o.review}"
    else -> o::class.java.simpleName
}

private fun pageHasSajda(page: Int): Boolean {
    val p = Quran.cachedPage(page) ?: return false
    return p.lines.any { l -> l is Line.Text && l.tokens.any { it.w == 0 && Quran.isSajda(it.s, it.a) } }
}

private fun quarterLabel(rub: Int): String = when ((rub - 1) % 4) {
    1 -> " · ربع"; 2 -> " · نصف"; 3 -> " · ثلاثة أرباع"; else -> ""
}

@Composable
private fun BarButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, label, Modifier.size(24.dp))
            Text(label, fontSize = 11.sp, maxLines = 1)
        }
    }
}


/** Display mode + tajweed colour legend. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewSheet(prefs: Prefs, c: MushafColors, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("طريقة العرض", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                FilterChip(selected = prefs.mode == ViewMode.PAGE, onClick = { prefs.updateMode(ViewMode.PAGE) }, label = { Text("صفحة المصحف") })
                FilterChip(selected = prefs.mode == ViewMode.LARGE, onClick = { prefs.updateMode(ViewMode.LARGE) }, label = { Text("خط كبير") })
                FilterChip(selected = prefs.mode == ViewMode.TAJWEED, onClick = { prefs.updateMode(ViewMode.TAJWEED) }, label = { Text("تجويد ملوّن") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(PaperTheme.CREAM to "كريمي", PaperTheme.WHITE to "أبيض", PaperTheme.NIGHT to "ليلي").forEach { (t, n) ->
                    FilterChip(selected = prefs.theme == t, onClick = { prefs.updateTheme(t) }, label = { Text(n) })
                }
            }
            when (prefs.mode) {
                ViewMode.LARGE -> {
                    Text("حجم الخط: ${prefs.largeFontSize.toInt()}", modifier = Modifier.padding(top = 12.dp))
                    Slider(value = prefs.largeFontSize, onValueChange = { prefs.updateLargeFont(it) }, valueRange = 22f..56f)
                }
                ViewMode.TAJWEED -> {
                    Text("أقصى حجم للخط (الصفحة تُعرض كاملة دائمًا): ${prefs.tajweedFontSize.toInt()}", modifier = Modifier.padding(top = 12.dp))
                    Slider(value = prefs.tajweedFontSize, onValueChange = { prefs.updateTajweedFont(it) }, valueRange = 18f..48f)
                }
                else -> {}
            }
            Text("ألوان التجويد", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
            TajweedRules.legend.forEachIndexed { i, t ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                    Box(Modifier.size(16.dp).background(c.tajweed[i], CircleShape))
                    Text("  $t", fontSize = 14.sp)
                }
            }
            Text("تلوين التجويد آلي وقد يخطئ في مواضع قليلة؛ المرجع هو التلقي من قارئ متقن.", fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun MeaningCard(g: Gharib, c: MushafColors, onTafsir: () -> Unit, onClose: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp, tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(start = 18.dp, end = 10.dp, top = 8.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(g.phrase, fontFamily = hafsFamily, fontSize = 26.sp, color = c.gharib, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "إغلاق") }
            }
            Text(g.meaning, fontSize = 18.sp, lineHeight = 28.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text("${Quran.surahs[g.s - 1].name} · ${g.a.ar()}", fontSize = 12.sp, color = c.muted, modifier = Modifier.align(Alignment.CenterVertically).weight(1f))
                TextButton(onClick = onTafsir) {
                    Icon(Icons.Filled.AutoStories, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("التفسير الكامل")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AyahActions(
    sel: Selection, prefs: Prefs, colors: MushafColors, onDismiss: () -> Unit, onTafsir: () -> Unit, onTadabbur: () -> Unit, onMeanings: () -> Unit,
    onNote: () -> Unit, onPlayFrom: () -> Unit, onRepeat: () -> Unit, onWordAudio: () -> Unit, onRoot: (String) -> Unit,
    onBookmark: () -> Unit, onCopy: () -> Unit, onShare: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val meanings = remember(sel) { Quran.gharibOfAyah(sel.s, sel.a).size }
    val morph = remember(sel) { if (sel.w > 0) Quran.morph(sel.s, sel.a, sel.w) else null }
    val rootCount by produceState(0, morph?.root) {
        val r = morph?.root
        value = if (r.isNullOrEmpty()) 0 else withContext(Dispatchers.Default) { Quran.rootOccurrences(r).size }
    }
    val ic = ListItemDefaults.colors(containerColor = Color.Transparent)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        LazyColumn(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            item {
                Text("سورة ${Quran.surahs[sel.s - 1].name} · الآية ${sel.a.ar()}", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    Quran.ayahText(sel.s, sel.a), fontFamily = hafsFamily, fontSize = 22.sp, lineHeight = 40.sp,
                    maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 8.dp),
                )
                if (sel.w > 0) {
                    Surface(shape = RoundedCornerShape(14.dp), color = colors.frameFill, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(wordText(sel.s, sel.a, sel.w), fontFamily = hafsFamily, fontSize = 26.sp, color = colors.gharib, modifier = Modifier.weight(1f))
                                IconButton(onClick = onWordAudio) { Icon(Icons.Filled.RecordVoiceOver, "استمع للكلمة") }
                            }
                            if (morph != null) {
                                Text(morph.description, fontSize = 14.sp)
                                if (morph.root.isNotEmpty()) Text("الجذر: ${morph.root.toList().joinToString(" ")}   ·   الأصل: ${morph.lemma}", fontSize = 14.sp, color = colors.muted)
                                if (morph.root.isNotEmpty() && rootCount > 0) TextButton(onClick = { onRoot(morph.root) }) {
                                    Icon(Icons.Filled.Spellcheck, null, Modifier.size(18.dp)); Text("  كل مواضع الجذر (${rootCount.ar()})")
                                }
                            }
                        }
                    }
                }
                HorizontalDivider()
            }
            item {
                if (prefs.tadabburSheet) ListItem(headlineContent = { Text("وقفة تدبّر") }, supportingContent = { Text("مقصد السورة، أسئلة، ترديد، دفتر التدبّر") },
                    leadingContent = { Icon(Icons.Filled.Lightbulb, null) }, modifier = Modifier.clickableRow(onTadabbur), colors = ic)
                ListItem(headlineContent = { Text("التفسير") }, leadingContent = { Icon(Icons.Filled.AutoStories, null) }, modifier = Modifier.clickableRow(onTafsir), colors = ic)
                if (meanings > 0) ListItem(headlineContent = { Text("معاني كلمات الآية (${meanings.ar()})") }, leadingContent = { Icon(Icons.Filled.Translate, null) },
                    modifier = Modifier.clickableRow(onMeanings), colors = ic)
                ListItem(headlineContent = { Text("استمع من هذه الآية") }, leadingContent = { Icon(Icons.Filled.Headphones, null) }, modifier = Modifier.clickableRow(onPlayFrom), colors = ic)
                ListItem(headlineContent = { Text("تكرار للحفظ…") }, leadingContent = { Icon(Icons.Filled.Repeat, null) }, modifier = Modifier.clickableRow(onRepeat), colors = ic)
                ListItem(headlineContent = { Text(if (prefs.note(sel.s, sel.a) != null) "تعديل ما كتبته في دفتر التدبّر" else "دفتر التدبّر والتظليل") },
                    leadingContent = { Icon(Icons.Filled.EditNote, null) }, modifier = Modifier.clickableRow(onNote), colors = ic)
                ListItem(headlineContent = { Text("علامة توقف هنا") }, leadingContent = { Icon(Icons.Filled.Bookmark, null) }, modifier = Modifier.clickableRow(onBookmark), colors = ic)
                ListItem(headlineContent = { Text("نسخ الآية") }, leadingContent = { Icon(Icons.Filled.ContentCopy, null) }, modifier = Modifier.clickableRow(onCopy), colors = ic)
                ListItem(headlineContent = { Text("مشاركة الآية") }, leadingContent = { Icon(Icons.Filled.Share, null) }, modifier = Modifier.clickableRow(onShare), colors = ic)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeaningsSheet(sel: Selection, c: MushafColors, onDismiss: () -> Unit) {
    val list = remember(sel) { Quran.gharibOfAyah(sel.s, sel.a) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            item { Text("معاني كلمات الآية ${sel.a.ar()} — سورة ${Quran.surahs[sel.s - 1].name}", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(bottom = 8.dp)) }
            items(list.size) { i ->
                val g = list[i]
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(g.phrase, fontFamily = hafsFamily, fontSize = 24.sp, color = c.gharib)
                    Text(g.meaning, fontSize = 17.sp, lineHeight = 27.sp)
                }
                if (i < list.size - 1) HorizontalDivider()
            }
        }
    }
}

fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

fun ayahShareText(s: Int, a: Int) = "﴿${Quran.ayahText(s, a)}﴾ [${Quran.surahs[s - 1].name}: ${a.ar()}]"

fun copyAyah(context: Context, s: Int, a: Int) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("آية", ayahShareText(s, a)))
    Toast.makeText(context, "تم نسخ الآية", Toast.LENGTH_SHORT).show()
}

fun shareAyah(context: Context, s: Int, a: Int) {
    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, ayahShareText(s, a))
    context.startActivity(Intent.createChooser(i, "مشاركة الآية"))
}
