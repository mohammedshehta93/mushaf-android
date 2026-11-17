package com.shehta.mushaf.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class PaperTheme { CREAM, WHITE, NIGHT }
enum class ViewMode { PAGE, LARGE, TAJWEED }

data class Bookmark(val page: Int, val s: Int, val a: Int, val time: Long)

/**
 * A personal note / highlight on an ayah. color: 0..4 (see NoteColors), -1 = note without highlight.
 * [act]: what the reader intends to do with the ayah («دفتر التدبر»); [done] when he did it; [asked]: last follow-up.
 */
data class Note(
    val s: Int, val a: Int, val text: String, val color: Int, val time: Long,
    val act: String = "", val done: Boolean = false, val asked: Long = 0,
)

/** A khatma plan: finish the whole Mushaf in [days] days starting at [start]. */
data class Khatma(val start: Long, val days: Int, val startPage: Int)

/**
 * Settings and all personal data (stop marks, notes, memorisation, khatma, fiqh progress).
 * Everything lives in SharedPreferences, is exposed as Compose state, and saves immediately.
 * Android Auto Backup also backs this file up to the user's Google account.
 */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("mushaf", Context.MODE_PRIVATE)

    // ------------------------------------------------------------------ reading settings
    var lastPage by mutableStateOf(sp.getInt("lastPage", 1)); private set
    var theme by mutableStateOf(PaperTheme.entries.getOrElse(sp.getInt("theme", 0)) { PaperTheme.CREAM }); private set
    var mode by mutableStateOf(ViewMode.entries.getOrElse(sp.getInt("mode", 0)) { ViewMode.PAGE }); private set
    var pageFontStep by mutableStateOf(sp.getInt("pageFontStepV2", 0)); private set
    var largeFontSize by mutableStateOf(sp.getFloat("largeFont", 30f)); private set
    var tajweedFontSize by mutableStateOf(sp.getFloat("tajweedFont", 26f)); private set
    var tafsirFontSize by mutableStateOf(sp.getFloat("tafsirFont", 19f)); private set
    var colorGharib by mutableStateOf(sp.getBoolean("colorGharib", true)); private set
    var keepScreenOn by mutableStateOf(sp.getBoolean("keepOn", true)); private set
    var volumeKeys by mutableStateOf(sp.getBoolean("volumeKeys", true)); private set
    var showPageNumber by mutableStateOf(sp.getBoolean("pageNo", true)); private set
    var twoPagesLandscape by mutableStateOf(sp.getBoolean("twoPages", true)); private set
    var tafsirSource by mutableStateOf((sp.getString("tafsirSrc", "ibnkathir") ?: "ibnkathir").let { if (it == "saadi") "saadi2" else it }); private set
    var lastUpdateCheck: Long
        get() = sp.getLong("updCheck", 0)
        set(v) = sp.edit().putLong("updCheck", v).apply()

    fun updateLastPage(p: Int) { if (p != lastPage) { lastPage = p; sp.edit().putInt("lastPage", p).apply() } }
    fun updateTheme(t: PaperTheme) { theme = t; sp.edit().putInt("theme", t.ordinal).apply() }
    fun updateMode(m: ViewMode) { mode = m; sp.edit().putInt("mode", m.ordinal).apply() }
    fun updatePageFontStep(v: Int) { pageFontStep = v; sp.edit().putInt("pageFontStepV2", v).apply() }
    fun updateLargeFont(v: Float) { largeFontSize = v; sp.edit().putFloat("largeFont", v).apply() }
    fun updateTajweedFont(v: Float) { tajweedFontSize = v; sp.edit().putFloat("tajweedFont", v).apply() }
    fun updateTafsirFont(v: Float) { tafsirFontSize = v; sp.edit().putFloat("tafsirFont", v).apply() }
    fun updateColorGharib(v: Boolean) { colorGharib = v; sp.edit().putBoolean("colorGharib", v).apply() }
    fun updateKeepScreenOn(v: Boolean) { keepScreenOn = v; sp.edit().putBoolean("keepOn", v).apply() }
    fun updateVolumeKeys(v: Boolean) { volumeKeys = v; sp.edit().putBoolean("volumeKeys", v).apply() }
    fun updateShowPageNumber(v: Boolean) { showPageNumber = v; sp.edit().putBoolean("pageNo", v).apply() }
    fun updateTwoPages(v: Boolean) { twoPagesLandscape = v; sp.edit().putBoolean("twoPages", v).apply() }
    fun updateTafsirSource(v: String) { tafsirSource = v; sp.edit().putString("tafsirSrc", v).apply() }

    // ------------------------------------------------------------------ audio settings
    var reciter by mutableStateOf(sp.getString("reciter", "Husary_128kbps") ?: "Husary_128kbps"); private set
    var repeatAyah by mutableStateOf(sp.getInt("repAyah", 1)); private set        // 0 = endless
    var repeatRange by mutableStateOf(sp.getInt("repRange", 1)); private set      // 0 = endless
    var speed by mutableStateOf(sp.getFloat("speed", 1f)); private set
    var followAudio by mutableStateOf(sp.getBoolean("follow", true)); private set
    fun updateReciter(v: String) { reciter = v; sp.edit().putString("reciter", v).apply() }
    fun updateRepeatAyah(v: Int) { repeatAyah = v; sp.edit().putInt("repAyah", v).apply() }
    fun updateRepeatRange(v: Int) { repeatRange = v; sp.edit().putInt("repRange", v).apply() }
    fun updateSpeed(v: Float) { speed = v; sp.edit().putFloat("speed", v).apply() }
    fun updateFollowAudio(v: Boolean) { followAudio = v; sp.edit().putBoolean("follow", v).apply() }

    // ------------------------------------------------------------------ stop marks
    var bookmarks by mutableStateOf(loadBookmarks()); private set

    fun isBookmarked(page: Int) = bookmarks.any { it.page == page }

    /** Adds a stop mark (or moves the existing one on the same page to the new ayah). Newest first. */
    fun addBookmark(page: Int, s: Int, a: Int) {
        bookmarks = listOf(Bookmark(page, s, a, System.currentTimeMillis())) + bookmarks.filter { it.page != page }
        saveBookmarks()
    }

    fun removeBookmark(page: Int) { bookmarks = bookmarks.filter { it.page != page }; saveBookmarks() }

    private fun loadBookmarks(): List<Bookmark> = readArray("bookmarks") { o ->
        Bookmark(o.getInt("p"), o.getInt("s"), o.getInt("a"), o.getLong("t"))
    }

    private fun saveBookmarks() = writeArray("bookmarks", bookmarks) {
        JSONObject().put("p", it.page).put("s", it.s).put("a", it.a).put("t", it.time)
    }

    // ------------------------------------------------------------------ notes & highlights
    var notes by mutableStateOf(readArray("notes") { o ->
        Note(o.getInt("s"), o.getInt("a"), o.getString("x"), o.getInt("c"), o.getLong("t"),
            o.optString("w"), o.optBoolean("d"), o.optLong("r"))
    }.associateBy { "${it.s}:${it.a}" }); private set

    fun note(s: Int, a: Int): Note? = notes["$s:$a"]

    fun saveNote(s: Int, a: Int, text: String, color: Int, act: String = note(s, a)?.act ?: "") {
        val old = note(s, a)
        val keepDone = old != null && old.act == act.trim()
        notes = if (text.isBlank() && color < 0 && act.isBlank()) notes - "$s:$a"
        else notes + ("$s:$a" to Note(s, a, text.trim(), color, System.currentTimeMillis(), act.trim(),
            keepDone && old!!.done, if (keepDone) old!!.asked else 0))
        saveNotes()
    }

    /** «عملتُ بها» on an intention written in the tadabbur notebook (or undo). */
    fun setActDone(s: Int, a: Int, done: Boolean) {
        val n = note(s, a) ?: return
        notes = notes + ("$s:$a" to n.copy(done = done)); saveNotes()
    }

    fun markAsked(s: Int, a: Int) {
        val n = note(s, a) ?: return
        notes = notes + ("$s:$a" to n.copy(asked = System.currentTimeMillis())); saveNotes()
    }

    private fun saveNotes() = writeArray("notes", notes.values.toList()) {
        JSONObject().put("s", it.s).put("a", it.a).put("x", it.text).put("c", it.color).put("t", it.time)
            .put("w", it.act).put("d", it.done).put("r", it.asked)
    }

    fun deleteNote(s: Int, a: Int) = saveNote(s, a, "", -1, "")

    // ------------------------------------------------------------------ tadabbur (all optional, from Settings)
    /** «وقفة تدبّر» in the ayah menu. */
    var tadabburSheet by mutableStateOf(sp.getBoolean("tdSheet", true)); private set
    /** «آيات متشابهة» inside the tadabbur sheet. */
    var showSimilar by mutableStateOf(sp.getBoolean("tdSimilar", true)); private set
    /** «ردّد الآية»: how many times (0 = until stopped) and the pause between repetitions in seconds. */
    var tdRepeat by mutableStateOf(sp.getInt("tdRepeat", 5)); private set
    var tdGap by mutableStateOf(sp.getInt("tdGap", 3)); private set
    /** A daily notification with «آية اليوم» (the widget's ayah) to reflect on. */
    var dailyAyahOn by mutableStateOf(sp.getBoolean("tdDaily", false)); private set
    /** Remind, a few days later, of what the reader wrote he would do with an ayah. */
    var actFollowUp by mutableStateOf(sp.getBoolean("tdFollow", true)); private set
    var tadabburMinutes by mutableStateOf(sp.getInt("tdAt", 7 * 60 + 30)); private set      // minutes after midnight
    fun updateTadabburSheet(v: Boolean) { tadabburSheet = v; sp.edit().putBoolean("tdSheet", v).apply() }
    fun updateShowSimilar(v: Boolean) { showSimilar = v; sp.edit().putBoolean("tdSimilar", v).apply() }
    fun updateTdRepeat(v: Int) { tdRepeat = v; sp.edit().putInt("tdRepeat", v).apply() }
    fun updateTdGap(v: Int) { tdGap = v; sp.edit().putInt("tdGap", v).apply() }
    fun updateDailyAyah(v: Boolean) { dailyAyahOn = v; sp.edit().putBoolean("tdDaily", v).apply() }
    fun updateActFollowUp(v: Boolean) { actFollowUp = v; sp.edit().putBoolean("tdFollow", v).apply() }
    fun updateTadabburMinutes(v: Int) { tadabburMinutes = v; sp.edit().putInt("tdAt", v).apply() }
    fun todayKey(): String = today()

    // ------------------------------------------------------------------ memorisation (hifz)
    var hifzHideFirstWord by mutableStateOf(sp.getBoolean("hifzFirst", false)); private set
    fun updateHifzHideFirstWord(v: Boolean) { hifzHideFirstWord = v; sp.edit().putBoolean("hifzFirst", v).apply() }

    /** Mistakes per word "s:a:w" -> count. */
    var mistakes by mutableStateOf(readObject("mistakes").let { o ->
        o.keys().asSequence().associateWith { o.getInt(it) }
    }); private set

    fun addMistake(s: Int, a: Int, w: Int) {
        val k = "$s:$a:$w"
        mistakes = mistakes + (k to (mistakes[k] ?: 0) + 1)
        saveMistakes()
    }

    fun clearMistake(key: String) { mistakes = mistakes - key; saveMistakes() }

    private fun saveMistakes() {
        val o = JSONObject(); mistakes.forEach { (k, v) -> o.put(k, v) }
        sp.edit().putString("mistakes", o.toString()).apply()
    }

    var memorized by mutableStateOf(readIntSet("memorized")); private set
    fun toggleMemorized(page: Int) {
        memorized = if (page in memorized) memorized - page else memorized + page
        writeIntSet("memorized", memorized)
    }

    // ------------------------------------------------------------------ khatma, reading log, reminder
    var khatma by mutableStateOf(readObject("khatma").let { o ->
        if (o.has("days")) Khatma(o.getLong("start"), o.getInt("days"), o.optInt("startPage", 1)) else null
    }); private set

    /** Pages read since the khatma started (unique). */
    var khatmaPages by mutableStateOf(readIntSet("khatmaPages")); private set

    /** Pages read per day: "yyyy-MM-dd" -> count of distinct pages. */
    var readLog by mutableStateOf(readObject("readLog").let { o -> o.keys().asSequence().associateWith { o.getInt(it) } }); private set
    private var todayPages: MutableSet<Int> = readIntSet("todayPages:" + today()).toMutableSet()

    fun startKhatma(days: Int, startPage: Int) {
        khatma = Khatma(System.currentTimeMillis(), days, startPage)
        khatmaPages = emptySet()
        sp.edit().putString("khatma", JSONObject().put("start", khatma!!.start).put("days", days).put("startPage", startPage).toString()).apply()
        writeIntSet("khatmaPages", khatmaPages)
    }

    fun stopKhatma() { khatma = null; khatmaPages = emptySet(); sp.edit().remove("khatma").remove("khatmaPages").apply() }

    /** Called when the reader has stayed on a page long enough to count it as read. */
    fun markPageRead(page: Int) {
        val day = today()
        if (todayPages.add(page)) {
            sp.edit().putString("todayPages:$day", JSONArray(todayPages.toList()).toString()).apply()
            readLog = readLog + (day to todayPages.size)
            val o = JSONObject(); readLog.entries.sortedBy { it.key }.takeLast(400).forEach { (k, v) -> o.put(k, v) }
            sp.edit().putString("readLog", o.toString()).apply()
        }
        if (khatma != null && page !in khatmaPages) {
            khatmaPages = khatmaPages + page
            writeIntSet("khatmaPages", khatmaPages)
        }
    }

    fun pagesToday(): Int = readLog[today()] ?: 0

    /** Consecutive days (ending today or yesterday) with at least one page read. */
    fun streak(): Int {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        var d = System.currentTimeMillis()
        var n = 0
        if ((readLog[fmt.format(Date(d))] ?: 0) == 0) d -= 86_400_000L
        while ((readLog[fmt.format(Date(d))] ?: 0) > 0) { n++; d -= 86_400_000L }
        return n
    }

    var reminderOn by mutableStateOf(sp.getBoolean("remOn", false)); private set
    var reminderMinutes by mutableStateOf(sp.getInt("remAt", 20 * 60)); private set   // minutes after midnight
    fun updateReminder(on: Boolean, minutes: Int) {
        reminderOn = on; reminderMinutes = minutes
        sp.edit().putBoolean("remOn", on).putInt("remAt", minutes).apply()
    }

    // ------------------------------------------------------------------ fiqh (Bulugh al-Maram) progress
    var fiqhRead by mutableStateOf(readStringSet("fiqhRead")); private set
    var fiqhMemorized by mutableStateOf(readStringSet("fiqhMem")); private set
    var fiqhFavorites by mutableStateOf(readStringSet("fiqhFav")); private set
    var fiqhDaily by mutableStateOf(sp.getInt("fiqhDaily", 3)); private set
    var fiqhQuizBest by mutableStateOf(readObject("fiqhQuiz").let { o -> o.keys().asSequence().associateWith { o.getInt(it) } }); private set

    fun markFiqhRead(key: String) { if (key !in fiqhRead) { fiqhRead = fiqhRead + key; writeStringSet("fiqhRead", fiqhRead) } }
    fun toggleFiqhMemorized(key: String) {
        fiqhMemorized = if (key in fiqhMemorized) fiqhMemorized - key else fiqhMemorized + key
        writeStringSet("fiqhMem", fiqhMemorized)
    }
    fun toggleFiqhFavorite(key: String) {
        fiqhFavorites = if (key in fiqhFavorites) fiqhFavorites - key else fiqhFavorites + key
        writeStringSet("fiqhFav", fiqhFavorites)
    }
    fun updateFiqhDaily(v: Int) { fiqhDaily = v; sp.edit().putInt("fiqhDaily", v).apply() }
    fun saveQuizScore(book: Int, percent: Int) {
        if (percent <= (fiqhQuizBest["$book"] ?: -1)) return
        fiqhQuizBest = fiqhQuizBest + ("$book" to percent)
        val o = JSONObject(); fiqhQuizBest.forEach { (k, v) -> o.put(k, v) }
        sp.edit().putString("fiqhQuiz", o.toString()).apply()
    }

    // ------------------------------------------------------------------ backup (export / import everything)
    fun exportJson(): String {
        val o = JSONObject()
        for ((k, v) in sp.all) {
            when (v) {
                is Int -> o.put(k, JSONObject().put("t", "i").put("v", v))
                is Long -> o.put(k, JSONObject().put("t", "l").put("v", v))
                is Float -> o.put(k, JSONObject().put("t", "f").put("v", v.toDouble()))
                is Boolean -> o.put(k, JSONObject().put("t", "b").put("v", v))
                is String -> o.put(k, JSONObject().put("t", "s").put("v", v))
            }
        }
        return JSONObject().put("app", "mushaf").put("version", 1).put("data", o).toString()
    }

    /** Restores a backup. Returns false if the file is not a backup of this app. Caller restarts the activity. */
    fun importJson(json: String): Boolean {
        val root = try { JSONObject(json) } catch (e: Exception) { return false }
        if (root.optString("app") != "mushaf") return false
        val d = root.getJSONObject("data")
        val e = sp.edit().clear()
        for (k in d.keys()) {
            val x = d.getJSONObject(k)
            when (x.getString("t")) {
                "i" -> e.putInt(k, x.getInt("v"))
                "l" -> e.putLong(k, x.getLong("v"))
                "f" -> e.putFloat(k, x.getDouble("v").toFloat())
                "b" -> e.putBoolean(k, x.getBoolean("v"))
                "s" -> e.putString(k, x.getString("v"))
            }
        }
        e.commit()
        return true
    }

    // ------------------------------------------------------------------ helpers
    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun readObject(key: String): JSONObject = try { JSONObject(sp.getString(key, "{}") ?: "{}") } catch (e: Exception) { JSONObject() }

    private fun <T> readArray(key: String, f: (JSONObject) -> T): List<T> = try {
        val arr = JSONArray(sp.getString(key, "[]"))
        List(arr.length()) { f(arr.getJSONObject(it)) }
    } catch (e: Exception) { emptyList() }

    private fun <T> writeArray(key: String, list: List<T>, f: (T) -> JSONObject) {
        val arr = JSONArray(); list.forEach { arr.put(f(it)) }
        sp.edit().putString(key, arr.toString()).apply()
    }

    private fun readIntSet(key: String): Set<Int> = try {
        val arr = JSONArray(sp.getString(key, "[]")); (0 until arr.length()).map { arr.getInt(it) }.toSet()
    } catch (e: Exception) { emptySet() }

    private fun writeIntSet(key: String, set: Set<Int>) = sp.edit().putString(key, JSONArray(set.sorted()).toString()).apply()

    private fun readStringSet(key: String): Set<String> = try {
        val arr = JSONArray(sp.getString(key, "[]")); (0 until arr.length()).map { arr.getString(it) }.toSet()
    } catch (e: Exception) { emptySet() }

    private fun writeStringSet(key: String, set: Set<String>) = sp.edit().putString(key, JSONArray(set.toList()).toString()).apply()
}
