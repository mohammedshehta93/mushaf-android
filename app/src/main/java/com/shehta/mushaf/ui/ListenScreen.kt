package com.shehta.mushaf.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shehta.mushaf.audio.AudioEngine
import com.shehta.mushaf.data.Episode
import com.shehta.mushaf.data.Listen
import com.shehta.mushaf.data.ListenPrefs
import com.shehta.mushaf.data.Podcast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** «استمع»: live Quran radios, podcasts (public directory + RSS), ibtihalat & nasheeds (opened on YouTube). */
@Composable
fun ListenScreen(c: MushafColors, lp: ListenPrefs, onBack: () -> Unit, startTab: Int = 0, podcastQuery: String? = null) {
    var tab by rememberSaveable { mutableIntStateOf(startTab) }
    var openPodcast by remember { mutableStateOf<Podcast?>(null) }
    val back: () -> Unit = { if (openPodcast != null) { openPodcast = null } else { onBack() } }

    ScreenScaffold(openPodcast?.title ?: "استمع", c, back, subtitle = openPodcast?.author) {
        if (openPodcast == null) {
            TabRow(selectedTabIndex = tab) {
                listOf("إذاعات مباشرة", "بودكاست", "أناشيد بدون موسيقى").forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, fontSize = 14.sp, maxLines = 1) })
                }
            }
        }
        Box(Modifier.weight(1f)) {
            when {
                openPodcast != null -> EpisodesList(c, lp, openPodcast!!)
                tab == 0 -> RadiosTab(c)
                tab == 1 -> PodcastsTab(c, lp, podcastQuery, onOpen = { openPodcast = it })
                else -> NasheedsTab(c, lp)
            }
        }
        if (AudioEngine.state.active) MiniPlayer(c, onOpen = {})
    }
}

@Composable
private fun RadiosTab(c: MushafColors) {
    val radios = remember { Listen.radios() }
    var filter by rememberSaveable { mutableStateOf("") }
    val st = AudioEngine.state
    if (radios.isEmpty()) {
        Centered { Text("قائمة الإذاعات غير متوفرة في هذه النسخة.", color = c.muted, modifier = Modifier.padding(24.dp)) }
        return
    }
    val shown = remember(filter) { if (filter.isBlank()) radios else radios.filter { it.name.contains(filter.trim()) } }
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            OutlinedTextField(filter, { filter = it }, Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("ابحث باسم القارئ أو الإذاعة") }, leadingIcon = { Icon(Icons.Filled.Search, null) })
            Text("بث مباشر من إذاعات mp3quran.net — يحتاج إنترنت.", fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(top = 6.dp))
        }
        items(shown, key = { it.url }) { r ->
            val now = st.stream && st.streamUrl == r.url
            Card2(c, Modifier.clickable { if (now) AudioEngine.toggle() else AudioEngine.playStream(r.name, "إذاعة", r.url, live = true) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (now) Icons.Filled.GraphicEq else Icons.Filled.Radio, null, tint = if (now) c.gharib else c.marker)
                    Text("  " + r.name, fontWeight = if (now) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PodcastsTab(c: MushafColors, lp: ListenPrefs, initial: String?, onOpen: (Podcast) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Podcast>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun search(q: String) {
        if (q.isBlank()) return
        query = q; loading = true; error = null
        scope.launch {
            try { results = withContext(Dispatchers.IO) { Listen.searchPodcasts(q) } }
            catch (e: Exception) { error = "تعذّر البحث. تأكد من الاتصال بالإنترنت." }
            loading = false
        }
    }
    LaunchedEffect(initial) { val q0 = initial; if (q0 != null && q0.isNotBlank() && results == null) search(q0) }

    LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("ابحث عن بودكاست ديني") }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search(query) }))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Listen.SUGGESTED.forEach { s -> AssistChip(onClick = { search(s) }, label = { Text(s) }) }
            }
        }
        if (results == null && lp.subscriptions.isNotEmpty()) {
            item { Text("اشتراكاتي", fontWeight = FontWeight.Bold, color = c.gharib) }
            items(lp.subscriptions, key = { "sub" + it.feed }) { p -> PodcastRow(c, lp, p, onOpen) }
        }
        if (results == null && lp.subscriptions.isEmpty()) item {
            Text("اختر موضوعًا من الأعلى أو اكتب اسم شيخ أو برنامج، ثم اضغط ♡ للاشتراك ليظهر هنا دائمًا. " +
                "البرامج من دليل البودكاست العام وتُشغَّل من مصدرها مباشرة؛ راجع المحتوى ومصدره بنفسك.", color = c.muted, fontSize = 14.sp)
        }
        if (loading) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        error?.let { e -> item { Text(e, color = c.mistake) } }
        results?.let { list ->
            if (list.isEmpty() && !loading) item { Text("لا نتائج.", color = c.muted) }
            items(list, key = { "r" + it.feed }) { p -> PodcastRow(c, lp, p, onOpen) }
        }
    }
}

