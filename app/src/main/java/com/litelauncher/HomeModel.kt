package com.litelauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class HomeItem(
    var id: Long,
    var type: Int,            // TYPE_APP, TYPE_FOLDER or TYPE_WIDGET
    var container: Long,      // DESKTOP, HOTSEAT, or the id of a folder
    var screen: Int,
    var x: Int,               // for folder children: order index
    var y: Int,
    var title: String,
    var component: String?,   // flattened ComponentName (apps: the activity, widgets: the provider)
    var spanX: Int = 1,       // widgets cover spanX x spanY cells
    var spanY: Int = 1,
    var widgetId: Int = -1,   // AppWidgetHost id (widgets only)
) {
    companion object {
        const val TYPE_APP = 0
        const val TYPE_FOLDER = 1
        const val TYPE_WIDGET = 2
        const val DESKTOP = -100L
        const val HOTSEAT = -101L
    }
}

/** Home screen layout: pages of cells, a dock (hotseat), folders and widgets. Persisted as JSON. */
class HomeModel {
    var cols = 5
    var rows = 5
    var hotseat = 5
    var pages = 1
    var nextId = 1L
    val items = ArrayList<HomeItem>()

    fun nextId(): Long = nextId++
    fun find(id: Long): HomeItem? = items.firstOrNull { it.id == id }
    fun children(folderId: Long): List<HomeItem> = items.filter { it.container == folderId }.sortedBy { it.x }

    /** The item covering this cell (widgets cover several cells). */
    fun at(container: Long, screen: Int, x: Int, y: Int): HomeItem? = items.firstOrNull {
        it.container == container && (container != HomeItem.DESKTOP || it.screen == screen) &&
            x >= it.x && x < it.x + it.spanX && y >= it.y && y < it.y + it.spanY
    }

    fun remove(item: HomeItem) {
        items.remove(item)
        if (item.type == HomeItem.TYPE_FOLDER) items.removeAll { it.container == item.id }
        if (item.container > 0) tidyFolder(item.container)
    }

    /** Empty folder disappears; a folder with one app turns back into that app. */
    private fun tidyFolder(folderId: Long) {
        val folder = find(folderId) ?: return
        val kids = children(folderId)
        when {
            kids.isEmpty() -> items.remove(folder)
            kids.size == 1 -> {
                val k = kids[0]
                k.container = folder.container; k.screen = folder.screen; k.x = folder.x; k.y = folder.y
                items.remove(folder)
            }
            else -> kids.forEachIndexed { i, k -> k.x = i }
        }
    }

    private fun place(item: HomeItem, container: Long, screen: Int, x: Int, y: Int) {
        val old = item.container
        item.container = container; item.screen = screen; item.x = x; item.y = y
        if (old > 0 && old != container) tidyFolder(old)
    }

    /** Can a sx x sy block start at (x, y) on this page? [ignore] is allowed to be in the way (itself). */
    fun fits(ignore: HomeItem?, screen: Int, x: Int, y: Int, sx: Int, sy: Int): Boolean {
        if (x < 0 || y < 0 || x + sx > cols || y + sy > rows) return false
        for (dy in 0 until sy) {
            for (dx in 0 until sx) {
                val o = at(HomeItem.DESKTOP, screen, x + dx, y + dy)
                if (o != null && o !== ignore) return false
            }
        }
        return true
    }

    fun findSpace(screen: Int, sx: Int, sy: Int): Pair<Int, Int>? {
        for (y in 0..(rows - sy)) {
            for (x in 0..(cols - sx)) {
                if (fits(null, screen, x, y, sx, sy)) return x to y
            }
        }
        return null
    }

    fun resize(widget: HomeItem, sx: Int, sy: Int): Boolean {
        if (!fits(widget, widget.screen, widget.x, widget.y, sx, sy)) return false
        widget.spanX = sx
        widget.spanY = sy
        return true
    }

    /** Drop an existing item on a cell: move it, or merge into a folder. */
    fun dropAt(moving: HomeItem, container: Long, screen: Int, x: Int, y: Int): Boolean {
        if (moving.type == HomeItem.TYPE_WIDGET) {
            if (container != HomeItem.DESKTOP) return false
            if (!fits(moving, screen, x, y, moving.spanX, moving.spanY)) return false
            place(moving, container, screen, x, y)
            return true
        }
        val target = at(container, screen, x, y)
        if (target === moving) return true
        when {
            target == null -> place(moving, container, screen, x, y)
            target.type == HomeItem.TYPE_WIDGET -> return false
            target.type == HomeItem.TYPE_FOLDER -> {
                if (moving.type == HomeItem.TYPE_FOLDER) return false
                if (moving.container == target.id) return true
                place(moving, target.id, 0, children(target.id).size, 0)
            }
            else -> {
                if (moving.type == HomeItem.TYPE_FOLDER) return false
                val folder = HomeItem(nextId(), HomeItem.TYPE_FOLDER, target.container, target.screen, target.x, target.y, "Folder", null)
                items.add(folder)
                target.container = folder.id; target.screen = 0; target.x = 0; target.y = 0
                place(moving, folder.id, 0, 1, 0)
            }
        }
        return true
    }

