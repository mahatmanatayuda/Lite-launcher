package com.litelauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** An icon the user picked from their own images (stored in files/icons). */
class CustomIconDrawable(res: Resources, val bmp: Bitmap) : BitmapDrawable(res, bmp)

/**
 * Loads icons off the main thread. Order: per-app custom icon (image or icon pack),
 * active icon pack, system icon. Bitmap cache is capped at 8 MB.
 */
class IconManager(ctx: Context) {
    private val appCtx = ctx.applicationContext
    private val pm = appCtx.packageManager
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
        val s = (p ?: "") + "#" + ov.toSortedMap().toString() + "#" + prefs.iconShape
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
            if (v.startsWith("file|")) {
                val f = File(appCtx.filesDir, "icons/" + v.substring(5))
                if (f.exists()) {
                    BitmapFactory.decodeFile(f.path)?.let { return CustomIconDrawable(appCtx.resources, it) }
                }
            } else {
                val i = v.indexOf('|')
                if (i > 0) {
                    val p = packFor(v.substring(0, i))
                    p.load(pm)
                    p.drawable(v.substring(i + 1))?.let { return it }
                }
            }
        }
        activePack?.let { p ->
            p.load(pm)
            p.drawableNameFor(component)?.let { n -> p.drawable(n)?.let { return it } }
        }
        return try { pm.getActivityIcon(component) } catch (e: Exception) { null }
    }

    private fun render(d: Drawable, size: Int): Bitmap {
        val shape = prefs.iconShape
        if (d is CustomIconDrawable) return renderCustom(d, size, if (shape == "system") "rounded" else shape)
        if (shape != "system" && d is AdaptiveIconDrawable) {
            renderShaped(d, size, shape)?.let { return it }
        }
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, size, size)
        d.draw(Canvas(bmp))
        return bmp
    }

    private fun radiusFor(shape: String, size: Int): Float = when (shape) {
        "circle" -> size / 2f
        "squircle" -> size * 0.36f
        else -> size * 0.2f
    }

    /** A picked image, cropped to the chosen shape. */
    private fun renderCustom(d: CustomIconDrawable, size: Int, shape: String): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val r = radiusFor(shape, size)
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        c.drawRoundRect(rect, r, r, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        c.drawBitmap(d.bmp, null, rect, paint)
        return out
    }

    /** Re-mask an adaptive icon with our own shape (circle / squircle / rounded square). */
    private fun renderShaped(d: AdaptiveIconDrawable, size: Int, shape: String): Bitmap? {
        val layers = listOfNotNull(d.background, d.foreground)
        if (layers.isEmpty()) return null
        // Adaptive layers are 108 units wide, of which the central 72 are the visible area.
        val full = (size * 108f / 72f).toInt()
        val off = (size - full) / 2
        val content = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cc = Canvas(content)
        for (layer in layers) {
            layer.setBounds(off, off, off + full, off + full)
            layer.draw(cc)
        }
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = radiusFor(shape, size)
        c.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), radius, radius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        c.drawBitmap(content, 0f, 0f, paint)
        return out
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
