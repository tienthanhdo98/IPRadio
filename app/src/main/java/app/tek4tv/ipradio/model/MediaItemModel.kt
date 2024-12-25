package app.tek4tv.ipradio.model

import android.net.Uri
import android.util.Log
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import java.io.File
import java.io.Serializable

@JsonClass(generateAdapter = true)
data class MediaItemModel(
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

    @Json(name = "Title")
    val title: String? = null,

    @Json(name = "Description")
    val description: String? = null,

    @Json(name = "Duration")
    val duration: String? = null,

    @Json(name = "Priority")
    val priority: Priority? = null,

    @Json(name = "Category")
    val category: Category? = null,

    @Json(name = "BroadcastRegion")
    val broadcastRegion: BroadcastRegion? = null,

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

    @Json(name = "ProjectID")
    val projectID: String? = null,

    @Json(name = "Metadata")
    val metadata: List<Any?>? = null,

    @Json(name = "Tag")
    val tag: List<Any?>? = null,
    @Json(name = "FieldType")
    val fieldType: Category? = null,

) : Serializable {

    @Json(name = "Level")
    var level: Int = -1

    var pathBackup: String = ""
        set(value) {
            if (value.startsWith("http"))
                field = value
        }

    init {
        pathBackup = body!!
    }

    fun getFileName() = if (body!!.startsWith("http")) File(Uri.parse(body).path).name
    else File("file://$body").name

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
//        if (getMediaType() == MediaType.LIVE) {
//            Log.d("player", "live: $body")
//            return Media(mLibVLC, Uri.parse(body))
//        }
        return if (body!!.isNotEmpty() && File(body).exists()) {
            Log.d("player", "local: $body")
            Media(mLibVLC, Uri.parse("file://$body"))
        } else {
            Log.d("player", "online: $pathBackup")
            Media(mLibVLC, Uri.parse(pathBackup))
        }
    }

    companion object {
        const val TUNER_REG = "^\\d,\\d+,\\d+,\\d+,[ -~]++\$"
    }
}

enum class MediaType {
    FILE,
    LIVE,
    FM,
    AM,
    UNKNOWN
}
data class BroadcastRegion (
    @Json(name = "Id")
    val id: String?,

    @Json(name = "Value")
    val value: String
)

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

data class Tag(
    val destinationID: String,
    val destinationName: String
)


