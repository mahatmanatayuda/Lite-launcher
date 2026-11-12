package com.litelauncher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs

/** A widget view that reports a long press, so a widget can be dragged like an icon. */
class LauncherWidgetView(context: Context) : AppWidgetHostView(context) {
    var onLongPress: (() -> Unit)? = null

    private var fired = false
    private var downX = 0f
    private var downY = 0f
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPress = Runnable {
        fired = true
        onLongPress?.invoke()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                fired = false
                downX = ev.x
                downY = ev.y
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop) removeCallbacks(longPress)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(longPress)
        }
        return fired
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Tell the widget how much room it has so responsive widgets pick the right layout.
        val d = resources.displayMetrics.density
        val wd = (w / d).toInt()
        val hd = (h / d).toInt()
        updateAppWidgetSize(Bundle(), wd, hd, wd, hd)
    }
}

class LauncherWidgetHost(context: Context, id: Int) : AppWidgetHost(context, id) {
    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?,
    ): AppWidgetHostView = LauncherWidgetView(context)
}
