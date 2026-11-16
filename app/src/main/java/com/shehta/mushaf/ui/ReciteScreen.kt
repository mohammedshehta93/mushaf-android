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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Switch
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

/**
 * Listens to the reciter.
 *  - Android 13+ with the on-device recogniser: ONE continuous «segmented» session — no restarts, no beeps.
 *  - Otherwise: the classic recogniser, restarted after each pause; the media volume is muted while listening so the
 *    start chime is not heard, and after repeated failures it stops and says why instead of looping.
 */
private class RecitationListener(
    private val context: Context,
    private val onWords: (tokens: List<String>, final: Boolean) -> Unit,
    private val onStatus: (listening: Boolean, error: String?) -> Unit,
) {
    private var sr: SpeechRecognizer? = null
    private var active = false
    private var continuous = false
    private var failures = 0
    private var paused = false
    private var muted = false
    private val streams = intArrayOf(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.STREAM_NOTIFICATION,
        android.media.AudioManager.STREAM_SYSTEM)
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    /** set when the on-device Arabic model is missing (the screen offers to download it) */
    var needsModel = false; private set

    private fun intent(segmented: Boolean) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-SA")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L)
        if (segmented && Build.VERSION.SDK_INT >= 33) {
            // keep one session open for up to 30 minutes, delivering results segment by segment
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 30 * 60_000L)
            putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS)
        }
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
        override fun onPartialResults(partialResults: Bundle?) { if (!paused) onWords(tokens(partialResults), false) }
        override fun onSegmentResults(segmentResults: Bundle) { if (!paused) { failures = 0; onWords(tokens(segmentResults), true) } }
        override fun onEndOfSegmentedSession() { if (active && !paused) restart(300) }
        override fun onResults(results: Bundle?) {
            if (paused) return
            val t = tokens(results)
            if (t.isNotEmpty()) failures = 0
            onWords(t, true)
            restart(250)
        }
        override fun onError(error: Int) {
            if (paused) return
            onWords(emptyList(), true)
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> { stop(); onStatus(false, "اسمح للتطبيق باستخدام الميكروفون.") }
                12, 13 -> {                       // language not supported / not downloaded (on-device)
                    if (continuous) { needsModel = true; switchToClassic() }
                    else { stop(); onStatus(false, "العربية غير متاحة في خدمة التعرّف على الكلام على هذا الهاتف.") }
                }
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> fail(
                    "لا يوجد اتصال بخدمة التعرّف على الكلام. اتصل بالإنترنت، أو نزّل العربية للاستخدام بدون إنترنت.")
                SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> { recreate(); fail(null) }
                else -> fail(null)                // no match / speech timeout
            }
        }
    }

    private fun fail(message: String?) {
        failures++
        if (failures >= 4) {
            stop()
            onStatus(false, message ?: "لم أسمع قراءة واضحة. اقرأ بصوت أعلى قريبًا من الهاتف، ثم اضغط الميكروفون من جديد.")
        } else restart(if (message != null) 1500 else 400)
    }

    private fun create(onDevice: Boolean) {
        try { sr?.destroy() } catch (e: Exception) { }
        sr = (if (onDevice && Build.VERSION.SDK_INT >= 31) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
              else SpeechRecognizer.createSpeechRecognizer(context)).also { it.setRecognitionListener(listener) }
    }

    private fun recreate() { create(continuous) }

    fun start() {
        if (active) return
        active = true; failures = 0
        continuous = Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context) && !needsModel
        create(continuous)
        if (!continuous) mute(true)
        try { sr?.startListening(intent(continuous)) } catch (e: Exception) { fail(null) }
    }

    private fun switchToClassic() {
        continuous = false
        create(false)
        mute(true)
        restart(300)
    }

    private fun restart(delayMs: Long) {
        if (!active || paused) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ if (active) try { sr?.startListening(intent(continuous)) } catch (e: Exception) { recreate() } }, delayMs)
    }

    /**
     * Mutes the recogniser's start/stop chime while listening with the classic recogniser. Phones play it on
     * different streams (media, notification or system sounds), so all three are muted and restored afterwards.
     */
    fun mute(on: Boolean) {
        if (on == muted) return
        muted = on
        for (st in streams) try {
            audio.adjustStreamVolume(st, if (on) android.media.AudioManager.ADJUST_MUTE else android.media.AudioManager.ADJUST_UNMUTE, 0)
        } catch (e: Exception) { }   // e.g. «Do not disturb» forbids changing the notification stream
    }

    /** Plays a word's sound: briefly unmutes. */
    fun withSound(block: () -> Unit) {
        val wasMuted = muted
        if (wasMuted) mute(false)
        block()
        if (wasMuted && active) handler.postDelayed({ if (active && !continuous) mute(true) }, 2500)
    }

    /** Stops listening for a moment (the app is speaking a correction) without ending the session. */
    fun pause() {
        if (!active) return
        paused = true
        handler.removeCallbacksAndMessages(null)
        try { sr?.cancel() } catch (e: Exception) { }
        mute(false)
    }

    fun resume() {
        if (!active || !paused) return
        paused = false
        if (!continuous) mute(true)
        restart(60)
    }

    /** Android 13+: asks the system to download the Arabic on-device model. */
    fun downloadModel() {
        if (Build.VERSION.SDK_INT >= 33) try {
            val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            r.triggerModelDownload(intent(false))
            handler.postDelayed({ try { r.destroy() } catch (e: Exception) { } }, 3000)
            needsModel = false
        } catch (e: Exception) { }
    }

    fun stop() {
        active = false; paused = false
        handler.removeCallbacksAndMessages(null)
        try { sr?.cancel(); sr?.destroy() } catch (e: Exception) { }
        sr = null
        mute(false)
        onStatus(false, null)
    }
}

