package app.tek4tv.ipradio.ui

import android.content.res.Resources
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import app.tek4tv.ipradio.R
import app.tek4tv.ipradio.model.MediaItem

class AutoScrollAdapter(private val items: List<MediaItem>,private val resources: Resources) :
    RecyclerView.Adapter<AutoScrollAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.findViewById(R.id.textView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_text, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        try {
            val item = items[position % items.size]  // Lặp lại danh sách
            holder.textView.text = item.body
            holder.textView.textSize =  holder.textView.textSize / resources.displayMetrics.density
        }catch (_: Exception)
        {

        }

    }

    override fun getItemCount(): Int = Int.MAX_VALUE  // Tạo danh sách vô tận
}