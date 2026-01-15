package app.tek4tv.ipradio.model

import android.content.Context
import app.tek4tv.ipradio.di.MoshiInst
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.GlobalProperties
import com.squareup.moshi.Json
import java.text.SimpleDateFormat
import java.util.*

data class ForceMedia(
    @Json(name = "Message")
    var path: String = "",
    @Json(name = "Duration")
    var duration: String? = null,

    @Json(name = "Type")
    var type: String = "",

    @Json(name = "Level")
    var level: Int = 0,

    var mediaItem: MediaItem? = null
) {
    //Thời điểm kết thúc
    @Json(name = "EndTime")
    var endTime = ""

    //Thời điểm bắt đầu nhận lệnh
    @Json(name = "FixTime")
    var fixTime = ""

    @Json(name = "Name")
    var name = ""

    fun toMediaItem(): MediaItem {
//        val h = duration / 60
//        val m = duration - h * 60
//
//        val hs = if (h >= 10) "$h" else "0$h"
//        val ms = if (m >= 10) "$m" else "0$m"

        return MediaItem(
            body = path,
            title = mediaItem!!.title,
            type = type,
            fixTime = fixTime,
            end = endTime,
            duration = mediaItem!!.duration,
            author = mediaItem!!.author,
            mediaID = mediaItem!!.mediaID,
            category = mediaItem!!.category,
            priority = mediaItem!!.priority,
            contentType = mediaItem!!.contentType,
            description = mediaItem!!.description,
            sourceID = mediaItem!!.sourceID,
            sourceName = mediaItem!!.sourceName,
            sourceReference = mediaItem!!.sourceReference,
            startTime = mediaItem!!.startTime,
            createDate = mediaItem!!.createDate,
            id = mediaItem!!.id,
            projectID = mediaItem!!.mediaID,
            fieldType = mediaItem!!.fieldType
        ).also { it.level = level }
    }

    fun saveItemToPref(appContext: Context) {
        ConfigUtil.run {
            putBool(appContext, HAVE_FORCE_MEDIA, true)
            val value = jsonAdapter.toJson(this@ForceMedia)
            putString(appContext, FORCE_MEDIA, value)
        }
    }
    //duration = "$hs:$ms:00",

    companion object {
        val jsonAdapter = MoshiInst.moshi.adapter(ForceMedia::class.java)

        fun getLocalForceMedia(appContext: Context): ForceMedia? {
            val value = ConfigUtil.getString(appContext, ConfigUtil.FORCE_MEDIA, "")
            if (value == null || value == "") return null
            return jsonAdapter.fromJson(value)
        }

        fun hasValidForceMedia(appContext: Context): Boolean {
            val hasForceMedia = ConfigUtil.getBool(appContext, ConfigUtil.HAVE_FORCE_MEDIA, false)
            if (!hasForceMedia) return false

            val value = ConfigUtil.getString(appContext, ConfigUtil.FORCE_MEDIA, "")
            if (value == null || value == "") return false

            val fm = jsonAdapter.fromJson(value) ?: return false

            return SimpleDateFormat("dd/MM/yyyy HH:mm:ss").parse(fm.endTime) > Date()
        }

        fun deleteForeMediaPref(appContext: Context) {
            ConfigUtil.run {
                putBool(appContext, HAVE_FORCE_MEDIA, false)
                putString(appContext, FORCE_MEDIA, "")
            }
        }

        fun clearMonthlyData(appContext: Context) {
            ConfigUtil.putString(appContext, ConfigUtil.MONTHLY_SCENARIO_WSS_DATA, "")
        }
    }
}






