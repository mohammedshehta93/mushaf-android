package com.shehta.mushaf.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shehta.mushaf.data.ArabicText
import com.shehta.mushaf.data.DailyHadith
import com.shehta.mushaf.data.MuslimData
import com.shehta.mushaf.data.RItem
import com.shehta.mushaf.data.Reading
import com.shehta.mushaf.data.ar

/** Settings for intentions and tadabbur reminders. */
class ReadingPrefs(context: Context) {
    private val sp = context.getSharedPreferences("mushaf", Context.MODE_PRIVATE)
    var intentionsSeen: Boolean
        get() = sp.getBoolean("intentionsSeen", false)
        set(v) = sp.edit().putBoolean("intentionsSeen", v).apply()
    var intentionsDaily by mutableStateOf(sp.getBoolean("intentionsDaily", false)); private set
    fun updateIntentionsDaily(v: Boolean) { intentionsDaily = v; sp.edit().putBoolean("intentionsDaily", v).apply() }
    var lastIntentionsDay: Int
        get() = sp.getInt("intentionsDay", 0)
        set(v) = sp.edit().putInt("intentionsDay", v).apply()
    /** Show a tadabbur reminder every N pages turned (0 = off). */
    val tadabburEvery: Int get() = sp.getInt("tadabburEvery", 5)
    fun updateTadabburEvery(v: Int) { sp.edit().putInt("tadabburEvery", v).apply() }
    var reminderIndex: Int
        get() = sp.getInt("tadabburIdx", 0)
        set(v) = sp.edit().putInt("tadabburIdx", v).apply()

    fun today(): Int = java.util.Calendar.getInstance().let { it.get(java.util.Calendar.YEAR) * 400 + it.get(java.util.Calendar.DAY_OF_YEAR) }
    fun shouldShowIntentions(): Boolean = !intentionsSeen || (intentionsDaily && lastIntentionsDay != today())
}

