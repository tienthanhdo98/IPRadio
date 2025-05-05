package app.tek4tv.ipradio

import android.app.Application
import android.content.Context
import app.tek4tv.ipradio.utils.ConfigUtil
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        instance = this
//        ConfigUtil.putStringEncrypt(appContext, ConfigUtil.USERNAME, "tek4tv_quangngai")
//        ConfigUtil.putStringEncrypt(appContext, ConfigUtil.PASSWORD, "4bfb2c4a-e049-4d21-86e0-4ffdf854b8a1")
//        ConfigUtil.putStringEncrypt(appContext, ConfigUtil.USERNAME_KEY, "USERNAME")
//        ConfigUtil.putStringEncrypt(appContext, ConfigUtil.PASSWORD_KEY, "PASSWORD")
//        ConfigUtil.putStringEncrypt(appContext, ConfigUtil.URL_TOKEN, "https://gateway-thongtinnguon.quangngai.gov.vn/api/v2/oauth/token")
//        ConfigUtil.putStringEncrypt(appContext, ConfigUtil.URL_MQTT, "tcp://mqtt-thongtinnguon.quangngai.gov.vn:1883")
//        ConfigUtil.putStringEncrypt(appContext, ConfigUtil.URL_MQTT_INFO, "https://gateway-thongtinnguon.quangngai.gov.vn/v2/mqtt-ipradio/ipradio/mqtt/dangkythietbi")
//        ConfigUtil.putStringEncrypt(appContext, ConfigUtil.URL_MQTT_SOURCE, "https://v2-provider-data-warehouse.tek4tv.vn/Source/")
//        ConfigUtil.putStringEncrypt(appContext, ConfigUtil.URL_WSS, "wss://websocket-thongtinnguon.quangngai.gov.vn/ws/ipradio")
        //
        ConfigUtil.putStringEncrypt(appContext, ConfigUtil.USERNAME, "tek@vnptics.vn")
        ConfigUtil.putStringEncrypt(appContext, ConfigUtil.PASSWORD, "VnptICS@2023")
        ConfigUtil.putStringEncrypt(appContext, ConfigUtil.USERNAME_KEY, "Username")
        ConfigUtil.putStringEncrypt(appContext, ConfigUtil.PASSWORD_KEY, "Password")
        ConfigUtil.putStringEncrypt(
            appContext,
            ConfigUtil.URL_TOKEN,
            "https://htttn-api.bacninh.gov.vn/oauth/token"
        )
        ConfigUtil.putStringEncrypt(appContext, ConfigUtil.URL_MQTT, "htttn-mqtt.bacninh.gov.vn")
        ConfigUtil.putStringEncrypt(
            appContext,
            ConfigUtil.URL_MQTT_INFO,
            "https://htttn-api.bacninh.gov.vn/mqtt/dangkythietbi"
        )
//        ConfigUtil.putStringEncrypt(
//            appContext,
//            ConfigUtil.URL_MQTT_SOURCE,
//            "https://v2-provider-data-warehouse.tek4tv.vn/Source/"
//        )
        ConfigUtil.putStringEncrypt(
            appContext,
            ConfigUtil.URL_WSS,
            "wss://gateway-thongtinnguon.tek4tv.vn/ws/ipradio"
        )
    }

    companion object {
        lateinit var appContext: Context
        var instance: MyApp? = null
    }
}