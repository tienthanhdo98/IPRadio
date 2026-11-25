package app.tek4tv.ipradio.model

import android.util.Log
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import java.io.File
import java.io.Serializable
import androidx.core.net.toUri

@JsonClass(generateAdapter = true)
data class MediaItem(

    @Json(name = "ID")
    val id: Int? = null,

    @Json(name = "Start")
    val start: String? = null,

    @Json(name = "End")
    val end: String? = null,

    @Json(name = "FixTime")
    val fixTime: String? = null,

    @Json(name = "MediaID")
    var mediaID: String? = null,

    @Json(name = "SourceID")
    val sourceID: String? = null,

    @Json(name = "SourceName")
    val sourceName: String? = null,

    @Json(name = "DestinationID")
    val destinationID: String? = null,

    @Json(name = "DestinationName")
    val destinationName: String? = null,

    @Json(name = "ContentType")
    val contentType: ContentType? = null,

    @Json(name = "FieldType")
    val fieldType: Category? = null,

    @Json(name = "Title")
    var title: String? = null,

    @Json(name = "Description")
    val description: String? = null,

    @Json(name = "Duration")
    val duration: String? = null,

    @Json(name = "Priority")
    val priority: Priority? = null,

    @Json(name = "Category")
    val category: Category? = null,

    @Json(name = "Author")
    val author: Author? = null,

    @Json(name = "Body")
    var body: String? = null,

    @Json(name = "SourceReference")
    val sourceReference: String? = null,

    @Json(name = "StartTime")
    val startTime: String? = null,

    @Json(name = "Type")
    val type: String? = null,

    @Json(name = "CreateDate")
    val createDate: String? = null,

    @Json(name = "Metadata")
    val metadata: List<Any?>? = null,

    @Json(name = "Tag")
    val tag: List<Any?>? = null,

    @Json(name = "ProjectID")
    val projectID: String? = null,

    @Json(name = "PathBackup")
    var pathBackup: String = ""

    ) : Serializable {

    @Json(name = "Level")
    var level: Int = -1

    init {
        if (pathBackup.isEmpty()) {
            pathBackup = body ?: ""
        }
    }


    fun getMediaType(): MediaType {
        return when (type) {
            "VOD" -> MediaType.FILE
            "LIVE" -> MediaType.LIVE
            "FM" -> MediaType.FM
            "AM" -> MediaType.AM
            else -> MediaType.UNKNOWN
        }
    }

    fun getVlcMedia(mLibVLC: LibVLC): Media {
        val currentBody = body
        if (getMediaType() == MediaType.LIVE) {
            val url = prepareMediaUrl(currentBody, false) ?: ""
            return Media(mLibVLC, url.toUri())
        }
        return if (!currentBody.isNullOrEmpty() && File(currentBody).exists()) {
            Log.d("player", "local: $currentBody")
            Media(mLibVLC, "file://$currentBody".toUri())
        } else {
            Log.d("player", "online: $pathBackup")
            val onlineUrl = prepareMediaUrl(pathBackup, false) ?: ""
            Media(mLibVLC, onlineUrl.toUri())
        }
    }

    fun prepareMediaUrl(originalUrl: String?, forceHttp: Boolean): String? {
        if (originalUrl == null || originalUrl.isEmpty()) {
            return originalUrl
        }

        var cleanedUrl: String? = originalUrl

        // Xóa các dấu '//' thừa trong đường dẫn (sau tên miền).
        cleanedUrl = cleanedUrl!!.replace("(?<!:)//".toRegex(), "/")

        // Mã hóa ký tự '#' thành '%23' để tránh VLC và HTTP client cắt cụt đường dẫn xem như fragment
        if (cleanedUrl.contains("#")) {
            cleanedUrl = cleanedUrl.replace("#", "%23")
        }

        // Hạ cấp từ HTTPS xuống HTTP nếu bạn muốn bỏ qua lỗi chứng chỉ (và server hỗ trợ HTTP)
        if (forceHttp && cleanedUrl.startsWith("https://")) {
            cleanedUrl = cleanedUrl.replaceFirst("https://".toRegex(), "http://")
        }

        return cleanedUrl
    }

}

enum class MediaType {
    FILE,
    LIVE,
    FM,
    AM,
    UNKNOWN
}

data class Author(
    @Json(name = "Id")
    val id: String? = null,

    @Json(name = "FullName")
    val fullName: String? = null,

    @Json(name = "UserName")
    val userName: String? = null,

    @Json(name = "Avatar")
    val avatar: String? = null,

    @Json(name = "Email")
    val email: String? = null
)

data class Category(
    @Json(name = "Id")
    val id: Long? = null,

    @Json(name = "Value")
    val value: String? = null
)

data class ContentType(
    @Json(name = "Id")
    val id: Long? = null,

    @Json(name = "Value")
    val value: String? = null
)

data class Priority(
    @Json(name = "Id")
    val id: Long? = null,

    @Json(name = "Value")
    val value: String? = null
)


/*
@JsonClass(generateAdapter = true)
data class Category(

    @Json(name = "ID")
    var id: Long = 0,

    @Json(name = "Name")
    var name: String? = null
)


class ResponseList {
    @Json(name = "Playlist")
    var playlists: List<MediaItem>? = null
}
*/
