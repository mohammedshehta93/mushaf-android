package com.shehta.mushaf.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shehta.mushaf.data.ArabicText
import com.shehta.mushaf.data.NuzulReport
import com.shehta.mushaf.data.Quran
import com.shehta.mushaf.data.SurahInfo
import com.shehta.mushaf.data.ar

/** «مقاصد السور وأسباب النزول»: every surah's aims and the revelation reports of its ayahs. */
@Composable
fun SurahInfoScreen(c: MushafColors, onBack: () -> Unit, onGo: (Int, Int) -> Unit) {
    var open by rememberSaveable { mutableStateOf(0) }
    if (open > 0) { SurahInfoDetail(c, open, onBack = { open = 0 }, onGo = onGo); return }
    var q by rememberSaveable { mutableStateOf("") }
    val shown = remember(q) {
        val n = ArabicText.normalize(q)
        (1..114).filter { n.isEmpty() || ArabicText.normalize(Quran.surahs[it - 1].name).contains(n) || it.toString() == q.trim() }
    }
    ScreenScaffold("مقاصد السور وأسباب النزول", c, onBack, subtitle = "موضوع كل سورة، وما صحّ في نزول آياتها") {
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("اسم السورة أو رقمها") }) }
            items(shown.size) { i ->
                val s = shown[i]
                val info = SurahInfo.of(s)
                Card2(c, Modifier.clickable { open = s }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${s.ar()}. سورة ${Quran.surahs[s - 1].name}", fontWeight = FontWeight.Bold, color = c.gharib, modifier = Modifier.weight(1f))
                        val n = info?.nuzul?.size ?: 0
                        if (n > 0) Text("${n.ar()} آية لها سبب نزول", fontSize = 12.sp, color = c.muted)
                    }
                    info?.aims?.firstOrNull()?.let { Text(it, fontSize = 14.sp, lineHeight = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

@Composable
private fun SurahInfoDetail(c: MushafColors, s: Int, onBack: () -> Unit, onGo: (Int, Int) -> Unit) {
    val info = SurahInfo.of(s)
    ScreenScaffold("سورة ${Quran.surahs[s - 1].name}", c, onBack, subtitle = "${Quran.surahs[s - 1].type} · ${Quran.surahs[s - 1].ayahs.ar()} آية") {
        LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (info == null) { item { Text("غير متوفر في هذه النسخة.", color = c.muted) }; return@LazyColumn }
            if (info.why.isNotEmpty()) item {
                Text("سبب التسمية", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = c.gharib)
                info.why.forEach { Text("• $it", fontSize = 16.sp, lineHeight = 27.sp, textAlign = TextAlign.Justify) }
            }
            item {
                Text("من مقاصد السورة", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = c.gharib, modifier = Modifier.padding(top = 6.dp))
                info.aims.forEach { Text("• $it", fontSize = 16.sp, lineHeight = 27.sp, textAlign = TextAlign.Justify, modifier = Modifier.padding(bottom = 4.dp)) }
                Text("المصدر: ${SurahInfo.source}", fontSize = 11.sp, color = c.muted)
            }
            item {
                Text("ما ورد في نزول آياتها", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = c.gharib, modifier = Modifier.padding(top = 10.dp))
                Text(if (info.nuzul.isEmpty()) "لم يُعثر في الكتب الستة على رواية صحيحة أو حسنة تذكر سبب نزول آية من هذه السورة."
                     else "روايات صحيحة أو حسنة من الكتب الستة تذكر نزول الآية وقصتها، بنصها ورقمها.", fontSize = 13.sp, color = c.muted)
            }
            info.nuzul.keys.sorted().forEach { a ->
                item {
                    Card2(c) {
                        Text("﴿${Quran.ayahText(s, a)}﴾ ${a.ar()}", fontFamily = hafsFamily, fontSize = 20.sp, lineHeight = 38.sp, textAlign = TextAlign.Justify)
                        TextButton(onClick = { onGo(s, a) }) { Text("افتح في المصحف") }
                        info.nuzul[a]!!.forEach { NuzulView(c, it) }
                    }
                }
            }
        }
    }
}

/** A revelation report: the story part first, the whole narration (with its chain) on request. */
@Composable
fun NuzulView(c: MushafColors, r: NuzulReport, size: Int = 16) {
    var full by remember(r) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(if (full || r.start == 0) r.text else "… " + r.text.substring(r.start.coerceIn(0, r.text.length)).trimStart(),
            fontSize = size.sp, lineHeight = (size * 1.75f).sp, textAlign = TextAlign.Justify)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.source, fontSize = 12.sp, color = c.muted, modifier = Modifier.weight(1f))
            if (!full && r.start > 0) TextButton(onClick = { full = true }) { Text("النص كاملًا") }
        }
    }
}
