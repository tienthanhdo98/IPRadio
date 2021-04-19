package app.tek4tv.ipradio.model

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.di.MoshiInst
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.toCalendar
import com.squareup.moshi.Json
import java.text.SimpleDateFormat
import java.util.*

data class ForceMedia(
    @Json(name = "Message")
    var path: String = "",
    @Json(name = "Duration")
    var duration: Int = 0,

    @Json(name = "Type")
    var type: String = "",

    @Json(name = "Level")
    var level: Int = 0
) {
    @Json(name = "EndTime")
    var endTime = ""

    init {
        val endTime = Calendar.getInstance().apply {
            add(Calendar.MINUTE, duration)
        }
        this.endTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(endTime.time)
    }

    fun toMediaItem(): MediaItem = MediaItem(path = path, name = path, type = type)

    fun saveItemToPref(appContext: Context) {
        ConfigUtil.run {
            putBool(appContext, HAVE_FORCE_MEDIA, true)
            val value = jsonAdapter.toJson(this@ForceMedia)
            putString(appContext, FORCE_MEDIA, value)
        }
    }

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

            if (SimpleDateFormat("dd/MM/yyyy HH:mm:ss").parse(fm.endTime) <= Date()) return false

            return true
        }

        fun deleteForeMediaPref(appContext: Context) {
            ConfigUtil.run {
                putBool(appContext, HAVE_FORCE_MEDIA, false)
                putString(appContext, FORCE_MEDIA, "")
            }
        }
    }
}






