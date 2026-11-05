package app.tek4tv.ipradio.ui

import android.content.res.Resources
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import app.tek4tv.ipradio.R
import app.tek4tv.ipradio.model.MediaItem
import java.io.File

class AutoScrollAdapter(
    items: List<MediaItem>,
    private val resources: Resources
) : RecyclerView.Adapter<AutoScrollAdapter.ViewHolder>() {

    private val displayList: List<CharSequence> = items.mapNotNull { extractDisplayText(it) }

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
            if (displayList.isNotEmpty()) {
                val content = displayList[position % displayList.size]
                holder.textView.text = content
            }
        } catch (_: Exception) {
        }
    }

    override fun getItemCount(): Int = if (displayList.isEmpty()) 0 else Int.MAX_VALUE

    companion object {
        private val MEDIA_EXTENSIONS = hashSetOf(
            "mp4", "mkv", "avi", "mov", "flv", "ts", "wmv", "3gp",
            "mp3", "m4a", "aac", "wav", "ogg", "flac", "wma",
            "jpg", "jpeg", "png", "webp", "gif", "bmp"
        )

        fun isMediaResourcePath(path: String?): Boolean {
            if (path.isNullOrBlank()) return false
            val clean = path.trim().lowercase()
            if (clean.startsWith("http://") || clean.startsWith("https://") ||
                clean.startsWith("rtmp://") || clean.startsWith("rtsp://")
            ) {
                return true
            }
            val ext = clean.substringAfterLast('.', "")
            if (MEDIA_EXTENSIONS.contains(ext)) {
                return true
            }
            if (clean.startsWith("/data/") || clean.startsWith("/storage/") ||
                clean.startsWith("/sdcard/") || clean.startsWith("file://")
            ) {
                return true
            }
            return false
        }

        fun extractDisplayText(item: MediaItem): CharSequence? {
            val title = item.title?.trim().orEmpty()
            val desc = item.description?.trim().orEmpty()

            var bodyContent = ""
            val body = item.body?.trim().orEmpty()
            if (body.isNotEmpty() && body != "start" && body != "end") {
                val isLocalPath = body.startsWith("/") || body.startsWith("file://")
                val isTxt = body.endsWith(".txt", ignoreCase = true) || body.endsWith(".text", ignoreCase = true)
                if (isLocalPath && isTxt) {
                    try {
                        val filePath = if (body.startsWith("file://")) body.substring(7) else body
                        val file = File(filePath)
                        if (file.exists() && file.isFile) {
                            bodyContent = file.readText(Charsets.UTF_8).trim()
                        }
                    } catch (_: Exception) {
                    }
                } else if (!isMediaResourcePath(body)) {
                    bodyContent = body
                }
            }

            val hasTitle = title.isNotEmpty()
            val hasDesc = desc.isNotEmpty() && !desc.equals(title, ignoreCase = true)
            val hasBody = bodyContent.isNotEmpty() &&
                    !bodyContent.equals(title, ignoreCase = true) &&
                    !bodyContent.equals(desc, ignoreCase = true)

            if (!hasTitle && !hasDesc && !hasBody) {
                return null
            }

            val builder = SpannableStringBuilder()
            if (hasTitle) {
                val start = builder.length
                builder.append(title)
                builder.setSpan(
                    StyleSpan(Typeface.BOLD),
                    start,
                    builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            if (hasDesc) {
                if (builder.isNotEmpty()) builder.append("\n\n")
                builder.append(desc)
            }
            if (hasBody) {
                if (builder.isNotEmpty()) builder.append("\n\n")
                builder.append(bodyContent)
            }
            return builder
        }
    }
}