    fun firstFree(container: Long, screen: Int): Pair<Int, Int>? {
        val c = if (container == HomeItem.HOTSEAT) hotseat else cols
        val r = if (container == HomeItem.HOTSEAT) 1 else rows
        for (y in 0 until r) for (x in 0 until c) if (at(container, screen, x, y) == null) return x to y
        return null
    }

    fun addApp(component: String, label: String, preferredScreen: Int): Boolean {
        val order = listOf(preferredScreen) + (0 until pages).filter { it != preferredScreen }
        for (s in order) {
            val c = firstFree(HomeItem.DESKTOP, s) ?: continue
            items.add(HomeItem(nextId(), HomeItem.TYPE_APP, HomeItem.DESKTOP, s, c.first, c.second, label, component))
            return true
        }
        return false
    }

    fun isPageEmpty(index: Int) = items.none { it.container == HomeItem.DESKTOP && it.screen == index }

    fun removePage(index: Int): Boolean {
        if (pages <= 1 || !isPageEmpty(index)) return false
        items.filter { it.container == HomeItem.DESKTOP && it.screen > index }.forEach { it.screen-- }
        pages--
        return true
    }

    /** Fix items that no longer fit after the grid or dock size changed. Returns true if anything moved. */
    fun normalize(): Boolean {
        var changed = false
        if (pages < 1) { pages = 1; changed = true }
        val maxScreen = items.filter { it.container == HomeItem.DESKTOP }.maxOfOrNull { it.screen } ?: 0
        if (maxScreen >= pages) { pages = maxScreen + 1; changed = true }

        // Widgets: shrink and shift them back into the grid.
        for (w in items.filter { it.type == HomeItem.TYPE_WIDGET }) {
            val sx = w.spanX.coerceIn(1, cols)
            val sy = w.spanY.coerceIn(1, rows)
            val nx = w.x.coerceIn(0, cols - sx)
            val ny = w.y.coerceIn(0, rows - sy)
            if (sx != w.spanX || sy != w.spanY || nx != w.x || ny != w.y) {
                w.spanX = sx; w.spanY = sy; w.x = nx; w.y = ny
                changed = true
            }
        }

        val misplaced = items.filter {
            it.type != HomeItem.TYPE_WIDGET &&
                ((it.container == HomeItem.DESKTOP && (it.x >= cols || it.y >= rows)) ||
                    (it.container == HomeItem.HOTSEAT && it.x >= hotseat))
        }
        for (m in misplaced) {
            var placed = false
            for (s in 0 until pages) {
                val c = firstFree(HomeItem.DESKTOP, s)
                if (c != null) {
                    m.container = HomeItem.DESKTOP; m.screen = s; m.x = c.first; m.y = c.second
                    placed = true
                    break
                }
            }
            if (!placed) {
                m.container = HomeItem.DESKTOP; m.screen = pages; m.x = 0; m.y = 0
                pages++
            }
            changed = true
        }
        return changed
    }

    /** Remove apps that are no longer installed. */
    fun removeMissing(installed: Set<String>): Boolean {
        val gone = items.filter { it.type == HomeItem.TYPE_APP && it.component !in installed }
        if (gone.isEmpty()) return false
        gone.forEach { remove(it) }
        return true
    }

    fun toJson(): String {
        val arr = JSONArray()
        for (i in items) {
            arr.put(
                JSONObject().put("id", i.id).put("t", i.type).put("c", i.container).put("s", i.screen)
                    .put("x", i.x).put("y", i.y).put("n", i.title).put("k", i.component ?: "")
                    .put("sx", i.spanX).put("sy", i.spanY).put("w", i.widgetId)
            )
        }
        return JSONObject().put("pages", pages).put("next", nextId).put("items", arr).toString()
    }

    fun save(ctx: Context) {
        val f = file(ctx)
        val tmp = File(f.path + ".tmp")
        tmp.writeText(toJson())
        tmp.renameTo(f)
    }

    companion object {
        fun file(ctx: Context) = File(ctx.filesDir, "home.json")
        fun exists(ctx: Context) = file(ctx).exists()

        fun load(ctx: Context): HomeModel {
            val m = HomeModel()
            try {
                val o = JSONObject(file(ctx).readText())
                m.pages = o.optInt("pages", 1)
                m.nextId = o.optLong("next", 1)
                val a = o.getJSONArray("items")
                for (i in 0 until a.length()) {
                    val j = a.getJSONObject(i)
                    m.items.add(
                        HomeItem(
                            j.getLong("id"), j.getInt("t"), j.getLong("c"), j.getInt("s"),
                            j.getInt("x"), j.getInt("y"), j.optString("n"), j.optString("k").ifEmpty { null },
                            j.optInt("sx", 1), j.optInt("sy", 1), j.optInt("w", -1),
                        )
                    )
                }
            } catch (e: Exception) {
                // missing or corrupt file: start empty
            }
            return m
        }
    }
}
