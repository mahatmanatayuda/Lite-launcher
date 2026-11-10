package com.litelauncher

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Reads a Nova Launcher backup (.novabackup = zip with a SQLite database + preferences XML)
 * and rebuilds the home layout from the database's `favorites` table.
 */
object NovaImporter {

    class Result(
        val model: HomeModel,
        val cols: Int,
        val rows: Int,
        val hotseat: Int,
        val apps: Int,
        val folders: Int,
        val skippedOther: Int,
        val missingApps: Int,
    )

    private class Row(
        val id: Long, val title: String, val intent: String?, val container: Long,
        val screen: Int, val x: Int, val y: Int, val type: Int, val rank: Int,
    )

    private fun Cursor.long(name: String, def: Long = 0): Long {
        val i = getColumnIndex(name)
        return if (i < 0 || isNull(i)) def else getLong(i)
    }

    private fun Cursor.str(name: String): String? {
        val i = getColumnIndex(name)
        return if (i < 0 || isNull(i)) null else getString(i)
    }

    fun import(ctx: Context, uri: Uri, installed: List<AppInfo>): Result {
        val dbs = ArrayList<Pair<String, File>>()
        val input = ctx.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Cannot open file")
        input.use { raw ->
            ZipInputStream(raw).use { zip ->
                var e = zip.nextEntry
                var n = 0
                while (e != null) {
                    if (!e.isDirectory && e.name.lowercase().endsWith(".db")) {
                        val f = File(ctx.cacheDir, "nova_import_${n++}.db")
                        f.outputStream().use { zip.copyTo(it) }
                        dbs.add(e.name.lowercase() to f)
                    }
                    e = zip.nextEntry
                }
            }
        }
        if (dbs.isEmpty()) throw IllegalStateException("No database found in backup")
        val chosen = dbs.firstOrNull { it.first.contains("launcher") } ?: dbs[0]
        try {
            val db = SQLiteDatabase.openDatabase(chosen.second.path, null, SQLiteDatabase.OPEN_READONLY)
            try {
                return build(db, installed)
            } finally {
                db.close()
            }
        } finally {
            dbs.forEach { it.second.delete() }
        }
    }

    private fun build(db: SQLiteDatabase, installed: List<AppInfo>): Result {
        db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='favorites'", null).use {
            if (!it.moveToFirst()) throw IllegalStateException("No favorites table in backup")
        }
        val rows = ArrayList<Row>()
        db.rawQuery("SELECT * FROM favorites", null).use { c ->
            while (c.moveToNext()) {
                rows.add(
                    Row(
                        c.long("_id"), c.str("title") ?: "", c.str("intent"), c.long("container"),
                        c.long("screen").toInt(), c.long("cellX").toInt(), c.long("cellY").toInt(),
                        c.long("itemType").toInt(), c.long("rank").toInt(),
                    )
                )
            }
        }

        fun resolve(intent: String?): AppInfo? {
            if (intent.isNullOrEmpty()) return null
            val comp = try { Intent.parseUri(intent, 0).component } catch (e: Exception) { null } ?: return null
            return installed.firstOrNull { it.component == comp }
                ?: installed.firstOrNull { it.packageName == comp.packageName }
        }

        val model = HomeModel()
        var apps = 0
        var folders = 0
        var skipped = 0
        var missing = 0

        val desktopScreens = rows.filter { it.container == HomeItem.DESKTOP }.map { it.screen }.distinct().sorted()
        val screenIndex = desktopScreens.withIndex().associate { it.value to it.index }
        model.pages = maxOf(1, desktopScreens.size)

        fun addTopLevel(r: Row, container: Long, screen: Int, x: Int, y: Int) {
            when (r.type) {
                0, 1 -> {
                    val app = resolve(r.intent)
                    if (app == null) { missing++; return }
                    model.items.add(
                        HomeItem(model.nextId(), HomeItem.TYPE_APP, container, screen, x, y, app.label, app.component.flattenToString())
                    )
                    apps++
                }
                2 -> {
                    val kids = rows.filter { it.container == r.id }
                        .sortedWith(compareBy({ it.rank }, { it.y }, { it.x }))
                        .mapNotNull { resolve(it.intent) }
                    missing += rows.count { it.container == r.id } - kids.size
                    when {
                        kids.isEmpty() -> {}
                        kids.size == 1 -> {
                            val k = kids[0]
                            model.items.add(
                                HomeItem(model.nextId(), HomeItem.TYPE_APP, container, screen, x, y, k.label, k.component.flattenToString())
                            )
                            apps++
                        }
                        else -> {
                            val folder = HomeItem(
                                model.nextId(), HomeItem.TYPE_FOLDER, container, screen, x, y,
                                r.title.ifBlank { "Folder" }, null,
                            )
                            model.items.add(folder)
                            kids.forEachIndexed { i, k ->
                                model.items.add(
                                    HomeItem(model.nextId(), HomeItem.TYPE_APP, folder.id, 0, i, 0, k.label, k.component.flattenToString())
                                )
                            }
                            folders++
                            apps += kids.size
                        }
                    }
                }
                else -> skipped++
            }
        }

        // Desktop
        for (r in rows.filter { it.container == HomeItem.DESKTOP }) {
            addTopLevel(r, HomeItem.DESKTOP, screenIndex[r.screen] ?: 0, r.x, r.y)
        }
        // Dock: keep order, drop gaps
        val dockRows = rows.filter { it.container == HomeItem.HOTSEAT }.sortedWith(compareBy({ it.screen }, { it.x }, { it.y }))
        var slot = 0
        for (r in dockRows) {
            val before = model.items.size
            addTopLevel(r, HomeItem.HOTSEAT, 0, slot, 0)
            if (model.items.size > before) slot++
        }
        // Items in folders / unknown containers that are not top-level are handled via their folder.
        rows.filter { it.container != HomeItem.DESKTOP && it.container != HomeItem.HOTSEAT && rows.none { f -> f.id == it.container } }
            .forEach { _ -> skipped++ }

        val desktopItems = model.items.filter { it.container == HomeItem.DESKTOP }
        val cols = ((desktopItems.maxOfOrNull { it.x } ?: 3) + 1).coerceIn(4, 8)
        val rowsCount = ((desktopItems.maxOfOrNull { it.y } ?: 3) + 1).coerceIn(4, 8)
        val hotseat = maxOf(slot, 4).coerceIn(3, 7)
        return Result(model, cols, rowsCount, hotseat, apps, folders, skipped, missing)
    }
}
