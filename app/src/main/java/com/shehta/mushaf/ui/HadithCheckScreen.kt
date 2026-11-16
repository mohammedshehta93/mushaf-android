package com.shehta.mushaf.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shehta.mushaf.data.HadithDb
import com.shehta.mushaf.data.Quran
import com.shehta.mushaf.data.ar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** One entry of «مشهور لا يصح» (assets/quran/hadith/not_sahih.json, built by tools/hadith_check.py). */
private class NotSahih(val text: String, val src: String, val ruling: String, val verdict: Int, val altText: String, val altSrc: String)

private fun loadNotSahih(): List<NotSahih> = try {
    val a = JSONArray(Quran.asset("hadith/not_sahih.json"))
    List(a.length()) { i ->
        val o = a.getJSONObject(i); val alt = o.optJSONObject("alt")
        NotSahih(o.getString("t"), o.optString("src"), o.optString("r"), o.optInt("c", 1), alt?.optString("t") ?: "", alt?.optString("src") ?: "")
    }
} catch (e: Exception) { emptyList() }

private val VERDICT_COLORS = listOf(Color(0xFF2E7D32), Color(0xFFE08A00), Color(0xFFD84315), Color(0xFFC62828))

private fun dorar(context: Context, text: String) {
    val url = "https://dorar.net/hadith/search?q=" + Uri.encode(text.take(120))
    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { }
}

private fun copy(context: Context, text: String) {
    try {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("حديث", text))
        Toast.makeText(context, "نُسخ", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) { }
}