/** A hadith with its source and (when available) its explanation, lessons and word meanings. */
@Composable
fun HadithBody(c: MushafColors, h: DailyHadith, fontSize: Int = 17, startOpen: Boolean = false) {
    var open by remember(h.text) { mutableStateOf(startOpen) }
    if (h.title.isNotEmpty()) Text(h.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.gharib)
    Text(h.text, fontSize = fontSize.sp, lineHeight = (fontSize * 1.7).sp, textAlign = TextAlign.Justify, modifier = Modifier.padding(vertical = 6.dp))
    Text(h.source, fontSize = 12.sp, color = c.muted)
    if (h.explanation.isNotEmpty()) {
        TextButton(onClick = { open = !open }) { Text(if (open) "إخفاء الشرح ▲" else "الشرح ▼") }
        AnimatedVisibility(open) {
            Column {
                Text(h.explanation, fontSize = 16.sp, lineHeight = 28.sp, textAlign = TextAlign.Justify)
                if (h.hints.isNotEmpty()) {
                    Text("من فوائد الحديث", fontWeight = FontWeight.Bold, color = c.gharib, modifier = Modifier.padding(top = 10.dp))
                    h.hints.forEach { Text("• $it", fontSize = 15.sp, lineHeight = 26.sp, modifier = Modifier.padding(top = 4.dp)) }
                }
                if (h.words.isNotEmpty()) {
                    Text("معاني الكلمات", fontWeight = FontWeight.Bold, color = c.gharib, modifier = Modifier.padding(top = 10.dp))
                    h.words.forEach { (w, m) -> Text("$w: $m", fontSize = 15.sp, lineHeight = 26.sp, modifier = Modifier.padding(top = 4.dp)) }
                }
                Text("الشرح من «موسوعة الأحاديث النبوية» (hadeethenc.com)", fontSize = 11.sp, color = c.muted, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

/** An ayah (Mushaf font) or a hadith. */
@Composable
fun RItemView(c: MushafColors, item: RItem, onGo: ((Int, Int) -> Unit)?) {
    if (item.isAyah) {
        Text("﴿${item.text}﴾", fontFamily = hafsFamily, fontSize = 22.sp, lineHeight = 42.sp, textAlign = TextAlign.Justify)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.source, fontSize = 12.sp, color = c.muted, modifier = Modifier.weight(1f))
            if (onGo != null) TextButton(onClick = { onGo(item.s, item.a) }) { Text("افتح في المصحف") }
        }
    } else item.hadith?.let { HadithBody(c, it) }
}

// ===================================================================================================== intentions

@Composable
fun IntentionsScreen(c: MushafColors, rp: ReadingPrefs, onDone: () -> Unit) {
    val done = { rp.intentionsSeen = true; rp.lastIntentionsDay = rp.today(); onDone() }
    var open by remember { mutableStateOf<Int?>(null) }
    ScreenScaffold("نوايا قبل القراءة", c, done, subtitle = "«إنما الأعمال بالنيات» — متفق عليه") {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("استحضر قبل أن تقرأ ما تستطيع من هذه النوايا؛ فكلما تعددت النوايا الصالحة في العمل الواحد تعدد أجره بإذن الله.",
                    fontSize = 15.sp, lineHeight = 26.sp, color = c.muted)
            }
            Reading.intentions.forEachIndexed { i, it ->
                item {
                    Card2(c, Modifier.clickable { open = if (open == i) null else i }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${(i + 1).ar()}.", fontWeight = FontWeight.Bold, color = c.gharib)
                            Text("  " + it.label, fontSize = 17.sp, lineHeight = 28.sp, modifier = Modifier.weight(1f))
                            Text(if (open == i) "▲" else "▼", color = c.muted, fontSize = 12.sp)
                        }
                        AnimatedVisibility(open == i) { Column(Modifier.padding(top = 6.dp)) { RItemView(c, it, null) } }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { rp.updateIntentionsDaily(!rp.intentionsDaily) }) {
                    androidx.compose.material3.Checkbox(checked = rp.intentionsDaily, onCheckedChange = { rp.updateIntentionsDaily(it) })
                    Text("ذكّرني بالنوايا كل يوم عند أول فتح للتطبيق")
                }
            }
        }
        Button(onClick = done, modifier = Modifier.fillMaxWidth().padding(14.dp).navigationBarsPadding()) { Text("بسم الله، ابدأ القراءة", fontSize = 17.sp) }
    }
}

/** Small card shown while reading, every few pages. */
@Composable
fun TadabburCard(c: MushafColors, item: RItem, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth().padding(10.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp) {
        Column(Modifier.padding(14.dp)) {
            Text("وقفة تدبّر · ${item.label}", fontWeight = FontWeight.Bold, color = c.gharib)
            Column(Modifier.padding(top = 4.dp)) {
                if (item.isAyah) {
                    Text("﴿${item.text}﴾", fontFamily = hafsFamily, fontSize = 20.sp, lineHeight = 38.sp, textAlign = TextAlign.Justify)
                    Text(item.source, fontSize = 12.sp, color = c.muted)
                } else item.hadith?.let { h ->
                    Text(h.text, fontSize = 15.sp, lineHeight = 25.sp, maxLines = 7, textAlign = TextAlign.Justify)
                    Text(h.source, fontSize = 12.sp, color = c.muted)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onDismiss) { Text("جزاك الله خيرًا") } }
        }
    }
}

// ===================================================================================================== virtues

@Composable
fun VirtuesScreen(c: MushafColors, onBack: () -> Unit, onGo: (Int, Int) -> Unit, onIntentions: () -> Unit) {
    ScreenScaffold("فضل القرآن", c, onBack, subtitle = "قراءته وحفظه وتعلّمه والعمل به") {
        LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Card2(c, Modifier.clickable(onClick = onIntentions)) {
                    Text("نوايا قبل القراءة ›", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = c.gharib)
                    Text("استحضر نواياك قبل أن تبدأ.", fontSize = 13.sp, color = c.muted)
                }
                Text("الآيات من النص الرسمي لمجمع الملك فهد، والأحاديث بنصها من «رياض الصالحين» مع تخريجها.", fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(top = 6.dp))
            }
            Reading.virtues.forEach { g ->
                item { Text(g.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = c.gharib, modifier = Modifier.padding(top = 10.dp)) }
                items(g.items) { it -> Card2(c) { RItemView(c, it, onGo) } }
            }
        }
    }
}

// ===================================================================================================== explained hadiths

@Composable
fun HadithLibraryScreen(c: MushafColors, onBack: () -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    val all = remember { MuslimData.explained() }
    val norm = remember(all) { all.map { ArabicText.normalize(it.title + " " + it.text) } }
    val shown = remember(q, all) {
        val nq = ArabicText.normalize(q)
        if (nq.length < 2) all.indices.toList() else all.indices.filter { norm[it].contains(nq) }
    }
    ScreenScaffold("أحاديث مشروحة", c, onBack, subtitle = if (all.isEmpty()) null else "${all.size.ar()} حديثًا صحيحًا أو حسنًا مع الشرح والفوائد") {
        if (all.isEmpty()) {
            Centered { Text("الأحاديث المشروحة غير متوفرة في هذه النسخة.", color = c.muted, modifier = Modifier.padding(24.dp)) }
            return@ScreenScaffold
        }
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("ابحث بكلمة من الحديث") }, leadingIcon = { Icon(Icons.Filled.Search, null) })
                Text("المصدر: «موسوعة الأحاديث النبوية» hadeethenc.com — بإشراف متخصصين.", fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(top = 4.dp))
            }
            items(shown.take(300)) { i -> Card2(c) { HadithBody(c, all[i], 16) } }
        }
    }
}
