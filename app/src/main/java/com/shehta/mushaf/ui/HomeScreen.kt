package com.shehta.mushaf.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shehta.mushaf.data.Hijri
import com.shehta.mushaf.data.MuslimData
import com.shehta.mushaf.data.MuslimPrefs
import com.shehta.mushaf.data.PrayerCalc
import com.shehta.mushaf.data.ar
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** The Muslim's daily hub: next prayer, today's azkar, hadith of the day and every tool. */
@Composable
fun HomeScreen(mp: MuslimPrefs, c: MushafColors, onBack: () -> Unit, onPick: (String) -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    var hadithOffset by remember { mutableIntStateOf(0) }
    val hijri = Hijri.of(Date(now), mp.hijriAdjust)
    val times = mp.times(Date(now))
    val next = mp.next(now)
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val hourNow = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60.0

    ScreenScaffold("المسلم", c, onBack, subtitle = "$hijri · ${SimpleDateFormat("EEEE d MMMM yyyy", Locale("ar")).format(Date(now))}") {
        LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ---- prayer card
            item {
                Card2(c, Modifier.clickable { onPick("prayer") }) {
                    if (times == null || next == null) {
                        Text("مواقيت الصلاة", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("اضغط هنا لتحديد موقعك وعرض المواقيت والأذان.", color = c.muted)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${PrayerCalc.NAMES[next.first]} بعد ${countdown(next.second - now)}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = c.gharib)
                                Text(mp.cityName, fontSize = 12.sp, color = c.muted)
                            }
                            Icon(Icons.Filled.AccessTime, null, tint = c.gharib)
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                            times.list().forEachIndexed { i, h ->
                                if (i == 1) return@forEachIndexed
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(PrayerCalc.NAMES[i], fontSize = 12.sp, fontWeight = if (next.first == i) FontWeight.Bold else FontWeight.Normal,
                                        color = if (next.first == i) c.gharib else c.ink)
                                    Text(PrayerCalc.format(h).substringBefore(" "), fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
            // ---- azkar for this time of day
            item {
                val (title, cat, icon) = when {
                    times != null && hourNow >= times.fajr && hourNow < times.dhuhr -> Triple("أذكار الصباح", MuslimData.MORNING, Icons.Filled.WbSunny)
                    times != null && hourNow >= times.asr && hourNow < times.isha + 1 -> Triple("أذكار المساء", MuslimData.EVENING, Icons.Filled.NightsStay)
                    times != null && (hourNow >= times.isha + 1 || hourNow < times.fajr) -> Triple("أذكار النوم", MuslimData.SLEEP, Icons.Filled.NightsStay)
                    else -> Triple("الأذكار بعد الصلاة", MuslimData.AFTER_PRAYER, Icons.Filled.TouchApp)
                }
                Card2(c, Modifier.clickable { onPick("azkar:$cat") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = c.marker); Text("  $title", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        Text("ابدأ ›", color = c.gharib)
                    }
                }
            }
            // ---- Friday & occasions
            item {
                val friday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY
                val soon = Hijri.OCCASIONS.firstOrNull { it.month == hijri.month && it.day in hijri.day..hijri.day + 2 }
                if (friday) Card2(c, Modifier.clickable { onPick("kahf") }) {
                    Text("يوم الجمعة", fontWeight = FontWeight.Bold)
                    Text("اقرأ سورة الكهف وأكثِر من الصلاة على النبي ﷺ — اضغط لفتح السورة.", fontSize = 14.sp)
                }
                if (soon != null) Card2(c, Modifier.padding(top = if (friday) 8.dp else 0.dp).clickable { onPick("hijri") }) {
                    Text(if (soon.day == hijri.day) "اليوم: ${soon.title}" else "قريبًا: ${soon.title}", fontWeight = FontWeight.Bold)
                    if (soon.note.isNotEmpty()) Text(soon.note, fontSize = 14.sp, color = c.muted)
                } else if (Hijri.isWhiteDay(hijri) || hijri.day == 12) Card2(c, Modifier.padding(top = if (friday) 8.dp else 0.dp)) {
                    Text(if (hijri.day == 12) "غدًا أول الأيام البيض (١٣–١٥)" else "من الأيام البيض — يُستحب صيامها", fontWeight = FontWeight.SemiBold)
                }
            }
            // ---- hadith of the day
            item {
                val h = MuslimData.hadithOfDay(hadithOffset)
                Card2(c) {
                    Text("حديث اليوم", fontWeight = FontWeight.Bold, color = c.gharib)
                    HadithBody(c, h)
                    Row {
                        TextButton(onClick = { hadithOffset++ }) { Text("حديث آخر") }
                        TextButton(onClick = {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${h.text}\n— ${h.source}"), "مشاركة"))
                        }) { Text("مشاركة") }
                    }
                }
            }
            // ---- every tool, grouped so nothing gets lost
            val sections: List<Pair<String, List<Triple<String, ImageVector, String>>>> = listOf(
                "القرآن الكريم" to listOf(
                    Triple("virtues", Icons.Filled.Favorite, "فضل القرآن"),
                    Triple("search", Icons.Filled.Search, "البحث في القرآن"),
                    Triple("tafsir", Icons.Filled.AutoStories, "التفسير"),
                    Triple("tajweed", Icons.Filled.RecordVoiceOver, "تعلّم التجويد"),
                    Triple("hifz", Icons.Filled.Psychology, "الحفظ والمراجعة"),
                    Triple("khatma", Icons.Filled.Event, "الختمة والورد"),
                    Triple("marks", Icons.Filled.Bookmarks, "العلامات"),
                    Triple("notes", Icons.Filled.EditNote, "الملاحظات"),
                ),
                "الصلاة والعبادات اليومية" to listOf(
                    Triple("prayer", Icons.Filled.AccessTime, "مواقيت الصلاة"),
                    Triple("qibla", Icons.Filled.Explore, "القبلة"),
                    Triple("azkar", Icons.Filled.WbSunny, "الأذكار"),
                    Triple("tasbih", Icons.Filled.TouchApp, "السبحة"),
                    Triple("tracker", Icons.Filled.CheckBox, "صلواتي"),
                    Triple("hijri", Icons.Filled.CalendarMonth, "التقويم الهجري"),
                    Triple("ramadan", Icons.Filled.NightsStay, "رمضان"),
                    Triple("zakat", Icons.Filled.Calculate, "الزكاة"),
                ),
                "طلب العلم" to listOf(
                    Triple("sheikhs", Icons.Filled.School, "الشيوخ والدروس"),
                    Triple("poems", Icons.AutoMirrored.Filled.LibraryBooks, "المتون والمنظومات"),
                    Triple("hadiths", Icons.Filled.FormatQuote, "أحاديث مشروحة"),
                    Triple("fiqh", Icons.Filled.Gavel, "تعلّم الفقه"),
                    Triple("names", Icons.Filled.Star, "أسماء الله الحسنى"),
                ),
                "استمع" to listOf(
                    Triple("radio", Icons.Filled.Radio, "إذاعات القرآن"),
                    Triple("podcast", Icons.Filled.Podcasts, "بودكاست"),
                    Triple("nasheed", Icons.Filled.Headphones, "قصائد ومتون صوتية"),
                ),
                "English" to listOf(
                    Triple("english", Icons.Filled.Language, "English corner\nللمسلمين الأجانب"),
                ),
                "التطبيق" to listOf(
                    Triple("feedback", Icons.Filled.Feedback, "المشاكل والمقترحات"),
                    Triple("settings", Icons.Filled.Settings, "الإعدادات"),
                    Triple("share", Icons.Filled.Share, "شارك التطبيق"),
                ),
            )
            sections.forEach { (title, tools) ->
                item {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c.gharib, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
                    Column {
                        tools.chunked(4).forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                row.forEach { (id, icon, label) ->
                                    Column(Modifier.weight(1f).clickable { onPick(id) }.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(Modifier.size(50.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                                            Icon(icon, label, tint = MaterialTheme.colorScheme.primary)
                                        }
                                        Text(label, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp))
                                    }
                                }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
            item { Icon(Icons.Filled.Favorite, null, tint = c.frame, modifier = Modifier.fillMaxWidth().padding(8.dp)) }
        }
    }
}
