package com.shehta.mushaf.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shehta.mushaf.data.Prefs
import com.shehta.mushaf.data.Quran
import com.shehta.mushaf.data.TafsirBlock
import com.shehta.mushaf.data.Tafsirs
import com.shehta.mushaf.data.SurahInfo
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.mutableStateOf
import com.shehta.mushaf.data.ar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tafsir Ibn Kathir for one ayah. Long commentaries are split into paragraphs inside a LazyColumn,
 * so even the longest passages open instantly and scroll smoothly.
 */
@Composable
fun TafsirScreen(startS: Int, startA: Int, prefs: Prefs, onBack: () -> Unit, onGo: (Int, Int) -> Unit) {
    BackHandler(onBack = onBack)
    var s by remember { mutableIntStateOf(startS) }
    var a by remember { mutableIntStateOf(startA) }
    val colors = mushafColors(prefs.theme)
    val context = LocalContext.current

    var reload by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    val src = prefs.tafsirSource
    val block by produceState<TafsirBlock?>(null, s, a, src, reload) {
        value = null; failed = false
        value = try { withContext(Dispatchers.IO) { Tafsirs.blockFor(src, s, a) } } catch (e: Exception) { failed = true; null }
    }
    val paragraphs = remember(block) { block?.text?.let { splitParagraphs(it) } ?: emptyList() }
    val listState = rememberLazyListState()
    LaunchedEffect(block) { listState.scrollToItem(0) }

    fun step(dir: Int) {
        val b = block ?: return
        // move to the next/previous *block* (Ibn Kathir often comments on several ayahs together)
        if (dir > 0) {
            if (b.to < Quran.surahs[s - 1].ayahs) a = b.to + 1
            else if (s < 114) { s += 1; a = 1 }
        } else {
            if (b.from > 1) a = b.from - 1
            else if (s > 1) { s -= 1; a = Quran.surahs[s - 1].ayahs }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = colors.bar, contentColor = colors.onBar) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
                    Column(Modifier.weight(1f)) {
                        Text(if (src == "irab") "إعراب القرآن" else "تفسير ${Tafsirs.source(src).name}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        val b = block
                        val range = if (b == null || b.from == b.to) "الآية ${a.ar()}" else "الآيات ${b.from.ar()}–${b.to.ar()}"
                        Text("سورة ${Quran.surahs[s - 1].name} · $range", fontSize = 13.sp, color = colors.onBar.copy(alpha = 0.8f))
                    }
                    IconButton(onClick = { prefs.updateTafsirFont((prefs.tafsirFontSize - 1f).coerceAtLeast(14f)) }) { Icon(Icons.Filled.TextDecrease, "تصغير الخط") }
                    IconButton(onClick = { prefs.updateTafsirFont((prefs.tafsirFontSize + 1f).coerceAtMost(30f)) }) { Icon(Icons.Filled.TextIncrease, "تكبير الخط") }
                }
            }

            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tafsirs.sources.forEach { t ->
                    FilterChip(selected = src == t.id, onClick = { prefs.updateTafsirSource(t.id) }, label = { Text(t.name) })
                }
            }
            Box(Modifier.weight(1f)) {
                val b = block
                if (b == null && failed) {
                    Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("هذا التفسير يُنزَّل من الإنترنت أول مرة لكل سورة، ثم يبقى على جهازك.", textAlign = TextAlign.Center)
                        OutlinedButton(onClick = { reload++ }, modifier = Modifier.padding(top = 12.dp)) { Text("إعادة المحاولة") }
                    }
                } else if (b == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
                        // the ayah(s) being explained
                        item {
                            Surface(shape = RoundedCornerShape(16.dp), color = colors.frameFill, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp)) {
                                    val text = (b.from..b.to).joinToString(" ") { "${Quran.ayahText(s, it)} ${it.ar()}" }
                                    Text(text, fontFamily = hafsFamily, fontSize = (prefs.tafsirFontSize + 5).sp,
                                        lineHeight = (prefs.tafsirFontSize * 2.1f).sp, textAlign = TextAlign.Justify)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        TextButton(onClick = { onGo(s, b.from) }) { Text("اذهب للآية في المصحف") }
                                        IconButton(onClick = { copyAyah(context, s, a) }) { Icon(Icons.Filled.ContentCopy, "نسخ") }
                                    }
                                }
                            }
                        }
                        // what the six books report (sahih/hasan) about the revelation of these ayahs
                        val nz = (b.from..b.to).flatMap { x -> SurahInfo.nuzul(s, x).map { x to it } }
                        if (nz.isNotEmpty()) item {
                            Surface(shape = RoundedCornerShape(16.dp), color = colors.frameFill, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                                Column(Modifier.padding(14.dp)) {
                                    Text("ما ورد في نزول الآية", fontWeight = FontWeight.Bold, color = colors.gharib, fontSize = 16.sp)
                                    nz.forEach { (x, r) ->
                                        if (b.from != b.to) Text("الآية ${x.ar()}", fontSize = 12.sp, color = colors.muted, modifier = Modifier.padding(top = 6.dp))
                                        NuzulView(colors, r, prefs.tafsirFontSize.toInt())
                                    }
                                }
                            }
                        }
                        items(paragraphs.size) { i ->
                            Text(
                                styled(paragraphs[i], colors.gharib, colors.muted),
                                fontSize = prefs.tafsirFontSize.sp,
                                lineHeight = (prefs.tafsirFontSize * 1.75f).sp,
                                textAlign = TextAlign.Justify,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                        item {
                            HorizontalDivider(Modifier.padding(top = 20.dp))
                            Text(if (src == "ibnkathir") "المصدر: تفسير القرآن العظيم للحافظ ابن كثير. النص الباهت بين قوسين من حواشي المحقق." else "المصدر: ${Tafsirs.source(src).name} — عبر مشروع tafsir_api (quran.com).",
                                fontSize = 12.sp, color = colors.muted, modifier = Modifier.padding(vertical = 10.dp))
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding().padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onClick = { step(-1) }, modifier = Modifier.weight(1f)) { Text("السابق") }
                OutlinedButton(onClick = { step(1) }, modifier = Modifier.weight(1f)) { Text("التالي") }
            }
        }
    }
}

private fun splitParagraphs(t: String): List<String> =
    t.split(Regex("\n\\s*\n|\n")).map { it.trim() }.filter { it.isNotEmpty() }

/** Quran quotes ﴿…﴾ in the Uthmani font and colour; editor footnotes [[…]] small and muted. */
private fun styled(p: String, quote: Color, muted: Color): AnnotatedString = buildAnnotatedString {
    val re = Regex("﴿[^﴾]*﴾|\\[\\[.*?]]")
    var last = 0
    for (m in re.findAll(p)) {
        append(p.substring(last, m.range.first))
        if (m.value.startsWith("﴿")) {
            withStyle(SpanStyle(color = quote, fontFamily = hafsFamily)) { append(m.value) }
        } else {
            withStyle(SpanStyle(color = muted, fontSize = 13.sp)) { append(" (" + m.value.removePrefix("[[").removeSuffix("]]").trim() + ") ") }
        }
        last = m.range.last + 1
    }
    append(p.substring(last))
}
