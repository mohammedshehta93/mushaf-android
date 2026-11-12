package com.shehta.mushaf.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.shehta.mushaf.audio.AudioEngine
import com.shehta.mushaf.data.Tafsirs
import com.shehta.mushaf.data.ar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.shehta.mushaf.BuildConfig
import com.shehta.mushaf.data.PaperTheme
import com.shehta.mushaf.data.Prefs
import com.shehta.mushaf.data.ViewMode
import com.shehta.mushaf.update.Updater

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(prefs: Prefs, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(prefs.exportJson().toByteArray()) }
                Toast.makeText(context, "تم حفظ النسخة الاحتياطية", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(context, "تعذّر الحفظ", Toast.LENGTH_SHORT).show() }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val ok = try {
                val json = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                prefs.importJson(json)
            } catch (e: Exception) { false }
            Toast.makeText(context, if (ok) "تمت الاستعادة" else "الملف ليس نسخة احتياطية للمصحف", Toast.LENGTH_SHORT).show()
            if (ok) (context as? Activity)?.recreate()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("الإعدادات", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(bottom = 8.dp))

            Label("لون الصفحة")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(PaperTheme.CREAM to "كريمي", PaperTheme.WHITE to "أبيض", PaperTheme.NIGHT to "ليلي").forEach { (t, n) ->
                    FilterChip(selected = prefs.theme == t, onClick = { prefs.updateTheme(t) }, label = { Text(n) })
                }
            }

            Label("طريقة العرض")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = prefs.mode == ViewMode.PAGE, onClick = { prefs.updateMode(ViewMode.PAGE) }, label = { Text("صفحة المصحف كاملة") })
                FilterChip(selected = prefs.mode == ViewMode.LARGE, onClick = { prefs.updateMode(ViewMode.LARGE) }, label = { Text("خط كبير") })
                FilterChip(selected = prefs.mode == ViewMode.TAJWEED, onClick = { prefs.updateMode(ViewMode.TAJWEED) }, label = { Text("تجويد") })
            }
            Text("«صفحة المصحف» تعرض الصفحة كما في المصحف المطبوع وتملأ الشاشة. «خط كبير» يكبّر الكلمات ويعيد ترتيبها مع التمرير داخل الصفحة.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (prefs.mode == ViewMode.LARGE) {
                Label("حجم الخط الكبير: ${prefs.largeFontSize.toInt()}")
                Slider(value = prefs.largeFontSize, onValueChange = { prefs.updateLargeFont(it) }, valueRange = 22f..56f)
            }
            if (prefs.mode == ViewMode.TAJWEED) {
                Label("أقصى حجم لخط التجويد: ${prefs.tajweedFontSize.toInt()}")
                Slider(value = prefs.tajweedFontSize, onValueChange = { prefs.updateTajweedFont(it) }, valueRange = 18f..48f)
            }

            Label("حجم خط التفسير: ${prefs.tafsirFontSize.toInt()}")
            Slider(value = prefs.tafsirFontSize, onValueChange = { prefs.updateTafsirFont(it) }, valueRange = 14f..30f)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Toggle("تلوين الكلمات الغريبة (اضغط عليها لمعرفة المعنى)", prefs.colorGharib) { prefs.updateColorGharib(it) }
            Toggle("إبقاء الشاشة مضاءة أثناء القراءة", prefs.keepScreenOn) { prefs.updateKeepScreenOn(it) }
            Toggle("تقليب الصفحات بأزرار الصوت", prefs.volumeKeys) { prefs.updateVolumeKeys(it) }
            Toggle("إظهار رقم الصفحة", prefs.showPageNumber) { prefs.updateShowPageNumber(it) }
            Toggle("صفحتان متجاورتان عند تدوير الجهاز", prefs.twoPagesLandscape) { prefs.updateTwoPages(it) }
            Toggle("في وضع الحفظ: أظهر أول كلمة من كل آية", prefs.hifzHideFirstWord) { prefs.updateHifzHideFirstWord(it) }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Label("التفاسير المنزّلة على الجهاز")
            Tafsirs.sources.filter { it.slug != null }.forEach { t ->
                var n by remember { mutableIntStateOf(Tafsirs.downloadedCount(t.id)) }
                var busy by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${t.name} · ${n.ar()}/١١٤ سورة", modifier = Modifier.weight(1f), fontSize = 14.sp)
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else if (n < 114) TextButton(onClick = {
                        busy = true
                        scope.launch {
                            val ok = try { withContext(Dispatchers.IO) { Tafsirs.downloadAll(t.id) { done -> n = done } }; true } catch (e: Exception) { false }
                            n = Tafsirs.downloadedCount(t.id); busy = false
                            if (!ok) Toast.makeText(context, "تعذّر التنزيل، تحقق من الإنترنت", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("تنزيل الكل") }
                    else TextButton(onClick = { Tafsirs.delete(t.id); n = 0 }) { Text("حذف") }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val reciterName = AudioEngine.reciter(prefs.reciter).name
                var count by remember(prefs.reciter) { mutableIntStateOf(AudioEngine.downloadedSurahs(prefs.reciter)) }
                Text("تلاوات $reciterName المنزّلة: ${count.ar()} سورة", modifier = Modifier.weight(1f), fontSize = 14.sp)
                if (count > 0) TextButton(onClick = { AudioEngine.deleteDownloads(prefs.reciter); count = 0 }) { Text("حذف") }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Label("النسخ الاحتياطي")
            Text("يحفظ العلامات والملاحظات والحفظ والختمة والإعدادات في ملف تحتفظ به (مثلًا على Google Drive). كما يحفظ أندرويد نسخة تلقائيًا في حسابك.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { exportLauncher.launch("mushaf-backup.json") }) { Text("تصدير نسخة") }
                TextButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) { Text("استيراد نسخة") }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("الإصدار ${BuildConfig.VERSION_NAME}", modifier = Modifier.weight(1f), fontSize = 14.sp)
                TextButton(onClick = { Updater.checkNow(force = true) }) { Text("التحقق من التحديثات") }
            }
            Text(
                "المصادر: خطوط وصفحات مصحف المدينة النبوية — مجمع الملك فهد لطباعة المصحف الشريف (رواية حفص عن عاصم). " +
                    "معاني الكلمات: «المُيسَّر في غريب القرآن» — مجمع الملك فهد. التفسير: «تفسير القرآن العظيم» لابن كثير. " +
                    "التلاوات: everyayah.com. الجذور: Quranic Arabic Corpus. تلوين التجويد آلي (quran-tajweed) وقد يخطئ في مواضع قليلة. " +
                    "الفقه: «بلوغ المرام» لابن حجر مع تخريج المحقق.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 19.sp,
            )
        }
    }
}

@Composable
private fun Label(t: String) = Text(t, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))

@Composable
private fun Toggle(t: String, v: Boolean, on: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(t, modifier = Modifier.weight(1f), fontSize = 15.sp)
        Switch(checked = v, onCheckedChange = on)
    }
}
