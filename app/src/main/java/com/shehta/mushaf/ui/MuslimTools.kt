package com.shehta.mushaf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shehta.mushaf.data.Hijri
import com.shehta.mushaf.data.HijriDate
import com.shehta.mushaf.data.MuslimData
import com.shehta.mushaf.data.MuslimPrefs
import com.shehta.mushaf.data.PrayerCalc
import com.shehta.mushaf.data.Prefs
import com.shehta.mushaf.data.Quran
import com.shehta.mushaf.data.ar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ------------------------------------------------------------------ Hijri calendar & occasions

@Composable
fun HijriScreen(mp: MuslimPrefs, c: MushafColors, onBack: () -> Unit) {
    val adj = mp.hijriAdjust
    val today = remember(adj) { Hijri.today(adj) }
    var monthOffset by remember { mutableIntStateOf(0) }
    val greg = SimpleDateFormat("d MMM", Locale("ar"))
    val upcoming = remember(adj) {
        Hijri.OCCASIONS.mapNotNull { o ->
            var y = today.year
            if (o.month < today.month || (o.month == today.month && o.day < today.day)) y += 1
            Hijri.toGregorian(HijriDate(y, o.month, o.day), adj)?.let { o to it }
        }.sortedBy { it.second.time }
    }
    ScreenScaffold("التقويم الهجري", c, onBack, subtitle = today.toString()) {
        LazyColumn(contentPadding = PaddingValues(14.dp)) {
            item {
                // month grid: walk days from the 1st of the shown month
                val first = remember(adj, monthOffset) {
                    var d = Date(System.currentTimeMillis() - (today.day - 1) * 86_400_000L)
                    var step = monthOffset
                    while (step > 0) { d = Date(d.time + 30 * 86_400_000L); while (Hijri.of(d, adj).day != 1) d = Date(d.time - 86_400_000L); step-- }
                    while (step < 0) { d = Date(d.time - 20 * 86_400_000L); while (Hijri.of(d, adj).day != 1) d = Date(d.time - 86_400_000L); step++ }
                    d
                }
                val h1 = Hijri.of(first, adj)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.TextButton(onClick = { monthOffset-- }) { Text("‹ السابق") }
                    Text("${h1.monthName} ${h1.year.ar()}", fontSize = 19.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    androidx.compose.material3.TextButton(onClick = { monthOffset++ }) { Text("التالي ›") }
                }
                val days = (0 until 30).map { Date(first.time + it * 86_400_000L) }.filter { Hijri.of(it, adj).month == h1.month }
                val dow = Calendar.getInstance().apply { time = first }.get(Calendar.DAY_OF_WEEK) % 7   // Sat = 0
                Row { listOf("سبت", "أحد", "اثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة").forEach { Text(it, fontSize = 11.sp, color = c.muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) } }
                val cells = List(dow) { null } + days
                cells.chunked(7).forEach { week ->
                    Row {
                        for (k in 0 until 7) {
                            val d = week.getOrNull(k)
                            Box(Modifier.weight(1f).aspectRatio(0.85f).padding(2.dp), contentAlignment = Alignment.Center) {
                                if (d != null) {
                                    val h = Hijri.of(d, adj)
                                    val isToday = h == today
                                    val special = Hijri.OCCASIONS.any { it.month == h.month && it.day == h.day } || Hijri.isWhiteDay(h)
                                    Column(
                                        Modifier.fillMaxWidth().background(
                                            when { isToday -> c.gharib; special -> c.ayahHighlight; else -> c.frameFill }, RoundedCornerShape(10.dp),
                                        ).padding(vertical = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(h.day.ar(), fontWeight = FontWeight.Bold, color = if (isToday) c.paper else c.ink)
                                        Text(greg.format(d), fontSize = 9.sp, color = if (isToday) c.paper else c.muted)
                                    }
                                }
                            }
                        }
                    }
                }
                Text("الأيام المظللة: مناسبات أو الأيام البيض (١٣–١٥). التقويم على حساب أم القرى، ويمكن تعديله بيوم أو يومين من إعدادات المواقيت.",
                    fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(vertical = 8.dp))
                HorizontalDivider()
                Text("المناسبات القادمة", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
            }
            items(upcoming.size) { i ->
                val (o, d) = upcoming[i]
                val daysLeft = ((d.time - System.currentTimeMillis()) / 86_400_000L + 1).toInt().coerceAtLeast(0)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(o.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("${o.day.ar()} ${Hijri.MONTHS[o.month - 1]} · ${SimpleDateFormat("EEEE d MMMM", Locale("ar")).format(d)}" + if (o.note.isNotEmpty()) " · ${o.note}" else "",
                            fontSize = 12.sp, color = c.muted)
                    }
                    Text(if (daysLeft == 0) "اليوم" else "بعد ${daysLeft.ar()} يوم", fontSize = 13.sp, color = c.gharib)
                }
                HorizontalDivider()
            }
            item {
                Text("صيام الاثنين والخميس والأيام البيض من كل شهر سنة.", fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(vertical = 10.dp))
            }
        }
    }
}

// ------------------------------------------------------------------ Ramadan

@Composable
fun RamadanScreen(mp: MuslimPrefs, prefs: Prefs, c: MushafColors, onBack: () -> Unit, onKhatma: () -> Unit) {
    val adj = mp.hijriAdjust
    val today = remember(adj) { Hijri.today(adj) }
    val year = if (today.month <= 9 || (today.month == 10 && today.day == 1)) today.year else today.year + 1
    val start = remember(adj, year) { Hijri.toGregorian(HijriDate(year, 9, 1), adj) }
    var tab by remember { mutableIntStateOf(0) }
    ScreenScaffold("رمضان ${year.ar()}هـ", c, onBack, subtitle = start?.let { "يبدأ تقريبًا " + SimpleDateFormat("EEEE d MMMM yyyy", Locale("ar")).format(it) }) {
        TabRow(selectedTabIndex = tab, containerColor = c.frameFill) {
            listOf("الإمساكية", "صيامي وقيامي", "ختمة رمضان").forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
        }
        when (tab) {
            0 -> {
                if (!mp.hasLocation || start == null) { Centered { Text("حدّد موقعك من «مواقيت الصلاة» لعرض الإمساكية.", color = c.muted) }; return@ScreenScaffold }
                val fmt = SimpleDateFormat("EEE d/M", Locale("ar"))
                LazyColumn(contentPadding = PaddingValues(10.dp)) {
                    item {
                        Row(Modifier.padding(vertical = 6.dp)) {
                            listOf("اليوم", "التاريخ", "الإمساك", "الفجر", "المغرب", "العشاء").forEach { Text(it, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
                        }
                    }
                    items(30) { i ->
                        val d = Date(start.time + i * 86_400_000L)
                        if (Hijri.of(d, adj).month != 9) return@items
                        val t = mp.times(d) ?: return@items
                        val isToday = SimpleDateFormat("yyyyMMdd", Locale.US).let { it.format(d) == it.format(Date()) }
                        Row(Modifier.fillMaxWidth().background(if (isToday) c.ayahHighlight else Color.Transparent, RoundedCornerShape(8.dp)).padding(vertical = 7.dp)) {
                            listOf((i + 1).ar(), fmt.format(d), PrayerCalc.format(t.fajr - 10 / 60.0), PrayerCalc.format(t.fajr), PrayerCalc.format(t.maghrib), PrayerCalc.format(t.isha))
                                .forEach { Text(it, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
                        }
                    }
                    item { Text("الإمساك قبل الفجر بعشر دقائق احتياطًا؛ والعبرة بأذان الفجر.", fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(8.dp)) }
                }
            }
            1 -> Column(Modifier.verticalScroll(rememberScrollState()).padding(14.dp)) {
                val fasted = (1..30).count { ((mp.fasting["$year"] ?: 0L) shr it) and 1L == 1L }
                val prayed = (1..30).count { ((mp.taraweeh["$year"] ?: 0L) shr it) and 1L == 1L }
                Text("صمت ${fasted.ar()} يومًا · قمت ${prayed.ar()} ليلة", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.gharib)
                Row(Modifier.padding(top = 8.dp)) {
                    Text("اليوم", modifier = Modifier.width(60.dp), fontSize = 13.sp)
                    Text("الصيام", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 13.sp)
                    Text("التراويح / القيام", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 13.sp)
                }
                for (d in 1..30) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d.ar(), modifier = Modifier.width(60.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Checkbox(checked = ((mp.fasting["$year"] ?: 0L) shr d) and 1L == 1L, onCheckedChange = { mp.toggleFast(year, d) })
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Checkbox(checked = ((mp.taraweeh["$year"] ?: 0L) shr d) and 1L == 1L, onCheckedChange = { mp.toggleTaraweeh(year, d) })
                    }
                }
                MuslimData.category("الدعاء عند إفطار الصائم")?.let { cat ->
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    Text("دعاء الإفطار", fontWeight = FontWeight.SemiBold)
                    cat.items.forEach { z -> Text(z.text, fontSize = 17.sp, lineHeight = 29.sp, modifier = Modifier.padding(vertical = 4.dp)) }
                }
            }
            else -> Column(Modifier.padding(18.dp)) {
                Text("ختمة في رمضان", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("جزء كل يوم (حوالي ٢٠ صفحة) تختم به المصحف في ٣٠ يومًا. يمكنك تقسيمه على الصلوات: ٤ صفحات بعد كل صلاة.",
                    color = c.muted, lineHeight = 24.sp, modifier = Modifier.padding(vertical = 8.dp))
                val k = prefs.khatma
                if (k != null) Text("لديك ختمة جارية (${k.days.ar()} يومًا). بدء ختمة جديدة سيحل محلها.", fontSize = 13.sp, color = c.mistake)
                Button(onClick = { prefs.startKhatma(30, 1); onKhatma() }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("ابدأ ختمة رمضان (٣٠ يومًا)") }
                Button(onClick = { prefs.startKhatma(15, 1); onKhatma() }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text("ختمتين في رمضان (١٥ يومًا لكل ختمة)") }
            }
        }
    }
}

// ------------------------------------------------------------------ Zakat

@Composable
fun ZakatScreen(mp: MuslimPrefs, c: MushafColors, onBack: () -> Unit) {
    var cash by remember { mutableStateOf("") }
    var gold by remember { mutableStateOf("") }
    var karat by remember { mutableIntStateOf(21) }
    var silver by remember { mutableStateOf("") }
    var trade by remember { mutableStateOf("") }
    var owedToYou by remember { mutableStateOf("") }
    var debts by remember { mutableStateOf("") }
    var goldPrice by remember { mutableStateOf(if (mp.goldPrice > 0) mp.goldPrice.toString() else "") }
    var silverPrice by remember { mutableStateOf(if (mp.silverPrice > 0) mp.silverPrice.toString() else "") }
    var currency by remember { mutableStateOf(mp.currency) }
    var silverNisab by remember { mutableStateOf(false) }
    fun n(s: String) = s.replace('٫', '.').map { if (it in '٠'..'٩') '0' + (it - '٠') else it }.joinToString("").toDoubleOrNull() ?: 0.0
    fun money(v: Double) = String.format(Locale("ar"), "%,.2f", v) + " " + currency

    ScreenScaffold("حاسبة الزكاة", c, onBack, subtitle = "زكاة المال والذهب والفضة وعروض التجارة") {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("الأسعار اليوم (للجرام)", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Num("سعر جرام الذهب عيار ٢٤", goldPrice, Modifier.weight(1f)) { goldPrice = it }
                Num("سعر جرام الفضة", silverPrice, Modifier.weight(1f)) { silverPrice = it }
            }
            OutlinedTextField(value = currency, onValueChange = { currency = it }, label = { Text("العملة") }, singleLine = true, modifier = Modifier.width(140.dp))
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Text("ما تملكه وحال عليه الحول (سنة هجرية)", fontWeight = FontWeight.SemiBold)
            Num("النقود في البيت والبنك", cash) { cash = it }
            Num("الذهب (جرام)", gold) { gold = it }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(24, 22, 21, 18).forEach { k -> FilterChip(selected = karat == k, onClick = { karat = k }, label = { Text("عيار ${k.ar()}") }) }
            }
            Num("الفضة (جرام)", silver) { silver = it }
            Num("قيمة عروض التجارة (بضاعة للبيع)", trade) { trade = it }
            Num("ديون لك مرجوّة السداد", owedToYou) { owedToYou = it }
            Num("ديون عليك حالّة (تُخصم)", debts) { debts = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                FilterChip(selected = !silverNisab, onClick = { silverNisab = false }, label = { Text("النصاب بالذهب (٨٥ جم)") })
                FilterChip(selected = silverNisab, onClick = { silverNisab = true }, label = { Text("بالفضة (٥٩٥ جم)") })
            }

            val gp = n(goldPrice); val sp = n(silverPrice)
            val goldValue = n(gold) * karat / 24.0 * gp
            val total = n(cash) + goldValue + n(silver) * sp + n(trade) + n(owedToYou) - n(debts)
            val nisab = if (silverNisab) 595 * sp else 85 * gp
            Card2(c, Modifier.padding(top = 14.dp)) {
                if ((silverNisab && sp <= 0) || (!silverNisab && gp <= 0)) {
                    Text("أدخل سعر ${if (silverNisab) "الفضة" else "الذهب"} لحساب النصاب.", color = c.muted)
                } else {
                    Text("مجموع المال الزكوي: ${money(total.coerceAtLeast(0.0))}")
                    Text("النصاب: ${money(nisab)}", color = c.muted, fontSize = 14.sp)
                    if (total >= nisab && nisab > 0) {
                        Text("الزكاة الواجبة (٢٫٥٪): ${money(total * 0.025)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.gharib, modifier = Modifier.padding(top = 6.dp))
                    } else Text("لم يبلغ المال النصاب؛ لا زكاة فيه.", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                }
            }
            androidx.compose.material3.TextButton(onClick = { mp.updatePrices(n(goldPrice), n(silverPrice), currency) }) { Text("حفظ الأسعار والعملة") }
            Text("الحساب تقريبي للحالات الشائعة. في ذهب الزينة المستعمل خلاف بين العلماء، وللمسائل الخاصة (الأسهم، الرواتب، الديون المتعثّرة) اسأل أهل العلم.",
                fontSize = 12.sp, color = c.muted, lineHeight = 19.sp, modifier = Modifier.padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun Num(label: String, value: String, modifier: Modifier = Modifier.fillMaxWidth(), onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label, fontSize = 12.sp) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = modifier.padding(vertical = 3.dp))
}

// ------------------------------------------------------------------ The Beautiful Names

@Composable
fun NamesScreen(c: MushafColors, onBack: () -> Unit, onGo: (Int, Int) -> Unit) {
    val names = remember { MuslimData.names() }
    var open by remember { mutableStateOf<Int?>(null) }
    ScreenScaffold("أسماء الله الحسنى", c, onBack, subtitle = "«ولله الأسماء الحسنى فادعوه بها»") {
        LazyColumn(contentPadding = PaddingValues(12.dp)) {
            item { Text("قال النبي ﷺ: «إِنَّ لِلَّهِ تِسْعَةً وَتِسْعِينَ اسْمًا مِائَةً إِلاَّ وَاحِدًا، مَنْ أَحْصَاهَا دَخَلَ الْجَنَّةَ» (صحيح البخاري 7392). أما سرد الأسماء بهذا الترتيب فهو في جامع الترمذي (3507) وإسناده ضعيف (الألباني)، ولم يثبت تعيينها في حديث صحيح. والآيات تحت كل اسم: مواضع الاسم أو ما دلّ على معناه.", fontSize = 13.sp, lineHeight = 22.sp, color = c.muted, modifier = Modifier.padding(bottom = 8.dp)) }
            items(names.size) { i ->
                val n = names[i]
                Column(Modifier.fillMaxWidth().clickable { open = if (open == i) null else i }.padding(vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text((i + 1).ar(), color = c.muted, modifier = Modifier.width(36.dp))
                        Text(n.name, fontFamily = hafsFamily, fontSize = 26.sp, color = c.gharib, modifier = Modifier.weight(1f))
                        if (n.refs.isNotEmpty()) Text("${n.refs.size.ar()} آية", fontSize = 12.sp, color = c.muted)
                    }
                    if (open == i) n.refs.take(6).forEach { r ->
                        Column(Modifier.fillMaxWidth().clickable { onGo(r[0], r[1]) }.padding(top = 8.dp, start = 36.dp)) {
                            Text("سورة ${Quran.surahs[r[0] - 1].name} · ${r[1].ar()}", fontSize = 12.sp, color = c.muted)
                            Text(Quran.ayahText(r[0], r[1]), fontFamily = hafsFamily, fontSize = 19.sp, lineHeight = 34.sp)
                        }
                    }
                }
                HorizontalDivider()
            }
            item { Text("اضغط على الاسم لعرض الآيات التي ورد فيها، ثم اضغط الآية لفتحها في المصحف.", fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(8.dp)) }
        }
    }
}
