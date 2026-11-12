package com.shehta.mushaf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shehta.mushaf.data.Prefs
import com.shehta.mushaf.data.Quran
import com.shehta.mushaf.data.ar

/** Add / edit a personal note and highlight colour on an ayah. */
@Composable
fun NoteDialog(s: Int, a: Int, prefs: Prefs, onDismiss: () -> Unit) {
    val existing = prefs.note(s, a)
    var text by remember { mutableStateOf(existing?.text ?: "") }
    var color by remember { mutableIntStateOf(existing?.color ?: 0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ملاحظة · ${Quran.surahs[s - 1].name} ${a.ar()}") },
        text = {
            Column {
                Text(Quran.ayahText(s, a), fontFamily = hafsFamily, fontSize = 19.sp, lineHeight = 34.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    placeholder = { Text("اكتب تدبّرك أو فائدة…") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp).padding(top = 10.dp),
                )
                Text("لون التظليل", fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    NoteColors.forEachIndexed { i, c ->
                        Box(
                            Modifier.size(34.dp).background(c.copy(alpha = 0.9f), CircleShape)
                                .border(if (color == i) 3.dp else 0.dp, Color(0xFF0F4C3A), CircleShape)
                                .clickable { color = i },
                        )
                    }
                    IconButton(onClick = { color = -1 }) {
                        Icon(Icons.Filled.Block, "بدون تظليل", tint = if (color == -1) Color(0xFF0F4C3A) else Color.Gray)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { prefs.saveNote(s, a, text, color); onDismiss() }) { Text("حفظ") } },
        dismissButton = {
            Row {
                if (existing != null) TextButton(onClick = { prefs.deleteNote(s, a); onDismiss() }) { Text("حذف") }
                TextButton(onClick = onDismiss) { Text("إلغاء") }
            }
        },
    )
}

@Composable
fun NotesList(prefs: Prefs, c: MushafColors, onGo: (Int, Selection?) -> Unit) {
    val list = prefs.notes.values.sortedWith(compareBy({ it.s }, { it.a }))
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("لا توجد ملاحظات بعد.\nاضغط مطولًا على آية واختر «ملاحظة وتظليل».",
                textAlign = TextAlign.Center, color = c.muted, fontSize = 16.sp, lineHeight = 26.sp)
        }
        return
    }
    LazyColumn {
        items(list.size) { i ->
            val n = list[i]
            Row(
                Modifier.fillMaxWidth().clickable { onGo(Quran.pageOf(n.s, n.a), Selection(n.s, n.a)) }.padding(start = 16.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(width = 8.dp, height = 46.dp).background(if (n.color >= 0) NoteColors[n.color].copy(alpha = 1f) else c.muted))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("سورة ${Quran.surahs[n.s - 1].name} · الآية ${n.a.ar()}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    if (n.text.isNotBlank()) Text(n.text, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    else Text(Quran.ayahText(n.s, n.a), fontFamily = hafsFamily, fontSize = 15.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { prefs.deleteNote(n.s, n.a) }) { Icon(Icons.Filled.Delete, "حذف", tint = c.muted) }
            }
            HorizontalDivider()
        }
    }
}
