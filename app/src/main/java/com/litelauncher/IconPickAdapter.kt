package com.litelauncher

import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class IconPickAdapter(
    private val pack: IconPack,
    private val icons: IconManager,
    private val sizePx: Int,
    private val onPick: (String) -> Unit,
) : RecyclerView.Adapter<IconPickAdapter.VH>() {

    private var names: List<String> = pack.names

    class VH(val iv: ImageView) : RecyclerView.ViewHolder(iv)

    fun filter(q: String) {
        val s = q.trim()
        names = if (s.isEmpty()) pack.names else pack.names.filter { it.contains(s, ignoreCase = true) }
        notifyDataSetChanged()
    }

    override fun getItemCount() = names.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val pad = (8 * parent.resources.displayMetrics.density).toInt()
        val iv = ImageView(parent.context)
        iv.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, sizePx + 2 * pad)
        iv.setPadding(pad, pad, pad, pad)
        return VH(iv)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val n = names[position]
        holder.iv.tag = n
        holder.iv.setImageDrawable(null)
        icons.loadPackDrawable(pack.pkg, n, sizePx) { if (holder.iv.tag == n) holder.iv.setImageBitmap(it) }
        holder.iv.setOnClickListener { onPick(n) }
    }
}
