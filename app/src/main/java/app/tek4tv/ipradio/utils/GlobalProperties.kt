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
    var providerValue: String = "VATC"


    init {
        //Quang Ngai
        volume = ConfigUtil.getString(appContext, DEFAULT_VOLUME, "100") ?: "100"
        piCode = ConfigUtil.getString(appContext, DEFAULT_PI_CODE, "0") ?: "0"
        psName = ConfigUtil.getString(appContext, DEFAULT_PS_NAME, "AAA") ?: "AAA"
        destinationId = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_ID, "H48.52.01") ?: "H48.52.01"
        destinationName = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_NAME, "Thị trấn Ba Tơ") ?: "Thị trấn Ba Tơ"
        nameDevice = ConfigUtil.getString(appContext, NAME_DEVICE, "TB Thị trấn Ba Tơ") ?: "TB Thị trấn Ba Tơ"
        logEnabled = ConfigUtil.getBool(appContext, DEFAULT_LOG_ENABLE, false)
        resumeFromStart = ConfigUtil.getBool(appContext, DEFAULT_RESUME_FROM_START, false)
        lat = ConfigUtil.getFloat(appContext, LATITUDE, 0f)
        lng = ConfigUtil.getFloat(appContext, LONGTITUDE, 0f)
        userNameController = ConfigUtil.getString(appContext, USER_NAME_CONTROLLER, "") ?: ""
        providerValue = ConfigUtil.getString(appContext, PROVIDER, "TEK4TV") ?: "TEK4TV"
        //hai phong
//        volume = ConfigUtil.getString(appContext, DEFAULT_VOLUME, "100") ?: "100"
//        piCode = ConfigUtil.getString(appContext, DEFAULT_PI_CODE, "0") ?: "0"
//        psName = ConfigUtil.getString(appContext, DEFAULT_PS_NAME, "AAA") ?: "AAA"
//        destinationId = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_ID, "H24.30.3") ?: "H24.30.3"
//        destinationName = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_NAME, "Xã Hiền Hào") ?: "Xã Hiền Hào"
//        nameDevice = ConfigUtil.getString(appContext, NAME_DEVICE, "TB Xã Hiền Hào") ?: "TB Xã Hiền Hào"
//        logEnabled = ConfigUtil.getBool(appContext, DEFAULT_LOG_ENABLE, false)
//        resumeFromStart = ConfigUtil.getBool(appContext, DEFAULT_RESUME_FROM_START, false)
//        lat = ConfigUtil.getFloat(appContext, LATITUDE, 0f)
//        lng = ConfigUtil.getFloat(appContext, LONGTITUDE, 0f)
//        userNameController = ConfigUtil.getString(appContext, USER_NAME_CONTROLLER, "") ?: ""
//        providerValue = ConfigUtil.getStringEncrypt(appContext, PROVIDER, "VATC") ?: "VATC"

        //binh dinh
//        volume = ConfigUtil.getString(appContext, DEFAULT_VOLUME, "100") ?: "100"
//        piCode = ConfigUtil.getString(appContext, DEFAULT_PI_CODE, "0") ?: "0"
//        psName = ConfigUtil.getString(appContext, DEFAULT_PS_NAME, "AAA") ?: "AAA"
//        destinationId = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_ID, "H19.32") ?: "H19.32"
//        destinationName = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_NAME, "Huyện Nhơn Trạch") ?: "Huyện Nhơn Trạch"
//        nameDevice = ConfigUtil.getString(appContext, NAME_DEVICE, "TB Huyện Nhơn Trạch") ?: "TB Huyện Nhơn Trạch"
//        logEnabled = ConfigUtil.getBool(appContext, DEFAULT_LOG_ENABLE, false)
//        resumeFromStart = ConfigUtil.getBool(appContext, DEFAULT_RESUME_FROM_START, false)
//        lat = ConfigUtil.getFloat(appContext, LATITUDE, 0f)
//        lng = ConfigUtil.getFloat(appContext, LONGTITUDE, 0f)
//        userNameController = ConfigUtil.getString(appContext, USER_NAME_CONTROLLER, "") ?: ""
//        providerValue = ConfigUtil.getStringEncrypt(appContext, PROVIDER, "VATC") ?: "VATC"

        //binh dinh an hữu
