package com.litelauncher

import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

/** An installed icon pack (Nova/ADW/Apex style): maps app components to drawables via appfilter.xml. */
class IconPack(val pkg: String, var label: String) {
    private var res: Resources? = null
    private val map = HashMap<String, String>()
    var names: List<String> = emptyList()
        private set
    @Volatile private var loaded = false

    fun load(pm: PackageManager) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            try {
                val r = pm.getResourcesForApplication(pkg)
                res = r
                val id = r.getIdentifier("appfilter", "xml", pkg)
                val parser: XmlPullParser? = if (id != 0) {
                    r.getXml(id)
                } else {
                    try {
                        Xml.newPullParser().also { it.setInput(r.assets.open("appfilter.xml"), null) }
                    } catch (e: Exception) {
                        null
                    }
                }
                if (parser != null) parse(parser)
            } catch (e: Exception) {
                // pack unreadable: behaves as empty
            }
            names = map.values.distinct().sorted()
            loaded = true
        }
    }

    private fun parse(p: XmlPullParser) {
        var e = p.eventType
        while (e != XmlPullParser.END_DOCUMENT) {
            if (e == XmlPullParser.START_TAG && p.name == "item") {
                val comp = p.getAttributeValue(null, "component")
                val drawable = p.getAttributeValue(null, "drawable")
                if (comp != null && drawable != null) {
                    val s = comp.removePrefix("ComponentInfo{").removeSuffix("}")
                    if (s.contains('/')) map.putIfAbsent(s, drawable)
                }
            }
            e = p.next()
        }
    }

    fun drawableNameFor(c: ComponentName): String? = map[c.packageName + "/" + c.className]

    fun drawable(name: String): Drawable? {
        val r = res ?: return null
        val id = r.getIdentifier(name, "drawable", pkg)
        if (id == 0) return null
        return try { r.getDrawable(id, null) } catch (e: Exception) { null }
    }
}
