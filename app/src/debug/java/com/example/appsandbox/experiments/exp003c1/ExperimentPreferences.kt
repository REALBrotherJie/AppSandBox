package com.example.appsandbox.experiments.exp003c1

import android.content.SharedPreferences
import android.util.AtomicFile
import android.util.Xml
import org.json.JSONArray
import org.xmlpull.v1.XmlPullParser
import java.io.File

/** Debug-only synchronous persistence; not a framework SharedPreferences replacement. */
class ExperimentPreferences(val file: File) : SharedPreferences {
    private val values = linkedMapOf<String, Any>()
    private val listeners = linkedSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()
    init {
        val atomic = AtomicFile(file)
        if (file.exists() || File(file.path + ".bak").exists()) atomic.openRead().use { input ->
            val parser = Xml.newPullParser().apply { setInput(input, "UTF-8") }
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "entry") {
                    val key = parser.getAttributeValue(null, "key")
                    val type = parser.getAttributeValue(null, "type")
                    val value = parser.nextText()
                    values[key] = when (type) {
                        "Int" -> value.toInt()
                        "Long" -> value.toLong()
                        "Float" -> value.toFloat()
                        "Boolean" -> value.toBooleanStrict()
                        "Set" -> JSONArray(value).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                        "String" -> value
                        else -> error("Unknown preference type $type")
                    }
                }
            }
        }
    }
    @Synchronized override fun getAll(): MutableMap<String, *> = values.mapValues { (_, v) -> copy(v) }.toMutableMap()
    private fun copy(v: Any): Any = if (v is Set<*>) v.toSet() else v
    @Synchronized override fun getString(k: String, d: String?): String? = values[k]?.let { it as String } ?: d
    @Synchronized override fun getInt(k: String, d: Int) = values[k]?.let { it as Int } ?: d
    @Synchronized override fun getLong(k: String, d: Long) = values[k]?.let { it as Long } ?: d
    @Synchronized override fun getFloat(k: String, d: Float) = values[k]?.let { it as Float } ?: d
    @Synchronized override fun getBoolean(k: String, d: Boolean) = values[k]?.let { it as Boolean } ?: d
    @Suppress("UNCHECKED_CAST")
    @Synchronized override fun getStringSet(k: String, d: MutableSet<String>?): MutableSet<String>? =
        (values[k] as Set<String>?)?.toMutableSet() ?: d?.toMutableSet()
    @Synchronized override fun contains(k: String) = values.containsKey(k)
    @Synchronized override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) { listeners += l }
    @Synchronized override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) { listeners -= l }
    override fun edit(): SharedPreferences.Editor = Editor()
    private inner class Editor : SharedPreferences.Editor {
        private val changes = linkedMapOf<String, Any?>()
        private var clear = false
        override fun putString(k: String, v: String?) = apply { changes[k] = v }
        override fun putInt(k: String, v: Int) = apply { changes[k] = v }
        override fun putLong(k: String, v: Long) = apply { changes[k] = v }
        override fun putFloat(k: String, v: Float) = apply { changes[k] = v }
        override fun putBoolean(k: String, v: Boolean) = apply { changes[k] = v }
        override fun putStringSet(k: String, v: MutableSet<String>?) = apply { changes[k] = v?.toSet() }
        override fun remove(k: String) = apply { changes[k] = null }
        override fun clear() = apply { clear = true }
        override fun apply() { commit() }
        override fun commit(): Boolean {
            val changed: Set<String>
            val targets: List<SharedPreferences.OnSharedPreferenceChangeListener>
            synchronized(this@ExperimentPreferences) {
                val next = if (clear) linkedMapOf() else LinkedHashMap(values)
                changes.forEach { (k, v) -> if (v == null) next.remove(k) else next[k] = v }
                changed = (values.keys + next.keys).filter { values[it] != next[it] }.toSet()
                file.parentFile!!.mkdirs()
                val atomic = AtomicFile(file)
                val stream = try { atomic.startWrite() } catch (_: Exception) { return false }
                try {
                    val xml = Xml.newSerializer().apply { setOutput(stream, "UTF-8"); startDocument("UTF-8", true); startTag(null, "preferences") }
                    next.forEach { (k, v) ->
                        val type = when (v) { is Set<*> -> "Set"; is Int -> "Int"; is Long -> "Long"; is Float -> "Float"; is Boolean -> "Boolean"; else -> "String" }
                        xml.startTag(null, "entry").attribute(null, "key", k).attribute(null, "type", type)
                            .text(if (v is Set<*>) JSONArray(v.toList()).toString() else v.toString()).endTag(null, "entry")
                    }
                    xml.endTag(null, "preferences"); xml.endDocument()
                    atomic.finishWrite(stream)
                } catch (_: Exception) { atomic.failWrite(stream); return false }
                values.clear(); values.putAll(next)
                targets = listeners.toList()
            }
            changed.forEach { key -> targets.forEach { it.onSharedPreferenceChanged(this@ExperimentPreferences, key) } }
            return true
        }
    }
}
