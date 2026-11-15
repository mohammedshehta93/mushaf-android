package com.shehta.mushaf.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shehta.mushaf.data.Feedback
import com.shehta.mushaf.data.ar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** «المشاكل والمقترحات»: the message reaches the app's owner on Telegram. */
@Composable
fun FeedbackScreen(c: MushafColors, page: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val kinds = listOf("مشكلة في التطبيق", "خطأ في المصحف", "اقتراح", "أخرى")
    var kind by rememberSaveable { mutableStateOf(kinds[0]) }
    var msg by rememberSaveable { mutableStateOf("") }
    var contact by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var sent by remember { mutableStateOf(false) }
    var downloads by remember { mutableStateOf<IntArray?>(null) }
    LaunchedEffect(Unit) { downloads = withContext(Dispatchers.IO) { Feedback.downloads() } }

    ScreenScaffold("المشاكل والمقترحات", c, onBack, subtitle = "رسالتك تصل مباشرة لصاحب التطبيق") {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (sent) {
                Card2(c) {
                    Text("وصلت رسالتك، جزاك الله خيرًا ✓", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = c.gharib)
                    Text("سنراجعها ونعمل على إصلاح المشكلة أو دراسة الاقتراح في التحديثات القادمة.", fontSize = 15.sp)
                }
                Button(onClick = { sent = false; msg = ""; result = null }, modifier = Modifier.fillMaxWidth()) { Text("إرسال رسالة أخرى") }
            } else {
                Text("نوع الرسالة", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    kinds.take(2).forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    kinds.drop(2).forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k) }) }
                }
                if (kind == "خطأ في المصحف") Text("اذكر رقم الصفحة أو السورة والآية والكلمة، ويُرسل رقم الصفحة الحالية (${page.ar()}) تلقائيًا.", fontSize = 13.sp, color = c.muted)
                OutlinedTextField(msg, { msg = it.take(3000) }, Modifier.fillMaxWidth().heightIn(min = 160.dp),
                    label = { Text("اكتب رسالتك") })
                OutlinedTextField(contact, { contact = it.take(200) }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("اسمك أو وسيلة للتواصل (اختياري)") })
                Text("يُرسل مع رسالتك فقط: رقم إصدار التطبيق، إصدار أندرويد، نوع الهاتف، والصفحة الحالية.", fontSize = 12.sp, color = c.muted)
                result?.let { Text(it, color = c.mistake) }
                Button(
                    onClick = {
                        sending = true; result = null
                        scope.launch {
                            val err = withContext(Dispatchers.IO) { Feedback.send(context, kind, msg, contact, page) }
                            sending = false
                            if (err == null) sent = true else result = err
                        }
                    },
                    enabled = !sending && msg.isNotBlank() && Feedback.enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (sending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("إرسال")
                }
                if (!Feedback.enabled) Text("الإرسال غير مفعّل في هذه النسخة.", fontSize = 13.sp, color = c.muted)
            }
            downloads?.let { d ->
                Text("نُزّل آخر إصدار ${d[0].ar()} مرة، وإجمالي التنزيلات ${d[1].ar()}.", fontSize = 12.sp, color = c.muted,
                    modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}