/** Speaks a correction: a short word by the phone's Arabic voice (if it has one), then the Mushaf word by the reciter. */
private class ReciteVoice(context: Context) {
    @Volatile private var ttsOk = false
    private var tts: android.speech.tts.TextToSpeech? = null
    private var player: android.media.MediaPlayer? = null

    init {
        try {
            tts = android.speech.tts.TextToSpeech(context.applicationContext) { st ->
                if (st == android.speech.tts.TextToSpeech.SUCCESS) try {
                    ttsOk = (tts?.setLanguage(java.util.Locale("ar")) ?: -2) >= android.speech.tts.TextToSpeech.LANG_AVAILABLE
                } catch (e: Exception) { }
            }
        } catch (e: Exception) { }
    }

    suspend fun say(text: String) {
        val t = tts ?: return
        if (!ttsOk) return
        kotlinx.coroutines.withTimeoutOrNull(3000) {
            kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
                val id = "fix" + System.nanoTime()
                fun done() { if (cont.isActive) cont.resumeWith(Result.success(Unit)) }
                t.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { if (utteranceId == id) done() }
                    @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { done() }
                })
                if (t.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, id) != android.speech.tts.TextToSpeech.SUCCESS) done()
            }
        }
    }

    /** The word in the reciter's voice (word-by-word recordings, needs the internet); returns when it has finished. */
    suspend fun playWord(s: Int, a: Int, w: Int) {
        kotlinx.coroutines.withTimeoutOrNull(5000) {
            kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
                val url = String.format(java.util.Locale.US, "https://audio.qurancdn.com/wbw/%03d_%03d_%03d.mp3", s, a, w)
                val mp = android.media.MediaPlayer()
                player = mp
                fun done() { try { mp.release() } catch (e: Exception) { }; if (player === mp) player = null; if (cont.isActive) cont.resumeWith(Result.success(Unit)) }
                mp.setOnPreparedListener { it.start() }
                mp.setOnCompletionListener { done() }
                mp.setOnErrorListener { _, _, _ -> done(); true }
                cont.invokeOnCancellation { try { mp.release() } catch (e: Exception) { } }
                try { mp.setDataSource(url); mp.prepareAsync() } catch (e: Exception) { done() }
            }
        }
    }

    fun release() {
        try { player?.release() } catch (e: Exception) { }
        try { tts?.shutdown() } catch (e: Exception) { }
    }
}