//        volume = ConfigUtil.getString(appContext, DEFAULT_VOLUME, "100") ?: "100"
//        piCode = ConfigUtil.getString(appContext, DEFAULT_PI_CODE, "0") ?: "0"
//        psName = ConfigUtil.getString(appContext, DEFAULT_PS_NAME, "AAA") ?: "AAA"
//        destinationId = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_ID, "H08.34.78") ?: "H08.34.78"
//        destinationName = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_NAME, "Phường Hoài Thanh") ?: "Phường Hoài Thanh"
//        nameDevice = ConfigUtil.getString(appContext, NAME_DEVICE, "TB Phường Hoài Thanh encoder") ?: "TB Phường Hoài Thanh"
//        logEnabled = ConfigUtil.getBool(appContext, DEFAULT_LOG_ENABLE, false)
//        resumeFromStart = ConfigUtil.getBool(appContext, DEFAULT_RESUME_FROM_START, false)
//        lat = ConfigUtil.getFloat(appContext, LATITUDE, 0f)
//        lng = ConfigUtil.getFloat(appContext, LONGTITUDE, 0f)
//        userNameController = ConfigUtil.getString(appContext, USER_NAME_CONTROLLER, "") ?: ""
//        providerValue = ConfigUtil.getStringEncrypt(appContext, PROVIDER, "VATC") ?: "VATC"

//        volume = ConfigUtil.getString(appContext, DEFAULT_VOLUME, "100") ?: "100"
//        piCode = ConfigUtil.getString(appContext, DEFAULT_PI_CODE, "0") ?: "0"
//        psName = ConfigUtil.getString(appContext, DEFAULT_PS_NAME, "AAA") ?: "AAA"
//        destinationId = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_ID, "H08.34.82") ?: "H08.34.82"
//        destinationName = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_NAME, "Phường Hoài Hương") ?: "Phường Hoài Hương"
//        nameDevice = ConfigUtil.getString(appContext, NAME_DEVICE, "TB Phường Hoài Hương") ?: "Phường Hoài Hương"
//        logEnabled = ConfigUtil.getBool(appContext, DEFAULT_LOG_ENABLE, false)
//        resumeFromStart = ConfigUtil.getBool(appContext, DEFAULT_RESUME_FROM_START, false)
//        lat = ConfigUtil.getFloat(appContext, LATITUDE, 0f)
//        lng = ConfigUtil.getFloat(appContext, LONGTITUDE, 0f)
//        userNameController = ConfigUtil.getString(appContext, USER_NAME_CONTROLLER, "") ?: ""
//        providerValue = ConfigUtil.getStringEncrypt(appContext, PROVIDER, "") ?: ""

//        volume = ConfigUtil.getString(appContext, DEFAULT_VOLUME, "100") ?: "100"
//        piCode = ConfigUtil.getString(appContext, DEFAULT_PI_CODE, "0") ?: "0"
//        psName = ConfigUtil.getString(appContext, DEFAULT_PS_NAME, "AAA") ?: "AAA"
//        destinationId = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_ID, "H55.94.01") ?: "H55.94.01"
//        destinationName = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_NAME, "Thị trấn Hương Sơn") ?: "Thị trấn Hương Sơn"
//        nameDevice = ConfigUtil.getString(appContext, NAME_DEVICE, "TB Thị trấn Hương Sơn") ?: "TB Thị trấn Hương Sơn"
//        logEnabled = ConfigUtil.getBool(appContext, DEFAULT_LOG_ENABLE, false)
//        resumeFromStart = ConfigUtil.getBool(appContext, DEFAULT_RESUME_FROM_START, false)
//        lat = ConfigUtil.getFloat(appContext, LATITUDE, 0f)
//        lng = ConfigUtil.getFloat(appContext, LONGTITUDE, 0f)
//        userNameController = ConfigUtil.getString(appContext, USER_NAME_CONTROLLER, "") ?: ""
//        providerValue = ConfigUtil.getStringEncrypt(appContext, PROVIDER, "") ?: ""

