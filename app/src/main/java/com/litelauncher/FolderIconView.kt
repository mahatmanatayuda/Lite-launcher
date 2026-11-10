package com.litelauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** Folder icon: translucent rounded square with up to 4 mini icons. */
class FolderIconView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF }
    private val bmps = arrayOfNulls<Bitmap>(4)
    private val dst = RectF()

    fun clearIcons() {
        for (i in bmps.indices) bmps[i] = null
        invalidate()
    }

    fun setIcon(index: Int, b: Bitmap) {
        if (index in bmps.indices) {
            bmps[index] = b
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val s = minOf(width, height).toFloat()
        val left = (width - s) / 2f
        val top = (height - s) / 2f
        canvas.drawRoundRect(left, top, left + s, top + s, s * 0.3f, s * 0.3f, bg)
        val pad = s * 0.1f
        val gap = s * 0.06f
        val mini = (s - 2 * pad - gap) / 2f
        for (i in 0 until 4) {
            val b = bmps[i] ?: continue
            val x = left + pad + (i % 2) * (mini + gap)
            val y = top + pad + (i / 2) * (mini + gap)
            dst.set(x, y, x + mini, y + mini)
            canvas.drawBitmap(b, null, dst, null)
        }
    }
}
