package com.shehta.mushaf.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.BackHandler
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
    /** When to show the intentions as reading starts: 0 never, 1 once a day (default), 2 every time. */
    var intentionsMode by mutableStateOf(sp.getInt("intentionsMode", 1)); private set
    fun updateIntentionsMode(v: Int) { intentionsMode = v; sp.edit().putInt("intentionsMode", v).apply() }
    var lastIntentionsDay: Int
        get() = sp.getInt("intentionsDay", 0)
        set(v) = sp.edit().putInt("intentionsDay", v).apply()
    /** One tadabbur reminder when reading starts (no repeated reminders). */
    var tadabburOnStart by mutableStateOf(sp.getBoolean("tadabburOnStart", true)); private set
    fun updateTadabburOnStart(v: Boolean) { tadabburOnStart = v; sp.edit().putBoolean("tadabburOnStart", v).apply() }
    var reminderIndex: Int
        get() = sp.getInt("tadabburIdx", 0)
        set(v) = sp.edit().putInt("tadabburIdx", v).apply()

    fun today(): Int = java.util.Calendar.getInstance().let { it.get(java.util.Calendar.YEAR) * 400 + it.get(java.util.Calendar.DAY_OF_YEAR) }
    fun shouldShowIntentions(): Boolean = intentionsMode == 2 || (intentionsMode == 1 && lastIntentionsDay != today())
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
fun RItemView(c: MushafColors, item: RItem, onGo: ((Int, Int) -> Unit)?, heading: Boolean = false) {
    if (heading && item.label.isNotEmpty()) Text(item.label, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c.gharib, modifier = Modifier.padding(bottom = 4.dp))
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
    val done = { rp.lastIntentionsDay = rp.today(); onDone() }
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
            item { Column { IntentionOptions(c, rp) } }
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
        LazyColumn(state = keptListState("virtues"), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Card2(c, Modifier.clickable(onClick = onIntentions)) {
                    Text("نوايا قبل القراءة ›", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = c.gharib)
                    Text("استحضر نواياك قبل أن تبدأ.", fontSize = 13.sp, color = c.muted)
                }
                Text("الآيات من النص الرسمي لمجمع الملك فهد، والأحاديث بنصها من «رياض الصالحين» والكتب الستة مع رقم الحديث وحكمه.", fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(top = 6.dp))
            }
            Reading.virtues.forEach { g ->
                item {
                    Text(g.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = c.gharib, modifier = Modifier.padding(top = 10.dp))
                    if (g.note.isNotEmpty()) Text(g.note, fontSize = 13.sp, color = c.muted)
                }
                items(g.items) { it -> Card2(c) { RItemView(c, it, onGo, heading = true) } }
            }
        }
    }
}

// ===================================================================================================== explained hadiths

@Composable
fun HadithLibraryScreen(c: MushafColors, onBack: () -> Unit) {
    val all = remember { MuslimData.explained() }
    val (src, groups) = MuslimData.library
    var group by rememberSaveable { mutableStateOf(-1) }        // -1 = the list of collections
    var q by rememberSaveable { mutableStateOf("") }
    if (all.isEmpty() || groups.isEmpty()) {
        ScreenScaffold("الأحاديث المشروحة", c, onBack) {
            Centered { Text("الأحاديث المشروحة غير متوفرة في هذه النسخة.", color = c.muted, modifier = Modifier.padding(24.dp)) }
        }
        return
    }
    val g = groups.getOrNull(group)
    if (g == null) {
        ScreenScaffold("الأحاديث المشروحة", c, onBack, subtitle = "كل حديث صحيح أو حسن، ومعه شرحه وفوائده") {
            LazyColumn(state = keptListState("hadiths"), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(groups.size) { i ->
                    val x = groups[i]
                    Card2(c, Modifier.clickable { group = i; q = "" }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(x.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = c.gharib, modifier = Modifier.weight(1f))
                            Text("${x.items.size.ar()} حديثًا ›", fontSize = 13.sp, color = c.muted)
                        }
                        if (x.desc.isNotEmpty()) Text(x.desc, fontSize = 14.sp, color = c.muted)
                    }
                }
                item { Text("المصدر: $src", fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(4.dp)) }
            }
        }
        return
    }
    BackHandler { group = -1 }
    val norm = remember(g) { g.items.map { ArabicText.normalize(all[it].title + " " + all[it].text) } }
    val shown = remember(q, g) {
        val nq = ArabicText.normalize(q)
        if (nq.length < 2) g.items.indices.toList() else g.items.indices.filter { norm[it].contains(nq) }
    }
    ScreenScaffold(g.title, c, onBack = { group = -1 }, subtitle = "${g.items.size.ar()} حديثًا مع الشرح") {
        LazyColumn(state = keptListState("hadiths:$group"), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("ابحث بكلمة من الحديث") }, leadingIcon = { Icon(Icons.Filled.Search, null) })
            }
            items(shown.take(400)) { k ->
                val h = all[g.items[k]]
                Card2(c) {
                    g.nums.getOrNull(k)?.let { Text("الحديث ${it.ar()}", fontSize = 12.sp, color = c.muted) }
                    HadithBody(c, h, 16)
                }
            }
        }
    }
}

/** Choices shared by the intentions screen and the settings. */
@Composable
fun IntentionOptions(c: MushafColors, rp: ReadingPrefs) {
    Text("متى تظهر النوايا عند بدء القراءة؟", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(1 to "مرة يوميًا", 2 to "كل مرة", 0 to "لا تظهر").forEach { (v, n) ->
            androidx.compose.material3.FilterChip(selected = rp.intentionsMode == v, onClick = { rp.updateIntentionsMode(v) }, label = { Text(n) })
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { rp.updateTadabburOnStart(!rp.tadabburOnStart) }) {
        androidx.compose.material3.Checkbox(checked = rp.tadabburOnStart, onCheckedChange = { rp.updateTadabburOnStart(it) })
        Text("وقفة تدبّر واحدة عند بدء القراءة")
    }
}

/** Shown once after every update. Items: assets/quran/whatsnew.json (tools/content/whatsnew.json). */
@Composable
fun WhatsNewDialog(onDismiss: () -> Unit) {
    val data = remember {
        try { org.json.JSONObject(com.shehta.mushaf.data.Quran.asset("whatsnew.json")) } catch (e: Exception) { null }
    }
    val items = remember(data) { data?.optJSONArray("items")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList() }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(data?.optString("title")?.ifEmpty { null } ?: "تحديث جديد", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("الإصدار ${com.shehta.mushaf.BuildConfig.VERSION_NAME}", fontSize = 13.sp) }
                items(items) { Text("• $it", fontSize = 15.sp, lineHeight = 24.sp) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("تمام") } },
    )
}
