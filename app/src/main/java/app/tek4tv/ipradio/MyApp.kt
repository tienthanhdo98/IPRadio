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
        ConfigUtil.putStringEncrypt(appContext, ConfigUtil.USERNAME, "tek4tv_quangngai")
        ConfigUtil.putStringEncrypt(appContext, ConfigUtil.PASSWORD, "4bfb2c4a-e049-4d21-86e0-4ffdf854b8a1")
        ConfigUtil.putStringEncrypt(appContext, ConfigUtil.USERNAME_KEY, "USERNAME")
        ConfigUtil.putStringEncrypt(appContext, ConfigUtil.PASSWORD_KEY, "PASSWORD")
        ConfigUtil.putStringEncrypt(appContext, ConfigUtil.URL_TOKEN, "https://gateway-thongtinnguon.tek4tv.vn/api/v2/oauth/token")
        ConfigUtil.putStringEncrypt(appContext, ConfigUtil.URL_MQTT, "tcp://ipradio-mqtt-thongtinnguon.tek4tv.vn:1886")
        ConfigUtil.putStringEncrypt(appContext, ConfigUtil.URL_MQTT_INFO, "https://gateway-thongtinnguon.tek4tv.vn/v2/mqtt-ipradio/Ipradio/mqtt/dangkythietbi")
        ConfigUtil.putStringEncrypt(appContext, ConfigUtil.URL_MQTT_SOURCE, "https://v2-provider-data-warehouse.tek4tv.vn/Source/")
        ConfigUtil.putStringEncrypt(appContext, ConfigUtil.URL_WSS, "wss://gateway-thongtinnguon.tek4tv.vn/ws/ipradio")
    }

    companion object {
        lateinit var appContext: Context
        var instance: MyApp? = null
    }
}