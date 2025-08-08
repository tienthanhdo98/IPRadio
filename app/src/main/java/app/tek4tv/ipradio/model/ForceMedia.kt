package app.tek4tv.ipradio.model

import android.content.Context
import app.tek4tv.ipradio.di.JsonAdapterProvider.jsonListMediaItemAdapter
import app.tek4tv.ipradio.di.MoshiInst
import app.tek4tv.ipradio.utils.ConfigUtil
import com.squareup.moshi.Json

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
            id = mediaItem!!.id,
            body = mediaItem!!.body,
            title = mediaItem!!.title,
            type = mediaItem!!.type,
            fixTime = fixTime,
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
            projectID = mediaItem!!.projectID,
            broadcastRegion = mediaItem!!.broadcastRegion,
            end = endTime
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

        fun getLocalForceMedia(appContext: Context): List<MediaItem>? {
            val value = ConfigUtil.getString(appContext, ConfigUtil.FORCE_MEDIA, "")
            if (value == null || value == "") return null
            return jsonListMediaItemAdapter.fromJson(value)
        }

        //        fun hasValidForceMedia(appContext: Context): Boolean {
//            val hasForceMedia = ConfigUtil.getBool(appContext, ConfigUtil.HAVE_FORCE_MEDIA, false)
//            if (!hasForceMedia) return false
//
//            val value = ConfigUtil.getString(appContext, ConfigUtil.FORCE_MEDIA, "")
//            if (value == null || value == "") return false
//
//            val fm = jsonAdapter.fromJson(value) ?: return false
//
//            if (SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(fm.endTime) <= Date()) return false
//
//            return true
//        }
        fun hasValidForceMedia(appContext: Context): Boolean {
            try {
                val hasForceMedia =
                    ConfigUtil.getBool(appContext, ConfigUtil.HAVE_FORCE_MEDIA, false)
                if (!hasForceMedia) return false

                return true
            } catch (_: Exception) {
                return false
            }

        }

        fun deleteForeMediaPref(appContext: Context) {
            ConfigUtil.run {
                putBool(appContext, HAVE_FORCE_MEDIA, false)
                putString(appContext, FORCE_MEDIA, "")
            }
        }

        fun saveListForceToPref(appContext: Context, jsonListMedia: String) {
            ConfigUtil.run {
                putBool(appContext, HAVE_FORCE_MEDIA, true)
                putString(appContext, FORCE_MEDIA, jsonListMedia)
            }
        }
    }
}






