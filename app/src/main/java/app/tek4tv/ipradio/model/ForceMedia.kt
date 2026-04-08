package app.tek4tv.ipradio.model

import android.content.Context
import app.tek4tv.ipradio.di.JsonAdapterProvider.mediaInfoAdapter
import app.tek4tv.ipradio.utils.ConfigUtil
import com.squareup.moshi.Json


data class ForceMedia(
    @Json(name = "Message")
    var path: String = "",
    @Json(name = "Duration")
    var duration: Int? = null,

    @Json(name = "Type")
    var type: String = "",

    @Json(name = "Level")
    var level: Int = 0,

    ) {
    //Thời điểm kết thúc
    @Json(name = "EndTime")
    var endTime = ""

    //Thời điểm bắt đầu nhận lệnh
    @Json(name = "FixTime")
    var fixTime = ""

    @Json(name = "Name")
    var name = ""

    //duration = "$hs:$ms:00",

    companion object {

        fun getLocalForceMedia(appContext: Context): MediaInfo? {
            val value = ConfigUtil.getString(appContext, ConfigUtil.FORCE_MEDIA_INFO, "")
            if (value == null || value == "") return null
            return mediaInfoAdapter.fromJson(value)
        }

        fun hasValidForceMedia(appContext: Context): Boolean {
            try {
                val hasForceMedia =
                    ConfigUtil.getBool(appContext, ConfigUtil.HAVE_FORCE_MEDIA, false)
                return hasForceMedia
            } catch (_: Exception) {
                return false
            }

        }

        fun deleteForeMediaPref(appContext: Context) {
            ConfigUtil.run {
                putBool(appContext, HAVE_FORCE_MEDIA, false)
                putString(appContext, FORCE_MEDIA_INFO, "")
            }
        }

        fun saveForceMediaInfoToPref(appContext: Context, json: String) {
            ConfigUtil.run {
                putBool(appContext, HAVE_FORCE_MEDIA, true)
                putString(appContext, FORCE_MEDIA_INFO, json)
            }
        }


    }
}

data class ForceProgressState(
    val isExpired: Boolean,
    val completedLoops: Int,
    val itemIndex: Int,
    val itemOffsetSec: Int
)





