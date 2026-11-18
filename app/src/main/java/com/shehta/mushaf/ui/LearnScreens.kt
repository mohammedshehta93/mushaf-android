package com.shehta.mushaf.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shehta.mushaf.data.Learn
import com.shehta.mushaf.data.Lesson
import com.shehta.mushaf.data.Poem
import com.shehta.mushaf.data.Quran
import com.shehta.mushaf.data.Sheikh
import com.shehta.mushaf.data.TajweedExample
import com.shehta.mushaf.data.ar

internal fun openUrl(context: Context, url: String) {
    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    catch (e: Exception) { Toast.makeText(context, "لا يوجد تطبيق لفتح الرابط", Toast.LENGTH_SHORT).show() }
}

@Composable
private fun GroupTitle(t: String, c: MushafColors) =
    Text(t, fontWeight = FontWeight.Bold, color = c.gharib, fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))

/** One big, clearly labelled action row used in the choice sheets. */
@Composable
private fun ActionRow(icon: ImageVector, title: String, sub: String, c: MushafColors, onClick: () -> Unit) {
    Card2(c, Modifier.padding(vertical = 4.dp).clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = c.marker)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = c.muted)
            }
        }
    }
}

// =====================================================================================================  الشيوخ والدروس

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheikhsScreen(c: MushafColors, onBack: () -> Unit, onPodcast: (String) -> Unit) {
    val context = LocalContext.current
    var open by remember { mutableStateOf<Sheikh?>(null) }
    ScreenScaffold("الشيوخ والدروس", c, onBack, subtitle = "دروس صوتية داخل التطبيق، ومعها الفيديو على يوتيوب") {
        LazyColumn(state = keptListState("sheikhs"), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { Text("اختر الشيخ لتسمع دروسه صوتًا داخل التطبيق، أو تشاهدها فيديو على يوتيوب. البحث في المواعظ والمقاطع القصيرة مقيّد بـ«بدون موسيقى».", fontSize = 13.sp, color = c.muted) }
            Learn.sheikhs.forEach { g ->
                item { GroupTitle(g.title, c) }
                items(g.items, key = { it.name }) { s ->
                    Card2(c, Modifier.clickable { open = s }) {
                        Text(s.name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        if (s.desc.isNotEmpty()) Text(s.desc, fontSize = 13.sp, color = c.muted)
                    }
                }
            }
        }
    }
    open?.let { s ->
        ModalBottomSheet(onDismissRequest = { open = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Text(s.name, fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.padding(bottom = 6.dp))
                // audio first (played inside the app, resumes where you stopped); every lesson also has «فيديو على يوتيوب»
                ActionRow(Icons.Filled.Headphones, "استمع لدروسه (صوت)", "داخل التطبيق، ويكمل من حيث توقفت — ولكل درس زر «شاهده فيديو»", c) { open = null; onPodcast(s.query) }
                Text("أو شاهد على يوتيوب", fontSize = 13.sp, color = c.muted, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                ActionRow(Icons.AutoMirrored.Filled.PlaylistPlay, "دروس وسلاسل منظّمة (فيديو)", "قوائم تشغيل مرتبة (شرح كتاب أو سلسلة كاملة)", c) { openUrl(context, Learn.playlists(s.query)) }
                ActionRow(Icons.Filled.VideoLibrary, "محاضرات ومواعظ (فيديو)", "خطب ودروس منفردة", c) { openUrl(context, Learn.lectures(s.query)) }
                ActionRow(Icons.Filled.SmartDisplay, "مقاطع قصيرة", "Shorts — بدون موسيقى", c) { openUrl(context, Learn.shorts(s.query)) }
                ActionRow(Icons.Filled.Subscriptions, "القناة على يوتيوب", "ابحث عن القناة الرسمية واشترك فيها", c) { openUrl(context, Learn.channel(s.query)) }
            }
        }
    }
}

// =====================================================================================================  المتون والمنظومات

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoemsScreen(c: MushafColors, lp: com.shehta.mushaf.data.ListenPrefs, onBack: () -> Unit) {
    val context = LocalContext.current
    var open by remember { mutableStateOf<Poem?>(null) }
    ScreenScaffold("المتون والمنظومات", c, onBack, subtitle = "قصائد تعلّم العقيدة والآداب والتجويد والفقه") {
        LazyColumn(state = keptListState("poems"), Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { Text("مرتبة حسب العلم. اضغط على المنظومة لتسمعها داخل التطبيق أو تسمع شرحها أو تقرأ نصها. 🎧 = لها تسجيل صوتي.", fontSize = 13.sp, color = c.muted) }
            Learn.poems.forEach { g ->
                item { GroupTitle(g.title, c) }
                items(g.items, key = { it.name + it.author }) { p ->
                    Card2(c, Modifier.clickable { open = p }) {
                        Text(p.name + if (p.tracks.isNotEmpty()) "  🎧" else "", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Text(listOf(p.author, p.desc).filter { it.isNotEmpty() }.joinToString(" — "), fontSize = 13.sp, color = c.muted)
                    }
                }
            }
        }
        if (com.shehta.mushaf.audio.AudioEngine.state.active) MiniPlayer(c, onOpen = {})
    }
    open?.let { p ->
        ModalBottomSheet(onDismissRequest = { open = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Text(p.name, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text(p.author, color = c.muted, modifier = Modifier.padding(bottom = 6.dp))
                if (p.tracks.isNotEmpty()) {
                    Card2(c, Modifier.padding(vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Headphones, null, tint = c.marker)
                            Text("  استمع للمنظومة", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        }
                        PoemTrackRows(c, lp, p)
                    }
                } else Text("لا يوجد تسجيل صوتي لهذه المنظومة بعد.", fontSize = 13.sp, color = c.muted, modifier = Modifier.padding(vertical = 6.dp))
                ActionRow(Icons.AutoMirrored.Filled.PlaylistPlay, "شرح المنظومة", "سلاسل شرح كاملة للمشايخ", c) { openUrl(context, Learn.playlists("شرح ${p.query}")) }
                ActionRow(Icons.AutoMirrored.Filled.MenuBook, "نص المنظومة", "ابحث عن النص المضبوط بالشكل", c) { openUrl(context, Learn.webSearch("نص ${p.query} مشكول")) }
            }
        }
    }
}

// =====================================================================================================  تعلّم التجويد

@Composable
fun TajweedCourseScreen(c: MushafColors, onBack: () -> Unit, onGo: (Int, Int) -> Unit) {
    val context = LocalContext.current
    val sp = remember { context.getSharedPreferences("mushaf", Context.MODE_PRIVATE) }
    var done by remember { mutableStateOf(sp.getStringSet("tajweedDone", emptySet())!!.toSet()) }
    var lessonIdx by rememberSaveable { mutableIntStateOf(-1) }
    var quiz by rememberSaveable { mutableStateOf(false) }
    val lessons = Learn.lessons

    fun markDone(id: String) { done = done + id; sp.edit().putStringSet("tajweedDone", done).apply() }

    val lesson = lessons.getOrNull(lessonIdx)
    when {
        lesson != null && quiz -> TajweedQuiz(c, lesson, onBack = { quiz = false })
        lesson != null -> LessonScreen(c, lesson, lessonIdx, lessons.size,
            onBack = { lessonIdx = -1 },
            onNext = { markDone(lesson.id); lessonIdx = if (lessonIdx + 1 < lessons.size) lessonIdx + 1 else -1 },
            onQuiz = { markDone(lesson.id); quiz = true }, onGo = onGo)
        else -> ScreenScaffold("تعلّم التجويد", c, onBack, subtitle = "شرح ميسّر برواية حفص — ${done.size.ar()} من ${lessons.size.ar()} دروس") {
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text("دروس قصيرة بالترتيب، وكل حكم معه أمثلة حقيقية من المصحف ملوّنة (اضغط المثال لفتح الآية)، واختبار في آخر الدرس. " +
                        "التجويد يُتلقّى بالمشافهة، فاستمع للقارئ وطبّق مع معلّم.", fontSize = 13.sp, color = c.muted)
                }
                items(lessons.size) { i ->
                    val l = lessons[i]
                    Card2(c, Modifier.clickable { lessonIdx = i; quiz = false }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(34.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                                Text((i + 1).ar(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(l.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                                if (l.quiz.isNotEmpty()) Text("فيه اختبار", fontSize = 12.sp, color = c.muted)
                            }
                            if (l.id in done) Icon(Icons.Filled.CheckCircle, "تمت القراءة", tint = c.gharib)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExampleWord(ex: TajweedExample, c: MushafColors, highlight: Color, size: Int = 24, onClick: (() -> Unit)? = null) {
    val txt = buildAnnotatedString {
        val a = ex.from.coerceIn(0, ex.text.length); val b = ex.to.coerceIn(a, ex.text.length)
        append(ex.text.substring(0, a))
        withStyle(SpanStyle(color = highlight)) { append(ex.text.substring(a, b)) }
        append(ex.text.substring(b))
    }
    Surface(shape = RoundedCornerShape(12.dp), color = c.paper, modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier) {
        Text(txt, fontFamily = hafsFamily, fontSize = size.sp, color = c.ink, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
    }
}

@Composable
private fun LessonScreen(c: MushafColors, l: Lesson, idx: Int, total: Int, onBack: () -> Unit, onNext: () -> Unit, onQuiz: () -> Unit, onGo: (Int, Int) -> Unit) {
    val context = LocalContext.current
    ScreenScaffold(l.title, c, onBack, subtitle = "الدرس ${(idx + 1).ar()} من ${total.ar()}") {
        LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(l.sections.size) { i ->
                val sec = l.sections[i]
                Card2(c) {
                    Text(sec.heading, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = c.gharib)
                    Text(withQuranFont(sec.text), fontSize = 17.sp, lineHeight = 32.sp, modifier = Modifier.padding(top = 4.dp))
                    sec.examples.forEach { rule ->
                        val exs = remember(rule) { Learn.examples(rule).take(6) }
                        if (exs.isNotEmpty()) {
                            Text("أمثلة من المصحف${if (sec.examples.size > 1) " — " + (Learn.ruleNames[rule] ?: "") else ""}:", fontSize = 13.sp, color = c.muted, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                            FlowRowCompat {
                                exs.forEach { ex -> ExampleWord(ex, c, c.mistake) { onGo(ex.s, ex.a) } }
                            }
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { openUrl(context, Learn.playlists("شرح ${l.title} تجويد")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null); Text("  استمع لشرح هذا الدرس")
                }
                Spacer(Modifier.height(6.dp))
                if (l.quiz.isNotEmpty()) Button(onClick = onQuiz, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Quiz, null); Text("  اختبر نفسك") }
                TextButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text(if (idx + 1 < total) "الدرس التالي ›" else "انتهت الدروس ✓") }
            }
        }
    }
}

/** Quran quotations ﴿…﴾ inside lesson prose are drawn with the Mushaf (Hafs) font so every mark renders. */
private fun withQuranFont(t: String) = buildAnnotatedString {
    var i = 0
    while (i < t.length) {
        val a = t.indexOf('﴿', i)
        val b = if (a >= 0) t.indexOf('﴾', a) else -1
        if (a < 0 || b < 0) { append(t.substring(i)); break }
        append(t.substring(i, a))
        withStyle(SpanStyle(fontFamily = hafsFamily, fontSize = 20.sp)) { append(t.substring(a, b + 1)) }
        i = b + 1
    }
}

/** Wraps children onto several lines (simple flow layout, RTL aware through the parent direction). */
@Composable
private fun FlowRowCompat(content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content) { measurables, constraints ->
        val gap = 6.dp.roundToPx()
        val ps = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val rows = ArrayList<MutableList<androidx.compose.ui.layout.Placeable>>()
        var w = 0
        for (p in ps) {
            if (rows.isEmpty() || w + p.width > constraints.maxWidth) { rows += mutableListOf(p); w = p.width + gap } else { rows.last() += p; w += p.width + gap }
        }
        val height = rows.sumOf { r -> r.maxOf { it.height } + gap }
        layout(constraints.maxWidth, height) {
            var y = 0
            for (r in rows) {
                var x = 0
                for (p in r) { p.placeRelative(x, y); x += p.width + gap }
                y += r.maxOf { it.height } + gap
            }
        }
    }
}

@Composable
private fun TajweedQuiz(c: MushafColors, l: Lesson, onBack: () -> Unit) {
    val questions = remember(l.id) {
        l.quiz.flatMap { r -> Learn.examples(r).shuffled().take(4) }.shuffled().take(10)
    }
    var i by rememberSaveable { mutableIntStateOf(0) }
    var score by rememberSaveable { mutableIntStateOf(0) }
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    ScreenScaffold("اختبار: ${l.title}", c, onBack, subtitle = if (i < questions.size) "السؤال ${(i + 1).ar()} من ${questions.size.ar()}" else null) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (i >= questions.size) {
                Text("النتيجة: ${score.ar()} من ${questions.size.ar()}", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = c.gharib)
                Text(if (score * 10 >= questions.size * 8) "ممتاز! انتقل للدرس التالي." else "راجع الدرس وأعد المحاولة؛ التكرار يثبّت الأحكام.", fontSize = 16.sp)
                Button(onClick = { i = 0; score = 0; picked = null }, modifier = Modifier.fillMaxWidth()) { Text("أعد الاختبار") }
                TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("رجوع للدرس") }
            } else {
            val q = questions[i]
            Text("ما حكم الجزء الملوّن؟", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ExampleWord(q, c, c.mistake, size = 34) }
            Text("سورة ${Quran.surahs[q.s - 1].name} · الآية ${q.a.ar()}", fontSize = 12.sp, color = c.muted, modifier = Modifier.fillMaxWidth())
            l.quiz.forEach { r ->
                val name = Learn.ruleNames[r] ?: r
                val bg = when {
                    picked == null -> c.frameFill
                    r == q.rule -> Color(0x3328A745)
                    r == picked -> Color(0x33C62828)
                    else -> c.frameFill
                }
                Surface(shape = RoundedCornerShape(14.dp), color = bg, modifier = Modifier.fillMaxWidth().clickable(enabled = picked == null) {
                    picked = r; if (r == q.rule) score++
                }) { Text(name, fontSize = 17.sp, modifier = Modifier.padding(14.dp)) }
            }
            if (picked != null) {
                Text(if (picked == q.rule) "إجابة صحيحة ✓" else "الصواب: ${Learn.ruleNames[q.rule]}", fontWeight = FontWeight.Bold,
                    color = if (picked == q.rule) c.gharib else c.mistake)
                Button(onClick = { i++; picked = null }, modifier = Modifier.fillMaxWidth()) { Text(if (i + 1 < questions.size) "التالي" else "النتيجة") }
            }
            }
        }
    }
}
