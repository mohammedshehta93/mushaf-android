package com.shehta.mushaf.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shehta.mushaf.audio.AudioEngine
import com.shehta.mushaf.audio.PlayRequest
import com.shehta.mushaf.data.Prefs
import com.shehta.mushaf.data.Quran
import com.shehta.mushaf.data.Reading
import com.shehta.mushaf.data.Similar
import com.shehta.mushaf.data.SurahInfo
import com.shehta.mushaf.data.ar

/** «ردّد الآية» with the reader's settings (times and pause between repetitions). */
fun repeatAyah(prefs: Prefs, s: Int, a: Int) =
    AudioEngine.play(PlayRequest(prefs.reciter, s, a, s, a, prefs.tdRepeat, 1, prefs.speed, prefs.tdGap * 1000L))

fun repeatLabel(prefs: Prefs): String {
    val times = if (prefs.tdRepeat <= 0) "حتى توقفه" else "${prefs.tdRepeat.ar()} مرات"
    return "ردّد الآية ($times" + (if (prefs.tdGap > 0) "، وقفة ${prefs.tdGap.ar()} ث" else "") + ")"
}

/**
 * «وقفة تدبّر» on one ayah: what the surah is about, whether something is reported about its revelation, three questions,
 * repeating it, writing in the tadabbur notebook, and ayahs with similar wording. Nothing here repeats the tafsir screen:
 * it links to it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TadabburSheet(
    s: Int, a: Int, prefs: Prefs, c: MushafColors, onDismiss: () -> Unit,
    onTafsir: () -> Unit, onSurahInfo: () -> Unit, onNote: () -> Unit, onGo: (Int, Int) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val aim = remember(s) { SurahInfo.of(s)?.aims?.firstOrNull() }
    val nuzul = remember(s, a) { SurahInfo.nuzul(s, a).size }
    val similar = remember(s, a) { if (prefs.showSimilar) Similar.of(s, a) else emptyList() }
    val note = prefs.note(s, a)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        LazyColumn(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("وقفة تدبّر · سورة ${Quran.surahs[s - 1].name} · الآية ${a.ar()}", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("﴿${Quran.ayahText(s, a)}﴾", fontFamily = hafsFamily, fontSize = 23.sp, lineHeight = 42.sp, textAlign = TextAlign.Justify,
                    modifier = Modifier.padding(top = 6.dp))
            }
            if (aim != null) item {
                Card2(c) {
                    Text("من مقاصد سورة ${Quran.surahs[s - 1].name}", fontWeight = FontWeight.Bold, color = c.gharib, fontSize = 15.sp)
                    Text(aim, fontSize = 15.sp, lineHeight = 25.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    Row {
                        TextButton(onClick = onSurahInfo) { Text("مقاصد السورة كاملة") }
                        if (nuzul > 0) TextButton(onClick = onTafsir) { Text("ما ورد في نزولها") }
                    }
                }
            }
            if (Reading.tadabburQuestions.isNotEmpty()) item {
                Card2(c) {
                    Text("اسأل نفسك", fontWeight = FontWeight.Bold, color = c.gharib, fontSize = 15.sp)
                    Reading.tadabburQuestions.forEachIndexed { i, q ->
                        Text("${(i + 1).ar()}. $q", fontSize = 16.sp, lineHeight = 27.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    TextButton(onClick = onTafsir) { Text("افتح التفسير") }
                }
            }
            item {
                Card2(c) {
                    Button(onClick = { repeatAyah(prefs, s, a); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Repeat, null, Modifier.size(18.dp)); Text("  " + repeatLabel(prefs))
                    }
                    Reading.repeatHadith?.let { h ->
                        Text(h.text, fontSize = 14.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 8.dp))
                        Text(h.source, fontSize = 12.sp, color = c.muted)
                    }
                }
            }
            item {
                Card2(c) {
                    Text("دفتر التدبّر", fontWeight = FontWeight.Bold, color = c.gharib, fontSize = 15.sp)
                    if (note != null && (note.text.isNotBlank() || note.act.isNotBlank())) {
                        if (note.text.isNotBlank()) Text(note.text, fontSize = 15.sp, lineHeight = 25.sp, modifier = Modifier.padding(top = 4.dp))
                        if (note.act.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                            Text((if (note.done) "✓ عملتُ بها: " else "نويت: ") + note.act, color = c.gharib, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { prefs.setActDone(s, a, !note.done) }) { Text(if (note.done) "تراجع" else "عملتُ بها") }
                        }
                    } else Text("اكتب ما وقع في قلبك من هذه الآية، وما ستعمل به.", fontSize = 14.sp, color = c.muted, modifier = Modifier.padding(top = 4.dp))
                    OutlinedButton(onClick = onNote, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Icon(Icons.Filled.EditNote, null, Modifier.size(18.dp))
                        Text(if (note == null) "  اكتب تدبّرك" else "  تعديل")
                    }
                }
            }
            if (similar.isNotEmpty()) {
                item { Text("آيات متشابهة الألفاظ", fontWeight = FontWeight.Bold, color = c.gharib, fontSize = 15.sp) }
                items(similar.size) { i ->
                    val x = similar[i]
                    Column(Modifier.fillMaxWidth().clickable { onGo(x.s, x.a) }.padding(vertical = 4.dp)) {
                        Text(Quran.ayahText(x.s, x.a), fontFamily = hafsFamily, fontSize = 19.sp, lineHeight = 34.sp,
                            maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text("سورة ${Quran.surahs[x.s - 1].name} · الآية ${x.a.ar()} — تشترك في ${x.words.ar()} ${if (x.words <= 10) "كلمات" else "كلمة"} متتالية",
                            fontSize = 12.sp, color = c.muted)
                    }
                    if (i < similar.size - 1) HorizontalDivider()
                }
            }
        }
    }
}
