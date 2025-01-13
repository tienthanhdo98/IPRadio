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
    var destinationName = ""
    var destinationId = ""
    var resumeFromStart = false
    var lat: Float = 0f
    var lng: Float = 0f
    var providerValue: String = "VATC"




    init {
        ////Quang ngai
//        volume = ConfigUtil.getString(appContext, DEFAULT_VOLUME, "100") ?: "100"
//        androidVolume = ConfigUtil.getString(appContext, DEFAULT_ANDROID_VOLUME, "10") ?: "10"
//        piCode = ConfigUtil.getString(appContext, DEFAULT_PI_CODE, "0") ?: "0"
//        psName = ConfigUtil.getString(appContext, DEFAULT_PS_NAME, "AAA") ?: "AAA"
//        destinationId = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_ID, "H48.51.02") ?: "H48.51.02"
//        destinationName = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_NAME, "Phường Lê Hồng Phong") ?: "Phường Lê Hồng Phong"
//        nameDevice = ConfigUtil.getString(appContext, NAME_DEVICE, "TB DI Phường Lê Hồng Phong") ?: "TB DI Phường Lê Hồng Phong"
//        logEnabled = ConfigUtil.getBool(appContext, DEFAULT_LOG_ENABLE, false)
//        resumeFromStart = ConfigUtil.getBool(appContext, DEFAULT_RESUME_FROM_START, false)
//        lat = ConfigUtil.getFloat(appContext, LATITUDE, 0f)
//        lng = ConfigUtil.getFloat(appContext, LONGTITUDE, 0f)
//        userNameController = ConfigUtil.getString(appContext, USER_NAME_CONTROLLER, "") ?: ""
//        providerValue = ConfigUtil.getStringEncrypt(appContext, PROVIDER, "VATC") ?: "VATC"
        ////ha nam
//        volume = ConfigUtil.getString(appContext, DEFAULT_VOLUME, "100") ?: "100"
//        androidVolume = ConfigUtil.getString(appContext, DEFAULT_ANDROID_VOLUME, "10") ?: "10"
//        piCode = ConfigUtil.getString(appContext, DEFAULT_PI_CODE, "0") ?: "0"
//        psName = ConfigUtil.getString(appContext, DEFAULT_PS_NAME, "AAA") ?: "AAA"
//        destinationId = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_ID, "H25.25.33") ?: "H25.25.33"
//        destinationName = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_NAME, "Xã Đồn Xá") ?: "Xã Đồn Xá"
//        nameDevice = ConfigUtil.getString(appContext, NAME_DEVICE, "TB DI Xã Đồn Xá R") ?: "TB Xã Đồn Xá R"
//        logEnabled = ConfigUtil.getBool(appContext, DEFAULT_LOG_ENABLE, false)
//        resumeFromStart = ConfigUtil.getBool(appContext, DEFAULT_RESUME_FROM_START, false)
//        lat = ConfigUtil.getFloat(appContext, LATITUDE, 0f)
//        lng = ConfigUtil.getFloat(appContext, LONGTITUDE, 0f)
//        userNameController = ConfigUtil.getString(appContext, USER_NAME_CONTROLLER, "") ?: ""
//        providerValue = ConfigUtil.getStringEncrypt(appContext, PROVIDER, "VATC") ?: "VATC"

        volume = ConfigUtil.getString(appContext, DEFAULT_VOLUME, "100") ?: "100"
        androidVolume = ConfigUtil.getString(appContext, DEFAULT_ANDROID_VOLUME, "10") ?: "10"
        piCode = ConfigUtil.getString(appContext, DEFAULT_PI_CODE, "0") ?: "0"
        psName = ConfigUtil.getString(appContext, DEFAULT_PS_NAME, "AAA") ?: "AAA"
        destinationId = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_ID, "H39.20.25") ?: "H39.20.25"
        destinationName = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_NAME, "Xã Thạnh Lợi") ?: "Xã Thạnh Lợi"
        nameDevice = ConfigUtil.getString(appContext, NAME_DEVICE, "TB DI Xã Thạnh Lợi") ?: "TB Xã Thạnh Lợi"
        resumeFromStart = ConfigUtil.getBool(appContext, DEFAULT_RESUME_FROM_START, false)
        lat = ConfigUtil.getFloat(appContext, LATITUDE, 0f)
        lng = ConfigUtil.getFloat(appContext, LONGTITUDE, 0f)
        userNameController = ConfigUtil.getString(appContext, USER_NAME_CONTROLLER, "") ?: ""
        providerValue = ConfigUtil.getStringEncrypt(appContext, PROVIDER, "VATC") ?: "VATC"

        //bac giang
