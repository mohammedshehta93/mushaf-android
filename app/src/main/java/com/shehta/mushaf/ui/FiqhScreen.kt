package com.shehta.mushaf.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.shehta.mushaf.data.Fiqh
import com.shehta.mushaf.data.FiqhBook
import com.shehta.mushaf.data.Hadith
import com.shehta.mushaf.data.Prefs
import com.shehta.mushaf.data.QuizQuestion
import com.shehta.mushaf.data.ar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Learning fiqh from its evidences: «بلوغ المرام» by Ibn Hajar — the hadiths on which rulings are built,
 * arranged by the chapters of fiqh, with the editor's grading of every hadith.
 */
@Composable
fun FiqhScreen(prefs: Prefs, onBack: () -> Unit) {
    val c = mushafColors(prefs.theme)
    val books by produceState<List<FiqhBook>?>(null) { value = withContext(Dispatchers.IO) { Fiqh.books() } }
    var tab by remember { mutableIntStateOf(0) }
    var openBook by remember { mutableStateOf<Int?>(null) }
    var openHadith by remember { mutableStateOf<List<Hadith>?>(null) }
    var openIndex by remember { mutableIntStateOf(0) }
    var quizBook by remember { mutableStateOf<Int?>(null) }

    BackHandler {
        when {
            openHadith != null -> openHadith = null
            quizBook != null -> quizBook = null
            openBook != null -> openBook = null
            else -> onBack()
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = c.bar, contentColor = c.onBar) {
                Column(Modifier.statusBarsPadding()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                        IconButton(onClick = {
                            when {
                                openHadith != null -> openHadith = null
                                quizBook != null -> quizBook = null
                                openBook != null -> openBook = null
                                else -> onBack()
                            }
                        }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
                        Column(Modifier.weight(1f)) {
                            Text("تعلّم الفقه بأدلته", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                            Text("بلوغ المرام من أدلة الأحكام — ابن حجر", fontSize = 12.sp, color = c.onBar.copy(alpha = 0.8f))
                        }
                    }
                    if (openBook == null && openHadith == null && quizBook == null) {
                        ScrollableTabRow(selectedTabIndex = tab, containerColor = c.bar, contentColor = c.onBar, edgePadding = 8.dp) {
                            listOf("الأبواب", "درس اليوم", "اختبر نفسك", "المفضلة", "بحث").forEachIndexed { i, t ->
                                Tab(tab == i, { tab = i }, text = { Text(t) })
                            }
                        }
                    }
                }
            }
            val bs = books
            Box(Modifier.weight(1f).navigationBarsPadding()) {
                when {
                    bs == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    openHadith != null -> HadithPager(openHadith!!, openIndex, prefs, c)
                    quizBook != null -> QuizView(quizBook!!, bs, prefs, c) { quizBook = null }
                    openBook != null -> HadithList(bs[openBook!!].hadiths, prefs, c) { list, i -> openHadith = list; openIndex = i }
                    tab == 0 -> BookList(bs, prefs, c) { openBook = it }
                    tab == 1 -> DailyLesson(prefs, c) { list, i -> openHadith = list; openIndex = i }
                    tab == 2 -> QuizBooks(bs, prefs, c) { quizBook = it }
                    tab == 3 -> {
                        val fav = Fiqh.all().filter { it.key in prefs.fiqhFavorites || it.key in prefs.fiqhMemorized }
                        if (fav.isEmpty()) Empty("أضف أحاديث إلى المفضلة أو علّمها «محفوظ» لتجدها هنا.", c)
                        else HadithList(fav, prefs, c) { list, i -> openHadith = list; openIndex = i }
                    }
                    else -> FiqhSearch(prefs, c) { list, i -> openHadith = list; openIndex = i }
                }
            }
        }
    }
}

@Composable
private fun Empty(t: String, c: MushafColors) = Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
    Text(t, textAlign = TextAlign.Center, color = c.muted, fontSize = 16.sp, lineHeight = 26.sp)
}