//        volume = ConfigUtil.getString(appContext, DEFAULT_VOLUME, "100") ?: "100"
//        piCode = ConfigUtil.getString(appContext, DEFAULT_PI_CODE, "0") ?: "0"
//        psName = ConfigUtil.getString(appContext, DEFAULT_PS_NAME, "AAA") ?: "AAA"
//        destinationId = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_ID, "H13.36") ?: "H13.36"
//        destinationName = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_NAME, "Quận Ninh Kiều") ?: "Quận Ninh Kiều"
//        nameDevice = ConfigUtil.getString(appContext, NAME_DEVICE, "TB Quận Ninh Kiều") ?: "TB Quận Ninh Kiều"
//        logEnabled = ConfigUtil.getBool(appContext, DEFAULT_LOG_ENABLE, false)
//        resumeFromStart = ConfigUtil.getBool(appContext, DEFAULT_RESUME_FROM_START, false)
//        lat = ConfigUtil.getFloat(appContext, LATITUDE, 0f)
//        lng = ConfigUtil.getFloat(appContext, LONGTITUDE, 0f)
//        userNameController = ConfigUtil.getString(appContext, USER_NAME_CONTROLLER, "") ?: ""
//        providerValue = ConfigUtil.getStringEncrypt(appContext, PROVIDER, "") ?: ""

        //bac giang
//            volume = ConfigUtil.getString(appContext, DEFAULT_VOLUME, "100") ?: "100"
//        piCode = ConfigUtil.getString(appContext, DEFAULT_PI_CODE, "0") ?: "0"
//        psName = ConfigUtil.getString(appContext, DEFAULT_PS_NAME, "AAA") ?: "AAA"
//        destinationId = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_ID, "H02.37.30") ?: "H02.37.30"
//        destinationName = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_NAME, "Xã Tuấn Đạo") ?: "Xã Tuấn Đạo"
//        nameDevice = ConfigUtil.getString(appContext, NAME_DEVICE, "TB Xã Tuấn Đạo") ?: "TB Xã Tuấn Đạo"
//        logEnabled = ConfigUtil.getBool(appContext, DEFAULT_LOG_ENABLE, false)
//        resumeFromStart = ConfigUtil.getBool(appContext, DEFAULT_RESUME_FROM_START, false)
//        lat = ConfigUtil.getFloat(appContext, LATITUDE, 0f)
//        lng = ConfigUtil.getFloat(appContext, LONGTITUDE, 0f)
//        userNameController = ConfigUtil.getString(appContext, USER_NAME_CONTROLLER, "") ?: ""
//        providerValue = ConfigUtil.getStringEncrypt(appContext, PROVIDER, "VATC") ?: "VATC"

//        thai nguyen
//            volume = ConfigUtil.getString(appContext, DEFAULT_VOLUME, "100") ?: "100"
//        piCode = ConfigUtil.getString(appContext, DEFAULT_PI_CODE, "0") ?: "0"
//        psName = ConfigUtil.getString(appContext, DEFAULT_PS_NAME, "AAA") ?: "AAA"
//        destinationId = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_ID, "H02.37.27") ?: "H02.37.27"
//        destinationName = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_NAME, "Xã Dương Hưu") ?: "Xã Dương Hưu"
//        nameDevice = ConfigUtil.getString(appContext, NAME_DEVICE, "TB Xã Dương Hưu") ?: "TB Xã Dương Hưu"
//        logEnabled = ConfigUtil.getBool(appContext, DEFAULT_LOG_ENABLE, false)
//        resumeFromStart = ConfigUtil.getBool(appContext, DEFAULT_RESUME_FROM_START, false)
//        lat = ConfigUtil.getFloat(appContext, LATITUDE, 0f)
//        lng = ConfigUtil.getFloat(appContext, LONGTITUDE, 0f)
//        userNameController = ConfigUtil.getString(appContext, USER_NAME_CONTROLLER, "") ?: ""
//        providerValue = ConfigUtil.getStringEncrypt(appContext, PROVIDER, "VATC") ?: "VATC"
    //
