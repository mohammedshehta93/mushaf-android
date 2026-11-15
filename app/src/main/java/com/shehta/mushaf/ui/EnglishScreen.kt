package com.shehta.mushaf.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shehta.mushaf.data.DailyHadith
import com.shehta.mushaf.data.Learn
import com.shehta.mushaf.data.ListenPrefs
import com.shehta.mushaf.data.Podcast
import com.shehta.mushaf.data.Quran
import com.shehta.mushaf.data.Sheikh
import com.shehta.mushaf.data.parseHadith
import org.json.JSONArray
import org.json.JSONObject

private object EnglishData {
    val saheeh: JSONObject by lazy { try { JSONObject(Quran.asset("english/saheeh.json")) } catch (e: Exception) { JSONObject() } }
    /** Explained hadiths in English (HadeethEnc); falls back to the Forty Hadith of an-Nawawi. */
    val hadiths: List<DailyHadith> by lazy {
        fun load(p: String) = try { JSONArray(Quran.asset(p)).let { a -> List(a.length()) { parseHadith(a.getJSONObject(it)) } } } catch (e: Exception) { emptyList() }
        load("english/hadith.json").ifEmpty { load("english/nawawi40.json") }
    }
}

/** «English corner» for Muslims who do not read Arabic: the Quran with Saheeh International, explained hadith,
 *  lectures by well-known speakers, learning series (new Muslims, prayer, Quran) and English podcasts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnglishScreen(c: MushafColors, lp: ListenPrefs, onBack: () -> Unit, onGo: (Int, Int) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var surah by rememberSaveable { mutableIntStateOf(0) }
    var podcastQuery by remember { mutableStateOf<String?>(null) }
    var openPodcast by remember { mutableStateOf<Podcast?>(null) }
    val back: () -> Unit = {
        when {
            openPodcast != null -> openPodcast = null
            surah > 0 -> surah = 0
            else -> onBack()
        }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        ScreenScaffold(
            when { openPodcast != null -> openPodcast!!.title; surah > 0 -> "Surah ${surah} · ${Quran.surahs[surah - 1].name}"; else -> "English corner" },
            c, back, subtitle = if (surah == 0 && openPodcast == null) "Quran · Hadith · Lectures · Learn · Podcasts" else null,
        ) {
            if (surah == 0 && openPodcast == null) {
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                    listOf("Quran", "Hadith", "Lectures", "Learn", "Podcasts").forEachIndexed { i, t ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                when {
                    openPodcast != null -> EpisodesList(c, lp, openPodcast!!)
                    surah > 0 -> SurahTranslation(c, surah, onGo)
                    tab == 0 -> SurahList(c) { surah = it }
                    tab == 1 -> EnHadith(c)
                    tab == 2 -> EnSpeakers(c, onPodcast = { podcastQuery = it; tab = 4 })
                    tab == 3 -> EnSeries(c)
                    else -> androidx.compose.runtime.key(podcastQuery) {
                        PodcastsTab(c, lp, podcastQuery, onOpen = { openPodcast = it }, groups = Learn.enPodcasts, english = true)
                    }
                }
            }
            if (com.shehta.mushaf.audio.AudioEngine.state.active) MiniPlayer(c, onOpen = {})
        }
    }
}

private fun open(context: android.content.Context, url: String) {
    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    catch (e: Exception) { Toast.makeText(context, "No app can open this link", Toast.LENGTH_SHORT).show() }
}

@Composable
private fun SurahList(c: MushafColors, onPick: (Int) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text("The meaning of the Quran in English — Saheeh International translation. The Arabic is the official text of the King Fahd Complex.", fontSize = 13.sp, color = c.muted) }
        items(Quran.surahs) { s ->
            Card2(c, Modifier.clickable { onPick(s.n) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${s.n}", fontWeight = FontWeight.Bold, color = c.gharib, modifier = Modifier.padding(end = 12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.name, fontFamily = hafsFamily, fontSize = 20.sp)
                        Text("${s.ayahs} verses · ${if (s.type.startsWith("مك")) "Makki" else "Madani"}", fontSize = 12.sp, color = c.muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun SurahTranslation(c: MushafColors, s: Int, onGo: (Int, Int) -> Unit) {
    val n = Quran.surahs[s - 1].ayahs
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(n) { i ->
            val a = i + 1
            Card2(c) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Text(Quran.ayahText(s, a), fontFamily = hafsFamily, fontSize = 22.sp, lineHeight = 42.sp, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                }
                Text("$a. " + EnglishData.saheeh.optString("$s:$a"), fontSize = 16.sp, lineHeight = 25.sp, modifier = Modifier.padding(top = 6.dp))
                TextButton(onClick = { onGo(s, a) }) { Text("Open in the Mushaf") }
            }
        }
        item { Text("Translation: Saheeh International.", fontSize = 12.sp, color = c.muted) }
    }
}

@Composable
private fun EnHadith(c: MushafColors) {
    val list = EnglishData.hadiths
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Authentic hadith with explanation, lessons and word meanings (HadeethEnc.com).", fontSize = 13.sp, color = c.muted) }
        items(list) { h ->
            var open by remember(h.text) { mutableStateOf(false) }
            Card2(c) {
                if (h.title.isNotEmpty()) Text(h.title, fontWeight = FontWeight.Bold, color = c.gharib)
                Text(h.text, fontSize = 16.sp, lineHeight = 25.sp, modifier = Modifier.padding(vertical = 4.dp))
                Text(h.source, fontSize = 12.sp, color = c.muted)
                if (h.explanation.isNotEmpty()) {
                    TextButton(onClick = { open = !open }) { Text(if (open) "Hide explanation ▲" else "Explanation ▼") }
                    AnimatedVisibility(open) {
                        Column {
                            Text(h.explanation, fontSize = 15.sp, lineHeight = 24.sp)
                            if (h.hints.isNotEmpty()) {
                                Text("Lessons", fontWeight = FontWeight.Bold, color = c.gharib, modifier = Modifier.padding(top = 8.dp))
                                h.hints.forEach { Text("• $it", fontSize = 15.sp, lineHeight = 24.sp) }
                            }
                            if (h.words.isNotEmpty()) {
                                Text("Word meanings", fontWeight = FontWeight.Bold, color = c.gharib, modifier = Modifier.padding(top = 8.dp))
                                h.words.forEach { (w, m) -> Text("$w: $m", fontSize = 15.sp, lineHeight = 24.sp) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnSpeakers(c: MushafColors, onPodcast: (String) -> Unit) {
    val context = LocalContext.current
    var sel by remember { mutableStateOf<Sheikh?>(null) }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text("Choose a speaker, then the kind of content. Videos open on YouTube.", fontSize = 13.sp, color = c.muted) }
        items(Learn.enSpeakers) { s ->
            Card2(c, Modifier.clickable { sel = s }) {
                Text(s.name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                if (s.desc.isNotEmpty()) Text(s.desc, fontSize = 13.sp, color = c.muted)
            }
        }
    }
    sel?.let { s ->
        ModalBottomSheet(onDismissRequest = { sel = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Text(s.name, fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.padding(bottom = 6.dp))
                EnAction(c, Icons.AutoMirrored.Filled.PlaylistPlay, "Series & playlists", "Complete courses and series") { open(context, Learn.playlists(s.query)) }
                EnAction(c, Icons.Filled.VideoLibrary, "Lectures", "Talks and khutbahs") { open(context, Learn.enLectures(s.query)) }
                EnAction(c, Icons.Filled.SmartDisplay, "Short reminders", "Shorts") { open(context, Learn.enShorts(s.query)) }
                EnAction(c, Icons.Filled.Podcasts, "Podcast", "Plays inside the app") { sel = null; onPodcast(s.query) }
                EnAction(c, Icons.Filled.Subscriptions, "YouTube channel", "Find and subscribe to the official channel") { open(context, Learn.channel(s.query)) }
            }
        }
    }
}

@Composable
private fun EnAction(c: MushafColors, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Card2(c, Modifier.padding(vertical = 4.dp).clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = c.marker)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(sub, fontSize = 12.sp, color = c.muted)
            }
        }
    }
}

@Composable
private fun EnSeries(c: MushafColors) {
    val context = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text("Step-by-step learning: for new Muslims, prayer, reading the Quran, Seerah and belief. Each opens complete playlists on YouTube.", fontSize = 13.sp, color = c.muted) }
        Learn.enSeries.forEach { g ->
            item { Text(g.title, fontWeight = FontWeight.Bold, color = c.gharib, fontSize = 17.sp, modifier = Modifier.padding(top = 8.dp)) }
            items(g.items) { t ->
                Card2(c, Modifier.clickable { open(context, Learn.playlists(t.query)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = c.marker)
                        Text(t.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 10.dp))
                    }
                }
            }
        }
    }
}
