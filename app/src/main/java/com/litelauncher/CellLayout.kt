package com.litelauncher

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup

/** A simple grid: every child sits in one cell (cx, cy). */
class CellLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : ViewGroup(context, attrs) {
    var cols = 4
    var rows = 5

    class LP(val cx: Int, val cy: Int) : ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)

    override fun checkLayoutParams(p: ViewGroup.LayoutParams?) = p is LP
    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams = LP(0, 0)
    override fun generateLayoutParams(p: ViewGroup.LayoutParams?): ViewGroup.LayoutParams = LP(0, 0)

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        val h = MeasureSpec.getSize(heightSpec)
        setMeasuredDimension(w, h)
        val cw = w / cols
        val ch = h / rows
        for (i in 0 until childCount) {
            getChildAt(i).measure(
                MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY),
            )
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val cw = (r - l) / cols
        val ch = (b - t) / rows
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val lp = c.layoutParams as LP
            c.layout(lp.cx * cw, lp.cy * ch, (lp.cx + 1) * cw, (lp.cy + 1) * ch)
        }
    }

    /** Cell under a point given in this view's own coordinates. */
    fun cellAt(px: Float, py: Float): Pair<Int, Int> {
        val cw = (width.toFloat() / cols).coerceAtLeast(1f)
        val ch = (height.toFloat() / rows).coerceAtLeast(1f)
        return (px / cw).toInt().coerceIn(0, cols - 1) to (py / ch).toInt().coerceIn(0, rows - 1)
    }
}
