package com.shehta.mushaf.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.shehta.mushaf.data.Cities
import com.shehta.mushaf.data.Hijri
import com.shehta.mushaf.data.Methods
import com.shehta.mushaf.data.MuslimPrefs
import com.shehta.mushaf.data.PrayerCalc
import com.shehta.mushaf.data.ar
import com.shehta.mushaf.reminder.PrayerAlarms
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** "باقي ٢:١٥" countdown text. */
fun countdown(ms: Long): String {
    val m = (ms / 60_000).coerceAtLeast(0)
    val h = m / 60; val mm = m % 60
    return if (h > 0) "${h.toInt().ar()} س ${mm.toInt().ar()} د" else "${mm.toInt().ar()} دقيقة"
}

@Composable
fun PrayerScreen(mp: MuslimPrefs, c: MushafColors, onBack: () -> Unit, startTab: Int = 0) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(if (!mp.hasLocation && startTab == 0) 2 else startTab) }
    ScreenScaffold("مواقيت الصلاة", c, onBack, subtitle = if (mp.hasLocation) mp.cityName else "حدّد موقعك أولًا") {
        TabRow(selectedTabIndex = tab, containerColor = c.frameFill) {
            listOf("اليوم", "صلواتي", "الإعدادات").forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
        }
        when (tab) {
            0 -> TodayTimes(mp, c)
            1 -> PrayerTracker(mp, c)
            else -> PrayerSettings(mp, c, context)
        }
    }
}

