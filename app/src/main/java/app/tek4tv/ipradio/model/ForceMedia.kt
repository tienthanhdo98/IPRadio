package app.tek4tv.ipradio.model

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.utils.ConfigUtil
import com.squareup.moshi.Json
import java.text.SimpleDateFormat
import java.util.*

data class ForceMedia(
    @Json(name = "Message")
    var path : String = ""
)
{
    @Json(name = "Duration")
    var duration : Int = 0

    fun toMediaItem() = MediaItem(path = path, name = "Force : $path")

    fun saveItemToPref(appContext: Context)
    {
        ConfigUtil.run {
            putBool(appContext,HAVE_FORCE_MEDIA,true)
            putString(appContext, FORCE_MEDIA_PATH, path)

            val endTime = Calendar.getInstance().apply {
                add(Calendar.MINUTE, duration)
            }
            val endTimeStr = SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(endTime.time)
            Log.d("alarmx",endTimeStr)

            putString(appContext, FORCE_MEDIA_END_TIME, endTimeStr)
        }
    }

    companion object
    {
        fun getLocalForceMedia(appContext: Context) : ForceMedia?
        {
            val path = ConfigUtil.getString(appContext, ConfigUtil.FORCE_MEDIA_PATH, "")

            if(path==null || path =="") return null

            return ForceMedia(path)
        }

        fun hasValidForceMedia(appContext: Context) : Boolean
        {
            val hasForceMedia = ConfigUtil.getBool(appContext, ConfigUtil.HAVE_FORCE_MEDIA, false)
            if(!hasForceMedia) return false

            val path = ConfigUtil.getString(appContext, ConfigUtil.FORCE_MEDIA_PATH, "")
            if(path == null || path =="") return false

            val endTime = ConfigUtil.getString(appContext, ConfigUtil.FORCE_MEDIA_END_TIME, "")
            if(endTime == null || endTime =="") return false

            if (getForceMediaEndTime(appContext) <= Calendar.getInstance().timeInMillis) return false

            return true
        }

        fun getForceMediaEndTime(appContext: Context) : Long
        {
            val mediaEndTime = ConfigUtil.getString(appContext, ConfigUtil.FORCE_MEDIA_END_TIME,"")
            if(mediaEndTime == null || mediaEndTime == "") return -1L

            return Calendar.getInstance().apply {
                time = SimpleDateFormat("dd/MM/yyyy HH:mm:ss").parse(mediaEndTime)
            }.timeInMillis
        }

        fun deleteForeMediaPref(appContext: Context)
        {
            ConfigUtil.run {
                putBool(appContext,HAVE_FORCE_MEDIA,false)
                putString(appContext, FORCE_MEDIA_PATH, "")
                putString(appContext, FORCE_MEDIA_END_TIME, "")
            }
        }
    }
}






