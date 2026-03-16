package app.tek4tv.ipradio.model

import android.content.Context
import android.util.Log
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import java.io.File
import androidx.core.net.toUri
import app.tek4tv.ipradio.utils.storage.getFileNameFromUrl

@JsonClass(generateAdapter = true)
data class BaseMqttMessage(
    @Json(name = "payload") val payload: Any?,
    @Json(name = "id") val id: String?,
    @Json(name = "topic") val topic: String?
)

@JsonClass(generateAdapter = true)
data class AppData(
    @Json(name = "n") val n: String,
    @Json(name = "t") val t: Double,
    @Json(name = "vs") val vs: String
)

@JsonClass(generateAdapter = true)
data class OphRebootData(
    @Json(name = "option") val option: Int? = null
)

@JsonClass(generateAdapter = true)
data class McuSettingsData(
    @Json(name = "txtype") val txtype: Int? = null,
    @Json(name = "sim") val sim: Int? = null,
    @Json(name = "mobile_mode") val mobileMode: Int? = null,
    @Json(name = "ioct") val ioct: Int? = null,
    @Json(name = "classd") val classd: Int? = null,
    @Json(name = "disabled") val disabled: Int? = null,
    @Json(name = "main_volume") val mainVolume: Int? = null,
    @Json(name = "fm_volume") val fmVolume: Int? = null,
    @Json(name = "fm_auto") val fmAuto: Int? = null,
    @Json(name = "fm_threshold") val fmThreshold: Int? = null
)

@JsonClass(generateAdapter = true)
data class McuUpdateFirmData(
    @Json(name = "option") val option: Int? = null,
    @Json(name = "url") val url: String? = null,
)

@JsonClass(generateAdapter = true)
data class ServerData(
    @Json(name = "bootstrap_server") val bootstrapServer: String? = null,
    @Json(name = "mqtt_security") val mqttSecurity: Int? = null,
    @Json(name = "mqtt_server") val mqttServer: String? = null,
    @Json(name = "mqtt_port") val mqttPort: Int? = null,
)

@JsonClass(generateAdapter = true)
data class McuAckData(
    @Json(name = "opcode") val opcode: Int? = null,
    @Json(name = "t") val t: Double? = null,
)

@JsonClass(generateAdapter = true)
data class McuStreamBackDataData(
    @Json(name = "stream_url") val streamUrl: String? = null,
    //0=CH1-3, 1=CH2-4, 2=Stop.
    @Json(name = "cmd") val cmd: Int? = null,
    @Json(name = "timeout") val timeout: Int? = null,
)

@JsonClass(generateAdapter = true)
data class OpuSyncData(
    val shows: List<Long> = emptyList() // Danh sách show_id hiện có trên thiết bị
)

@JsonClass(generateAdapter = true)
data class SenMLItem(
    val n: String, val t: Double, val vs: String
)