// ===================================================================================================== range

private class ReciteRange(val label: String, val words: List<ExpectedWord>, val pages: IntArray)

private fun buildRange(label: String, pages: IntRange, surah: Int = 0, keep: (Int, Int) -> Boolean = { _, _ -> true }): ReciteRange {
    val out = ArrayList<ExpectedWord>(); val pg = ArrayList<Int>()
    val seen = HashSet<String>()
    val cache = HashMap<String, Map<Int, String>>()
    for (p in pages) {
        for (l in Quran.page(p).lines) {
            if (l !is Line.Text) continue
            for (t in l.tokens) {
                if (t.w == 0 || (surah > 0 && t.s != surah) || !keep(t.s, t.a)) continue
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
    val sp = remember { context.getSharedPreferences("recite", Context.MODE_PRIVATE) }
    if (s == null) ReciteSetup(c, plan, currentPage, review, onBack, onStart = { r, m, th -> session = Triple(r, m, th) })
    else ReciteSession(c, prefs, plan, s.first, s.second, s.third,
        voice = s.second != MODE_FREE && sp.getBoolean("voiceFix", false), onBack = { session = null })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReciteSetup(c: MushafColors, plan: ReviewPlan, currentPage: Int, review: Boolean, onBack: () -> Unit,
                        onStart: (ReciteRange, Int, Float) -> Unit) {
    val context = LocalContext.current
    val due = remember { plan.due() }
    var kind by remember { mutableIntStateOf(if (review && due.isNotEmpty()) 4 else 0) }     // 0 page, 1 surah, 2 juz, 3 pages, 4 review
    var surah by remember { mutableIntStateOf(Quran.firstAyah(currentPage)[0]) }
    var fromA by remember { mutableStateOf("1") }
    var toA by remember { mutableStateOf(Quran.surahs[surah - 1].ayahs.toString()) }
    var juz by remember { mutableIntStateOf(Quran.juzOfPage(currentPage)) }
    var fromP by remember { mutableStateOf(currentPage.toString()) }
    var toP by remember { mutableStateOf(currentPage.toString()) }
    var picking by remember { mutableStateOf(false) }
    var mode by remember { mutableIntStateOf(MODE_PROMPT) }
    var strict by remember { mutableIntStateOf(1) }
    val sp = remember { context.getSharedPreferences("recite", Context.MODE_PRIVATE) }
    var voice by remember { mutableStateOf(sp.getBoolean("voiceFix", false)) }
    var error by remember { mutableStateOf<String?>(null) }
    val available = remember { SpeechRecognizer.isRecognitionAvailable(context) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) error = "التسميع يحتاج إذن الميكروفون."
    }
    fun num(t: String) = t.filter { it.isDigit() || it in '٠'..'٩' }.map { if (it in '٠'..'٩') '0' + (it - '٠') else it }.joinToString("").toIntOrNull()

    fun start() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.RECORD_AUDIO); return
        }
        error = null
        val th = floatArrayOf(0.55f, 0.7f, 0.85f)[strict]
        val r = when (kind) {
            1 -> {
                val n = Quran.surahs[surah - 1].ayahs
                val a1 = (num(fromA) ?: 1).coerceIn(1, n); val a2 = (num(toA) ?: n).coerceIn(a1, n)
                val label = "سورة ${Quran.surahs[surah - 1].name}" + if (a1 == 1 && a2 == n) "" else " (${a1.ar()}–${a2.ar()})"
                buildRange(label, Quran.pageOf(surah, a1)..Quran.pageOf(surah, a2), surah) { _, a -> a in a1..a2 }
            }
            2 -> {
                val start = Quran.juz[juz - 1]; val next = Quran.juz.getOrNull(juz)
                val endPage = next?.page ?: Quran.PAGES
                fun key(s: Int, a: Int) = s * 1000 + a
                buildRange("الجزء ${juz.ar()}", start.page..endPage) { s, a ->
                    key(s, a) >= key(start.s, start.a) && (next == null || key(s, a) < key(next.s, next.a))
                }
            }
            3 -> {
                val p1 = (num(fromP) ?: currentPage).coerceIn(1, Quran.PAGES); val p2 = (num(toP) ?: p1).coerceIn(1, Quran.PAGES)
                buildRange("من صفحة ${minOf(p1, p2).ar()} إلى ${maxOf(p1, p2).ar()}", minOf(p1, p2)..maxOf(p1, p2))
            }
            4 -> { val p = due.first(); buildRange("مراجعة اليوم · صفحة ${p.ar()}", p..p) }
            else -> buildRange("صفحة ${currentPage.ar()}", currentPage..currentPage)
        }
        if (r.words.isEmpty()) { error = "لا توجد كلمات في هذا الاختيار."; return }
        onStart(r, mode, th)
    }

    ScreenScaffold("التسميع", c, onBack, subtitle = "اقرأ من حفظك، والتطبيق يتابعك كلمة كلمة") {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!available) Card2(c) {
                Text("خدمة التعرّف على الكلام غير موجودة على هذا الهاتف.", fontWeight = FontWeight.Bold, color = c.mistake)
                Text("ثبّت أو حدّث تطبيق «Google» من المتجر ثم أعد المحاولة.", fontSize = 14.sp)
            }
            if (due.isNotEmpty()) Card2(c, Modifier.clickable { kind = 4 }) {
                Text("مراجعة اليوم: ${due.size.ar()} صفحة", fontWeight = FontWeight.Bold, color = c.gharib)
                Text("صفحات حان موعد مراجعتها حسب نتائج تسميعك السابقة.", fontSize = 13.sp, color = c.muted)
            }
            Text("ماذا تُسمّع؟", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(kind == 0, { kind = 0 }, { Text("الصفحة الحالية (${currentPage.ar()})") })
                FilterChip(kind == 1, { kind = 1 }, { Text("سورة") })
                FilterChip(kind == 2, { kind = 2 }, { Text("جزء") })
                FilterChip(kind == 3, { kind = 3 }, { Text("صفحات") })
                if (due.isNotEmpty()) FilterChip(kind == 4, { kind = 4 }, { Text("المراجعة") })
            }
            when (kind) {
                1 -> {
                    OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("${surah.ar()}. سورة ${Quran.surahs[surah - 1].name}  ▾", fontSize = 17.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        NumField("من آية", fromA, Modifier.weight(1f)) { fromA = it }
                        NumField("إلى آية", toA, Modifier.weight(1f)) { toA = it }
                    }
                    Text("السورة فيها ${Quran.surahs[surah - 1].ayahs.ar()} آية — اترك الحقلين كما هما لتسميع السورة كلها.", fontSize = 12.sp, color = c.muted)
                }
                2 -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (j in 1..30) FilterChip(juz == j, { juz = j }, { Text(j.ar()) })
                }
                3 -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumField("من صفحة", fromP, Modifier.weight(1f)) { fromP = it }
                    NumField("إلى صفحة", toP, Modifier.weight(1f)) { toP = it }
                }
                else -> {}
            }
            Text("الطريقة", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(mode == MODE_PROMPT, { mode = MODE_PROMPT }, { Text("مع تلقين") })
                FilterChip(mode == MODE_FREE, { mode = MODE_FREE }, { Text("تسميع حر") })
                FilterChip(mode == MODE_TEST, { mode = MODE_TEST }, { Text("اختبار") })
            }
            Text(when (mode) {
                MODE_FREE -> "لا تلقين ولا تنبيه أثناء القراءة؛ تظهر الأخطاء في النهاية."
                MODE_TEST -> "يختار التطبيق آية من اختيارك ويقول لك «أكمل من هنا» لثلاث آيات."
                else -> "تظهر كل كلمة صحيحة في مكانها، ويُنبّهك عند الخطأ، ويلقّنك إن توقفت."
            }, fontSize = 13.sp, color = c.muted)
            if (mode != MODE_FREE) Row(Modifier.fillMaxWidth().clickable { voice = !voice; sp.edit().putBoolean("voiceFix", voice).apply() },
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("التصحيح بالصوت", fontWeight = FontWeight.SemiBold)
                    Text("عند الخطأ أو النسيان يتوقف التطبيق لحظة ويُسمعك الصواب بصوت القارئ، ثم يكمل الاستماع إليك (يحتاج إنترنت لصوت الكلمات).",
                        fontSize = 12.sp, color = c.muted)
                }
                Switch(voice, { voice = it; sp.edit().putBoolean("voiceFix", it).apply() })
            }
            Text("الدقة", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("متساهل", "عادي", "دقيق").forEachIndexed { i, n -> FilterChip(strict == i, { strict = i }, { Text(n) }) }
            }
            error?.let { Text(it, color = c.mistake) }
            Button(onClick = { start() }, enabled = available, modifier = Modifier.fillMaxWidth()) { Text("ابدأ التسميع", fontSize = 17.sp) }
            Card2(c) {
                Text("قبل أن تبدأ", fontWeight = FontWeight.Bold)
                Text("• التسميع يكشف الكلمة الخطأ والكلمة الناقصة والتوقف، ولا يكشف أخطاء التشكيل ولا أحكام التجويد؛ فهذه تحتاج شيخًا.\n" +
                    "• يعمل بخدمة التعرّف على الكلام في هاتفك، ولا يُحفظ أي تسجيل ولا يُرسل إلينا. أفضل تجربة (بدون إنترنت وبدون أصوات تنبيه) بعد تنزيل «العربية» للتعرّف على الكلام على الجهاز.\n" +
                    "• اقرأ بصوت واضح وبسرعة معتدلة؛ إن أخطأ التطبيق في كلمة صحيحة فاختر «متساهل».",
                    fontSize = 13.sp, lineHeight = 22.sp, color = c.muted)
            }
        }
    }
    if (picking) SurahPicker(c, onPick = { surah = it; fromA = "1"; toA = Quran.surahs[it - 1].ayahs.toString(); picking = false }, onDismiss = { picking = false })
}

