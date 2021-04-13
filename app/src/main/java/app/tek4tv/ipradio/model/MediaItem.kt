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
data class MediaItem(
    //@Json(name = "Index") var index: String? = null,

    @Json(name = "Name") var name: String? = null,

    @Json(name = "ID") var id: Int = 0,

    @Json(name = "Duration") var duration : Int = 0,

    @Json(name = "Path") var path: String = "",

    @Json(name = "FixTime") var fixTime: String = "",

    @Json(name = "Type") var type : String = "",
) : Serializable {

    var pathBackup: String = ""
        set(value) {
            if (value.startsWith("http"))
                field = value
        }

    init {
        pathBackup = path
    }

    fun getFileName() = if (path.startsWith("http")) File(Uri.parse(path).path).name
    else File("file://$path").name

    fun getMediaType(): MediaType {
        /*val mediaName = getFileName()

        val audioExt = listOf(".mp3",".wav")
        val liveExt = listOf("rtsp:")

        val isAudio = audioExt.any { mediaName.endsWith(it) }
        val isLive = liveExt.any { path.startsWith(it) }
        val isTuner = path.matches(Regex(TUNER_REG))*/

        return when(type) {
            "VOD" -> MediaType.VOD
            "LIVE" -> MediaType.LIVE
            "FM" -> MediaType.TUNER
            else -> MediaType.UNKNOWN
        }
    }

    fun getUri(): Uri {
        return if (path.isNotEmpty() && File(path).exists()) {
            Uri.parse("file://$path")
        } else {
            Uri.parse(pathBackup)
        }
    }

    fun getVlcMedia(mLibVLC: LibVLC): Media {
        if(getMediaType() == MediaType.LIVE)
        {
            Log.d("player", "live: $path")
            return Media(mLibVLC, Uri.parse(path))
        }

        return if (path.isNotEmpty() && File(path).exists()) {
            Log.d("player", "local: $path")
            Media(mLibVLC, Uri.parse("file://$path"))
        } else {
            Log.d("player", "online: $pathBackup")
            Media(mLibVLC, Uri.parse(pathBackup))
        }
    }

    fun getDisplayName() : String{
        return when(getMediaType())
        {
            MediaType.VOD -> name?:" "
            MediaType.LIVE, MediaType.TUNER ->  "${name?:""} $path"
            else -> ""
        }
    }

    companion object
    {
        const val TUNER_REG = "^\\d,\\d+,\\d+,\\d+,[ -~]++\$"
    }
}

enum class MediaType {
    VOD,
    LIVE,
    TUNER,
    UNKNOWN
}


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
