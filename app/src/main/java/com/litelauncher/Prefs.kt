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

    fun iconOverrides(): Map<String, String> {
        val s = sp.getString("icon_overrides", null) ?: return emptyMap()
        return try {
            val o = JSONObject(s)
            o.keys().asSequence().associateWith { o.getString(it) }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun setIconOverride(component: String, value: String?) {
        val o = try { JSONObject(sp.getString("icon_overrides", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }
        if (value == null) o.remove(component) else o.put(component, value)
        sp.edit().putString("icon_overrides", o.toString()).apply()
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
