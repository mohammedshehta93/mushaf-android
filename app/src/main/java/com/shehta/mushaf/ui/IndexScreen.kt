package com.shehta.mushaf.ui

import androidx.compose.runtime.saveable.rememberSaveable

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shehta.mushaf.data.ArabicText
import com.shehta.mushaf.data.Prefs
import com.shehta.mushaf.data.Quran
import com.shehta.mushaf.data.ar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lets the bottom bar open the index directly on a given tab. */
object IndexTabs { var request = -1 }

@Composable
fun IndexScreen(prefs: Prefs, currentPage: Int, onBack: () -> Unit, onGo: (Int, Selection?) -> Unit) {
    BackHandler(onBack = onBack)
    val colors = mushafColors(prefs.theme)
    var tab by remember { mutableIntStateOf(if (IndexTabs.request >= 0) IndexTabs.request else 0) }
    LaunchedEffect(Unit) { IndexTabs.request = -1 }
    var filter by remember { mutableStateOf("") }
    val tabs = listOf("السور", "الأجزاء", "الأحزاب", "العلامات", "التدبّر")

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = colors.bar, contentColor = colors.onBar) {
                Column(Modifier.statusBarsPadding()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
                        Text("الفهرس", fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.weight(1f))
                        Text("أنت في صفحة ${currentPage.ar()}", fontSize = 13.sp, modifier = Modifier.padding(end = 12.dp))
                    }
                    ScrollableTabRow(selectedTabIndex = tab, containerColor = colors.bar, contentColor = colors.onBar, edgePadding = 6.dp) {
                        tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, fontSize = 15.sp) }) }
                    }
                }
            }
            if (tab == 0) {
                OutlinedTextField(
                    value = filter, onValueChange = { filter = it },
                    placeholder = { Text("اسم السورة أو رقم الصفحة") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            Box(Modifier.weight(1f).navigationBarsPadding()) {
                when (tab) {
                    0 -> SurahList(filter, currentPage, colors, onGo)
                    1 -> JuzList(currentPage, colors, onGo)
                    2 -> HizbList(currentPage, colors, onGo)
                    3 -> BookmarkList(prefs, colors, onGo)
                    else -> NotesList(prefs, colors, onGo)
                }
            }
        }
    }
}

