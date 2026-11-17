package com.shehta.mushaf.data

import org.json.JSONObject

/** Ayahs sharing a run of words with an ayah (assets/quran/similar.json, computed from the official text by tools/similar.py). */
object Similar {
    class Link(val s: Int, val a: Int, val words: Int)

    private val root: JSONObject? by lazy { try { JSONObject(Quran.asset("similar.json")) } catch (e: Exception) { null } }

    fun of(s: Int, a: Int): List<Link> {
        val arr = root?.optJSONArray("$s:$a") ?: return emptyList()
        return List(arr.length()) { i ->
            val x = arr.getJSONArray(i); val k = x.getString(0).split(":")
            Link(k[0].toInt(), k[1].toInt(), x.getInt(1))
        }
    }
}