@Composable
private fun BookList(books: List<FiqhBook>, prefs: Prefs, c: MushafColors, onOpen: (Int) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            Text("ابدأ بكتاب الطهارة ثم الصلاة؛ اقرأ كل حديث وتأمّل الحكم المستفاد منه، وعلّم ما حفظته. " +
                "هذه الأداة لتعلّم أدلة الأحكام، ولمسائلك الخاصة اسأل أهل العلم.",
                fontSize = 13.sp, color = c.muted, lineHeight = 21.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        }
        items(books.size) { i ->
            val b = books[i]
            val read = b.hadiths.count { it.key in prefs.fiqhRead }
            val mem = b.hadiths.count { it.key in prefs.fiqhMemorized }
            Row(Modifier.fillMaxWidth().clickable { onOpen(i) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).background(c.frameFill, CircleShape), contentAlignment = Alignment.Center) {
                    Text((i + 1).ar(), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("${b.hadiths.size.ar()} حديثًا · قرأت ${read.ar()} · حفظت ${mem.ar()}", fontSize = 12.sp, color = c.muted)
                    LinearProgressIndicator(progress = { read.toFloat() / b.hadiths.size }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun HadithList(list: List<Hadith>, prefs: Prefs, c: MushafColors, onOpen: (List<Hadith>, Int) -> Unit) {
    LazyColumn {
        items(list.size) { i ->
            val h = list[i]
            Row(Modifier.fillMaxWidth().clickable { onOpen(list, i) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (h.key in prefs.fiqhRead) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null,
                    tint = if (h.key in prefs.fiqhRead) c.gharib else c.muted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("حديث ${h.number.ar()}", fontSize = 12.sp, color = c.muted, modifier = Modifier.weight(1f))
                        GradeBadge(h.grade)
                    }
                    Text(h.prophetWords.ifBlank { h.matn }, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 26.sp)
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
fun GradeBadge(g: String) {
    if (g.isBlank()) return
    val col = when {
        g.startsWith("صحيح") -> Color(0xFF16705A)
        g.startsWith("حسن") -> Color(0xFF1E6FD9)
        else -> Color(0xFFC62828)
    }
    Text(g, fontSize = 11.sp, color = col, modifier = Modifier.border(1.dp, col, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 1.dp))
}

private fun styledMatn(m: String, c: MushafColors): AnnotatedString = buildAnnotatedString {
    var depth = 0
    for (ch in m) {
        when (ch) {
            '{' -> { depth++; append('«') }
            '}' -> { depth = (depth - 1).coerceAtLeast(0); append('»') }
            else -> if (depth > 0) withStyle(SpanStyle(color = c.gharib, fontWeight = FontWeight.SemiBold)) { append(ch) } else append(ch)
        }
    }
}

@Composable
private fun HadithPager(list: List<Hadith>, start: Int, prefs: Prefs, c: MushafColors) {
    var i by remember { mutableIntStateOf(start.coerceIn(0, list.size - 1)) }
    val h = list[i]
    val context = LocalContext.current
    var showNotes by remember(i) { mutableStateOf(false) }
    LaunchedEffect(h.key) { prefs.markFiqhRead(h.key) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${Fiqh.books()[h.book].title} · حديث ${h.number.ar()}", fontSize = 13.sp, color = c.muted, modifier = Modifier.weight(1f))
                GradeBadge(h.grade)
            }
            Text(styledMatn(h.matn, c), fontSize = (prefs.tafsirFontSize + 2).sp, lineHeight = ((prefs.tafsirFontSize + 2) * 1.8f).sp,
                textAlign = TextAlign.Justify, modifier = Modifier.padding(vertical = 12.dp))
            if (h.takhrij.isNotBlank()) Text(h.takhrij, fontSize = 14.sp, color = c.muted)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val mem = h.key in prefs.fiqhMemorized
                OutlinedButton(onClick = { prefs.toggleFiqhMemorized(h.key) }) { Text(if (mem) "✓ محفوظ" else "علّمه محفوظًا") }
                IconButton(onClick = { prefs.toggleFiqhFavorite(h.key) }) {
                    Icon(if (h.key in prefs.fiqhFavorites) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "المفضلة", tint = c.ribbon)
                }
                IconButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("حديث", h.matn.replace("{", "«").replace("}", "»") + " " + h.takhrij))
                    Toast.makeText(context, "تم النسخ", Toast.LENGTH_SHORT).show()
                }) { Icon(Icons.Filled.ContentCopy, "نسخ") }
            }
            if (h.notes.isNotEmpty()) {
                TextButton(onClick = { showNotes = !showNotes }) { Text(if (showNotes) "إخفاء تخريج المحقق" else "تخريج المحقق ودرجة الحديث") }
                if (showNotes) h.notes.forEachIndexed { n, t ->
                    Text("${(n + 1).ar()}- $t", fontSize = 14.sp, lineHeight = 23.sp, color = c.muted, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { if (i > 0) i-- }, enabled = i > 0, modifier = Modifier.weight(1f)) { Text("السابق") }
            Text("${(i + 1).ar()} / ${list.size.ar()}", fontSize = 13.sp)
            OutlinedButton(onClick = { if (i < list.size - 1) i++ }, enabled = i < list.size - 1, modifier = Modifier.weight(1f)) { Text("التالي") }
        }
    }
}

@Composable
private fun DailyLesson(prefs: Prefs, c: MushafColors, onOpen: (List<Hadith>, Int) -> Unit) {
    val lesson = remember(prefs.fiqhRead.size, prefs.fiqhDaily) { Fiqh.nextLesson(prefs.fiqhRead, prefs.fiqhDaily) }
    val total = Fiqh.all().size
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("درس اليوم", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("قرأت ${prefs.fiqhRead.size.ar()} من ${total.ar()} حديثًا، وحفظت ${prefs.fiqhMemorized.size.ar()}.", color = c.muted, fontSize = 14.sp)
        LinearProgressIndicator(progress = { prefs.fiqhRead.size.toFloat() / total }, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).height(8.dp))
        Text("عدد الأحاديث في الدرس اليومي", fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 3, 5, 10).forEach { n ->
                androidx.compose.material3.FilterChip(selected = prefs.fiqhDaily == n, onClick = { prefs.updateFiqhDaily(n) }, label = { Text(n.ar()) })
            }
        }
        if (lesson.isEmpty()) {
            Text("أتممت الكتاب كاملًا، ما شاء الله! راجع محفوظك من «المفضلة».", modifier = Modifier.padding(top = 20.dp))
        } else {
            Text("الباب: ${Fiqh.books()[lesson[0].book].title}", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp))
            lesson.forEachIndexed { i, h ->
                Surface(shape = RoundedCornerShape(14.dp), color = c.frameFill, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clickable { onOpen(lesson, i) }) {
                    Column(Modifier.padding(14.dp)) {
                        Row { Text("حديث ${h.number.ar()}", fontSize = 12.sp, color = c.muted, modifier = Modifier.weight(1f)); GradeBadge(h.grade) }
                        Text(h.prophetWords.ifBlank { h.matn }, fontSize = 17.sp, lineHeight = 28.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Button(onClick = { onOpen(lesson, 0) }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) { Text("ابدأ الدرس") }
        }
    }
}

@Composable
private fun QuizBooks(books: List<FiqhBook>, prefs: Prefs, c: MushafColors, onStart: (Int) -> Unit) {
    LazyColumn {
        item {
            Text("أسئلة من نص الأحاديث نفسها: راوي الحديث، إكمال لفظ النبي ﷺ، ودرجة الحديث عند المحقق.",
                fontSize = 13.sp, color = c.muted, modifier = Modifier.padding(16.dp), lineHeight = 21.sp)
        }
        items(books.size) { i ->
            val best = prefs.fiqhQuizBest["$i"]
            Row(Modifier.fillMaxWidth().clickable { onStart(i) }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(books[i].title, fontSize = 17.sp, modifier = Modifier.weight(1f))
                Text(if (best != null) "أفضل نتيجة ${best.ar()}٪" else "ابدأ", fontSize = 13.sp, color = if (best != null) c.gharib else c.muted)
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun QuizView(book: Int, books: List<FiqhBook>, prefs: Prefs, c: MushafColors, onDone: () -> Unit) {
    var seed by remember { mutableStateOf(System.currentTimeMillis()) }
    val qs = remember(book, seed) { Fiqh.quiz(book, 10, seed) }
    var i by remember(seed) { mutableIntStateOf(0) }
    var picked by remember(seed, i) { mutableStateOf<Int?>(null) }
    var score by remember(seed) { mutableIntStateOf(0) }
    if (qs.isEmpty()) { Empty("لا تكفي أحاديث هذا الباب لاختبار.", c); return }
    if (i >= qs.size) {
        val pct = score * 100 / qs.size
        LaunchedEffect(seed) { prefs.saveQuizScore(book, pct) }
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("${score.ar()} / ${qs.size.ar()}", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = c.gharib)
            Text(if (pct >= 80) "ممتاز، بارك الله فيك" else if (pct >= 50) "جيد، راجع الباب مرة أخرى" else "اقرأ أحاديث الباب ثم أعد المحاولة", fontSize = 17.sp)
            Button(onClick = { seed = System.currentTimeMillis() }, modifier = Modifier.padding(top = 20.dp)) { Text("اختبار جديد") }
            TextButton(onClick = onDone) { Text("رجوع") }
        }
        return
    }
    val q: QuizQuestion = qs[i]
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("${books[book].title} · سؤال ${(i + 1).ar()} من ${qs.size.ar()}", fontSize = 13.sp, color = c.muted)
        LinearProgressIndicator(progress = { (i + 1f) / qs.size }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        Text(q.prompt, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(q.context, fontSize = 17.sp, lineHeight = 29.sp, modifier = Modifier.padding(vertical = 10.dp))
        q.options.forEachIndexed { k, o ->
            val p = picked
            val bg = when {
                p == null -> c.frameFill
                k == q.answer -> Color(0x3316705A)
                k == p -> Color(0x33C62828)
                else -> c.frameFill
            }
            Surface(shape = RoundedCornerShape(12.dp), color = bg, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                .clickable(enabled = p == null) { picked = k; if (k == q.answer) score++ }) {
                Text(o, fontSize = 16.sp, modifier = Modifier.padding(14.dp), lineHeight = 25.sp)
            }
        }
        if (picked != null) {
            Text(if (picked == q.answer) "✓ إجابة صحيحة" else "✗ الإجابة الصحيحة مظللة بالأخضر", color = if (picked == q.answer) c.gharib else c.mistake,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            Text(q.explain, fontSize = 14.sp, color = c.muted, lineHeight = 22.sp, modifier = Modifier.padding(vertical = 6.dp))
            Button(onClick = { i++ }, modifier = Modifier.fillMaxWidth()) { Text(if (i == qs.size - 1) "النتيجة" else "التالي") }
        }
    }
}

@Composable
private fun FiqhSearch(prefs: Prefs, c: MushafColors, onOpen: (List<Hadith>, Int) -> Unit) {
    var q by remember { mutableStateOf("") }
    val res by produceState(emptyList<Hadith>(), q) { value = withContext(Dispatchers.Default) { Fiqh.search(q) } }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(value = q, onValueChange = { q = it }, placeholder = { Text("ابحث في الأحاديث بدون تشكيل") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(14.dp))
        if (q.length >= 2) Text("${res.size.ar()} نتيجة", fontSize = 13.sp, color = c.muted, modifier = Modifier.padding(horizontal = 16.dp))
        Box(Modifier.weight(1f)) { HadithList(res, prefs, c, onOpen) }
    }
}
