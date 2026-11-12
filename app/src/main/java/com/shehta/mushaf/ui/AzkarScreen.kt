package com.shehta.mushaf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shehta.mushaf.data.ArabicText
import com.shehta.mushaf.data.MuslimData
import com.shehta.mushaf.data.MuslimPrefs
import com.shehta.mushaf.data.ar

/** Hisn al-Muslim: categories, then each dhikr with a tap counter. */
@Composable
fun AzkarScreen(mp: MuslimPrefs, c: MushafColors, start: String?, onBack: () -> Unit) {
    var open by remember { mutableStateOf(start) }
    val cat = open?.let { MuslimData.category(it) }
    if (cat != null) {
        ZikrList(cat.title, mp, c) { open = null; if (start != null) onBack() }
        return
    }
    var q by remember { mutableStateOf("") }
    val all = remember { MuslimData.azkar() }
    val featured = listOf(MuslimData.MORNING, MuslimData.EVENING, MuslimData.AFTER_PRAYER, MuslimData.SLEEP, MuslimData.WAKING)
    ScreenScaffold("الأذكار", c, onBack, subtitle = "حصن المسلم") {
        LazyColumn(contentPadding = PaddingValues(12.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    featured.take(3).forEach { t -> Tile(t, c, Modifier.weight(1f)) { open = t } }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    featured.drop(3).forEach { t -> Tile(t, c, Modifier.weight(1f)) { open = t } }
                }
                OutlinedTextField(value = q, onValueChange = { q = it }, singleLine = true, placeholder = { Text("ابحث عن دعاء: السفر، المطر، الهم…") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
            }
            val nq = ArabicText.normalize(q)
            val list = all.filter { it.title !in featured && (nq.isEmpty() || ArabicText.normalize(it.title).contains(nq) || it.items.any { z -> ArabicText.normalize(z.text).contains(nq) }) }
            items(list.size) { i ->
                Text(list[i].title, fontSize = 17.sp, modifier = Modifier.fillMaxWidth().clickable { open = list[i].title }.padding(vertical = 12.dp, horizontal = 4.dp))
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun Tile(title: String, c: MushafColors, modifier: Modifier, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = c.frameFill, modifier = modifier.height(64.dp).clickable(onClick = onClick)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(6.dp)) {
            Text(title.removePrefix("الأذكار ").removePrefix("أذكار "), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ZikrList(title: String, mp: MuslimPrefs, c: MushafColors, onBack: () -> Unit) {
    val cat = MuslimData.category(title) ?: return
    val haptic = LocalHapticFeedback.current
    val doneCount = cat.items.indices.count { mp.azkarCount(title, it, cat.items[it].count) >= cat.items[it].count }
    ScreenScaffold(title, c, onBack, subtitle = "أنجزت ${doneCount.ar()} من ${cat.items.size.ar()}", actions = {
        TextButton(onClick = { mp.azkarResetCategory(title) }) { Text("من جديد", color = c.onBar) }
    }) {
        LinearProgressIndicator(progress = { doneCount.toFloat() / cat.items.size }, modifier = Modifier.fillMaxWidth())
        LazyColumn(contentPadding = PaddingValues(12.dp)) {
            items(cat.items.size) { i ->
                val z = cat.items[i]
                val n = mp.azkarCount(title, i, z.count)
                val done = n >= z.count
                Surface(
                    shape = RoundedCornerShape(18.dp), color = if (done) c.ayahHighlight else c.frameFill,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(enabled = !done) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        mp.azkarTap(title, i)
                    },
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(z.text, fontSize = 19.sp, lineHeight = 33.sp, textAlign = TextAlign.Justify)
                        if (z.virtue.isNotBlank()) Text(z.virtue, fontSize = 13.sp, color = c.gharib, lineHeight = 21.sp, modifier = Modifier.padding(top = 8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            Text(z.reference, fontSize = 12.sp, color = c.muted, modifier = Modifier.weight(1f))
                            Box(Modifier.size(54.dp).background(if (done) c.gharib else c.paper, CircleShape), contentAlignment = Alignment.Center) {
                                Text(if (done) "✓" else (z.count - n).ar(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (done) c.paper else c.ink)
                            }
                        }
                    }
                }
            }
            item { Text("اضغط على الذِّكر بعد قوله؛ يُعاد العدّاد كل يوم تلقائيًا.", fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(8.dp)) }
        }
    }
}

/** Electronic tasbih with a target, a phrase and daily totals. */
@Composable
fun TasbihScreen(mp: MuslimPrefs, c: MushafColors, onBack: () -> Unit) {
    val phrases = listOf("سُبْحَانَ اللهِ", "الْحَمْدُ للهِ", "اللهُ أَكْبَرُ", "لَا إِلَهَ إِلَّا اللهُ", "أَسْتَغْفِرُ اللهَ",
        "اللَّهُمَّ صَلِّ عَلَى مُحَمَّدٍ", "سُبْحَانَ اللهِ وَبِحَمْدِهِ", "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللهِ")
    val haptic = LocalHapticFeedback.current
    ScreenScaffold("السبحة", c, onBack, subtitle = "اليوم ${mp.tasbihToday.ar()} · الإجمالي ${mp.tasbihTotal.toInt().ar()}") {
        Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            LazyRowChips(phrases, mp.tasbihPhrase) { mp.updateTasbih(mp.tasbihTarget, it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                listOf(33, 99, 100, 1000).forEach { t ->
                    FilterChip(selected = mp.tasbihTarget == t, onClick = { mp.updateTasbih(t, mp.tasbihPhrase) }, label = { Text(t.ar()) })
                }
            }
            Text(phrases.getOrElse(mp.tasbihPhrase) { phrases[0] }, fontSize = 30.sp, fontFamily = hafsFamily, modifier = Modifier.padding(vertical = 12.dp))
            val reached = mp.tasbihCount >= mp.tasbihTarget
            Box(
                Modifier.fillMaxWidth(0.8f).aspectRatio(1f).background(if (reached) c.gharib else c.frameFill, CircleShape)
                    .clickable {
                        mp.tasbihTap()
                        haptic.performHapticFeedback(if (mp.tasbihCount % mp.tasbihTarget == 0) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(mp.tasbihCount.ar(), fontSize = 64.sp, fontWeight = FontWeight.Bold, color = if (reached) c.paper else c.ink)
                    Text("من ${mp.tasbihTarget.ar()}", fontSize = 16.sp, color = if (reached) c.paper else c.muted)
                }
            }
            TextButton(onClick = { mp.tasbihReset() }, modifier = Modifier.padding(top = 12.dp)) { Text("تصفير العدّاد") }
            Text("اضغط في أي مكان داخل الدائرة.", fontSize = 12.sp, color = c.muted)
        }
    }
}

@Composable
private fun LazyRowChips(items: List<String>, selected: Int, onPick: (Int) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(items.size) { i -> FilterChip(selected = selected == i, onClick = { onPick(i) }, label = { Text(items[i]) }) }
    }
}
