package com.shehta.mushaf.audio

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.shehta.mushaf.data.Quran
import com.shehta.mushaf.data.Reciter
import com.shehta.mushaf.data.ar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** What is playing right now. word = 1-based word being recited (0 if unknown / basmala). */
data class AudioState(
    val active: Boolean = false,
    val playing: Boolean = false,
    val s: Int = 0, val a: Int = 0, val word: Int = 0,
    val basmala: Boolean = false,
    val reciter: String = "",
    val error: String? = null,
    /** Non-Quran audio (radio / podcast): shown with its own title and no ayah tracking. */
    val stream: Boolean = false,
    val title: String = "",
    val subtitle: String = "",
    val live: Boolean = false,
    val streamUrl: String = "",
)

data class PlayRequest(
    val reciter: String,
    val fromS: Int, val fromA: Int, val toS: Int, val toA: Int,
    val repeatAyah: Int,        // 0 = endless
    val repeatRange: Int,       // 0 = endless
    val speed: Float,
    /** Pause between repetitions, for reflection («ردّد الآية»); 0 = none. */
    val gapMs: Long = 0,
)

/**
 * Recitation player (Media3 ExoPlayer) shared by the UI and [PlaybackService] (background playback +
 * media notification). Audio comes from everyayah.com, or from the phone when the surah was downloaded.
 */
object AudioEngine {
    private lateinit var app: Context
    lateinit var player: ExoPlayer; private set
    private val scope = MainScope()
    private var ticker: Job? = null
    private var gapJob: Job? = null
    private var gapMs = 0L
    var state by mutableStateOf(AudioState()); private set

    val ready get() = this::player.isInitialized

