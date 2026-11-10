package com.litelauncher

import android.graphics.Path
import android.graphics.RectF

/** Icon masks applied to adaptive icons (the default app icons). Icon-pack icons are drawn as designed. */
object IconShape {
    const val SYSTEM = 0
    const val CIRCLE = 1
    const val SQUIRCLE = 2
    const val ROUNDED = 3
    const val TEARDROP = 4

    fun path(shape: Int, size: Float): Path {
        val p = Path()
        val r = RectF(0f, 0f, size, size)
        val half = size / 2f
        when (shape) {
            CIRCLE -> p.addOval(r, Path.Direction.CW)
            SQUIRCLE -> p.addRoundRect(r, size * 0.34f, size * 0.34f, Path.Direction.CW)
            ROUNDED -> p.addRoundRect(r, size * 0.2f, size * 0.2f, Path.Direction.CW)
            TEARDROP -> {
                val small = size * 0.12f
                // top-left, top-right, bottom-right, bottom-left (x and y radius each)
                p.addRoundRect(r, floatArrayOf(half, half, half, half, small, small, half, half), Path.Direction.CW)
            }
            else -> p.addRect(r, Path.Direction.CW)
        }
        return p
    }
}