//        volume = ConfigUtil.getString(appContext, DEFAULT_VOLUME, "100") ?: "100"
//        androidVolume = ConfigUtil.getString(appContext, DEFAULT_ANDROID_VOLUME, "10") ?: "10"
//        piCode = ConfigUtil.getString(appContext, DEFAULT_PI_CODE, "0") ?: "0"
//        psName = ConfigUtil.getString(appContext, DEFAULT_PS_NAME, "AAA") ?: "AAA"
//        destinationId = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_ID, "H02.37.27") ?: "H02.37.27"
//        destinationName = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_NAME, "Xã Dương Hưu") ?: "Xã Dương Hưu"
//        nameDevice = ConfigUtil.getString(appContext, NAME_DEVICE, "TB DI Xã Dương Hưu") ?: "TB Xã Dương Hưu"
//        logEnabled = ConfigUtil.getBool(appContext, DEFAULT_LOG_ENABLE, false)
//        resumeFromStart = ConfigUtil.getBool(appContext, DEFAULT_RESUME_FROM_START, false)
//        lat = ConfigUtil.getFloat(appContext, LATITUDE, 0f)
//        lng = ConfigUtil.getFloat(appContext, LONGTITUDE, 0f)
//        userNameController = ConfigUtil.getString(appContext, USER_NAME_CONTROLLER, "") ?: ""
//        providerValue = ConfigUtil.getStringEncrypt(appContext, PROVIDER, "VATC") ?: "VATC"

        //binh dinh
//        volume = ConfigUtil.getString(appContext, DEFAULT_VOLUME, "100") ?: "100"
//        androidVolume = ConfigUtil.getString(appContext, DEFAULT_ANDROID_VOLUME, "10") ?: "10"
//        piCode = ConfigUtil.getString(appContext, DEFAULT_PI_CODE, "0") ?: "0"
//        psName = ConfigUtil.getString(appContext, DEFAULT_PS_NAME, "AAA") ?: "AAA"
//        destinationId =
//            ConfigUtil.getString(appContext, DEFAULT_DESTINATION_ID, "H08.37") ?: "H08.37"
//        destinationName = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_NAME, "Thành phố Quy Nhơn")
//            ?: "Thành phố Quy Nhơn"
//        nameDevice =
//            ConfigUtil.getString(appContext, NAME_DEVICE, "TB DI Thành phố Quy Nhơn") ?: "TB Thành phố Quy Nhơn"
//        logEnabled = ConfigUtil.getBool(appContext, DEFAULT_LOG_ENABLE, false)
//        resumeFromStart = ConfigUtil.getBool(appContext, DEFAULT_RESUME_FROM_START, false)
//        lat = ConfigUtil.getFloat(appContext, LATITUDE, 0f)
//        lng = ConfigUtil.getFloat(appContext, LONGTITUDE, 0f)
//        userNameController = ConfigUtil.getString(appContext, USER_NAME_CONTROLLER, "") ?: ""
//        providerValue = ConfigUtil.getStringEncrypt(appContext, PROVIDER, "VATC") ?: "VATC"
        //
        //
//        volume = ConfigUtil.getString(appContext, DEFAULT_VOLUME, "100") ?: "100"
//        androidVolume = ConfigUtil.getString(appContext, DEFAULT_ANDROID_VOLUME, "10") ?: "10"
//        piCode = ConfigUtil.getString(appContext, DEFAULT_PI_CODE, "0") ?: "0"
//        psName = ConfigUtil.getString(appContext, DEFAULT_PS_NAME, "AAA") ?: "AAA"
//        destinationId =
//            ConfigUtil.getString(appContext, DEFAULT_DESTINATION_ID, "H19.32") ?: "H19.32"
//        destinationName =
//            ConfigUtil.getString(appContext, DEFAULT_DESTINATION_NAME, "Huyện Nhơn Trạch")
//                ?: "Huyện Nhơn Trạch"
//        nameDevice =
//            ConfigUtil.getString(appContext, NAME_DEVICE, "TB DI Thành phố Quy Nhơn")
//                ?: "TB Thành phố Quy Nhơn"
//        logEnabled = ConfigUtil.getBool(appContext, DEFAULT_LOG_ENABLE, false)
//        resumeFromStart = ConfigUtil.getBool(appContext, DEFAULT_RESUME_FROM_START, false)
//        lat = ConfigUtil.getFloat(appContext, LATITUDE, 0f)
//        lng = ConfigUtil.getFloat(appContext, LONGTITUDE, 0f)
//        userNameController = ConfigUtil.getString(appContext, USER_NAME_CONTROLLER, "") ?: ""
//        providerValue = ConfigUtil.getStringEncrypt(appContext, PROVIDER, "VATC") ?: "VATC"


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
        if (responseHub.destinationId != "" && responseHub.destinationId != null) {
            ConfigUtil.putString(appContext, DEFAULT_DESTINATION_ID, responseHub.destinationId)
            destinationId = responseHub.destinationId?: ""
        }
        if (responseHub.destinationName != "" && responseHub.destinationName != null) {
            ConfigUtil.putString(appContext, DEFAULT_DESTINATION_NAME, responseHub.destinationName)
            destinationName = responseHub.destinationName?: ""
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