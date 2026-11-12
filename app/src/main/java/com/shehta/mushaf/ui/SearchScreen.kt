package com.shehta.mushaf.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shehta.mushaf.data.Quran
import com.shehta.mushaf.data.QuranSearch
import com.shehta.mushaf.data.SearchHit
import com.shehta.mushaf.data.ar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Search the whole Quran without tashkeel (type «الرحمن» and it finds «ٱلرَّحْمَـٰنِ»). */
@Composable
fun SearchScreen(onBack: () -> Unit, onGo: (Int, Int) -> Unit) {
    BackHandler(onBack = onBack)
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val c = MaterialTheme.colorScheme

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(query) {
        delay(250)   // debounce typing
        searching = true
        hits = withContext(Dispatchers.Default) { QuranSearch.search(Quran.texts(), query) }
        searching = false
    }

    Surface(Modifier.fillMaxSize(), color = c.background) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Surface(color = c.surface, shadowElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
                    TextField(
                        value = query, onValueChange = { query = it },
                        placeholder = { Text("ابحث في القرآن بدون تشكيل") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                    )
                }
            }
            if (query.trim().length >= 2) {
                Text(
                    if (searching) "جارِ البحث…" else if (hits.size >= 300) "أكثر من ٣٠٠ نتيجة — زد كلمات البحث" else "${hits.size.ar()} نتيجة",
                    fontSize = 13.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            Box(Modifier.weight(1f)) {
                LazyColumn {
                    items(hits.size) { i ->
                        val h = hits[i]
                        Column(Modifier.fillMaxWidth().clickable { onGo(h.s, h.a) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text("سورة ${Quran.surahs[h.s - 1].name} · ${h.a.ar()} · صفحة ${Quran.pageOf(h.s, h.a).ar()}",
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.primary)
                            Text(h.text, fontFamily = hafsFamily, fontSize = 21.sp, lineHeight = 38.sp, textAlign = TextAlign.Start, maxLines = 4)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
