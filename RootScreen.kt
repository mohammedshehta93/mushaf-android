package com.shehta.mushaf.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shehta.mushaf.data.Prefs
import com.shehta.mushaf.data.Quran
import com.shehta.mushaf.data.ar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Every place a root occurs in the Quran (from the Quranic Arabic Corpus morphology). */
@Composable
fun RootScreen(root: String, prefs: Prefs, onBack: () -> Unit, onGo: (Int, Selection?) -> Unit) {
    BackHandler(onBack = onBack)
    val c = mushafColors(prefs.theme)
    val list by produceState<List<IntArray>?>(null, root) { value = withContext(Dispatchers.Default) { Quran.rootOccurrences(root) } }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = c.bar, contentColor = c.onBar) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
                    Column {
                        Text("الجذر: ${root.toList().joinToString(" ")}", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        list?.let { Text("${it.size.ar()} موضعًا في ${it.map { x -> "${x[0]}:${x[1]}" }.distinct().size.ar()} آية", fontSize = 13.sp) }
                    }
                }
            }
            val l = list
            if (l == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else LazyColumn(Modifier.navigationBarsPadding()) {
                items(l.size) { i ->
                    val (s, a, w) = l[i].let { Triple(it[0], it[1], it[2]) }
                    val m = Quran.morph(s, a, w)
                    Column(Modifier.fillMaxWidth().clickable { onGo(Quran.pageOf(s, a), Selection(s, a, w = w)) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text("سورة ${Quran.surahs[s - 1].name} · ${a.ar()}   —   ${m?.description ?: ""}", fontSize = 12.sp, color = c.muted)
                        val words = Quran.ayahWords(s, a).entries.toList()
                        val txt = buildAnnotatedString {
                            words.forEachIndexed { idx, (wn, tk) ->
                                if (wn == w) withStyle(SpanStyle(color = c.gharib, fontWeight = FontWeight.Bold)) { append(tk) } else append(tk)
                                if (idx < words.size - 1) append(" ")
                            }
                        }
                        Text(txt, fontFamily = hafsFamily, fontSize = 20.sp, lineHeight = 36.sp, maxLines = 3)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
