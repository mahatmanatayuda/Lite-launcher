package com.litelauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("lite", Context.MODE_PRIVATE)

    var cols: Int
        get() = sp.getInt("cols", 5)
        set(v) = sp.edit().putInt("cols", v).apply()

    var rows: Int
        get() = sp.getInt("rows", 5)
        set(v) = sp.edit().putInt("rows", v).apply()

    var hotseat: Int
        get() = sp.getInt("hotseat", 5)
        set(v) = sp.edit().putInt("hotseat", v).apply()

    var drawerCols: Int
        get() = sp.getInt("drawer_cols", 4)
        set(v) = sp.edit().putInt("drawer_cols", v).apply()

    var iconSizeDp: Int
        get() = sp.getInt("icon_size", 56)
        set(v) = sp.edit().putInt("icon_size", v).apply()

    var showLabels: Boolean
        get() = sp.getBoolean("labels", true)
        set(v) = sp.edit().putBoolean("labels", v).apply()

    var showClock: Boolean
        get() = sp.getBoolean("clock", true)
        set(v) = sp.edit().putBoolean("clock", v).apply()

    var lockDesktop: Boolean
        get() = sp.getBoolean("lock_desktop", false)
        set(v) = sp.edit().putBoolean("lock_desktop", v).apply()

    var notifBadges: Boolean
        get() = sp.getBoolean("notif_badges", false)
        set(v) = sp.edit().putBoolean("notif_badges", v).apply()

    var iconShape: String
        get() = sp.getString("icon_shape", "system") ?: "system"
        set(v) = sp.edit().putString("icon_shape", v).apply()

    var swipeUp: String
        get() = sp.getString("swipe_up", "drawer") ?: "drawer"
        set(v) = sp.edit().putString("swipe_up", v).apply()

    var swipeDown: String
        get() = sp.getString("swipe_down", "notifications") ?: "notifications"
        set(v) = sp.edit().putString("swipe_down", v).apply()

    var doubleTap: String
        get() = sp.getString("double_tap", "none") ?: "none"
        set(v) = sp.edit().putString("double_tap", v).apply()

    var hidden: Set<String>
        get() = HashSet(sp.getStringSet("hidden", emptySet()) ?: emptySet())
        set(v) = sp.edit().putStringSet("hidden", HashSet(v)).apply()

    var iconPack: String?
        get() = sp.getString("icon_pack", null)?.takeIf { it.isNotEmpty() }
        set(v) = sp.edit().putString("icon_pack", v ?: "").apply()

    // ---------- Per-app custom icon source ("pkg|drawable" or "file|name.png") ----------

    fun iconOverrides(): Map<String, String> = readStringMap("icon_overrides")

    fun setIconOverride(component: String, value: String?) = writeStringMapEntry("icon_overrides", component, value)

    // ---------- Per-app custom names ----------

    fun customLabels(): Map<String, String> = readStringMap("custom_labels")

    fun setCustomLabel(component: String, value: String?) = writeStringMapEntry("custom_labels", component, value)

    // ---------- Drawer groups (name -> list of flattened components) ----------

    fun groups(): Map<String, List<String>> {
        val s = sp.getString("groups", null) ?: return emptyMap()
        return try {
            val o = JSONObject(s)
            val out = LinkedHashMap<String, List<String>>()
            for (k in o.keys()) {
                val a = o.getJSONArray(k)
                out[k] = (0 until a.length()).map { a.getString(it) }
            }
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun setGroups(m: Map<String, List<String>>) {
        val o = JSONObject()
        for ((k, v) in m) o.put(k, JSONArray(v))
        sp.edit().putString("groups", o.toString()).apply()
    }

    private fun readStringMap(key: String): Map<String, String> {
        val s = sp.getString(key, null) ?: return emptyMap()
        return try {
            val o = JSONObject(s)
            o.keys().asSequence().associateWith { o.getString(it) }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun writeStringMapEntry(key: String, entry: String, value: String?) {
        val o = try { JSONObject(sp.getString(key, "{}") ?: "{}") } catch (e: Exception) { JSONObject() }
        if (value == null) o.remove(entry) else o.put(entry, value)
        sp.edit().putString(key, o.toString()).apply()
    }

    /** Every setting as JSON, for backups. */
    fun toJson(): JSONObject {
        val o = JSONObject()
        for ((k, v) in sp.all) {
            when (v) {
                is Set<*> -> o.put(k, JSONArray(v.map { it.toString() }))
                is Boolean, is Int, is Long, is Float, is String -> o.put(k, v)
            }
        }
        return o
    }

    /** Replace every setting with the contents of a backup. */
    fun fromJson(o: JSONObject) {
        val e = sp.edit().clear()
        for (k in o.keys()) {
            when (val v = o.get(k)) {
                is Boolean -> e.putBoolean(k, v)
                is Int -> e.putInt(k, v)
                is Long -> e.putLong(k, v)
                is Double -> e.putFloat(k, v.toFloat())
                is String -> e.putString(k, v)
                is JSONArray -> e.putStringSet(k, (0 until v.length()).map { v.getString(it) }.toSet())
            }
        }
        e.apply()
    }
}
