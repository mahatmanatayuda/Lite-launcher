package com.litelauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * Overlay for resizing a widget in whole cells. Drag the round handle on the right edge to change the
 * width, the one on the bottom edge to change the height. Tapping anywhere else finishes.
 *
 * [originX]/[originY] are the widget's top-left corner in this view's coordinates, [cellW]/[cellH] the
 * size of one grid cell. [fits] says whether a given size is free on the page.
 */
class WidgetResizeView(
    context: Context,
    private val originX: Float,
    private val originY: Float,
    private val cellW: Float,
    private val cellH: Float,
    startX: Int,
    startY: Int,
    private val fits: (Int, Int) -> Boolean,
    private val onDone: (Int, Int) -> Unit,
) : View(context) {

    private var spanX = startX
    private var spanY = startY
    private var mode = NONE
    private var finished = false

    private val density = resources.displayMetrics.density
    private val accent = 0xFF7C9CFF.toInt()
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = accent
    }
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val handleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = accent
    }
    private val rect = RectF()

    private fun right() = originX + spanX * cellW
    private fun bottom() = originY + spanY * cellH
    private fun rightHandleY() = originY + spanY * cellH / 2f
    private fun bottomHandleX() = originX + spanX * cellW / 2f

    private fun near(x: Float, y: Float, cx: Float, cy: Float): Boolean {
        val slop = 28f * density
        return abs(x - cx) < slop && abs(y - cy) < slop
    }

    /** Finish now, keeping the current size. */
    fun commit() {
        if (finished) return
        finished = true
        onDone(spanX, spanY)
    }

    override fun onDraw(canvas: Canvas) {
        rect.set(originX, originY, right(), bottom())
        canvas.drawRoundRect(rect, 8f * density, 8f * density, framePaint)
        val r = 10f * density
        canvas.drawCircle(right(), rightHandleY(), r, handleFill)
        canvas.drawCircle(right(), rightHandleY(), r, handleStroke)
        canvas.drawCircle(bottomHandleX(), bottom(), r, handleFill)
        canvas.drawCircle(bottomHandleX(), bottom(), r, handleStroke)
    }

    private fun tryResize(sx: Int, sy: Int) {
        if ((sx != spanX || sy != spanY) && fits(sx, sy)) {
            spanX = sx
            spanY = sy
            invalidate()
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mode = when {
                    near(e.x, e.y, right(), rightHandleY()) -> RIGHT
                    near(e.x, e.y, bottomHandleX(), bottom()) -> BOTTOM
                    else -> {
                        commit()
                        NONE
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == RIGHT) {
                    tryResize(((e.x - originX) / cellW + 0.5f).toInt().coerceAtLeast(1), spanY)
                } else if (mode == BOTTOM) {
                    tryResize(spanX, ((e.y - originY) / cellH + 0.5f).toInt().coerceAtLeast(1))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> mode = NONE
        }
        return true
    }

    companion object {
        private const val NONE = 0
        private const val RIGHT = 1
        private const val BOTTOM = 2
    }
}
