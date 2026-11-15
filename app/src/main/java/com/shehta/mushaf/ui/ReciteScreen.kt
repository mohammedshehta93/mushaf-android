package com.shehta.mushaf.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.shehta.mushaf.audio.AudioEngine
import com.shehta.mushaf.data.ExpectedWord
import com.shehta.mushaf.data.Line
import com.shehta.mushaf.data.Prefs
import com.shehta.mushaf.data.Quran
import com.shehta.mushaf.data.ReciteMatcher
import com.shehta.mushaf.data.ReciteText
import com.shehta.mushaf.data.ReviewPlan
import com.shehta.mushaf.data.WordState
import com.shehta.mushaf.data.ar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ===================================================================================================== speech

/** Keeps Android's speech recogniser listening (it stops after each pause) and reports words as they come. */
private class RecitationListener(
    private val context: Context,
    private val onWords: (tokens: List<String>, final: Boolean) -> Unit,
    private val onStatus: (listening: Boolean, error: String?) -> Unit,
) {
    private var sr: SpeechRecognizer? = null
    private var active = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-SA")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
    }

    private fun tokens(b: Bundle?): List<String> =
        b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.split(Regex("\\s+"))?.filter { it.isNotBlank() } ?: emptyList()

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { onStatus(true, null) }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
        override fun onPartialResults(partialResults: Bundle?) { onWords(tokens(partialResults), false) }
        override fun onResults(results: Bundle?) { onWords(tokens(results), true); restart(150) }
        override fun onError(error: Int) {
            onWords(emptyList(), true)
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> { stop(); onStatus(false, "اسمح للتطبيق باستخدام الميكروفون.") }
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                    { onStatus(active, "تعذّر الاتصال بخدمة التعرّف على الكلام. نزّل اللغة العربية للاستخدام بدون إنترنت من إعدادات «التعرّف على الكلام» في جوجل."); restart(1500) }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> restart(500)
                else -> restart(200)          // no match / speech timeout: keep listening
            }
        }
    }

    fun start() {
        if (active) return
        active = true
        sr = SpeechRecognizer.createSpeechRecognizer(context).also { it.setRecognitionListener(listener) }
        sr?.startListening(intent)
    }

    private fun restart(delayMs: Long) {
        if (!active) return
        handler.postDelayed({ if (active) try { sr?.startListening(intent) } catch (e: Exception) { } }, delayMs)
    }

    fun stop() {
        active = false
        handler.removeCallbacksAndMessages(null)
        try { sr?.cancel(); sr?.destroy() } catch (e: Exception) { }
        sr = null
        onStatus(false, null)
    }
}

// ===================================================================================================== range

private class ReciteRange(val label: String, val words: List<ExpectedWord>, val pages: IntArray)

private fun buildRange(label: String, pages: IntRange, surah: Int = 0): ReciteRange {
    val out = ArrayList<ExpectedWord>(); val pg = ArrayList<Int>()
    val seen = HashSet<String>()
    val cache = HashMap<String, Map<Int, String>>()
    for (p in pages) {
        for (l in Quran.page(p).lines) {
            if (l !is Line.Text) continue
            for (t in l.tokens) {
                if (t.w == 0 || (surah > 0 && t.s != surah)) continue
                val k = "${t.s}:${t.a}:${t.w}"
                if (!seen.add(k)) continue
                val text = cache.getOrPut("${t.s}:${t.a}") { Quran.ayahWords(t.s, t.a) }[t.w] ?: continue
                val auto = ReciteText.isMuqattaat(t.s, t.a, t.w) || ReciteText.norm(text).isEmpty()
                out += ExpectedWord(t.s, t.a, t.w, text, auto = auto); pg += p
            }
        }
    }
    return ReciteRange(label, out, pg.toIntArray())
}

// ===================================================================================================== screen

private const val MODE_PROMPT = 0
private const val MODE_FREE = 1
private const val MODE_TEST = 2