@Composable
private fun NumField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    androidx.compose.material3.OutlinedTextField(value, { onChange(it.take(4)) }, modifier, singleLine = true, label = { Text(label) },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
}

/** Searchable list of the 114 surahs. */
@Composable
private fun SurahPicker(c: MushafColors, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val shown = remember(q) {
        val nq = com.shehta.mushaf.data.ArabicText.normalize(q)
        Quran.surahs.filter { nq.isEmpty() || com.shehta.mushaf.data.ArabicText.normalize(it.name).contains(nq) || it.n.toString() == q.trim() }
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                androidx.compose.material3.OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("اكتب اسم السورة أو رقمها") })
                LazyColumn(Modifier.padding(top = 6.dp).fillMaxWidth().heightIn(max = 420.dp)) {
                    items(shown) { su ->
                        Row(Modifier.fillMaxWidth().clickable { onPick(su.n) }.padding(vertical = 10.dp, horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(su.n.ar(), color = c.gharib, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
                            Text("سورة ${su.name}", fontSize = 17.sp, modifier = Modifier.weight(1f))
                            Text("${su.ayahs.ar()} آية · ص ${su.page.ar()}", fontSize = 12.sp, color = c.muted)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReciteSession(c: MushafColors, prefs: Prefs, plan: ReviewPlan, range: ReciteRange, mode: Int, threshold: Float,
                          voice: Boolean, onBack: () -> Unit) {
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
    var activity by remember { mutableIntStateOf(0) }        // bumps whenever the recogniser hears something new
    val vib = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }

    fun buzz() {
        if (mode == MODE_FREE) return
        try {
            if (Build.VERSION.SDK_INT >= 26) vib?.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) { }
    }

    var correcting by remember { mutableStateOf(false) }
    val speaker = remember { ReciteVoice(context) }
    val listenerRef = remember { arrayOfNulls<RecitationListener>(1) }
    /** Voice correction: stop listening, say the right word(s) in the reciter's voice, then listen again. */
    fun correct(bad: List<Int>) {
        correcting = true
        listenerRef[0]?.pause()
        matcher.resumeAfterCorrection()
        scope.launch {
            val idx = bad.sorted().take(8)
            val skipped = idx.all { matcher.state[it] == WordState.SKIPPED }
            hint = (if (skipped) "نسيتَ: " else "الصواب: ") + idx.joinToString(" ") { words[it].text }
            speaker.say(if (skipped) "نسيت" else "الصواب")
            for (i in idx) speaker.playWord(words[i].s, words[i].a, words[i].w)
            delay(250)
            correcting = false
            if (!finished) listenerRef[0]?.resume()
        }
    }
    val listener = remember {
        RecitationListener(context,
            onWords = { tokens, final ->
                if (!finished && !correcting && (tokens.isNotEmpty() || final)) {
                    val before = matcher.pos
                    val bad = matcher.onResult(tokens, final)
                    if (bad.isNotEmpty()) { said.putAll(matcher.said); buzz(); if (voice) correct(bad) }
                    if (tokens.isNotEmpty()) { heard = tokens.joinToString(" "); activity++ }
                    if (matcher.pos != before || bad.isNotEmpty()) hint = ""
                    tick++
                    if (matcher.done) finished = true
                }
            },
            onStatus = { l, err -> listening = l; if (err != null) status = err })
    }
    listenerRef[0] = listener
    DisposableEffect(Unit) { onDispose { listener.stop(); speaker.release() } }
    LaunchedEffect(finished) {
        if (finished) {
            listener.stop()
            matcher.finish(); said.putAll(matcher.said); tick++
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
    // the reciter is stuck: first letters, then the word (and its sound). Only once he has started reciting
    // (never before the isti'adha/basmala), and only after a real silence: any new sound restarts the wait.
    LaunchedEffect(activity, matcher.pos, listening, finished) {
        if (!listening || finished || mode == MODE_FREE || activity == 0) return@LaunchedEffect
        delay(6000)
        while (correcting) delay(500)
        if (matcher.done) return@LaunchedEffect
        val w = words[matcher.pos]
        val plain = ReciteText.norm(w.text)
        hint = "تلميح: «${plain.take(2)}…»"
        delay(5000)
        if (matcher.done || correcting) return@LaunchedEffect
        matcher.giveCurrent(); tick++
        hint = "الكلمة: ${w.text}"
        if (matcher.done) { finished = true; return@LaunchedEffect }
        // the recogniser must not hear the prompt as if the reciter said it: pause, play, listen again
        // (in its own coroutine: this effect restarts as soon as the position changes)
        correcting = true
        listener.pause(); matcher.resumeAfterCorrection()
        scope.launch {
            try { speaker.playWord(w.s, w.a, w.w); delay(200) } finally { correcting = false; if (!finished) listener.resume() }
        }
    }

    val listState = rememberLazyListState()
    val ayahs = remember(words) { words.indices.groupBy { words[it].s * 1000 + words[it].a }.values.toList() }
    val currentAyah = remember(tick) { ayahs.indexOfFirst { matcher.pos in it } }
    LaunchedEffect(currentAyah) {          // follow the reciter ayah by ayah (not on every recogniser result)
        if (currentAyah >= 0) listState.animateScrollToItem(currentAyah)
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
                            listener.withSound { AudioEngine.playWord(words[i].s, words[i].a, words[i].w) { } }
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
                status?.let { Text(it, fontSize = 13.sp, color = c.mistake) }
                if (listener.needsModel && Build.VERSION.SDK_INT >= 33) TextButton(onClick = { listener.downloadModel() }) {
                    Text("نزّل العربية على الهاتف (تسميع بدون إنترنت وبدون أصوات)")
                }
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
