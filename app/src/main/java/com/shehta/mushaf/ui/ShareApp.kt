package com.shehta.mushaf.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.shehta.mushaf.BuildConfig
import java.io.File

/** Free sharing outside the Play Store: a download link, or the installed app file itself (works offline, e.g. Quick Share). */
object ShareApp {
    val downloadUrl get() = "https://github.com/${BuildConfig.UPDATE_REPO}/releases/latest"

    private const val PITCH = "تطبيق «المصحف» مجاني وبدون إعلانات: مصحف المدينة بخط مجمع الملك فهد، معاني الكلمات، تفسير ابن كثير، " +
        "التلاوة، وضع الحفظ، الختمة، التجويد الملوّن، وتعلّم الفقه."

    fun shareLink(context: Context) {
        val text = "$PITCH\n\nحمّله من هنا (ملف APK للأندرويد):\n$downloadUrl"
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "مشاركة التطبيق"))
    }

    fun shareApk(context: Context) {
        Toast.makeText(context, "جارِ تجهيز ملف التطبيق…", Toast.LENGTH_SHORT).show()
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        Thread {
            try {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val out = File(dir, "Mushaf-${BuildConfig.VERSION_NAME}.apk")
                if (!out.exists()) File(context.applicationInfo.sourceDir).copyTo(out, overwrite = true)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", out)
                val i = Intent(Intent.ACTION_SEND).setType("application/vnd.android.package-archive")
                    .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, PITCH)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                main.post { context.startActivity(Intent.createChooser(i, "إرسال ملف التطبيق")) }
            } catch (e: Exception) {
                main.post { Toast.makeText(context, "تعذّر تجهيز الملف، استخدم مشاركة الرابط", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareAppSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val ic = ListItemDefaults.colors(containerColor = Color.Transparent)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 24.dp)) {
            Text("شارك التطبيق مجانًا", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 8.dp))
            ListItem(
                headlineContent = { Text("مشاركة رابط التحميل") },
                supportingContent = { Text("واتساب أو تيليجرام… يفتح صفحة التحميل وينزّل آخر إصدار") },
                leadingContent = { Icon(Icons.Filled.Link, null) },
                modifier = Modifier.clickableRow { onDismiss(); ShareApp.shareLink(context) }, colors = ic,
            )
            ListItem(
                headlineContent = { Text("إرسال ملف التطبيق نفسه") },
                supportingContent = { Text("بدون إنترنت عبر المشاركة السريعة أو البلوتوث، أو كملف على واتساب") },
                leadingContent = { Icon(Icons.Filled.PhoneAndroid, null) },
                modifier = Modifier.clickableRow { onDismiss(); ShareApp.shareApk(context) }, colors = ic,
            )
            Text("عند التثبيت سيطلب أندرويد السماح بالتثبيت من هذا المصدر لأول مرة فقط.", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}