@Composable
private fun PodcastRow(c: MushafColors, lp: ListenPrefs, p: Podcast, onOpen: (Podcast) -> Unit) {
    Card2(c, Modifier.clickable { onOpen(p) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Podcasts, null, tint = c.marker)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(p.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (p.author.isNotEmpty()) Text(p.author, fontSize = 12.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { lp.toggle(p) }) {
                Icon(if (lp.isSubscribed(p.feed)) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "اشتراك", tint = c.gharib)
            }
        }
    }
}

@Composable
private fun EpisodesList(c: MushafColors, lp: ListenPrefs, p: Podcast) {
    var eps by remember(p.feed) { mutableStateOf<List<Episode>?>(null) }
    var error by remember(p.feed) { mutableStateOf(false) }
    LaunchedEffect(p.feed) {
        try { eps = withContext(Dispatchers.IO) { Listen.episodes(p.feed) } } catch (e: Exception) { error = true }
    }
    val st = AudioEngine.state
    when {
        error -> Centered { Text("تعذّر تحميل الحلقات. تأكد من الاتصال بالإنترنت.", color = c.mistake, modifier = Modifier.padding(24.dp)) }
        eps == null -> Centered { CircularProgressIndicator() }
        eps!!.isEmpty() -> Centered { Text("لا توجد حلقات صوتية.", color = c.muted) }
        else -> LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                TextButton(onClick = { lp.toggle(p) }) { Text(if (lp.isSubscribed(p.feed)) "♥ مشترك — اضغط للإلغاء" else "♡ اشترك في هذا البرنامج") }
            }
            items(eps!!, key = { it.url }) { e ->
                val now = st.stream && st.streamUrl == e.url
                val resume = remember(e.url, st.active) { lp.position(e.url) }
                Card2(c, Modifier.clickable {
                    if (now) AudioEngine.toggle()
                    else AudioEngine.playStream(e.title, p.title, e.url, live = false, startMs = lp.position(e.url), onPosition = { u, ms -> lp.savePosition(u, ms) })
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (now) Icons.Filled.GraphicEq else Icons.Filled.PlayArrow, null, tint = if (now) c.gharib else c.marker)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(e.title, fontWeight = if (now) FontWeight.Bold else FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val meta = listOfNotNull(e.date.ifEmpty { null }, formatDuration(e.duration),
                                if (resume > 5000) "تابع من ${formatMs(resume)}" else null).joinToString(" · ")
                            if (meta.isNotEmpty()) Text(meta, fontSize = 12.sp, color = c.muted)
                        }
                    }
                    if (e.description.isNotEmpty()) Text(e.description, fontSize = 13.sp, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(java.util.Locale.US, s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(java.util.Locale.US, s / 60, s % 60)
}

/** itunes:duration is either seconds or h:mm:ss. */
private fun formatDuration(d: String): String? {
    if (d.isBlank()) return null
    return if (d.all { it.isDigit() }) d.toLongOrNull()?.let { formatMs(it * 1000) } else d
}

@Composable
private fun NasheedsTab(c: MushafColors, lp: ListenPrefs) {
    val context = LocalContext.current
    var adding by remember { mutableStateOf(false) }
    fun open(url: String) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (e: Exception) { Toast.makeText(context, "لا يوجد تطبيق لفتح الرابط", Toast.LENGTH_SHORT).show() }
    }
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("كل البحث مقيّد بـ«بدون موسيقى» ويُفتح على يوتيوب. نتائج يوتيوب لا يمكن ضمانها ١٠٠٪، فلو صادفت مقطعًا بموسيقى تجاوزه.", fontSize = 12.sp, color = c.muted)
        }
        items(Listen.NASHEEDS, key = { it.query }) { n ->
            Card2(c, Modifier.clickable { open(Listen.youtubeSearch(n.query)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SmartDisplay, null, tint = c.marker)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(n.title, fontWeight = FontWeight.SemiBold)
                        if (n.note.isNotEmpty()) Text(n.note, fontSize = 12.sp, color = c.muted)
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("روابطي المحفوظة", fontWeight = FontWeight.Bold, color = c.gharib, modifier = Modifier.weight(1f))
                TextButton(onClick = { adding = true }) { Text("+ أضف رابطًا") }
            }
            if (lp.savedLinks.isEmpty()) Text("احفظ رابط قائمة تشغيل أو مقطع تحبه ليبقى هنا.", fontSize = 13.sp, color = c.muted)
        }
        items(lp.savedLinks, key = { "l" + it.second }) { (title, url) ->
            Card2(c, Modifier.clickable { open(url) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SmartDisplay, null, tint = c.gharib)
                    Text(title, Modifier.weight(1f).padding(start = 10.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { lp.removeLink(url) }) { Icon(Icons.Filled.Delete, "حذف", tint = c.muted) }
                }
            }
        }
    }
    if (adding) {
        var title by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        val valid = url.trim().startsWith("http")
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("حفظ رابط") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("الاسم") }, singleLine = true)
                    OutlinedTextField(url, { url = it }, label = { Text("الرابط (يوتيوب أو غيره)") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(enabled = valid, onClick = { lp.addLink(title.ifBlank { url.trim() }, url.trim()); adding = false }) { Text("حفظ") } },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("إلغاء") } },
        )
    }
}
