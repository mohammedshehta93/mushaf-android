package com.shehta.mushaf.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shehta.mushaf.data.Prefs
import com.shehta.mushaf.data.Quran
import com.shehta.mushaf.data.ar
import com.shehta.mushaf.reminder.Reminder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

/** Khatma plan, daily wird, reading streak and the daily reminder. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KhatmaScreen(prefs: Prefs, currentPage: Int, onBack: () -> Unit, onGo: (Int) -> Unit) {
    BackHandler(onBack = onBack)
    val c = mushafColors(prefs.theme)
    val context = LocalContext.current
    var showTime by remember { mutableStateOf(false) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        prefs.updateReminder(granted, prefs.reminderMinutes); Reminder.schedule(context, prefs)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = c.bar, contentColor = c.onBar) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
                    Text("الختمة والورد اليومي", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(18.dp)) {
                val k = prefs.khatma
                if (k == null) {
                    var days by remember { mutableIntStateOf(30) }
                    var fromCurrent by remember { mutableStateOf(false) }
                    Text("ابدأ ختمة جديدة", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("اختر المدة، وسيحسب التطبيق وردك اليومي ويتابع تقدّمك تلقائيًا أثناء القراءة.", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        listOf(7, 10, 15, 20, 30, 60, 90).forEach { d ->
                            FilterChip(selected = days == d, onClick = { days = d }, label = { Text("${d.ar()} يومًا") })
                        }
                    }
                    Text("الورد اليومي: ${ceil(604.0 / days).toInt().ar()} صفحة تقريبًا (≈ ${String.format(Locale("ar"), "%.1f", 30.0 / days)} جزء)", modifier = Modifier.padding(vertical = 10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !fromCurrent, onClick = { fromCurrent = false }, label = { Text("من أول المصحف") })
                        FilterChip(selected = fromCurrent, onClick = { fromCurrent = true }, label = { Text("من الصفحة الحالية (${currentPage.ar()})") })
                    }
                    Button(onClick = { prefs.startKhatma(days, if (fromCurrent) currentPage else 1) }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) { Text("ابدأ الختمة") }
                } else {
                    val daily = ceil(604.0 / k.days).toInt()
                    val elapsedDays = ((System.currentTimeMillis() - k.start) / 86_400_000L).toInt() + 1
                    val expected = (daily * elapsedDays).coerceAtMost(604)
                    val done = prefs.khatmaPages.size
                    val today = prefs.pagesToday()
                    Text("ختمة في ${k.days.ar()} يومًا", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("بدأت ${SimpleDateFormat("d MMMM", Locale("ar")).format(Date(k.start))} · اليوم ${elapsedDays.ar()}", color = c.muted, fontSize = 13.sp)
                    LinearProgressIndicator(progress = { done / 604f }, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).height(10.dp))
                    Text("قرأت ${done.ar()} من ٦٠٤ صفحة (${(done * 100 / 604).ar()}٪)", fontSize = 15.sp)
                    Text(
                        if (done >= expected) "أنت متقدّم على الخطة أو ملتزم بها، بارك الله فيك." else "متأخر ${(expected - done).ar()} صفحة عن الخطة.",
                        color = if (done >= expected) c.gharib else c.mistake, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp),
                    )
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Text("ورد اليوم: ${today.coerceAtMost(daily).ar()} / ${daily.ar()} صفحة", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    LinearProgressIndicator(progress = { (today.toFloat() / daily).coerceAtMost(1f) }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    val next = run {
                        var p = k.startPage
                        var guard = 0
                        while (p in prefs.khatmaPages && guard++ < 604) p = if (p == 604) 1 else p + 1
                        p
                    }
                    Button(onClick = { onGo(next) }, modifier = Modifier.fillMaxWidth()) {
                        Text("تابع وردك: سورة ${Quran.surahOfPage(next).name} — صفحة ${next.ar()}")
                    }
                    TextButton(onClick = { prefs.stopKhatma() }) { Text("إنهاء هذه الختمة") }
                }

                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text("آخر ٧ أيام · سلسلة القراءة ${prefs.streak().ar()} يوم", fontWeight = FontWeight.SemiBold)
                WeekChart(prefs, c)

                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                SwitchRow("تذكير يومي بالورد", prefs.reminderOn) { on ->
                    if (on && Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else { prefs.updateReminder(on, prefs.reminderMinutes); Reminder.schedule(context, prefs) }
                }
                if (prefs.reminderOn) {
                    val h = prefs.reminderMinutes / 60; val m = prefs.reminderMinutes % 60
                    OutlinedButton(onClick = { showTime = true }) {
                        Text("وقت التذكير: ${String.format(Locale.US, "%02d:%02d", if (h % 12 == 0) 12 else h % 12, m)} ${if (h < 12) "صباحًا" else "مساءً"}")
                    }
                }
                Text("يُحسب ما قرأته تلقائيًا: كل صفحة تبقى عليها ٢٠ ثانية على الأقل.", fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }

    if (showTime) {
        val st = rememberTimePickerState(prefs.reminderMinutes / 60, prefs.reminderMinutes % 60, false)
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text("وقت التذكير") },
            text = { TimePicker(state = st) },
            confirmButton = {
                TextButton(onClick = {
                    prefs.updateReminder(true, st.hour * 60 + st.minute); Reminder.schedule(context, prefs); showTime = false
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun WeekChart(prefs: Prefs, c: MushafColors) {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val dayName = SimpleDateFormat("EEE", Locale("ar"))
    val days = (6 downTo 0).map { Date(System.currentTimeMillis() - it * 86_400_000L) }
    val values = days.map { prefs.readLog[fmt.format(it)] ?: 0 }
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(110.dp).padding(top = 10.dp)) {
        val bw = size.width / 7f
        values.forEachIndexed { i, v ->
            val h = (size.height - 4f) * v / max
            // RTL: today on the left end, oldest on the right
            val x = size.width - (i + 1) * bw + bw * 0.2f
            drawRoundRect(c.frameFill, Offset(x, 0f), Size(bw * 0.6f, size.height), CornerRadius(8f, 8f))
            drawRoundRect(c.gharib, Offset(x, size.height - h), Size(bw * 0.6f, h), CornerRadius(8f, 8f))
        }
    }
    Row(Modifier.fillMaxWidth()) {
        days.forEachIndexed { i, d ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(values[i].ar(), fontSize = 11.sp)
                Text(dayName.format(d), fontSize = 10.sp, color = c.muted)
            }
        }
    }
}

@Suppress("unused")
private val shape = RoundedCornerShape(8.dp)
