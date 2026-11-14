package com.shehta.mushaf.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shehta.mushaf.data.Prefs
import com.shehta.mushaf.data.Quran
import com.shehta.mushaf.data.ar

/** Text of word w (1-based) of an ayah; pause marks are not words. */
fun wordText(s: Int, a: Int, w: Int): String =
    Quran.ayahWords(s, a)[w] ?: ""

/** Controls shown at the bottom of the page in memorisation mode. */
@Composable
fun HifzBar(
    c: MushafColors, page: Int, prefs: Prefs, revealed: Int, total: Int,
    onWord: () -> Unit, onAyah: () -> Unit, onHide: () -> Unit, onAll: () -> Unit, onMistake: () -> Unit, onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxWidth(), color = c.bar, contentColor = c.onBar) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("وضع الحفظ · ${revealed.coerceAtMost(total).ar()} / ${total.ar()} كلمة", fontSize = 13.sp, modifier = Modifier.weight(1f).padding(start = 6.dp))
                val mem = page in prefs.memorized
                TextButton(onClick = { prefs.toggleMemorized(page) }) {
                    Icon(if (mem) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null, Modifier.size(18.dp), tint = c.onBar)
                    Text(if (mem) " محفوظة" else " علّمها محفوظة", color = c.onBar, fontSize = 13.sp)
                }
                TextButton(onClick = onClose) { Text("إنهاء", color = c.onBar) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                HBtn("كلمة", onWord); HBtn("آية", onAyah); HBtn("الكل", onAll); HBtn("إخفاء", onHide)
                TextButton(onClick = onMistake) {
                    Icon(Icons.Filled.ErrorOutline, null, Modifier.size(18.dp), tint = c.onBar); Text(" أخطأت", color = c.onBar)
                }
            }
        }
    }
}

@Composable
private fun HBtn(t: String, onClick: () -> Unit) = TextButton(onClick = onClick) { Text(t, color = androidx.compose.material3.LocalContentColor.current, fontSize = 15.sp) }

/** Memorisation progress + mistakes to review. */
@Composable
fun HifzScreen(prefs: Prefs, onBack: () -> Unit, onGo: (Int, Selection?) -> Unit, onStartHifz: () -> Unit) {
    BackHandler(onBack = onBack)
    val c = mushafColors(prefs.theme)
    var tab by remember { mutableIntStateOf(0) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = c.bar, contentColor = c.onBar) {
                Column(Modifier.statusBarsPadding()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
                        Text("الحفظ والمراجعة", fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.weight(1f))
                    }
                    TabRow(selectedTabIndex = tab, containerColor = c.bar, contentColor = c.onBar) {
                        Tab(tab == 0, { tab = 0 }, text = { Text("التقدّم") })
                        Tab(tab == 1, { tab = 1 }, text = { Text("أخطائي (${prefs.mistakes.size.ar()})") })
                    }
                }
            }
            Box(Modifier.weight(1f).navigationBarsPadding()) {
                if (tab == 0) Progress(prefs, c, onGo, onStartHifz) else Mistakes(prefs, c, onGo)
            }
        }
    }
}

@Composable
private fun Progress(prefs: Prefs, c: MushafColors, onGo: (Int, Selection?) -> Unit, onStartHifz: () -> Unit) {
    val mem = prefs.memorized
    LazyColumn(Modifier.padding(horizontal = 16.dp)) {
        item {
            Column(Modifier.padding(vertical = 14.dp)) {
                Text("${mem.size.ar()} صفحة محفوظة من ٦٠٤", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                LinearProgressIndicator(progress = { mem.size / 604f }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(8.dp))
                Text("افتح أي صفحة، ثم اختر «حفظ» من الشريط السفلي: تختفي الكلمات وتُظهرها كلمةً كلمة وأنت تُسمّع. علّم الكلمة التي أخطأت فيها لتعود إليها هنا.",
                    fontSize = 14.sp, color = c.muted, lineHeight = 22.sp)
                TextButton(onClick = onStartHifz) { Text("ابدأ الحفظ من الصفحة الحالية") }
                HorizontalDivider()
                Text("الأجزاء", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
            }
        }
        items(30) { i ->
            val j = Quran.juz[i]
            val end = if (i < 29) Quran.juz[i + 1].page - 1 else 604
            val pages = (j.page..end)
            val done = pages.count { it in mem }
            Row(Modifier.fillMaxWidth().clickable { onGo(pages.firstOrNull { it !in mem } ?: j.page, null) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("الجزء ${j.n.ar()}", modifier = Modifier.width(90.dp), fontSize = 14.sp)
                Box(Modifier.weight(1f).height(10.dp).background(c.frameFill, RoundedCornerShape(5.dp))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(done.toFloat() / pages.count()).background(c.gharib, RoundedCornerShape(5.dp)))
                }
                Text("${done.ar()}/${pages.count().ar()}", fontSize = 12.sp, color = c.muted, modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun Mistakes(prefs: Prefs, c: MushafColors, onGo: (Int, Selection?) -> Unit) {
    val list = prefs.mistakes.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("لا توجد أخطاء مسجّلة.\nفي وضع الحفظ اضغط «أخطأت» عند الكلمة التي تعثّرت فيها.",
                textAlign = TextAlign.Center, color = c.muted, fontSize = 16.sp, lineHeight = 26.sp)
        }
        return
    }
    LazyColumn {
        items(list.size) { i ->
            val (k, n) = list[i].let { it.key to it.value }
            val (s, a, w) = k.split(":").map { it.toInt() }
            Row(
                Modifier.fillMaxWidth().clickable { onGo(Quran.pageOf(s, a), Selection(s, a, w = w)) }.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(wordText(s, a, w), fontFamily = hafsFamily, fontSize = 24.sp, color = c.mistake, modifier = Modifier.width(120.dp))
                Column(Modifier.weight(1f)) {
                    Text("سورة ${Quran.surahs[s - 1].name} · الآية ${a.ar()}", fontSize = 14.sp)
                    Text("أخطأت فيها ${n.ar()} ${if (n == 1) "مرة" else "مرات"}", fontSize = 12.sp, color = c.muted)
                }
                IconButton(onClick = { prefs.clearMistake(k) }) { Icon(Icons.Filled.Delete, "أتقنتها", tint = c.muted) }
            }
            HorizontalDivider()
        }
    }
}

@Composable
fun HifzToggleIcon(on: Boolean) = Icon(if (on) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, null)

@Composable
fun SpacerW(w: Int) = Spacer(Modifier.width(w.dp))

@Composable
fun SwitchRow(t: String, v: Boolean, on: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(t, modifier = Modifier.weight(1f), fontSize = 15.sp)
        Switch(checked = v, onCheckedChange = on)
    }
}
