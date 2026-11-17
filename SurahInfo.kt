package com.shehta.mushaf.data

import org.json.JSONObject

/** One narration about the revelation of an ayah (six books, sahih/hasan), shown verbatim. */
class NuzulReport(val text: String, val start: Int, val source: String)

/** Surah aims, why it is named so, and revelation reports (assets/quran/surahs_info.json, tools/surah_info.py). */
object SurahInfo {
    class Info(val why: List<String>, val aims: List<String>, val nuzul: Map<Int, List<NuzulReport>>)

    private val root: JSONObject? by lazy { try { JSONObject(Quran.asset("surahs_info.json")) } catch (e: Exception) { null } }
    val source: String get() = root?.optString("src") ?: ""
    private val cache = HashMap<Int, Info>()

    fun of(s: Int): Info? = synchronized(cache) {
        cache[s] ?: run {
            val o = root?.optJSONObject("surahs")?.optJSONObject(s.toString()) ?: return null
            fun list(k: String) = o.optJSONArray(k)?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
            val nz = HashMap<Int, List<NuzulReport>>()
            o.optJSONObject("nuzul")?.let { n ->
                for (key in n.keys()) {
                    val arr = n.getJSONArray(key)
                    nz[key.toInt()] = List(arr.length()) { i ->
                        arr.getJSONObject(i).let { r -> NuzulReport(r.getString("t"), r.optInt("st"), r.getString("src")) }
                    }
                }
            }
            Info(list("why"), list("aims"), nz).also { cache[s] = it }
        }
    }

    fun nuzul(s: Int, a: Int): List<NuzulReport> = of(s)?.nuzul?.get(a) ?: emptyList()
}