    fun init(context: Context) {
        if (ready) return
        app = context.applicationContext
        player = ExoPlayer.Builder(app)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { refresh() }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // «ردّد الآية»: a short silence before each repetition
                if (gapMs > 0 && player.isPlaying && (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT ||
                        reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)) {
                    player.pause()
                    gapJob?.cancel()
                    gapJob = scope.launch { delay(gapMs); gapJob = null; player.play() }
                }
                refresh()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) stop() else refresh()
            }
            override fun onPlayerError(error: PlaybackException) {
                state = state.copy(playing = false, error =
                    if (state.stream) "تعذّر التشغيل. تأكد من الاتصال بالإنترنت، أو قد يكون البث متوقفًا الآن."
                    else "تعذّر تشغيل التلاوة. تأكد من الاتصال بالإنترنت أو نزّل السورة أولًا.")
            }
        })
    }

    fun reciter(id: String): Reciter = Quran.reciters.firstOrNull { it.id == id } ?: Quran.reciters.first()

    fun localFile(reciter: String, s: Int, a: Int): File = File(app.filesDir, "audio/$reciter/${reciter(reciter).file(s, a)}")

    private fun uri(r: Reciter, s: Int, a: Int): Uri {
        val f = localFile(r.id, s, a)
        return if (f.exists()) Uri.fromFile(f) else Uri.parse(r.url(s, a))
    }

    private fun item(r: Reciter, s: Int, a: Int, basmalaOf: Int = 0): MediaItem {
        val title = if (basmalaOf > 0) "البسملة · سورة ${Quran.surahs[basmalaOf - 1].name}" else "سورة ${Quran.surahs[s - 1].name} · الآية ${a.ar()}"
        return MediaItem.Builder()
            .setUri(uri(r, s, a))
            .setMediaId(if (basmalaOf > 0) "b:$basmalaOf" else "$s:$a")
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(r.name).setAlbumTitle("تِبيان").build())
            .build()
    }

    fun play(req: PlayRequest) {
        saveStreamPosition(); positionSaver = null
        val r = reciter(req.reciter)
        val ayahs = ArrayList<IntArray>()
        var s = req.fromS; var a = req.fromA
        while (s < req.toS || (s == req.toS && a <= req.toA)) {
            ayahs += intArrayOf(s, a)
            a++
            if (a > Quran.surahs[s - 1].ayahs) { s++; a = 1; if (s > 114) break }
        }
        if (ayahs.isEmpty()) return
        val once = ArrayList<MediaItem>()
        for (x in ayahs) {
            if (x[1] == 1 && x[0] != 1 && x[0] != 9) once += item(r, 1, 1, basmalaOf = x[0])
            val times = if (req.repeatAyah <= 0) 1 else req.repeatAyah
            repeat(times) { once += item(r, x[0], x[1]) }
        }
        val items = ArrayList<MediaItem>()
        val rangeTimes = if (req.repeatRange <= 0) 1 else req.repeatRange
        repeat(rangeTimes) { items += once }

        startService()
        player.setMediaItems(items)
        player.repeatMode = when {
            req.repeatAyah <= 0 && ayahs.size == 1 -> Player.REPEAT_MODE_ONE
            req.repeatAyah <= 0 -> Player.REPEAT_MODE_ONE   // endless ayah repetition: stays on each ayah until "next"
            req.repeatRange <= 0 -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        player.playbackParameters = PlaybackParameters(req.speed)
        gapJob?.cancel(); gapMs = req.gapMs
        player.prepare()
        player.play()
        state = AudioState(active = true, playing = true, s = ayahs[0][0], a = ayahs[0][1], reciter = r.id)
        startTicker()
    }

    /** Plays a radio stream or a podcast episode. [startMs] resumes an episode; [onPosition] saves progress. */
    private var positionSaver: ((String, Long) -> Unit)? = null

    fun playStream(title: String, subtitle: String, url: String, live: Boolean, startMs: Long = 0, onPosition: ((String, Long) -> Unit)? = null) {
        saveStreamPosition()
        positionSaver = onPosition
        val item = MediaItem.Builder()
            .setUri(url)
            .setMediaId("x:" + url.hashCode())
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(subtitle).setAlbumTitle("استمع").build())
            .build()
        startService()
        player.setMediaItem(item, if (live) C.TIME_UNSET else startMs)
        player.repeatMode = Player.REPEAT_MODE_OFF
        gapJob?.cancel(); gapMs = 0
        player.playbackParameters = PlaybackParameters(1f)
        player.prepare()
        player.play()
        ticker?.cancel()
        state = AudioState(active = true, playing = true, stream = true, title = title, subtitle = subtitle, live = live, streamUrl = url)
        if (!live) ticker = scope.launch { while (isActive) { delay(15000); saveStreamPosition() } }
    }

    private fun saveStreamPosition() {
        val st = state
        if (ready && st.stream && !st.live && st.streamUrl.isNotEmpty()) positionSaver?.invoke(st.streamUrl, player.currentPosition)
    }

    fun seekBy(ms: Long) { if (ready) player.seekTo((player.currentPosition + ms).coerceAtLeast(0)) }

    fun toggle() {
        if (gapJob?.isActive == true) { gapJob?.cancel(); refresh(); return }     // «pause» during the silence: stay paused
        if (player.isPlaying) player.pause() else { if (player.playbackState == Player.STATE_IDLE) player.prepare(); player.play() } }
    fun next() { gapJob?.cancel(); if (player.hasNextMediaItem()) player.seekToNextMediaItem() }
    fun previous() { if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() else player.seekTo(0) }

    fun stop() {
        gapJob?.cancel(); gapMs = 0
        if (state.stream && player.playbackState == Player.STATE_ENDED) state.streamUrl.takeIf { it.isNotEmpty() }?.let { positionSaver?.invoke(it, 0L) }
        else saveStreamPosition()
        positionSaver = null
        player.stop()
        player.clearMediaItems()
        ticker?.cancel()
        state = AudioState()
        app.stopService(Intent(app, PlaybackService::class.java))
    }

    private fun startService() {
        try { app.startService(Intent(app, PlaybackService::class.java)) } catch (e: Exception) { /* app in background: playback continues in-process */ }
    }

    private fun refresh() {
        if (!state.active) return
        if (state.stream) {
            state = state.copy(playing = player.isPlaying, error = if (player.isPlaying) null else state.error)
            if (!player.isPlaying) saveStreamPosition()
            return
        }
        val id = player.currentMediaItem?.mediaId ?: return
        if (id.startsWith("x:")) return
        if (id.startsWith("b:")) {
            val s = id.removePrefix("b:").toInt()
            state = state.copy(playing = player.isPlaying || gapJob?.isActive == true, s = s, a = 1, word = 0, basmala = true, error = null)
        } else {
            val (s, a) = id.split(":").map { it.toInt() }
            state = state.copy(playing = player.isPlaying || gapJob?.isActive == true, s = s, a = a, basmala = false, error = null)
        }
    }

    /** Follows the recitation word by word (when timing data exists for the reciter). */
    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(70)
                val st = state
                if (!st.active || st.stream || st.basmala || !player.isPlaying) continue
                val t = Quran.timings(st.reciter, st.s, st.a) ?: continue
                val pos = player.currentPosition
                var w = 0
                var i = 0
                while (i + 2 < t.size) {
                    if (pos >= t[i + 1] && pos < t[i + 2]) { w = t[i]; break }
                    if (pos >= t[i + 1]) w = t[i]
                    i += 3
                }
                if (w != st.word) state = st.copy(word = w)
            }
        }
    }

    // ------------------------------------------------------------------ word pronunciation (quran.com word audio)

    private var wordPlayer: MediaPlayer? = null

    fun playWord(s: Int, a: Int, w: Int, onError: () -> Unit) {
        if (ready && player.isPlaying) player.pause()
        wordPlayer?.release()
        val url = String.format(java.util.Locale.US, "https://audio.qurancdn.com/wbw/%03d_%03d_%03d.mp3", s, a, w)
        wordPlayer = MediaPlayer().apply {
            setOnPreparedListener { it.start() }
            setOnCompletionListener { it.release(); if (wordPlayer === it) wordPlayer = null }
            setOnErrorListener { mp, _, _ -> mp.release(); if (wordPlayer === mp) wordPlayer = null; onError(); true }
            try { setDataSource(url); prepareAsync() } catch (e: Exception) { onError() }
        }
    }

    // ------------------------------------------------------------------ offline downloads

    var downloading by mutableStateOf<Pair<Int, Float>?>(null); private set   // surah, progress 0..1
    private var downloadJob: Job? = null

    fun isSurahDownloaded(reciter: String, s: Int): Boolean =
        (1..Quran.surahs[s - 1].ayahs).all { localFile(reciter, s, it).exists() }

    fun downloadSurah(reciter: String, s: Int, onDone: (Boolean) -> Unit) {
        if (downloadJob?.isActive == true) return
        val r = reciter(reciter)
        downloadJob = scope.launch {
            val n = Quran.surahs[s - 1].ayahs
            var ok = true
            downloading = s to 0f
            withContext(Dispatchers.IO) {
                val list = (1..n).map { s to it } + (if (s != 1 && s != 9) listOf(1 to 1) else emptyList())
                for ((i, pair) in list.withIndex()) {
                    val f = localFile(r.id, pair.first, pair.second)
                    if (!f.exists()) {
                        try {
                            f.parentFile?.mkdirs()
                            val c = URL(r.url(pair.first, pair.second)).openConnection() as HttpURLConnection
                            c.connectTimeout = 15000; c.readTimeout = 30000
                            val tmp = File(f.path + ".part")
                            c.inputStream.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
                            tmp.renameTo(f)
                        } catch (e: Exception) { ok = false; break }
                    }
                    withContext(Dispatchers.Main) { downloading = s to (i + 1f) / list.size }
                }
            }
            downloading = null
            onDone(ok)
        }
    }

    fun cancelDownload() { downloadJob?.cancel(); downloading = null }

    fun deleteDownloads(reciter: String) { File(app.filesDir, "audio/$reciter").deleteRecursively() }

    fun downloadedSurahs(reciter: String): Int = (1..114).count { isSurahDownloaded(reciter, it) }
}
