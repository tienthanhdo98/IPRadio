package app.tek4tv.ipradio.model

import android.net.Uri
import android.util.Log
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import java.io.File

@JsonClass(generateAdapter = true)
data class BaseMqttMessage(
    @Json(name = "payload") val payload: Any?,
    @Json(name = "id") val id: String?,
    @Json(name = "topic") val topic: String?
)

@JsonClass(generateAdapter = true)
data class AppData(
    @Json(name = "n") val n: String,
    @Json(name = "t") val t: Long,
    @Json(name = "vs") val vs: String
)

@JsonClass(generateAdapter = true)
data class OphRebootData(
    @Json(name = "option") val option: Int
)

@JsonClass(generateAdapter = true)
data class OphSettingsData(
    @Json(name = "txtype") val txtype: Int,
    @Json(name = "sim") val sim: Int,
    @Json(name = "mobile_mode") val mobileMode: Int,
    @Json(name = "ioct") val ioct: Int,
    @Json(name = "classd") val classd: Int,
    @Json(name = "disabled") val disabled: Int,
    @Json(name = "main_volume") val mainVolume: Int,
    @Json(name = "fm_volume") val fmVolume: Int,
    @Json(name = "fm_auto") val fmAuto: Int,
    @Json(name = "fm_threshold") val fmThreshold: Int
)

@JsonClass(generateAdapter = true)
data class OphMediaV3Data(
    @Json(name = "records") val records: List<ShowInfo> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ShowInfo(
    @Json(name = "id") val id: Long? = null,
    @Json(name = "show_id") val showId: Long,
    // base64(MediaInfo JSON)
    @Json(name = "show_info") val showInfo: String? = null,
    // 1 create, 2 update, 4 delete, 8 play, 16 stop
    @Json(name = "action") val action: Int,
    @Json(name = "description") val description: String? = null,
    @Json(name = "version") val version: Int? = null,
    // trong spec có dạng ISO string; tùy hệ thống có thể là number -> nullable String cho an toàn
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class MediaInfo(
    @Json(name = "mid") val mid: Long? = null,
    @Json(name = "version") val version: Int? = null,
    @Json(name = "prio") val prio: Int? = null,
    @Json(name = "summary") val summary: String? = null,

    // mode: theo spec (ví dụ 1/2...) -> để Int?
    @Json(name = "mode") val mode: Int? = null,

    @Json(name = "repeat") val repeat: Int? = null,
    @Json(name = "days") val days: Int? = null,

    // lịch chạy (nếu có). Spec dùng ts/ds arrays, để List<Long> cho an toàn
    @Json(name = "ts") val ts: List<Long>? = null,
    @Json(name = "ds") val ds: List<Long>? = null,

    @Json(name = "created") val created: Long? = null,
    @Json(name = "start") val start: Long? = null,
    @Json(name = "expired") val expired: Long? = null,

    @Json(name = "files") val files: List<MediaFile> = emptyList()
)

@JsonClass(generateAdapter = true)
data class MediaFile(
    @Json(name = "id") val id: Long? = null,
    @Json(name = "index") val index: Int? = null,

    // 0 silence, 1 audio, 2 FM(kHz), 3 stream url
    @Json(name = "type") val type: Int,

    @Json(name = "size") val size: Long? = null,
    @Json(name = "cs") val cs: Long? = null,          // checksum
    @Json(name = "duration") val duration: Int? = null,

    // audio/stream url hoặc FM freq; để String? cho tolerant
    @Json(name = "url") val url: String? = null
) {
    var pathBackup: String = ""

    init {
        pathBackup = url!!
    }

    fun getMediaType(): MediaFileType {
        return when (type) {
            0 -> MediaFileType.SILENT
            1 -> MediaFileType.FILE
            2 -> MediaFileType.FM
            3 -> MediaFileType.LIVE
            else -> MediaFileType.UNKNOWN
        }
    }

    fun getVlcMedia(mLibVLC: LibVLC): Media {
        if (getMediaType() == MediaFileType.LIVE) {
            return Media(mLibVLC, Uri.parse(url))
        }
        return if (url!!.isNotEmpty() && File(url!!).exists()) {
            Log.d("player", "local: $url")
            Media(mLibVLC, Uri.parse("file://$url"))
        } else {
            Log.d("player", "online: $pathBackup")
            Media(mLibVLC, Uri.parse(pathBackup))

        }
    }
}

enum class MediaFileType {
    SILENT, FILE, FM, LIVE, UNKNOWN
}


sealed class RepeatDays {
    data class Weekly(val weekdays: List<Int>) : RepeatDays() // 0=Mon..6=Sun
    data class Monthly(val daysOfMonth: List<Int>) : RepeatDays() // 1..31
}
