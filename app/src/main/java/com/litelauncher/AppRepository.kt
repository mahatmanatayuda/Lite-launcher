package com.litelauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent

object AppRepository {
    fun load(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .map {
                val ai = it.activityInfo
                AppInfo(it.loadLabel(pm).toString(), ai.packageName, ComponentName(ai.packageName, ai.name))
            }
            .sortedBy { it.label.lowercase() }
    }
}
