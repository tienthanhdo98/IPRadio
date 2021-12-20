package app.tek4tv.ipradio.utils

import android.content.Context
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.model.ResponseHub

class GlobalProperties {

    private val appContext: Context = MyApp.instance!!.applicationContext

    val DEFAULT_VOLUME = "DEFAULT_VOLUME"
    val DEFAULT_PI_CODE = "DEFAULT_PI_CODE"
    val DEFAULT_PS_NAME = "DEFAULT_PS_NAME"
    val DEFAULT_DESTINATION_ID = "DEFAULT_DESTINATION_ID"
    val DEFAULT_DESTINATION_NAME = "DEFAULT_DESTINATION_NAME"
    val DEFAULT_LOG_ENABLE = "DEFAULT_LOG_ENABLE"
    val DEFAULT_RESUME_FROM_START = "DEFAULT_RESUME_FROM_START"
    val NAME_DEVICE = "NAME_DEVICE"
    val LATITUDE = "LATITUDE"
    val LONGTITUDE = "LONGTITUDE"


    var volume = "60"
    var piCode = "0"
    var psName = "AAA"
    var nameDevice = ""
    var destinationName = ""
    var destinationId = ""
    var logEnabled = false
    var resumeFromStart = false
    var lat: Float = 0f
    var lng: Float = 0f

    init {
        volume = ConfigUtil.getString(appContext, DEFAULT_VOLUME, "60") ?: "60"
        piCode = ConfigUtil.getString(appContext, DEFAULT_PI_CODE, "0") ?: "0"
        psName = ConfigUtil.getString(appContext, DEFAULT_PS_NAME, "AAA") ?: "AAA"
        destinationId = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_ID, "") ?: ""
        destinationName = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_NAME, "") ?: ""
        nameDevice = ConfigUtil.getString(appContext, NAME_DEVICE, "") ?: ""
        logEnabled = ConfigUtil.getBool(appContext, DEFAULT_LOG_ENABLE, false)
        resumeFromStart = ConfigUtil.getBool(appContext, DEFAULT_RESUME_FROM_START, false)
        lat = ConfigUtil.getFloat(appContext, LATITUDE, 0f)
        lng = ConfigUtil.getFloat(appContext, LONGTITUDE, 0f)
    }

    fun saveProperties(responseHub: ResponseHub) {
        if (responseHub.volume != "") {
            saveVolume(responseHub.volume)
        }

        if (responseHub.piCode != "") {
            ConfigUtil.putString(appContext, DEFAULT_PI_CODE, responseHub.piCode)
            piCode = responseHub.piCode
        }
        if (responseHub.deviceName != "" && responseHub.destinationName != null) {
            ConfigUtil.putString(appContext, NAME_DEVICE, responseHub.deviceName)
            nameDevice = responseHub.deviceName!!
        }

        if (responseHub.psName != "") {
            ConfigUtil.putString(appContext, DEFAULT_PS_NAME, responseHub.psName)
            psName = responseHub.psName
        }
        if (responseHub.destinationId != "" && responseHub.destinationId != null) {
            ConfigUtil.putString(appContext, DEFAULT_DESTINATION_ID, responseHub.destinationId)
            destinationId = responseHub.destinationId!!
        }
        if (responseHub.destinationName != "" && responseHub.destinationName != null) {
            ConfigUtil.putString(appContext, DEFAULT_DESTINATION_NAME, responseHub.destinationName)
            destinationName = responseHub.destinationName!!
        }

        if (responseHub.logEnabled != null) {
            ConfigUtil.putBool(appContext, DEFAULT_LOG_ENABLE, responseHub.logEnabled!!)
            logEnabled = responseHub.logEnabled!!
        }

        if (responseHub.resumeFromStart != null) {
            ConfigUtil.putBool(appContext, DEFAULT_RESUME_FROM_START, responseHub.resumeFromStart!!)
            resumeFromStart = responseHub.resumeFromStart!!
        }
        if (responseHub.lat != null) {
            ConfigUtil.putFloat(appContext, LATITUDE, responseHub.lat!!)
            lat = responseHub.lat!!
        }
        if (responseHub.lng != null) {
            ConfigUtil.putFloat(appContext, LONGTITUDE, responseHub.lng!!)
            lng = responseHub.lng!!
        }
    }


    private fun saveVolume(vol: String) {
        ConfigUtil.putString(appContext, DEFAULT_VOLUME, vol)
        volume = vol
    }

    fun setTempVolume(vol: String) {
        volume = vol
    }

    companion object {

        private var instance: GlobalProperties? = null

        fun getInst(): GlobalProperties {
            if (instance == null)
                instance = GlobalProperties()

            return instance!!
        }
    }
}