//        volume = ConfigUtil.getString(appContext, DEFAULT_VOLUME, "100") ?: "100"
//        piCode = ConfigUtil.getString(appContext, DEFAULT_PI_CODE, "0") ?: "0"
//        psName = ConfigUtil.getString(appContext, DEFAULT_PS_NAME, "AAA") ?: "AAA"
//        destinationId = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_ID, "H55.93.10") ?: "H55.93.10"
//        destinationName = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_NAME, "Xã Thành Công") ?: "Xã Thành Công"
//        nameDevice = ConfigUtil.getString(appContext, NAME_DEVICE, "TB Xã Thành Công") ?: "TB Xã Thành Công"
//        logEnabled = ConfigUtil.getBool(appContext, DEFAULT_LOG_ENABLE, false)
//        resumeFromStart = ConfigUtil.getBool(appContext, DEFAULT_RESUME_FROM_START, false)
//        lat = ConfigUtil.getFloat(appContext, LATITUDE, 0f)
//        lng = ConfigUtil.getFloat(appContext, LONGTITUDE, 0f)
//        userNameController = ConfigUtil.getString(appContext, USER_NAME_CONTROLLER, "") ?: ""
//        providerValue = ConfigUtil.getStringEncrypt(appContext, PROVIDER, "VATC") ?: "VATC"
    //
    //      hanam
//            volume = ConfigUtil.getString(appContext, DEFAULT_VOLUME, "100") ?: "100"
//        piCode = ConfigUtil.getString(appContext, DEFAULT_PI_CODE, "0") ?: "0"
//        psName = ConfigUtil.getString(appContext, DEFAULT_PS_NAME, "AAA") ?: "AAA"
//        destinationId = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_ID, "H25.25.33") ?: "H25.25.33"
//        destinationName = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_NAME, "Xã Đồn Xá") ?: "Xã Đồn Xá"
//        nameDevice = ConfigUtil.getString(appContext, NAME_DEVICE, "TB Xã Đồn Xá") ?: "TB Xã Đồn Xá"
//        logEnabled = ConfigUtil.getBool(appContext, DEFAULT_LOG_ENABLE, false)
//        resumeFromStart = ConfigUtil.getBool(appContext, DEFAULT_RESUME_FROM_START, false)
//        lat = ConfigUtil.getFloat(appContext, LATITUDE, 0f)
//        lng = ConfigUtil.getFloat(appContext, LONGTITUDE, 0f)
//        userNameController = ConfigUtil.getString(appContext, USER_NAME_CONTROLLER, "") ?: ""
//        providerValue = ConfigUtil.getStringEncrypt(appContext, PROVIDER, "VATC") ?: "VATC"

        //binh duong
//        volume = ConfigUtil.getString(appContext, DEFAULT_VOLUME, "100") ?: "100"
//        piCode = ConfigUtil.getString(appContext, DEFAULT_PI_CODE, "0") ?: "0"
//        psName = ConfigUtil.getString(appContext, DEFAULT_PS_NAME, "AAA") ?: "AAA"
//        destinationId = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_ID, "H09.31.13") ?: "H09.31.13"
//        destinationName = ConfigUtil.getString(appContext, DEFAULT_DESTINATION_NAME, "Phường Phú Cường") ?: "Phường Phú Cường"
//        nameDevice = ConfigUtil.getString(appContext, NAME_DEVICE, "TB Phường Phú Cường") ?: "TB Phường Phú Cường"
//        logEnabled = ConfigUtil.getBool(appContext, DEFAULT_LOG_ENABLE, false)
//        resumeFromStart = ConfigUtil.getBool(appContext, DEFAULT_RESUME_FROM_START, false)
//        lat = ConfigUtil.getFloat(appContext, LATITUDE, 0f)
//        lng = ConfigUtil.getFloat(appContext, LONGTITUDE, 0f)
//        userNameController = ConfigUtil.getString(appContext, USER_NAME_CONTROLLER, "") ?: ""
//        providerValue = ConfigUtil.getString(appContext, PROVIDER, "VTTHANHDAT") ?: "VTTHANHDAT"
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
            destinationId = responseHub.destinationId?: ""
        }
        if (responseHub.destinationName != "" && responseHub.destinationName != null) {
            ConfigUtil.putString(appContext, DEFAULT_DESTINATION_NAME, responseHub.destinationName)
            destinationName = responseHub.destinationName?: ""
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
            if (instance == null)
                instance = GlobalProperties()

            return instance!!
        }
    }
}