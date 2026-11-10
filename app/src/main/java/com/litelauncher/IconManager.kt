package com.litelauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Loads icons off the main thread. Order: per-app custom icon, active icon pack, system icon.
 * Bitmap cache is capped at 8 MB.
 */
class IconManager(ctx: Context) {
    private val pm = ctx.applicationContext.packageManager
    private val prefs = Prefs(ctx)
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val executor = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private val packs = ConcurrentHashMap<String, IconPack>()

    @Volatile private var overrides: Map<String, String> = emptyMap()
    @Volatile private var activePack: IconPack? = null
    @Volatile private var version = 0
    private var sig = "?"

    init { reloadConfig() }

    /** Re-read icon settings. Returns true when they changed (icons must be redrawn). */
    fun reloadConfig(): Boolean {
        val ov = prefs.iconOverrides()
        val p = prefs.iconPack
        val s = (p ?: "") + "#" + ov.toSortedMap().toString()
        if (s == sig) return false
        sig = s
        overrides = ov
        activePack = p?.let { packFor(it) }
        version++
        cache.evictAll()
        return true
    }

    fun clear() = cache.evictAll()

    private fun packFor(pkg: String): IconPack = packs.getOrPut(pkg) { IconPack(pkg, pkg) }

    fun listPacks(): List<IconPack> {
        val found = LinkedHashMap<String, IconPack>()
        for (action in PACK_ACTIONS) {
            for (ri in pm.queryIntentActivities(Intent(action), 0)) {
                val p = ri.activityInfo.packageName
                if (!found.containsKey(p)) found[p] = packFor(p).also { it.label = ri.loadLabel(pm).toString() }
            }
        }
        return found.values.sortedBy { it.label.lowercase() }
    }

    /** Parse a pack on a background thread, then call back on the main thread. */
    fun withPack(pkg: String, cb: (IconPack) -> Unit) {
        executor.execute {
            val p = packFor(pkg)
            p.load(pm)
            main.post { cb(p) }
        }
    }

    fun load(component: ComponentName, sizePx: Int, cb: (Bitmap) -> Unit) {
        val comp = component.flattenToString()
        val key = "$comp@$sizePx@$version"
        cache.get(key)?.let { cb(it); return }
        val ver = version
        executor.execute {
            val d = resolve(component, comp) ?: return@execute
            val bmp = render(d, sizePx)
            if (ver == version) cache.put(key, bmp)
            main.post { cb(bmp) }
        }
    }

    fun loadPackDrawable(pkg: String, name: String, sizePx: Int, cb: (Bitmap) -> Unit) {
        val key = "pack:$pkg/$name@$sizePx"
        cache.get(key)?.let { cb(it); return }
        executor.execute {
            val p = packFor(pkg)
            p.load(pm)
            val d = p.drawable(name) ?: return@execute
            val bmp = render(d, sizePx)
            cache.put(key, bmp)
            main.post { cb(bmp) }
        }
    }

    private fun resolve(component: ComponentName, comp: String): Drawable? {
        overrides[comp]?.let { v ->
            val i = v.indexOf('|')
            if (i > 0) {
                val p = packFor(v.substring(0, i))
                p.load(pm)
                p.drawable(v.substring(i + 1))?.let { return it }
            }
        }
        activePack?.let { p ->
            p.load(pm)
            p.drawableNameFor(component)?.let { n -> p.drawable(n)?.let { return it } }
        }
        return try { pm.getActivityIcon(component) } catch (e: Exception) { null }
    }

    private fun render(d: Drawable, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, size, size)
        d.draw(Canvas(bmp))
        return bmp
    }

    companion object {
        val PACK_ACTIONS = listOf(
            "com.novalauncher.THEME",
            "org.adw.launcher.THEMES",
            "com.anddoes.launcher.THEME",
            "com.gau.go.launcherex.theme",
        )
    }
}

/** Show an app's icon in an ImageView, safe against view recycling. */
fun bindIcon(iv: ImageView, comp: ComponentName, sizePx: Int, icons: IconManager) {
    val lp = iv.layoutParams
    if (lp != null && (lp.width != sizePx || lp.height != sizePx)) {
        lp.width = sizePx
        lp.height = sizePx
        iv.layoutParams = lp
    }
    val tag = comp.flattenToString()
    iv.tag = tag
    iv.setImageDrawable(null)
    icons.load(comp, sizePx) { if (iv.tag == tag) iv.setImageBitmap(it) }
}