@Composable
private fun VerdictChip(v: Int, label: String = HadithDb.verdictNames.getOrElse(v) { "لا حكم في البيانات" }) {
    val col = VERDICT_COLORS.getOrElse(v) { Color.Gray }
    Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(col, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HadithCheckScreen(c: MushafColors, onBack: () -> Unit) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var q by rememberSaveable { mutableStateOf("") }
    var book by rememberSaveable { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(HadithDb.ready) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<HadithDb.Result?>(null) }
    val notSahih = remember { loadNotSahih() }

    LaunchedEffect(Unit) {
        if (HadithDb.opener == null) { val app = context.applicationContext; HadithDb.opener = { app.assets.open("quran/$it") } }
        withContext(Dispatchers.IO) { try { HadithDb.load() } catch (e: Exception) { } }
        ready = HadithDb.ready
    }
    LaunchedEffect(q, book, ready) {
        if (!ready) return@LaunchedEffect
        if (HadithDb.plain(q).length < 3) { result = null; return@LaunchedEffect }
        delay(350)
        busy = true
        result = withContext(Dispatchers.Default) { HadithDb.search(q, book) }
        busy = false
    }

    ScreenScaffold("تحقّق من حديث", c, onBack, subtitle = "الكتب الستة وموطأ مالك مع أحكام العلماء") {
        TabRow(tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text("ابحث عن حديث") })
            Tab(tab == 1, { tab = 1 }, text = { Text("مشهور لا يصح") })
        }
        if (tab == 0) LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("اكتب كلمات من الحديث") }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions())
                FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(book == null, { book = null }, { Text("الكل") })
                    for (b in HadithDb.books) FilterChip(book == b.id, { book = b.id }, { Text(b.name) })
                }
                Text("الحكم المذكور لهذه الرواية بإسنادها كما حكم عليها العلماء (الألباني وغيره)؛ أحاديث البخاري ومسلم كلها صحيحة. " +
                    "قد يَرِد الحديث نفسه بإسناد آخر بحكم آخر، فإن لم تجده أو احتجت التفصيل فابحث في «الدرر السنية».",
                    fontSize = 12.sp, color = c.muted, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp))
            }
            when {
                !ready -> item {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.padding(end = 12.dp)); Text("يجهّز البحث في نحو ٣٦ ألف حديث…")
                    }
                }
                busy && result == null -> item { CircularProgressIndicator(Modifier.padding(16.dp)) }
                result == null -> item {
                    Card2(c) {
                        Text("كيف تتحقق من حديث وصلك؟", fontWeight = FontWeight.Bold, color = c.gharib)
                        Text("اكتب كلمتين أو ثلاثًا مميزة من نصه (بدون تشكيل). ستظهر الروايات في الكتب الستة والموطأ بأرقامها وحكم كل رواية. " +
                            "وفي التبويب الثاني أحاديث مشهورة على الألسنة لا تصح.", fontSize = 14.sp, lineHeight = 23.sp)
                    }
                }
                result!!.total == 0 -> item {
                    Card2(c) {
                        Text("لم يُعثر عليه في الكتب الستة ولا الموطأ.", fontWeight = FontWeight.Bold)
                        Text("قد يكون بلفظ آخر (جرّب كلمات أقل)، أو في كتب أخرى، أو لا أصل له. ابحث عنه في الموسوعة الحديثية بالدرر السنية:",
                            fontSize = 14.sp, lineHeight = 23.sp, color = c.muted)
                        TextButton(onClick = { dorar(context, q) }) { Text("ابحث في الدرر السنية ›") }
                    }
                }
                else -> {
                    item { Text("${result!!.total.ar()} رواية" + if (result!!.total > result!!.hits.size) " — تظهر أول ${result!!.hits.size.ar()}؛ زد كلمات للتضييق" else "", fontSize = 13.sp, color = c.muted) }
                    items(result!!.hits) { h -> HitCard(c, h) }
                    item { TextButton(onClick = { dorar(context, q) }) { Text("التفصيل في الدرر السنية ›") } }
                }
            }
        } else LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("أحاديث يتداولها الناس وهي لا تصح عن النبي ﷺ؛ فلا تنسبها إليه ولا تنشرها. وتحت بعضها ما صحّ في معناها.",
                    fontSize = 14.sp, lineHeight = 23.sp, color = c.muted)
            }
            items(notSahih) { n ->
                Card2(c) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VerdictChip(n.verdict.coerceAtLeast(1), if (n.src.isEmpty()) "لا يصح" else HadithDb.verdictNames[n.verdict.coerceIn(1, 3)])
                        if (n.src.isNotEmpty()) Text("  " + n.src, fontSize = 12.sp, color = c.muted)
                    }
                    Text("«${n.text}»", fontSize = 18.sp, lineHeight = 32.sp, modifier = Modifier.padding(vertical = 6.dp))
                    Text(n.ruling, fontSize = 13.sp, color = c.mistake, lineHeight = 21.sp)
                    if (n.altText.isNotEmpty()) {
                        Text("الصحيح في معناه:", fontWeight = FontWeight.Bold, color = VERDICT_COLORS[0], fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                        Text(n.altText, fontSize = 16.sp, lineHeight = 29.sp)
                        Text(n.altSrc, fontSize = 12.sp, color = c.muted)
                    }
                    TextButton(onClick = { dorar(context, n.text) }) { Text("حكم العلماء بالتفصيل (الدرر السنية) ›") }
                }
            }
        }
    }
}

@Composable
private fun HitCard(c: MushafColors, h: HadithDb.Hit) {
    val context = LocalContext.current
    var open by remember(h) { mutableStateOf(false) }
    Card2(c, Modifier.clickable { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VerdictChip(h.verdict)
            Text("  ${h.book.name} (${h.number})", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.gharib)
        }
        Text(if (open || h.start == 0) h.text else "… " + h.text.substring(h.start).trimStart(), fontSize = 16.sp, lineHeight = 29.sp,
            maxLines = if (open) Int.MAX_VALUE else 7, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp))
        if (h.rulings.isNotEmpty()) Text("الحكم: ${h.rulings}", fontSize = 13.sp, color = c.muted, lineHeight = 21.sp, modifier = Modifier.padding(top = 4.dp))
        else if (h.book.id == "bukhari" || h.book.id == "muslim") Text("الحكم: صحيح (من أحاديث ${h.book.name})", fontSize = 13.sp, color = c.muted)
        Row {
            TextButton(onClick = { copy(context, "${h.text}\n— ${h.book.name} (${h.number})" + if (h.rulings.isNotEmpty()) "، ${h.rulings}" else "") }) { Text("نسخ") }
            if (!open) TextButton(onClick = { open = true }) { Text("النص كاملًا") }
        }
    }
}
