package app.tek4tv.ipradio.model

import android.net.Uri
import android.util.Log
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.File
import java.io.Serializable

@Entity(tableName = "media_logs")
@JsonClass(generateAdapter = true)
data class MediaItem(
    @PrimaryKey(autoGenerate = true)
    @Json(name = "ID")
    val id: Int? = null,

    @Json(name = "Start")
    val start: String? = null,

    @Json(name = "End")
    val end: String? = null,

    @Json(name = "FixTime")
    val fixTime: String? = null,

    @Json(name = "MediaID")
    var mediaID: Int? = null,

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

//    @Json(name = "Metadata")
//    val metadata: List<Any?>? = null,
//
//    @Json(name = "Tag")
//    val tag: List<Any?>? = null

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

    fun getFileName() = if (body!!.startsWith("http")) Uri.parse(body).path?.let { File(it).name }
    else File("file://$body").name

    fun shouldDownload(): Boolean {
        //1 - bản tin ký tự
        //2 - bản tin hình ảnh
        //3 - bản tin âm thanh
        //4 - Bản tin video
        //5 - Bản tin trực tiếp
        //6 - Bản tin FM
        //7 - Bản tin ghi âm trực tiếp
        //không phải files thì không tải, chỉ tải type 3 , type 4, type 2
        return !(body == "start" || body == "end") &&
                (contentType?.id == 3 || contentType?.id == 4 || contentType?.id == 2)
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

//    fun getVlcMedia(mLibVLC: LibVLC): Media {
//        return if (body!!.isNotEmpty() && File(body).exists()) {
//            Log.d("player", "local: $body")
//            Media(mLibVLC, Uri.parse("file://$body"))
//        } else {
//            Log.d("player", "online: $pathBackup")
//            Media(mLibVLC, Uri.parse(pathBackup))
//        }
//    }

    //    fun getExoMedia(): androidx.media3.common.MediaItem {
//        return if (body!!.isNotEmpty() && File(body).exists()) {
//            Log.d("player", "local: $body")
//            androidx.media3.common.MediaItem.fromUri(Uri.fromFile(File(body)))
//        } else {
//            Log.d("player", "online: $pathBackup")
//            androidx.media3.common.MediaItem.fromUri(pathBackup)
//        }
//    }
    fun getExoMedia(localPath: String): androidx.media3.common.MediaItem {
        return if (body!!.isNotEmpty() && File(localPath).exists()) {
            Log.d("player", "local api: $body")
            Log.d("player", "local device: $localPath")
            androidx.media3.common.MediaItem.fromUri(Uri.fromFile(File(localPath)))
        } else {
            Log.d("player", "online: $pathBackup")
            androidx.media3.common.MediaItem.fromUri(pathBackup)
        }
    }
}

enum class MediaType {
    FILE,
    LIVE,
    FM,
    AM,
    UNKNOWN
}

data class BroadcastRegion(
    @Json(name = "Id")
    val id: Int?,

    @Json(name = "Value")
    val value: Int
)


data class Author(
    @Json(name = "Id")
    val id: Any,

    @Json(name = "FullName")
    val fullName: String?,

    @Json(name = "UserName")
    val userName: String?,

    @Json(name = "Avatar")
    val avatar: String?,

    @Json(name = "Email")
    val email: String?
)

data class Category(
    @Json(name = "Id")
    val id: Long? = null,

    @Json(name = "Value")
    val value: String? = null
)

data class ContentType(
    @Json(name = "Id")
    val id: Int? = null,

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


