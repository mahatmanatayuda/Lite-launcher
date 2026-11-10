package com.litelauncher

import android.content.ComponentName

data class AppInfo(
    val label: String,
    val packageName: String,
    val component: ComponentName,
)