@Composable
private fun NumberBadge(n: Int, c: MushafColors, active: Boolean) {
    Box(
        Modifier.size(40.dp).background(if (active) c.gharib else c.frameFill, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text(n.ar(), fontSize = 15.sp, color = if (active) c.paper else c.ink, fontWeight = FontWeight.Bold) }
}

@Composable
private fun SurahList(filter: String, currentPage: Int, c: MushafColors, onGo: (Int, Selection?) -> Unit) {
    val q = ArabicText.normalize(filter.trim())
    val latin = filter.trim().map { if (it in '٠'..'٩') '0' + (it - '٠') else it }.joinToString("")
    val pageJump = latin.toIntOrNull()?.takeIf { it in 1..Quran.PAGES }
    val list = remember(q) {
        Quran.surahs.filter { q.isEmpty() || ArabicText.normalize(it.name).contains(q) || it.n.toString() == latin }
    }
    val currentSurah = Quran.surahOfPage(currentPage).n
    val state = rememberLazyListState(initialFirstVisibleItemIndex = if (q.isEmpty()) (currentSurah - 3).coerceAtLeast(0) else 0)
    LazyColumn(state = state) {
        if (pageJump != null) item {
            Row(Modifier.fillMaxWidth().clickable { onGo(pageJump, null) }.padding(16.dp)) {
                Text("انتقال إلى صفحة ${pageJump.ar()}", fontWeight = FontWeight.Bold, color = c.gharib, fontSize = 17.sp)
            }
            HorizontalDivider()
        }
        items(list.size) { i ->
            val s = list[i]
            Row(
                Modifier.fillMaxWidth().clickable { onGo(s.page, null) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NumberBadge(s.n, c, s.n == currentSurah)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("سورة ${s.name}", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("${s.type} · ${s.ayahs.ar()} آية", fontSize = 13.sp, color = c.muted)
                }
                Text(s.page.ar(), fontSize = 15.sp, color = c.muted, textAlign = TextAlign.End)
            }
            HorizontalDivider(Modifier.padding(start = 70.dp))
        }
    }
}

@Composable
private fun JuzList(currentPage: Int, c: MushafColors, onGo: (Int, Selection?) -> Unit) {
    val cur = Quran.juzOfPage(currentPage)
    LazyColumn(state = rememberLazyListState((cur - 3).coerceAtLeast(0))) {
        items(Quran.juz.size) { i ->
            val j = Quran.juz[i]
            Row(
                Modifier.fillMaxWidth().clickable { onGo(j.page, null) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NumberBadge(j.n, c, j.n == cur)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("الجزء ${juzName(j.n)}", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("${Quran.surahs[j.s - 1].name} ${j.a.ar()}", fontSize = 13.sp, color = c.muted)
                }
                Text(j.page.ar(), fontSize = 15.sp, color = c.muted)
            }
            HorizontalDivider(Modifier.padding(start = 70.dp))
        }
    }
}

@Composable
private fun HizbList(currentPage: Int, c: MushafColors, onGo: (Int, Selection?) -> Unit) {
    val cur = Quran.rubOfPage(currentPage).n
    LazyColumn(state = rememberLazyListState((cur - 4).coerceAtLeast(0))) {
        items(Quran.rub.size) { i ->
            val r = Quran.rub[i]
            val hizb = (r.n - 1) / 4 + 1
            val q = (r.n - 1) % 4
            Row(
                Modifier.fillMaxWidth().clickable { onGo(r.page, Selection(r.s, r.a)) }
                    .padding(start = if (q == 0) 16.dp else 32.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (q == 0) NumberBadge(hizb, c, r.n == cur) else Text(listOf("", "¼", "½", "¾")[q], fontSize = 20.sp, color = if (r.n == cur) c.gharib else c.marker, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (q == 0) "الحزب ${hizb.ar()}" else "${listOf("", "ربع", "نصف", "ثلاثة أرباع")[q]} الحزب ${hizb.ar()}", fontSize = 16.sp, fontWeight = if (q == 0) FontWeight.SemiBold else FontWeight.Normal)
                    Text(Quran.ayahText(r.s, r.a).take(48) + "…", fontFamily = hafsFamily, fontSize = 15.sp, color = c.muted, maxLines = 1)
                }
                Text(r.page.ar(), fontSize = 14.sp, color = c.muted)
            }
        }
    }
}

@Composable
private fun BookmarkList(prefs: Prefs, c: MushafColors, onGo: (Int, Selection?) -> Unit) {
    val list = prefs.bookmarks
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("لا توجد علامات بعد.\nاضغط مطولًا على أي آية واختر «علامة توقف هنا»، أو استخدم زر العلامة في الشريط العلوي.",
                textAlign = TextAlign.Center, color = c.muted, fontSize = 16.sp, lineHeight = 26.sp)
        }
        return
    }
    val fmt = remember { SimpleDateFormat("d MMMM، h:mm a", Locale("ar")) }
    LazyColumn {
        items(list.size) { i ->
            val b = list[i]
            Row(
                Modifier.fillMaxWidth().clickable { onGo(b.page, Selection(b.s, b.a)) }.padding(start = 16.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(width = 14.dp, height = 34.dp).background(c.ribbon))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("سورة ${Quran.surahs[b.s - 1].name} · الآية ${b.a.ar()}", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("صفحة ${b.page.ar()} · ${fmt.format(Date(b.time))}", fontSize = 13.sp, color = c.muted)
                }
                IconButton(onClick = { prefs.removeBookmark(b.page) }) { Icon(Icons.Filled.Delete, "حذف", tint = c.muted) }
            }
            HorizontalDivider()
        }
    }
}

private fun juzName(n: Int) = listOf(
    "الأول", "الثاني", "الثالث", "الرابع", "الخامس", "السادس", "السابع", "الثامن", "التاسع", "العاشر",
    "الحادي عشر", "الثاني عشر", "الثالث عشر", "الرابع عشر", "الخامس عشر", "السادس عشر", "السابع عشر", "الثامن عشر", "التاسع عشر", "العشرون",
    "الحادي والعشرون", "الثاني والعشرون", "الثالث والعشرون", "الرابع والعشرون", "الخامس والعشرون", "السادس والعشرون", "السابع والعشرون", "الثامن والعشرون", "التاسع والعشرون", "الثلاثون",
)[n - 1]
