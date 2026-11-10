package com.litelauncher

import android.content.Context
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

    /** One of IconShape.SYSTEM, CIRCLE, SQUIRCLE, ROUNDED, TEARDROP. */
    var iconShape: Int
        get() = sp.getInt("icon_shape", IconShape.SYSTEM)
        set(v) = sp.edit().putInt("icon_shape", v).apply()

    /** 0 = nothing, 1 = app drawer, 2 = notification panel, 3 = launcher settings. */
    var doubleTap: Int
        get() = sp.getInt("double_tap", 0)
        set(v) = sp.edit().putInt("double_tap", v).apply()

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
}