@Composable
private fun TodayTimes(mp: MuslimPrefs, c: MushafColors) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(20_000); now = System.currentTimeMillis() } }
    val t = mp.times(Date(now))
    if (t == null) { Centered { Text("اختر مدينتك من تبويب «الإعدادات».", color = c.muted) }; return }
    val next = mp.next(now)
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(Hijri.today(mp.hijriAdjust).toString() + " · " + SimpleDateFormat("EEEE d MMMM", Locale("ar")).format(Date(now)), color = c.muted, fontSize = 14.sp)
        if (next != null) Card2(c, Modifier.padding(vertical = 10.dp)) {
            Text("الصلاة القادمة", fontSize = 13.sp, color = c.muted)
            Text("${PrayerCalc.NAMES[next.first]} — بعد ${countdown(next.second - now)}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.gharib)
        }
        t.list().forEachIndexed { i, h ->
            val isNext = next?.first == i && (next.second - now) < 20 * 3_600_000L
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .background(if (isNext) c.ayahHighlight else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(PrayerCalc.NAMES[i], fontSize = 19.sp, fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                Text(PrayerCalc.format(h), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                if (i != 1) Icon(if (mp.adhanOn[i]) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff, null,
                    tint = if (mp.adhanOn[i]) c.gharib else c.muted, modifier = Modifier.padding(start = 10.dp).size(20.dp))
            }
        }
        Text("طريقة الحساب: ${Methods.byId(mp.method).name}${if (mp.hanafiAsr) " · العصر حنفي" else ""}. قارن بمواقيت مسجدك وعدّل بالدقائق من الإعدادات إن لزم.",
            fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(top = 10.dp), lineHeight = 19.sp)
    }
}

@Composable
private fun PrayerTracker(mp: MuslimPrefs, c: MushafColors) {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val dayName = SimpleDateFormat("EEEE", Locale("ar"))
    val names = listOf("الفجر", "الظهر", "العصر", "المغرب", "العشاء")
    LazyColumn(Modifier.padding(horizontal = 12.dp)) {
        item {
            Text("علّم الصلوات التي أدّيتها", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            Row(Modifier.fillMaxWidth().padding(start = 90.dp)) {
                names.forEach { Text(it, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
            }
        }
        items(14) { d ->
            val date = Date(System.currentTimeMillis() - d * 86_400_000L)
            val key = fmt.format(date)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (d == 0) "اليوم" else if (d == 1) "أمس" else dayName.format(date), fontSize = 13.sp, modifier = Modifier.width(90.dp))
                for (i in 0 until 5) Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Checkbox(checked = mp.prayed(key, i), onCheckedChange = { mp.togglePrayed(key, i) })
                }
            }
        }
        item {
            val week = (0 until 7).sumOf { d -> (0 until 5).count { mp.prayed(fmt.format(Date(System.currentTimeMillis() - d * 86_400_000L)), it) } }
            Text("هذا الأسبوع: ${week.ar()} من ٣٥ صلاة", fontSize = 14.sp, color = c.gharib, modifier = Modifier.padding(vertical = 8.dp))
            HorizontalDivider()
            Text("صلوات القضاء", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            Text("سجّل عدد ما عليك من فوائت وانقص منه كلما قضيت صلاة.", fontSize = 12.sp, color = c.muted)
            names.forEachIndexed { i, n ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Text(n, modifier = Modifier.weight(1f))
                    IconButton(onClick = { mp.updateQada(i, mp.qada[i] - 1) }) { Text("−", fontSize = 22.sp) }
                    Text(mp.qada[i].ar(), fontSize = 18.sp, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
                    IconButton(onClick = { mp.updateQada(i, mp.qada[i] + 1) }) { Text("+", fontSize = 22.sp) }
                }
            }
            Text("المجموع: ${mp.qada.sum().ar()}", color = c.muted, modifier = Modifier.padding(bottom = 20.dp))
        }
    }
}

@Composable
private fun PrayerSettings(mp: MuslimPrefs, c: MushafColors, context: Context) {
    var locating by remember { mutableStateOf(false) }
    var showCities by remember { mutableStateOf(false) }
    fun reschedule() = PrayerAlarms.scheduleAll(context)

    fun locate() {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = when {
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> null
        }
        if (provider == null) { Toast.makeText(context, "فعّل خدمة الموقع من إعدادات الهاتف", Toast.LENGTH_LONG).show(); return }
        locating = true
        try {
            LocationManagerCompat.getCurrentLocation(lm, provider, null as android.os.CancellationSignal?, ContextCompat.getMainExecutor(context)) { loc ->
                locating = false
                val l = loc ?: try { lm.getLastKnownLocation(provider) } catch (e: SecurityException) { null }
                if (l == null) Toast.makeText(context, "تعذّر تحديد الموقع، اختر مدينتك من القائمة", Toast.LENGTH_LONG).show()
                else {
                    val nearest = Cities.all.minByOrNull { (it.lat - l.latitude) * (it.lat - l.latitude) + (it.lng - l.longitude) * (it.lng - l.longitude) }
                    val close = nearest != null && kotlin.math.abs(nearest.lat - l.latitude) < 0.35 && kotlin.math.abs(nearest.lng - l.longitude) < 0.35
                    mp.setLocation(if (close) nearest!!.name else "موقعي الحالي", l.latitude, l.longitude)
                    reschedule()
                }
            }
        } catch (e: SecurityException) { locating = false }
    }

    val locPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) locate() }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { reschedule() }
    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) { }
            mp.updateAdhanSound(uri.toString())
        }
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("الموقع", fontWeight = FontWeight.SemiBold)
        Text(if (mp.hasLocation) "${mp.cityName} (${String.format(Locale.US, "%.3f، %.3f", mp.lat, mp.lng)})" else "لم يُحدَّد بعد", color = c.muted, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            Button(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) locate()
                else locPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }, enabled = !locating) { Icon(Icons.Filled.MyLocation, null, Modifier.size(18.dp)); Text(if (locating) "  جارٍ التحديد…" else "  موقعي الحالي") }
            OutlinedButton(onClick = { showCities = true }) { Text("اختر مدينة") }
        }

        HorizontalDivider(Modifier.padding(vertical = 10.dp))
        Text("طريقة الحساب", fontWeight = FontWeight.SemiBold)
        Methods.all.forEach { m ->
            Row(Modifier.fillMaxWidth().clickable { mp.updateMethod(m.id); reschedule() }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = mp.method == m.id, onClick = { mp.updateMethod(m.id); reschedule() }); Text(m.name, fontSize = 14.sp)
            }
        }
        SwitchRow("حساب العصر على المذهب الحنفي", mp.hanafiAsr) { mp.updateHanafi(it); reschedule() }

        Text("تعديل بالدقائق (لمطابقة مسجدك)", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
        PrayerCalc.NAMES.forEachIndexed { i, n ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(n, modifier = Modifier.weight(1f))
                IconButton(onClick = { mp.updateAdjustment(i, mp.adjustments[i] - 1); reschedule() }) { Text("−", fontSize = 20.sp) }
                Text((if (mp.adjustments[i] > 0) "+" else "") + mp.adjustments[i].ar(), modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
                IconButton(onClick = { mp.updateAdjustment(i, mp.adjustments[i] + 1); reschedule() }) { Text("+", fontSize = 20.sp) }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 10.dp))
        Text("التنبيهات", fontWeight = FontWeight.SemiBold)
        if (!PrayerAlarms.canNotify(context)) {
            OutlinedButton(onClick = { if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("السماح بالإشعارات") }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            val am = context.getSystemService(AlarmManager::class.java)
            if (!am.canScheduleExactAlarms()) {
                Text("ليصل الأذان في وقته بالضبط اسمح للتطبيق بالمنبّهات الدقيقة:", fontSize = 13.sp, color = c.mistake)
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                }) { Text("السماح بالمنبّهات الدقيقة") }
            }
        }
        PrayerCalc.NAMES.forEachIndexed { i, n ->
            if (i != 1) SwitchRow("تنبيه $n", mp.adhanOn[i]) { mp.updateAdhan(i, it); reschedule() }
        }
        AlarmHealth(context, c)
        Text("صوت الأذان", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        Text(if (mp.adhanSound.isEmpty()) "صوت الإشعار العادي. يمكنك اختيار ملف أذان (MP3) من هاتفك ليُشغَّل كاملًا." else "ملف أذان مختار ✓",
            fontSize = 13.sp, color = c.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { soundPicker.launch(arrayOf("audio/*")) }) { Text("اختيار ملف الأذان") }
            if (mp.adhanSound.isNotEmpty()) TextButton(onClick = { mp.updateAdhanSound("") }) { Text("إزالة") }
        }
        SwitchRow("تذكير بأذكار الصباح والمساء", mp.azkarReminders) { mp.updateAzkarReminders(it); reschedule() }
        SwitchRow("تذكير الجمعة (سورة الكهف)", mp.fridayOn) { mp.updateFriday(it, mp.fridayMinutes); reschedule() }
        if (mp.fridayOn) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            listOf(7, 8, 9, 10, 11).forEach { h ->
                FilterChip(selected = mp.fridayMinutes == h * 60, onClick = { mp.updateFriday(true, h * 60); reschedule() }, label = { Text("${h.ar()} صباحًا") })
            }
        }
        Text("التاريخ الهجري: تعديل بالأيام إن اختلفت رؤية الهلال في بلدك", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(-2, -1, 0, 1, 2).forEach { v ->
                FilterChip(selected = mp.hijriAdjust == v, onClick = { mp.updateHijriAdjust(v) }, label = { Text(if (v > 0) "+${v.ar()}" else v.ar()) })
            }
        }
        Text("اليوم: ${Hijri.today(mp.hijriAdjust)}", fontSize = 13.sp, color = c.muted, modifier = Modifier.padding(bottom = 24.dp))
    }

    if (showCities) {
        var q by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCities = false },
            title = { Text("اختر مدينتك") },
            text = {
                Column {
                    OutlinedTextField(value = q, onValueChange = { q = it }, singleLine = true, placeholder = { Text("بحث") })
                    LazyColumn(Modifier.height(360.dp)) {
                        val list = Cities.all.filter { q.isBlank() || it.name.contains(q.trim()) }
                        items(list.size) { i ->
                            val city = list[i]
                            Text(city.name, fontSize = 17.sp, modifier = Modifier.fillMaxWidth().clickable {
                                mp.setLocation(city.name, city.lat, city.lng); showCities = false; reschedule()
                            }.padding(vertical = 10.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCities = false }) { Text("إغلاق") } },
        )
    }
}


