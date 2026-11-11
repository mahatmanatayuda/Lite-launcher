package com.litelauncher

import android.content.Context
import android.net.Uri
import org.json.JSONObject

/** Export / import of the whole launcher state (home layout + every setting) as one JSON file. */
object BackupManager {

    fun write(ctx: Context, uri: Uri) {
        val o = JSONObject()
        o.put("app", "LiteLauncher")
        o.put("version", 1)
        o.put("prefs", Prefs(ctx).toJson())
        val f = HomeModel.file(ctx)
        if (f.exists()) o.put("home", JSONObject(f.readText()))
        val out = ctx.contentResolver.openOutputStream(uri, "wt") ?: throw IllegalStateException("Cannot write file")
        out.use { it.write(o.toString(2).toByteArray(Charsets.UTF_8)) }
    }

    fun read(ctx: Context, uri: Uri) {
        val input = ctx.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Cannot read file")
        val text = input.use { String(it.readBytes(), Charsets.UTF_8) }
        val o = JSONObject(text)
        if (o.optString("app") != "LiteLauncher") throw IllegalStateException("Not a Lite Launcher backup")
        Prefs(ctx).fromJson(o.getJSONObject("prefs"))
        val f = HomeModel.file(ctx)
        if (o.has("home")) f.writeText(o.getJSONObject("home").toString()) else f.delete()
    }
}
