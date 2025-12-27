package app.tek4tv.ipradio.utils

import android.content.Context
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.model.ResponseHub
import app.tek4tv.ipradio.utils.ConfigUtil.PROVIDER

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
    val USER_NAME_CONTROLLER = "USER_NAME_CONTROLLER"


    var volume = "100"
    var userNameController = ""
    var piCode = "0"
    var psName = "AAA"
    var nameDevice = ""
    var destinationName = ""
    var destinationId = ""
    var logEnabled = false
    var resumeFromStart = false
    var lat: Float = 0f
    var lng: Float = 0f
    var providerValue: String = "TEK4TV"


    init {
//        volume = ConfigUtil.getString(appContext, DEFAULT_VOLUME, "100") ?: "100"
//        piCode = ConfigUtil.getString(appContext, DEFAULT_PI_CODE, "0") ?: "0"
//        psName = ConfigUtil.getString(appContext, DEFAULT_PS_NAME, "AAA") ?: "AAA"
//        destinationId =
//            ConfigUtil.getString(appContext, DEFAULT_DESTINATION_ID, "H41.126") ?: "H41.126"
//        destinationName =
//            ConfigUtil.getString(appContext, DEFAULT_DESTINATION_NAME, "Phường Quỳnh Mai")
//                ?: "Phường Quỳnh Mai"
//        nameDevice =
//            ConfigUtil.getString(appContext, NAME_DEVICE, "TB Phường Quỳnh Mai")
//                ?: "TB Phường Quỳnh Mai"
//        logEnabled = ConfigUtil.getBool(appContext, DEFAULT_LOG_ENABLE, false)
//        resumeFromStart = ConfigUtil.getBool(appContext, DEFAULT_RESUME_FROM_START, false)
//        lat = ConfigUtil.getFloat(appContext, LATITUDE, 0f)
//        lng = ConfigUtil.getFloat(appContext, LONGTITUDE, 0f)
//        userNameController = ConfigUtil.getString(appContext, USER_NAME_CONTROLLER, "") ?: ""
//        providerValue = ConfigUtil.getString(appContext, PROVIDER, "TEK4TV") ?: "TEK4TV"

//        volume = ConfigUtil.getString(appContext, DEFAULT_VOLUME, "100") ?: "100"
//        piCode = ConfigUtil.getString(appContext, DEFAULT_PI_CODE, "0") ?: "0"
//        psName = ConfigUtil.getString(appContext, DEFAULT_PS_NAME, "AAA") ?: "AAA"
//        destinationId =
//            ConfigUtil.getString(appContext, DEFAULT_DESTINATION_ID, "H53.156")
//                ?: "H53.156"
//        destinationName =
//            ConfigUtil.getString(appContext, DEFAULT_DESTINATION_NAME, "Đặc khu Cát Hải")
//                ?: "Đặc khu Cát Hải"
//        nameDevice =
//            ConfigUtil.getString(appContext, NAME_DEVICE, "TB Đặc khu Cát Hải")
//                ?: "TB Đặc khu Cát Hải"
//        logEnabled = ConfigUtil.getBool(appContext, DEFAULT_LOG_ENABLE, false)
//        resumeFromStart = ConfigUtil.getBool(appContext, DEFAULT_RESUME_FROM_START, false)
//        lat = ConfigUtil.getFloat(appContext, LATITUDE, 0f)
//        lng = ConfigUtil.getFloat(appContext, LONGTITUDE, 0f)
//        userNameController = ConfigUtil.getString(appContext, USER_NAME_CONTROLLER, "") ?: ""
//        providerValue = ConfigUtil.getString(appContext, PROVIDER, "VATC") ?: "VATC"

//        volume = ConfigUtil.getString(appContext, DEFAULT_VOLUME, "100") ?: "100"
//        piCode = ConfigUtil.getString(appContext, DEFAULT_PI_CODE, "0") ?: "0"
//        psName = ConfigUtil.getString(appContext, DEFAULT_PS_NAME, "AAA") ?: "AAA"
//        destinationId =
//            ConfigUtil.getString(appContext, DEFAULT_DESTINATION_ID, "H05.142")
//                ?: "H05.142"
//        destinationName =
//            ConfigUtil.getString(appContext, DEFAULT_DESTINATION_NAME, "Phường Bắc Giang")
//                ?: "Phường Bắc Giang"
//        nameDevice =
//            ConfigUtil.getString(appContext, NAME_DEVICE, "TB Phường Bắc Giang")
//                ?: "TB Phường Bắc Giang"
//        logEnabled = ConfigUtil.getBool(appContext, DEFAULT_LOG_ENABLE, false)
//        resumeFromStart = ConfigUtil.getBool(appContext, DEFAULT_RESUME_FROM_START, false)
//        lat = ConfigUtil.getFloat(appContext, LATITUDE, 0f)
//        lng = ConfigUtil.getFloat(appContext, LONGTITUDE, 0f)
//        userNameController = ConfigUtil.getString(appContext, USER_NAME_CONTROLLER, "") ?: ""
//        providerValue = ConfigUtil.getString(appContext, PROVIDER, "VATC") ?: "VATC"

//        volume = ConfigUtil.getString(appContext, DEFAULT_VOLUME, "100") ?: "100"
//        piCode = ConfigUtil.getString(appContext, DEFAULT_PI_CODE, "0") ?: "0"
//        psName = ConfigUtil.getString(appContext, DEFAULT_PS_NAME, "AAA") ?: "AAA"
//        destinationId =
//            ConfigUtil.getString(appContext, DEFAULT_DESTINATION_ID, "H41.126") ?: "H41.126"
//        destinationName =
//            ConfigUtil.getString(appContext, DEFAULT_DESTINATION_NAME, "Phường Quỳnh Mai")
//                ?: "Phường Quỳnh Mai"
//        nameDevice =
//            ConfigUtil.getString(appContext, NAME_DEVICE, "TB Phường Quỳnh Mai")
//                ?: "TB Phường Quỳnh Mai"
//        logEnabled = ConfigUtil.getBool(appContext, DEFAULT_LOG_ENABLE, false)
//        resumeFromStart = ConfigUtil.getBool(appContext, DEFAULT_RESUME_FROM_START, false)
//        lat = ConfigUtil.getFloat(appContext, LATITUDE, 0f)
//        lng = ConfigUtil.getFloat(appContext, LONGTITUDE, 0f)
//        userNameController = ConfigUtil.getString(appContext, USER_NAME_CONTROLLER, "") ?: ""
//        providerValue = ConfigUtil.getString(appContext, PROVIDER, "TEK4TV") ?: "TEK4TV"

        //can tho
//        volume = ConfigUtil.getString(appContext, DEFAULT_VOLUME, "100") ?: "100"
//        piCode = ConfigUtil.getString(appContext, DEFAULT_PI_CODE, "0") ?: "0"
//        psName = ConfigUtil.getString(appContext, DEFAULT_PS_NAME, "AAA") ?: "AAA"
//        destinationId =
//            ConfigUtil.getString(appContext, DEFAULT_DESTINATION_ID, "H13.123") ?: "H13.123"
//        destinationName =
//            ConfigUtil.getString(appContext, DEFAULT_DESTINATION_NAME, "")
//                ?: ""
//        nameDevice =
//            ConfigUtil.getString(appContext, NAME_DEVICE, "TB phát thanh")
//                ?: "TB phát thanh"
//        logEnabled = ConfigUtil.getBool(appContext, DEFAULT_LOG_ENABLE, false)
//        resumeFromStart = ConfigUtil.getBool(appContext, DEFAULT_RESUME_FROM_START, false)
//        lat = ConfigUtil.getFloat(appContext, LATITUDE, 0f)
//        lng = ConfigUtil.getFloat(appContext, LONGTITUDE, 0f)
//        userNameController = ConfigUtil.getString(appContext, USER_NAME_CONTROLLER, "") ?: ""
//        providerValue = ConfigUtil.getString(appContext, PROVIDER, "TEK4TV") ?: "TEK4TV"

        volume = ConfigUtil.getString(appContext, DEFAULT_VOLUME, "100") ?: "100"
        piCode = ConfigUtil.getString(appContext, DEFAULT_PI_CODE, "0") ?: "0"
        psName = ConfigUtil.getString(appContext, DEFAULT_PS_NAME, "AAA") ?: "AAA"
        destinationId =
            ConfigUtil.getString(appContext, DEFAULT_DESTINATION_ID, "H39.21.12") ?: "H39.21.12"
        destinationName =
            ConfigUtil.getString(appContext, DEFAULT_DESTINATION_NAME, "Thị trấn Cần Đước")
                ?: "Thị trấn Cần Đước"
        nameDevice = ConfigUtil.getString(appContext, NAME_DEVICE, "TB Phát Thanh")
            ?: "TB Phát Thanh"
        logEnabled = ConfigUtil.getBool(appContext, DEFAULT_LOG_ENABLE, false)
        resumeFromStart = ConfigUtil.getBool(appContext, DEFAULT_RESUME_FROM_START, false)
        lat = ConfigUtil.getFloat(appContext, LATITUDE, 0f)
        lng = ConfigUtil.getFloat(appContext, LONGTITUDE, 0f)
        userNameController = ConfigUtil.getString(appContext, USER_NAME_CONTROLLER, "") ?: ""
        providerValue = ConfigUtil.getString(appContext, PROVIDER, "TEK4TV") ?: "TEK4TV"
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