/** Shows the next scheduled adhan and how late the last one arrived, plus the fixes for aggressive battery savers. */
@Composable
private fun AlarmHealth(context: Context, c: MushafColors) {
    val log = PrayerAlarms.log(context)
    val fmt = SimpleDateFormat("EEEE h:mm:ss a", Locale("ar"))
    val nextAt = log.getLong("nextAt", 0L)
    val lastAt = log.getLong("lastAt", 0L)
    val lastFired = log.getLong("lastFired", 0L)
    Card2(c, Modifier.padding(vertical = 8.dp)) {
        Text("حالة التنبيهات", fontWeight = FontWeight.SemiBold)
        if (nextAt > 0) Text("التنبيه القادم: ${PrayerCalc.NAMES[log.getInt("nextI", 0)]} — ${fmt.format(Date(nextAt))}", fontSize = 13.sp)
        if (lastFired > 0) {
            val late = ((lastFired - lastAt) / 1000).toInt()
            Text("آخر تنبيه وصل ${fmt.format(Date(lastFired))}" + when {
                late <= 60 -> " — في وقته ✓"
                else -> " — متأخر ${(late / 60).ar()} دقيقة"
            }, fontSize = 13.sp, color = if (late <= 60) c.gharib else c.mistake)
        }
        val pm = context.getSystemService(android.os.PowerManager::class.java)
        val optimized = !pm.isIgnoringBatteryOptimizations(context.packageName)
        if (optimized) {
            Text("توفير البطارية قد يؤخّر الأذان. استثنِ التطبيق منه:", fontSize = 13.sp, color = c.mistake, modifier = Modifier.padding(top = 6.dp))
            OutlinedButton(onClick = {
                try {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
                } catch (e: Exception) {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }) { Text("استثناء من توفير البطارية") }
        }
        Text("في هواتف Honor وHuawei أيضًا: الإعدادات ← البطارية ← تشغيل التطبيقات ← «تِبيان» ← إدارة يدوية، وفعّل الخيارات الثلاثة.",
            fontSize = 12.sp, color = c.muted, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp))
        TextButton(onClick = {
            PrayerAlarms.scheduleTest(context)
            Toast.makeText(context, "سيصلك تنبيه تجريبي بعد دقيقة — اقفل الشاشة وانتظر", Toast.LENGTH_LONG).show()
        }) { Text("جرّب التنبيه بعد دقيقة") }
    }
}
