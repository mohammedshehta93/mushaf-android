package com.shehta.mushaf.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.shehta.mushaf.BuildConfig
import com.shehta.mushaf.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    object UpToDate : UpdateState()
    class Available(val versionName: String, val versionCode: Int, val url: String, val notes: String) : UpdateState()
    class Downloading(val progress: Float) : UpdateState()
    class Failed(val message: String) : UpdateState()
}

/**
 * In-app updates from this app's GitHub Releases (repo set at build time via UPDATE_REPO).
 * The release asset is named mushaf-<versionCode>.apk and is signed with the same release key,
 * so Android installs it as an update over the current app (data and stop marks are kept).
 */
object Updater {
    private lateinit var app: Context
    private val scope = MainScope()
    var state by mutableStateOf<UpdateState>(UpdateState.Idle); private set
    private var userInitiated = false

    fun init(context: Context) { app = context.applicationContext }

    /** Automatic check at most once a day; silent unless an update exists. */
    fun checkDaily(prefs: Prefs) {
        val now = System.currentTimeMillis()
        if (now - prefs.lastUpdateCheck < 24L * 3600 * 1000) return
        prefs.lastUpdateCheck = now
        check(false)
    }

    fun checkNow(force: Boolean) = check(force)

    private fun check(user: Boolean) {
        if (state is UpdateState.Checking || state is UpdateState.Downloading) return
        userInitiated = user
        state = UpdateState.Checking
        scope.launch {
            state = try {
                withContext(Dispatchers.IO) { fetchLatest() }
            } catch (e: Exception) {
                if (user) UpdateState.Failed("تعذّر الاتصال. تأكد من الإنترنت ثم حاول مرة أخرى.") else UpdateState.Idle
            }
            if (!user && state is UpdateState.UpToDate) state = UpdateState.Idle
        }
    }

    private fun fetchLatest(): UpdateState {
        val conn = URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest").openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.connectTimeout = 10000; conn.readTimeout = 15000
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val o = JSONObject(body)
        val assets = o.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val m = Regex("mushaf-(\\d+)\\.apk").find(a.getString("name")) ?: continue
            val code = m.groupValues[1].toInt()
            return if (code > BuildConfig.VERSION_CODE)
                UpdateState.Available(o.optString("name", o.getString("tag_name")), code, a.getString("browser_download_url"), o.optString("body", ""))
            else UpdateState.UpToDate
        }
        return UpdateState.UpToDate
    }

    fun download(u: UpdateState.Available) {
        state = UpdateState.Downloading(0f)
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val dir = File(app.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                    val out = File(dir, "mushaf-${u.versionCode}.apk")
                    fun open(link: String) = (URL(link).openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = false; connectTimeout = 15000; readTimeout = 30000
                    }
                    var conn = open(u.url)
                    var redirects = 0
                    while (conn.responseCode in 300..399 && redirects < 5) {   // GitHub redirects to its CDN
                        redirects++
                        conn = open(conn.getHeaderField("Location"))
                    }
                    val total = conn.contentLengthLong
                    conn.inputStream.use { input ->
                        out.outputStream().use { output ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                output.write(buf, 0, n)
                                done += n
                                if (total > 0) {
                                    val p = done.toFloat() / total
                                    withContext(Dispatchers.Main) { state = UpdateState.Downloading(p) }
                                }
                            }
                        }
                    }
                    out
                }
                state = UpdateState.Idle
                install(file)
            } catch (e: Exception) {
                state = UpdateState.Failed("فشل تنزيل التحديث. حاول مرة أخرى.")
            }
        }
    }

    private fun install(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !app.packageManager.canRequestPackageInstalls()) {
            // first time only: Android asks the user to allow installing updates from this app
            app.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            state = UpdateState.Failed("اسمح للتطبيق بتثبيت التحديثات من الشاشة التي فُتحت، ثم ارجع واضغط «تحديث» مرة أخرى.")
            return
        }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
        app.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun dismiss() { state = UpdateState.Idle }
}

@Composable
fun UpdateDialog(prefs: Prefs) {
    LaunchedEffect(Unit) { Updater.checkDaily(prefs) }
    when (val s = Updater.state) {
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = { Updater.dismiss() },
            title = { Text("يوجد تحديث جديد") },
            text = { Text("الإصدار ${s.versionName} متاح. سيتم تنزيله وتثبيته فوق النسخة الحالية مع الاحتفاظ بعلاماتك وإعداداتك." + if (s.notes.isNotBlank()) "\n\n${s.notes.take(600)}" else "") },
            confirmButton = { TextButton(onClick = { Updater.download(s) }) { Text("تحديث") } },
            dismissButton = { TextButton(onClick = { Updater.dismiss() }) { Text("لاحقًا") } },
        )
        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("جارِ تنزيل التحديث") },
            text = {
                Column {
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    Text("${(s.progress * 100).toInt()}٪", modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {},
        )
        is UpdateState.UpToDate -> AlertDialog(
            onDismissRequest = { Updater.dismiss() },
            title = { Text("أنت على أحدث إصدار") },
            text = { Text("الإصدار ${BuildConfig.VERSION_NAME}") },
            confirmButton = { TextButton(onClick = { Updater.dismiss() }) { Text("حسنًا") } },
        )
        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = { Updater.dismiss() },
            title = { Text("التحديث") },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = { Updater.dismiss() }) { Text("حسنًا") } },
        )
        else -> {}
    }
}
