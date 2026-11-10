package com.litelauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private val icons: IconManager,
    private val onClick: (AppInfo) -> Unit,
    private val onLong: (View, AppInfo) -> Unit,
) : RecyclerView.Adapter<AppAdapter.VH>() {

    private var items: List<AppInfo> = emptyList()
    var iconSizePx = 0
    var showLabels = true

    class VH(v: View) : RecyclerView.ViewHolder(v)

    fun submit(list: List<AppInfo>) {
        items = list
        notifyDataSetChanged()
    }

    fun first(): AppInfo? = items.firstOrNull()

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = items[position]
        val v = holder.itemView
        v.findViewById<View>(R.id.folder_icon).visibility = View.GONE
        val icon = v.findViewById<ImageView>(R.id.icon)
        icon.visibility = View.VISIBLE
        val label = v.findViewById<TextView>(R.id.label)
        label.text = app.label
        label.visibility = if (showLabels) View.VISIBLE else View.GONE
        bindIcon(icon, app.component, iconSizePx, icons)
        v.setOnClickListener { onClick(app) }
        v.setOnLongClickListener { onLong(v, app); true }
    }
}
