package app.tek4tv.ipradio.utils

import android.content.Context
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.model.ResponseHub
import app.tek4tv.ipradio.utils.ConfigUtil.DEFAULT_ANDROID_VOLUME
import app.tek4tv.ipradio.utils.ConfigUtil.DEFAULT_VOLUME
import app.tek4tv.ipradio.utils.ConfigUtil.PROVIDER

class GlobalProperties {

    private val appContext: Context = MyApp.instance!!.applicationContext

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
    var androidVolume = "10"
    var userNameController = ""
    var piCode = "0"
    var psName = "AAA"
    var nameDevice = ""
    var resumeFromStart = false
    var lat: Float = 0f
    var lng: Float = 0f
    var providerValue: String = "Tek4tv"

//    var dichId = "H26"
//    var tenDich = "Hà Nội"
//    var dichId = "H39"
//    var tenDich = "Long An"


    init {
        ////ha nam
        volume = ConfigUtil.getString(appContext, DEFAULT_VOLUME, "100") ?: "100"
        androidVolume = ConfigUtil.getString(appContext, DEFAULT_ANDROID_VOLUME, "10") ?: "10"
        piCode = ConfigUtil.getString(appContext, DEFAULT_PI_CODE, "0") ?: "0"
        psName = ConfigUtil.getString(appContext, DEFAULT_PS_NAME, "AAA") ?: "AAA"
        nameDevice = ConfigUtil.getString(appContext, NAME_DEVICE, "Bảng Điện Tử") ?: "Bảng Điện Tử"
        resumeFromStart = ConfigUtil.getBool(appContext, DEFAULT_RESUME_FROM_START, false)
        lat = ConfigUtil.getFloat(appContext, LATITUDE, 0f)
        lng = ConfigUtil.getFloat(appContext, LONGTITUDE, 0f)
        userNameController = ConfigUtil.getString(appContext, USER_NAME_CONTROLLER, "") ?: ""
        providerValue = ConfigUtil.getString(appContext, PROVIDER, "Tek4tv") ?: "Tek4tv"

//        volume = ConfigUtil.getString(appContext, DEFAULT_VOLUME, "100") ?: "100"
//        androidVolume = ConfigUtil.getString(appContext, DEFAULT_ANDROID_VOLUME, "10") ?: "10"
//        piCode = ConfigUtil.getString(appContext, DEFAULT_PI_CODE, "0") ?: "0"
//        psName = ConfigUtil.getString(appContext, DEFAULT_PS_NAME, "AAA") ?: "AAA"
//        destinationId = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_ID, "H25.25.20") ?: "H25.25.20"
//        destinationName = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_NAME, "Xã An Ninh") ?: "Xã An Ninh"
//        nameDevice = ConfigUtil.getString(appContext, NAME_DEVICE, "Màn hình dọc") ?: "Màn hình dọc"
//        logEnabled = ConfigUtil.getBool(appContext, DEFAULT_LOG_ENABLE, false)
//        resumeFromStart = ConfigUtil.getBool(appContext, DEFAULT_RESUME_FROM_START, false)
//        lat = ConfigUtil.getFloat(appContext, LATITUDE, 0f)
//        lng = ConfigUtil.getFloat(appContext, LONGTITUDE, 0f)
//        userNameController = ConfigUtil.getString(appContext, USER_NAME_CONTROLLER, "") ?: ""
//        providerValue = ConfigUtil.getString(appContext, PROVIDER, "VATC") ?: "VATC"

        //bac giang
//        volume = ConfigUtil.getString(appContext, DEFAULT_VOLUME, "100") ?: "100"
//        piCode = ConfigUtil.getString(appContext, DEFAULT_PI_CODE, "0") ?: "0"
//        psName = ConfigUtil.getString(appContext, DEFAULT_PS_NAME, "AAA") ?: "AAA"
//        destinationId = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_ID, "H39.26.15") ?: "H39.26.15"
//        destinationName = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_NAME, "Cửa khẩu Bình Hiệp") ?: "Cửa khẩu Bình Hiệp"
//        nameDevice = ConfigUtil.getString(appContext, NAME_DEVICE, "Digital Cửa khẩu Bình Hiệp") ?: "Digital Cửa khẩu Bình Hiệp"
//        logEnabled = ConfigUtil.getBool(appContext, DEFAULT_LOG_ENABLE, false)
//        resumeFromStart = ConfigUtil.getBool(appContext, DEFAULT_RESUME_FROM_START, false)
//        lat = ConfigUtil.getFloat(appContext, LATITUDE, 10.921957f)
//        lng = ConfigUtil.getFloat(appContext, LONGTITUDE, 105.66343f)
//        userNameController = ConfigUtil.getString(appContext, USER_NAME_CONTROLLER, "") ?: ""
//        providerValue = ConfigUtil.getStringEncrypt(appContext, PROVIDER, "VTC") ?: "VTC"


//        volume = ConfigUtil.getString(appContext, DEFAULT_VOLUME, "100") ?: "100"
//        piCode = ConfigUtil.getString(appContext, DEFAULT_PI_CODE, "0") ?: "0"
//        psName = ConfigUtil.getString(appContext, DEFAULT_PS_NAME, "AAA") ?: "AAA"
//        destinationId = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_ID, "H39.21.12") ?: "H39.21.12"
//        destinationName = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_NAME, "Thị trấn Cần Đước") ?: "Thị trấn Cần Đước"
//        nameDevice = ConfigUtil.getString(appContext, NAME_DEVICE, "Digital VTC 2") ?: "Digital VTC 2"
//        logEnabled = ConfigUtil.getBool(appContext, DEFAULT_LOG_ENABLE, false)
//        resumeFromStart = ConfigUtil.getBool(appContext, DEFAULT_RESUME_FROM_START, false)
//        lat = ConfigUtil.getFloat(appContext, LATITUDE, 10.921957f)
//        lng = ConfigUtil.getFloat(appContext, LONGTITUDE, 105.66343f)
//        userNameController = ConfigUtil.getString(appContext, USER_NAME_CONTROLLER, "") ?: ""
//        providerValue = ConfigUtil.getStringEncrypt(appContext, PROVIDER, "VATC") ?: "VATC"


//        volume = ConfigUtil.getString(appContext, DEFAULT_VOLUME, "100") ?: "100"
//        piCode = ConfigUtil.getString(appContext, DEFAULT_PI_CODE, "0") ?: "0"
//        psName = ConfigUtil.getString(appContext, DEFAULT_PS_NAME, "AAA") ?: "AAA"
//        destinationId = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_ID, "H26.40.25") ?: "H26.40.25"
//        destinationName = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_NAME, "Phường Ngọc Hà") ?: "Phường Ngọc Hà"
//        nameDevice = ConfigUtil.getString(appContext, NAME_DEVICE, "Màn hình 01") ?: "Màn hình 01"
//        logEnabled = ConfigUtil.getBool(appContext, DEFAULT_LOG_ENABLE, false)
//        resumeFromStart = ConfigUtil.getBool(appContext, DEFAULT_RESUME_FROM_START, false)
//        lat = ConfigUtil.getFloat(appContext, LATITUDE, 21.038430133643217.toFloat())
//        lng = ConfigUtil.getFloat(appContext, LONGTITUDE, 105.82349242421763.toFloat())
//        userNameController = ConfigUtil.getString(appContext, USER_NAME_CONTROLLER, "") ?: ""
//        providerValue = ConfigUtil.getStringEncrypt(appContext, PROVIDER, "Tek4tv") ?: "Tek4tv"

        //long an

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
            nameDevice = responseHub.deviceName?: ""
        }

        if (responseHub.psName != "") {
            ConfigUtil.putString(appContext, DEFAULT_PS_NAME, responseHub.psName)
            psName = responseHub.psName
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
    fun setAndroidPreVolume(vol: String) {
        androidVolume = vol
    }

    companion object {

        private var instance: GlobalProperties? = null
        val TYPE_SCREEN_KEY = "TYPE_SCREEN_KEY"
        val TYPE_PRE_SCREEN_KEY = "TYPE_SCREEN_PRE_KEY"
        fun getInst(): GlobalProperties {
            if (instance == null)
                instance = GlobalProperties()

            return instance!!
        }
    }
}