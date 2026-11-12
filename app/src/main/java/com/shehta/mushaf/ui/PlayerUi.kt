package com.shehta.mushaf.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.shehta.mushaf.audio.AudioEngine
import com.shehta.mushaf.audio.PlayRequest
import com.shehta.mushaf.data.Prefs
import com.shehta.mushaf.data.Quran
import com.shehta.mushaf.data.ar

/** Small player bar shown while a recitation is active. */
@Composable
fun MiniPlayer(c: MushafColors, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val st = AudioEngine.state
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp,
    ) {
        Column {
            Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (st.basmala) "البسملة · سورة ${Quran.surahs[st.s - 1].name}" else "سورة ${Quran.surahs.getOrNull(st.s - 1)?.name ?: ""} · الآية ${st.a.ar()}",
                        fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1,
                    )
                    Text(st.error ?: AudioEngine.reciter(st.reciter).name, fontSize = 12.sp,
                        color = if (st.error != null) c.mistake else c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { AudioEngine.previous() }) { Icon(Icons.Filled.SkipNext, "السابق") }      // RTL: next icon points back
                IconButton(onClick = { AudioEngine.toggle() }) { Icon(if (st.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "تشغيل/إيقاف") }
                IconButton(onClick = { AudioEngine.next() }) { Icon(Icons.Filled.SkipPrevious, "التالي") }
                IconButton(onClick = { AudioEngine.stop() }) { Icon(Icons.Filled.Close, "إغلاق") }
            }
        }
    }
}

/** Recitation options: reciter, range, repetition (for memorisation), speed, downloads. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSheet(prefs: Prefs, startS: Int, startA: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val surah = Quran.surahs[startS - 1]
    var fromA by remember { mutableIntStateOf(startA) }
    var toA by remember { mutableIntStateOf(surah.ayahs) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("الاستماع · سورة ${surah.name}", fontWeight = FontWeight.Bold, fontSize = 19.sp)

            Label2("القارئ")
            Quran.reciters.forEach { r ->
                Row(Modifier.fillMaxWidth().clickable { prefs.updateReciter(r.id) }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = prefs.reciter == r.id, onClick = { prefs.updateReciter(r.id) })
                    Text(r.name, modifier = Modifier.weight(1f))
                    if (r.timed) Text("تتبّع الكلمات", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
            }

            Label2("من الآية ${fromA.ar()} إلى الآية ${toA.ar()}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                FilterChip(selected = toA == surah.ayahs, onClick = { toA = surah.ayahs }, label = { Text("حتى آخر السورة") })
                FilterChip(selected = toA == fromA, onClick = { toA = fromA }, label = { Text("هذه الآية فقط") })
                val pageEnd = run {
                    val p = Quran.pageOf(startS, fromA)
                    (fromA..surah.ayahs).lastOrNull { Quran.pageOf(startS, it) == p } ?: fromA
                }
                FilterChip(selected = toA == pageEnd && toA != fromA, onClick = { toA = pageEnd }, label = { Text("حتى آخر الصفحة") })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("البداية", modifier = Modifier.weight(1f))
                IconButton(onClick = { if (fromA > 1) fromA-- }) { Text("−", fontSize = 22.sp) }
                Text(fromA.ar(), fontSize = 18.sp)
                IconButton(onClick = { if (fromA < surah.ayahs) { fromA++; if (toA < fromA) toA = fromA } }) { Text("+", fontSize = 22.sp) }
                Text("النهاية", modifier = Modifier.padding(start = 12.dp))
                IconButton(onClick = { if (toA > fromA) toA-- }) { Text("−", fontSize = 22.sp) }
                Text(toA.ar(), fontSize = 18.sp)
                IconButton(onClick = { if (toA < surah.ayahs) toA++ }) { Text("+", fontSize = 22.sp) }
            }

            Label2("تكرار كل آية")
            ChipRow(listOf(1, 2, 3, 5, 10, 0), prefs.repeatAyah, { if (it == 0) "∞" else it.ar() }) { prefs.updateRepeatAyah(it) }
            Label2("تكرار المقطع كاملًا")
            ChipRow(listOf(1, 2, 3, 5, 0), prefs.repeatRange, { if (it == 0) "∞" else it.ar() }) { prefs.updateRepeatRange(it) }
            Label2("السرعة")
            ChipRow(listOf(0.75f, 1f, 1.25f, 1.5f), prefs.speed, { "×" + it.toString() }) { prefs.updateSpeed(it) }

            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("اتبع التلاوة وقلّب الصفحات تلقائيًا", modifier = Modifier.weight(1f))
                Switch(checked = prefs.followAudio, onCheckedChange = { prefs.updateFollowAudio(it) })
            }

            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            val dl = AudioEngine.downloading
            val downloaded = remember(prefs.reciter, dl) { AudioEngine.isSurahDownloaded(prefs.reciter, startS) }
            if (dl != null) {
                Text("جارِ تنزيل سورة ${Quran.surahs[dl.first - 1].name}…", fontSize = 14.sp)
                LinearProgressIndicator(progress = { dl.second }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                TextButton(onClick = { AudioEngine.cancelDownload() }) { Text("إلغاء التنزيل") }
            } else if (downloaded) {
                Text("✓ السورة محفوظة على الجهاز وتعمل بدون إنترنت", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
            } else {
                OutlinedButton(onClick = {
                    AudioEngine.downloadSurah(prefs.reciter, startS) { ok ->
                        Toast.makeText(context, if (ok) "تم تنزيل السورة" else "تعذّر التنزيل، تحقق من الإنترنت", Toast.LENGTH_SHORT).show()
                    }
                }) { Icon(Icons.Filled.Download, null, Modifier.size(18.dp)); Text("  تنزيل السورة للاستماع بدون إنترنت") }
            }

            Button(
                onClick = {
                    AudioEngine.play(PlayRequest(prefs.reciter, startS, fromA, startS, toA, prefs.repeatAyah, prefs.repeatRange, prefs.speed))
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            ) { Icon(Icons.Filled.PlayArrow, null); Text("  تشغيل") }
            if (prefs.repeatAyah != 1 || prefs.repeatRange != 1) {
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Repeat, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("  وضع التكرار مفعّل — مفيد للحفظ", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun Label2(t: String) = Text(t, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))

@Composable
private fun <T> ChipRow(values: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        values.forEach { v -> FilterChip(selected = v == selected, onClick = { onPick(v) }, label = { Text(label(v)) }) }
    }
}
