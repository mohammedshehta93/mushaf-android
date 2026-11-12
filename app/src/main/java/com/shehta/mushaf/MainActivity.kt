package com.shehta.mushaf

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.shehta.mushaf.audio.AudioEngine
import com.shehta.mushaf.data.Prefs
import com.shehta.mushaf.data.Quran
import com.shehta.mushaf.data.Tafsirs
import com.shehta.mushaf.reminder.Reminder
import com.shehta.mushaf.ui.App
import com.shehta.mushaf.ui.MushafTheme
import com.shehta.mushaf.update.Updater
import com.shehta.mushaf.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.concurrent.thread

/** Volume-key page turning: the activity receives the keys, the reader consumes them. */
object KeyBridge {
    @Volatile var active = false
    val events = MutableSharedFlow<Int>(extraBufferCapacity = 8)
}

/** A page requested from outside (widget, notification). The reader jumps there and clears it. */
object Launch {
    var page by mutableStateOf(0)
}

class MainActivity : ComponentActivity() {
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // draw under the camera cut-out too: the page uses the whole screen
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        Quran.init(this)
        Tafsirs.init(this)
        Updater.init(this)
        AudioEngine.init(this)
        prefs = Prefs(this)
        Reminder.schedule(this, prefs)
        handle(intent)
        thread(name = "preload", priority = Thread.MIN_PRIORITY) { Quran.preload() }
        setContent {
            val p = remember { prefs }
            MushafTheme(p.theme) { App(p) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val p = intent?.getIntExtra(EXTRA_PAGE, 0) ?: 0
        if (p in 1..Quran.PAGES) Launch.page = p
    }

    override fun onStop() {
        super.onStop()
        WidgetUpdater.updateAll(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (KeyBridge.active) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> { KeyBridge.events.tryEmit(1); return true }
                KeyEvent.KEYCODE_VOLUME_UP -> { KeyBridge.events.tryEmit(-1); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object { const val EXTRA_PAGE = "page" }
}
