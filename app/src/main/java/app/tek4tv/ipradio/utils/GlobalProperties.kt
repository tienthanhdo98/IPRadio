package app.tek4tv.ipradio.utils

import android.content.Context
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.model.ResponseHub
import app.tek4tv.ipradio.utils.ConfigUtil.PROVIDER
import app.tek4tv.ipradio.utils.ConfigUtil.DEFAULT_DESTINATION_ID
import app.tek4tv.ipradio.utils.ConfigUtil.DEFAULT_DESTINATION_NAME
import app.tek4tv.ipradio.utils.ConfigUtil.DEFAULT_PI_CODE
import app.tek4tv.ipradio.utils.ConfigUtil.DEFAULT_PS_NAME
import app.tek4tv.ipradio.utils.ConfigUtil.DEFAULT_RESUME_FROM_START
import app.tek4tv.ipradio.utils.ConfigUtil.DEFAULT_VOLUME
import app.tek4tv.ipradio.utils.ConfigUtil.LATITUDE
import app.tek4tv.ipradio.utils.ConfigUtil.LONGTITUDE
import app.tek4tv.ipradio.utils.ConfigUtil.NAME_DEVICE
import app.tek4tv.ipradio.utils.ConfigUtil.USER_NAME_CONTROLLER

class GlobalProperties {

    private val appContext: Context = MyApp.instance!!.applicationContext

    object Defaults {
        const val VERSION_NAME = "v19.4.2.2f"
        const val VOLUME = "100"
        const val PI_CODE = "0"
        const val PS_NAME = "AAA"

        const val DESTINATION_ID = "H44.126"
        const val DESTINATION_NAME = "Xã Trí Đám"
        const val NAME_DEVICE = "TB Xã Trí Đám"

        //        const val DESTINATION_ID = "H05.167"
//        const val DESTINATION_NAME = "Xã Dương Hưu"
//        const val NAME_DEVICE = "TB Xã Dương Hưu"
        const val RESUME_FROM_START = false
        const val LAT = 0f
        const val LNG = 0f
        const val USER_NAME_CONTROLLER = ""
        const val PROVIDER_VALUE = "TEK4TV"
    }

    var volume = Defaults.VOLUME
    var userNameController = Defaults.USER_NAME_CONTROLLER
    var piCode = Defaults.PI_CODE
    var psName = Defaults.PS_NAME
    var nameDevice = Defaults.NAME_DEVICE
    var destinationName = Defaults.DESTINATION_NAME
    var destinationId = Defaults.DESTINATION_ID
    var resumeFromStart = Defaults.RESUME_FROM_START
    var lat: Float = Defaults.LAT
    var lng: Float = Defaults.LNG
    var providerValue: String = Defaults.PROVIDER_VALUE


    init {

        volume =
            ConfigUtil.getString(appContext, DEFAULT_VOLUME, Defaults.VOLUME) ?: Defaults.VOLUME
        piCode =
            ConfigUtil.getString(appContext, DEFAULT_PI_CODE, Defaults.PI_CODE) ?: Defaults.PI_CODE
        psName =
            ConfigUtil.getString(appContext, DEFAULT_PS_NAME, Defaults.PS_NAME) ?: Defaults.PS_NAME
        destinationId =
            ConfigUtil.getString(appContext, DEFAULT_DESTINATION_ID, Defaults.DESTINATION_ID)
                ?: Defaults.DESTINATION_ID
        destinationName =
            ConfigUtil.getString(appContext, DEFAULT_DESTINATION_NAME, Defaults.DESTINATION_NAME)
                ?: Defaults.DESTINATION_NAME
        nameDevice = ConfigUtil.getString(appContext, NAME_DEVICE, Defaults.NAME_DEVICE)
            ?: Defaults.NAME_DEVICE
        resumeFromStart =
            ConfigUtil.getBool(appContext, DEFAULT_RESUME_FROM_START, Defaults.RESUME_FROM_START)
        lat = ConfigUtil.getFloat(appContext, LATITUDE, Defaults.LAT)
        lng = ConfigUtil.getFloat(appContext, LONGTITUDE, Defaults.LNG)
        userNameController =
            ConfigUtil.getString(appContext, USER_NAME_CONTROLLER, Defaults.USER_NAME_CONTROLLER)
                ?: Defaults.USER_NAME_CONTROLLER
        providerValue = ConfigUtil.getString(appContext, PROVIDER, Defaults.PROVIDER_VALUE)
            ?: Defaults.PROVIDER_VALUE


    }

    fun saveProperties(responseHub: ResponseHub) {
        if (responseHub.volume != "") {
            saveVolume(responseHub.volume)
        }

        if (responseHub.piCode != "") {
            ConfigUtil.putString(appContext, DEFAULT_PI_CODE, responseHub.piCode)
            piCode = responseHub.piCode
        }
        if (responseHub.userNameControl != "" && responseHub.userNameControl != null) {
            ConfigUtil.putString(appContext, USER_NAME_CONTROLLER, responseHub.userNameControl)
            userNameController = responseHub.userNameControl ?: ""
        }
        if (responseHub.deviceName != "" && responseHub.destinationName != null) {
            ConfigUtil.putString(appContext, NAME_DEVICE, responseHub.deviceName)
            nameDevice = responseHub.deviceName ?: ""
        }

        if (responseHub.psName != "") {
            ConfigUtil.putString(appContext, DEFAULT_PS_NAME, responseHub.psName)
            psName = responseHub.psName
        }
        if (responseHub.destinationId != "" && responseHub.destinationId != null) {
            ConfigUtil.putString(appContext, DEFAULT_DESTINATION_ID, responseHub.destinationId)
            destinationId = responseHub.destinationId ?: ""
        }
        if (responseHub.destinationName != "" && responseHub.destinationName != null) {
            ConfigUtil.putString(appContext, DEFAULT_DESTINATION_NAME, responseHub.destinationName)
            destinationName = responseHub.destinationName ?: ""
        }

        if (responseHub.resumeFromStart != null) {
            ConfigUtil.putBool(appContext, DEFAULT_RESUME_FROM_START, responseHub.resumeFromStart!!)
            resumeFromStart = responseHub.resumeFromStart!!
        }
        if (responseHub.lat != null) {
            ConfigUtil.putFloat(appContext, LATITUDE, responseHub.lat!!)
            lat = responseHub.lat ?: 0f
        }
        if (responseHub.lng != null) {
            ConfigUtil.putFloat(appContext, LONGTITUDE, responseHub.lng!!)
            lng = responseHub.lng ?: 0f
        }
    }


    fun saveVolume(vol: String) {
        ConfigUtil.putString(appContext, DEFAULT_VOLUME, vol)
        volume = vol
    }

    fun setTempVolume(vol: String) {
        volume = vol
    }

    companion object {

        private var instance: GlobalProperties? = null

        fun getInst(): GlobalProperties {
            if (instance == null) instance = GlobalProperties()

            return instance!!
        }
    }
}