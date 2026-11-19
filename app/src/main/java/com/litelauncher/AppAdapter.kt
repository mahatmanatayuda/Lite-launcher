package com.litelauncher

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private val icons: IconManager,
    private val onClick: (AppInfo) -> Unit,
    private val onLong: (View, AppInfo) -> Unit,
    private val onDragStart: (View, AppInfo) -> Unit,
) : RecyclerView.Adapter<AppAdapter.VH>() {

    private var items: List<AppInfo> = emptyList()
    var iconSizePx = 0
    var showLabels = true
    var showBadges = false

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        var longPressed = false
        var downX = 0f
        var downY = 0f
    }

    fun submit(list: List<AppInfo>) {
        items = list
        notifyDataSetChanged()
    }

    fun first(): AppInfo? = items.firstOrNull()

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false))

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = items[position]
        val v = holder.itemView
        v.findViewById<View>(R.id.folder_icon).visibility = View.GONE
        val icon = v.findViewById<ImageView>(R.id.icon)
        icon.visibility = View.VISIBLE
        val label = v.findViewById<TextView>(R.id.label)
        label.text = app.label
        label.visibility = if (showLabels) View.VISIBLE else View.GONE
        // Screen readers still need the name when the visible label is turned off.
        v.contentDescription = app.label
        v.findViewById<View>(R.id.badge).visibility =
            if (showBadges && NotifState.has(app.packageName)) View.VISIBLE else View.GONE
        bindIcon(icon, app.component, iconSizePx, icons)

        v.setOnClickListener { onClick(app) }
        // Long press opens the menu. If the finger then keeps moving, the icon is dragged to the home screen.
        v.setOnLongClickListener {
            holder.longPressed = true
            v.parent?.requestDisallowInterceptTouchEvent(true)
            onLong(v, app)
            true
        }
        val slop = ViewConfiguration.get(v.context).scaledTouchSlop * 2f
        v.setOnTouchListener { view, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    holder.longPressed = false
                    holder.downX = ev.rawX
                    holder.downY = ev.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    if (holder.longPressed) {
                        val dx = ev.rawX - holder.downX
                        val dy = ev.rawY - holder.downY
                        if (dx * dx + dy * dy > slop * slop) {
                            holder.longPressed = false
                            onDragStart(view, app)
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> holder.longPressed = false
            }
            false
        }
    }
}