@JsonClass(generateAdapter = true)
data class McuStatusData(
//    @Json(name = "conn") val conn: Int? = null,
//
//    @Json(name = "iplan") val ipLan: String? = null,
//
//    @Json(name = "ipwan") val ipWan: String? = null,
//
//    @Json(name = "csqm") val csqm: Int? = null,
//
//    @Json(name = "csqw") val csqw: Int? = null,
//
//    @Json(name = "spkerr") val spkErr: Int? = null,
//
//    @Json(name = "fmsta") val fmSta: Int? = null,
//
//    @Json(name = "sdfree") val sdFree: Long? = null,
//
//    @Json(name = "sdtotal") val sdTotal: Long? = null,
//
//    @Json(name = "temp") val temp: Int? = null,
//
//    @Json(name = "playlist") val playlist: Long? = null,
//
//    @Json(name = "mids") val mids: String? = null

    //new
    @Json(name = "conn") val conn: Int? = 0,
    @Json(name = "iplan") val ipLan: String? = "",
    @Json(name = "ipwan") val ipWan: String? = "",
    @Json(name = "csqm") val csqm: Int? = 0,
    @Json(name = "rssim") val rssim: Int? = 0, // Bổ sung
    @Json(name = "csqw") val csqw: Int? = 0,
    @Json(name = "spkerr") val spkErr: Int? = 0,
    @Json(name = "sdtotal") val sdTotal: Long? = 0,
    @Json(name = "sdfree") val sdFree: Long? = 0,
    @Json(name = "fmsta") val fmSta: Int? = 0,
    @Json(name = "fmrssi") val fmRssi: Int? = 0, // Bổ sung
    @Json(name = "temp") val temp: Int? = 0,
    @Json(name = "playlist") val playlist: Long? = 0,
    @Json(name = "disconnection_cnt") val disconnectionCnt: Int? = 0, // Bổ sung
    @Json(name = "mids") val mids: List<Long>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class McuLogData(
    @Json(name = "id") val id: Long? = null,

    @Json(name = "time") val time: Double? = null,

    @Json(name = "type") val type: Int? = null,

    @Json(name = "detail") val detail: String? = null,
)


@JsonClass(generateAdapter = true)
data class McuInfoData(
    @Json(name = "id") val id: String? = null,

    @Json(name = "vcode") val vcode: String? = null,

    @Json(name = "fvers") val fvers: String? = null,

    @Json(name = "os_version") val osVersion: String? = null,

    @Json(name = "os_builddate") val osBuilddate: String? = null,

    @Json(name = "hw_version") val hwVersion: String? = null,

    @Json(name = "sd_cid") val sdCid: String? = null,

    @Json(name = "fm") val fm: String? = null,

    @Json(name = "classd_version") val classdVersion: String? = null,

    @Json(name = "classd_chipset") val classdChipset: String? = null,

    @Json(name = "gsm_model") val gsmModel: String? = null,

    @Json(name = "gsm_imei") val gsmImei: String? = null,

    @Json(name = "gsm_revision") val gsmRevision: String? = null,

    @Json(name = "gsm_imsi") val gsmImsi: String? = null,

    @Json(name = "gsm_phone") val gsmPhone: String? = null
)

@JsonClass(generateAdapter = true)
data class OphMediaV3Data(
    @Json(name = "records") val records: List<ShowInfo> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ShowInfo(
    @Json(name = "id") val id: Long? = null,
    @Json(name = "show_id") val showId: Long? = null,
    // base64(MediaInfo JSON)
    @Json(name = "show_info") val showInfo: String? = null,
    // 1 create, 2 update, 4 delete, 8 play, 16 stop
    @Json(name = "action") val action: Int? = null,
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
    @Json(name = "type") val type: Int? = null,

    @Json(name = "size") val size: Long? = null,
    @Json(name = "cs") val cs: Long? = null,          // checksum
    @Json(name = "duration") val duration: Int? = null,

    // audio/stream url hoặc FM freq; để String? cho tolerant
    @Json(name = "url") var url: String? = null
) {
    var pathBackup: String = url.orEmpty()


    fun getMediaType(): MediaFileType {
        return when (type) {
            0 -> MediaFileType.SILENT
            1 -> MediaFileType.FILE
            2 -> MediaFileType.FM
            3 -> MediaFileType.LIVE
            else -> MediaFileType.UNKNOWN
        }
    }

    fun getVlcMedia(mLibVLC: LibVLC, appContext: Context): Media {
        return when (getMediaType()) {
            MediaFileType.LIVE -> {
                Media(mLibVLC, pathBackup.toUri())
            }

            MediaFileType.FILE -> {
                val remoteUrl = url
                require(!remoteUrl.isNullOrBlank()) { "Media url is null or blank" }

                val fileName = getFileNameFromUrl(remoteUrl)
                val localFile = File(appContext.filesDir.path, fileName)

                return if (localFile.exists() && localFile.isFile) {
                    Log.d("player", "local: ${localFile.absolutePath}")
                    Media(mLibVLC, localFile.toUri())
                } else {
                    Log.d("player", "online: $pathBackup")
                    Media(mLibVLC, pathBackup.toUri())
                }
            }

            else -> {
                throw IllegalStateException("Unsupported media type: ${getMediaType()}")
            }
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