@Composable
fun ReciteScreen(c: MushafColors, prefs: Prefs, currentPage: Int, review: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val plan = remember { ReviewPlan(context) }
    var session by remember { mutableStateOf<Triple<ReciteRange, Int, Float>?>(null) }   // range, mode, threshold
    val s = session
    if (s == null) ReciteSetup(c, plan, currentPage, review, onBack, onStart = { r, m, th -> session = Triple(r, m, th) })
    else ReciteSession(c, prefs, plan, s.first, s.second, s.third, onBack = { session = null })
}

@Composable
private fun ReciteSetup(c: MushafColors, plan: ReviewPlan, currentPage: Int, review: Boolean, onBack: () -> Unit,
                        onStart: (ReciteRange, Int, Float) -> Unit) {
    val context = LocalContext.current
    val due = remember { plan.due() }
    var kind by remember { mutableIntStateOf(if (review && due.isNotEmpty()) 3 else 0) }     // 0 page, 1 surah, 2 pages, 3 review
    var surah by remember { mutableIntStateOf(Quran.firstAyah(currentPage)[0]) }
    var from by remember { mutableIntStateOf(currentPage) }
    var to by remember { mutableIntStateOf(currentPage) }
    var mode by remember { mutableIntStateOf(MODE_PROMPT) }
    var strict by remember { mutableIntStateOf(1) }
    var error by remember { mutableStateOf<String?>(null) }
    val available = remember { SpeechRecognizer.isRecognitionAvailable(context) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) error = "التسميع يحتاج إذن الميكروفون."
    }

    fun start() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.RECORD_AUDIO); return
        }
        val th = floatArrayOf(0.55f, 0.7f, 0.85f)[strict]
        val r = when (kind) {
            1 -> { val sp = Quran.surahs[surah - 1].page; val ep = if (surah < 114) Quran.surahs[surah].page else Quran.PAGES
                   buildRange("سورة ${Quran.surahs[surah - 1].name}", sp..ep, surah) }
            2 -> buildRange("من صفحة ${minOf(from, to).ar()} إلى ${maxOf(from, to).ar()}", minOf(from, to)..maxOf(from, to))
            3 -> { val p = due.first(); buildRange("مراجعة اليوم · صفحة ${p.ar()}", p..p) }
            else -> buildRange("صفحة ${currentPage.ar()}", currentPage..currentPage)
        }
        if (r.words.isEmpty()) { error = "لا توجد كلمات في هذا النطاق."; return }
        onStart(r, mode, th)
    }

    ScreenScaffold("التسميع", c, onBack, subtitle = "اقرأ من حفظك، والتطبيق يتابعك كلمة كلمة") {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!available) Card2(c) {
                Text("خدمة التعرّف على الكلام غير موجودة على هذا الهاتف.", fontWeight = FontWeight.Bold, color = c.mistake)
                Text("ثبّت أو حدّث تطبيق «Google» من المتجر ثم أعد المحاولة.", fontSize = 14.sp)
            }
            if (due.isNotEmpty()) Card2(c, Modifier.clickable { kind = 3 }) {
                Text("مراجعة اليوم: ${due.size.ar()} صفحة", fontWeight = FontWeight.Bold, color = c.gharib)
                Text("صفحات حان موعد مراجعتها حسب نتائج تسميعك السابقة.", fontSize = 13.sp, color = c.muted)
            }
            Text("ماذا تُسمّع؟", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(kind == 0, { kind = 0 }, { Text("الصفحة ${currentPage.ar()}") })
                FilterChip(kind == 1, { kind = 1 }, { Text("سورة") })
                FilterChip(kind == 2, { kind = 2 }, { Text("صفحات") })
                if (due.isNotEmpty()) FilterChip(kind == 3, { kind = 3 }, { Text("المراجعة") })
            }
            if (kind == 1) Stepper("السورة", "${surah.ar()}. ${Quran.surahs[surah - 1].name}", { surah = (surah - 1).coerceAtLeast(1) }, { surah = (surah + 1).coerceAtMost(114) })
            if (kind == 2) {
                Stepper("من صفحة", from.ar(), { from = (from - 1).coerceAtLeast(1) }, { from = (from + 1).coerceAtMost(Quran.PAGES) })
                Stepper("إلى صفحة", to.ar(), { to = (to - 1).coerceAtLeast(1) }, { to = (to + 1).coerceAtMost(Quran.PAGES) })
            }
            Text("الطريقة", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(mode == MODE_PROMPT, { mode = MODE_PROMPT }, { Text("مع تلقين") })
                FilterChip(mode == MODE_FREE, { mode = MODE_FREE }, { Text("تسميع حر") })
                FilterChip(mode == MODE_TEST, { mode = MODE_TEST }, { Text("اختبار") })
            }
            Text(when (mode) {
                MODE_FREE -> "لا تلقين ولا تنبيه أثناء القراءة؛ تظهر الأخطاء في النهاية."
                MODE_TEST -> "يختار التطبيق آية من النطاق ويقول لك «أكمل من هنا» لثلاث آيات."
                else -> "تظهر كل كلمة صحيحة في مكانها، ويُنبّهك عند الخطأ، ويلقّنك إن توقفت."
            }, fontSize = 13.sp, color = c.muted)
            Text("الدقة", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("متساهل", "عادي", "دقيق").forEachIndexed { i, n -> FilterChip(strict == i, { strict = i }, { Text(n) }) }
            }
            error?.let { Text(it, color = c.mistake) }
            Button(onClick = { start() }, enabled = available, modifier = Modifier.fillMaxWidth()) { Text("ابدأ التسميع", fontSize = 17.sp) }
            Card2(c) {
                Text("قبل أن تبدأ", fontWeight = FontWeight.Bold)
                Text("• التسميع يكشف الكلمة الخطأ والكلمة الناقصة والتوقف، ولا يكشف أخطاء التشكيل ولا أحكام التجويد؛ فهذه تحتاج شيخًا.\n" +
                    "• يعمل بخدمة التعرّف على الكلام في هاتفك. لا يُحفظ أي تسجيل ولا يُرسل إلينا. للعمل بدون إنترنت نزّل «العربية» من إعدادات التعرّف على الكلام في جوجل؛ وإلا تعالج جوجل الصوت عندها.\n" +
                    "• اقرأ بصوت واضح وبسرعة معتدلة؛ إن أخطأ التطبيق في كلمة صحيحة فاختر «متساهل».",
                    fontSize = 13.sp, lineHeight = 22.sp, color = c.muted)
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(80.dp))
        OutlinedButton(onClick = onMinus) { Text("−") }
        Text(value, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
        OutlinedButton(onClick = onPlus) { Text("+") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReciteSession(c: MushafColors, prefs: Prefs, plan: ReviewPlan, range: ReciteRange, mode: Int, threshold: Float, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // test mode: start at a random ayah (not the first) and recite three ayahs from it
    val (words, startIdx) = remember(range, mode) {
        if (mode != MODE_TEST) range.words to 0
        else {
            val starts = range.words.indices.filter { it > 0 && range.words[it].w == 1 }
            val st = starts.randomOrNull() ?: 0
            val ayahs = range.words.drop(st).map { it.s * 1000 + it.a }.distinct().take(3).toSet()
            val end = st + range.words.drop(st).takeWhile { (it.s * 1000 + it.a) in ayahs }.size
            range.words.subList(0, end) to st
        }
    }
    val matcher = remember(words) { ReciteMatcher(words, threshold, startIdx) }
    var tick by remember { mutableIntStateOf(0) }            // bumps on every change of the matcher
    var listening by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var heard by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }
    var finished by remember { mutableStateOf(false) }
    val said = remember { HashMap<Int, String>() }
    var consumed by remember { mutableIntStateOf(0) }
    val vib = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }

    fun buzz() {
        if (mode == MODE_FREE) return
        try {
            if (Build.VERSION.SDK_INT >= 26) vib?.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) { }
    }

    val listener = remember {
        RecitationListener(context,
            onWords = { tokens, final ->
                if (!finished) {
                    // words of a partial result can still change, except all but the last one
                    val stable = if (final) tokens.size else (tokens.size - 1).coerceAtLeast(0)
                    if (stable > consumed) {
                        val ev = matcher.feed(tokens.subList(consumed, stable))
                        for (e in ev) {
                            if (e.state == WordState.WRONG) { said[e.index] = e.said; buzz() }
                            if (e.state == WordState.SKIPPED) buzz()
                        }
                        if (ev.isNotEmpty()) { tick++; hint = "" }
                        consumed = stable
                    }
                    if (final) consumed = 0
                    heard = tokens.joinToString(" ")
                    if (matcher.done) finished = true
                }
            },
            onStatus = { l, err -> listening = l; if (err != null) status = err })
    }
    DisposableEffect(Unit) { onDispose { listener.stop() } }
    LaunchedEffect(finished) {
        if (finished) {
            listener.stop()
            // save: mistakes to the review list, and each page's score to the review plan
            for (i in matcher.mistakes) words[i].let { prefs.addMistake(it.s, it.a, it.w) }
            val byPage = HashMap<Int, IntArray>()           // page -> [tested, wrong]
            for (i in words.indices) {
                if (words[i].auto || matcher.state[i] == WordState.GIVEN || matcher.state[i] == WordState.HIDDEN) continue
                val p = range.pages[i]
                val x = byPage.getOrPut(p) { IntArray(2) }
                x[0]++; if (i in matcher.mistakes) x[1]++
            }
            for ((p, x) in byPage) if (x[0] >= 5) plan.record(p, (x[0] - x[1]) * 100 / x[0])
        }
    }
    // the reciter is stuck: first letters, then the word (and its sound)
    LaunchedEffect(matcher.pos, listening, finished) {
        if (!listening || finished || mode == MODE_FREE) return@LaunchedEffect
        delay(4500)
        if (matcher.done) return@LaunchedEffect
        val w = words[matcher.pos]
        val plain = ReciteText.norm(w.text)
        hint = "تلميح: «${plain.take(2)}…»"
        delay(4000)
        if (matcher.done) return@LaunchedEffect
        matcher.giveCurrent(); tick++
        hint = "الكلمة: ${w.text}"
        AudioEngine.playWord(w.s, w.a, w.w) { }
        if (matcher.done) finished = true
    }

    val listState = rememberLazyListState()
    val ayahs = remember(words) { words.indices.groupBy { words[it].s * 1000 + words[it].a }.values.toList() }
    LaunchedEffect(tick) {
        val idx = ayahs.indexOfFirst { matcher.pos in it }
        if (idx >= 0) listState.animateScrollToItem(idx.coerceAtLeast(0))
    }

    ScreenScaffold(range.label, c, { listener.stop(); onBack() },
        subtitle = if (finished) "انتهى التسميع" else "${(matcher.pos - startIdx).coerceAtLeast(0).ar()} من ${(words.size - startIdx).ar()} كلمة") {
        if (finished) {
            ReciteReport(c, words, matcher, said, onAgain = onBack)
            return@ScreenScaffold
        }
        LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (mode == MODE_TEST) item { Text("أكمل من هنا:", fontWeight = FontWeight.Bold, color = c.gharib) }
            items(ayahs.size) { ai ->
                val idxs = ayahs[ai]
                val redraw = tick   // recompose when the matcher changes
                if (redraw < 0) return@items
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in idxs) {
                        if (mode == MODE_TEST && i < startIdx - 12) continue
                        WordChip(c, words[i].text, displayState(matcher.state[i], mode), i == matcher.pos) {
                            AudioEngine.playWord(words[i].s, words[i].a, words[i].w) { }
                        }
                    }
                    val last = words[idxs.last()]
                    Text("﴿${last.a.ar()}﴾", fontSize = 16.sp, color = c.marker, modifier = Modifier.align(Alignment.CenterVertically))
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
            Column(Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (hint.isNotEmpty()) Text(hint, fontWeight = FontWeight.Bold, color = c.gharib, fontSize = 17.sp)
                status?.let { Text(it, fontSize = 12.sp, color = c.mistake) }
                if (heard.isNotEmpty() && mode != MODE_FREE) Text("سمعتُ: $heard", fontSize = 12.sp, color = c.muted, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TextButton(onClick = {
                        matcher.giveCurrent(); tick++; if (matcher.done) finished = true
                    }, enabled = mode != MODE_FREE) { Text("لقّنّي") }
                    Box(Modifier.size(68.dp).background(if (listening) c.mistake else MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable { if (listening) listener.stop() else { status = null; listener.start() } },
                        contentAlignment = Alignment.Center) {
                        Icon(if (listening) Icons.Filled.MicOff else Icons.Filled.Mic, if (listening) "إيقاف" else "ابدأ", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    TextButton(onClick = { finished = true }) { Text("إنهاء") }
                }
                Text(if (listening) "أستمع… اقرأ من حفظك" else "اضغط الميكروفون وابدأ القراءة", fontSize = 12.sp, color = c.muted)
            }
        }
    }
}

/** In free mode mistakes stay hidden until the end. */
private fun displayState(st: Int, mode: Int) = if (mode == MODE_FREE && (st == WordState.WRONG || st == WordState.SKIPPED)) WordState.HIDDEN else st

@Composable
private fun WordChip(c: MushafColors, text: String, state: Int, current: Boolean, onTap: () -> Unit) {
    val color = when (state) {
        WordState.OK -> c.ink
        WordState.WRONG -> c.mistake
        WordState.SKIPPED -> Color(0xFFE67E22)
        WordState.PROMPTED -> Color(0xFF1E6FD9)
        WordState.GIVEN -> c.muted
        else -> Color.Transparent
    }
    val bg = when {
        state == WordState.HIDDEN && current -> c.gharib.copy(alpha = 0.25f)
        state == WordState.HIDDEN -> c.muted.copy(alpha = 0.18f)
        state == WordState.WRONG -> c.mistake.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    Box(Modifier.background(bg, RoundedCornerShape(8.dp)).clickable(enabled = state != WordState.HIDDEN, onClick = onTap).padding(horizontal = 3.dp)) {
        Text(text, fontFamily = hafsFamily, fontSize = 24.sp, color = color)
    }
}

@Composable
private fun ReciteReport(c: MushafColors, words: List<ExpectedWord>, m: ReciteMatcher, said: Map<Int, String>, onAgain: () -> Unit) {
    val mistakes = m.mistakes
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card2(c) {
                Text("الإتقان: ${m.accuracy.ar()}٪", fontWeight = FontWeight.Bold, fontSize = 26.sp, color = if (m.accuracy >= 90) c.gharib else c.mistake)
                Text("${m.tested.ar()} كلمة · ${mistakes.size.ar()} ملاحظة", color = c.muted)
                Text(when {
                    m.accuracy >= 95 -> "ما شاء الله! حفظ متقن، ستعود إليه المراجعة بعد أيام."
                    m.accuracy >= 80 -> "جيد، راجع المواضع التالية وستعود إليك المراجعة قريبًا."
                    else -> "يحتاج إلى مراجعة؛ اقرأ الصفحة نظرًا ثم سمّعها مرة أخرى."
                }, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
                Text("حُفظت الأخطاء في «مراجعة الأخطاء»، وأُضيفت الصفحات إلى جدول المراجعة.", fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (mistakes.isNotEmpty()) item { Text("المواضع", fontWeight = FontWeight.Bold, color = c.gharib) }
        items(mistakes) { i ->
            val w = words[i]
            Card2(c) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(w.text, fontFamily = hafsFamily, fontSize = 24.sp)
                        Text("سورة ${Quran.surahs[w.s - 1].name} · الآية ${w.a.ar()} — " + when (m.state[i]) {
                            WordState.WRONG -> "قلتَ: «${said[i] ?: "?"}»"
                            WordState.SKIPPED -> "سقطت"
                            else -> "احتجتَ تلقينًا"
                        }, fontSize = 12.sp, color = c.muted)
                    }
                    IconButton(onClick = { AudioEngine.playWord(w.s, w.a, w.w) { } }) { Icon(Icons.AutoMirrored.Filled.VolumeUp, "استمع", tint = c.gharib) }
                }
            }
        }
        item {
            Spacer(Modifier.padding(4.dp))
            Button(onClick = onAgain, modifier = Modifier.fillMaxWidth()) { Text("تسميع آخر") }
        }
    }
}
