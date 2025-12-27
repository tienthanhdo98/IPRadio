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
        ConfigUtil.putStringEncrypt(appContext, ConfigUtil.USERNAME, "123433232323")
        ConfigUtil.putStringEncrypt(
            appContext,
            ConfigUtil.PASSWORD,
            "123433232323"
        )
        ConfigUtil.putStringEncrypt(appContext, ConfigUtil.USERNAME_KEY, "Username")
        ConfigUtil.putStringEncrypt(appContext, ConfigUtil.PASSWORD_KEY, "Password")
        ConfigUtil.putStringEncrypt(
            appContext,
            ConfigUtil.URL_TOKEN,
            "https://thongtinnguon.mobifone.vn/cmsapi/api/oauth/token"
        )
        ConfigUtil.putStringEncrypt(
            appContext,
            ConfigUtil.URL_MQTT,
            "tcp://thongtinnguon.mobifone.vn:8883"
        )
        ConfigUtil.putStringEncrypt(
            appContext,
            ConfigUtil.URL_MQTT_INFO,
            "https://thongtinnguon.mobifone.vn/cmsapi/api/mqtt/dangkythietbi"
        )
//        ConfigUtil.putStringEncrypt(
//            appContext,
//            ConfigUtil.URL_MQTT_SOURCE,
//            "https://v2-provider-data-warehouse.tek4tv.vn/Source/"
//        )
//        ConfigUtil.putStringEncrypt(
//            appContext,
//            ConfigUtil.URL_WSS,
//            "wss://websocket-thongtinnguon.quangngai.gov.vn/ws/ipradio"
//        )
//
//        ConfigUtil.putStringEncrypt(appContext, ConfigUtil.USERNAME, "nghean_tek4tv")
//        ConfigUtil.putStringEncrypt(
//            appContext,
//            ConfigUtil.PASSWORD,
//            "5ca8ecc0-ed98-4413-95c2-b5646155b490"
//        )
//        ConfigUtil.putStringEncrypt(appContext, ConfigUtil.USERNAME_KEY, "USERNAME")
//        ConfigUtil.putStringEncrypt(appContext, ConfigUtil.PASSWORD_KEY, "PASSWORD")
//        ConfigUtil.putStringEncrypt(
//            appContext,
//            ConfigUtil.URL_TOKEN,
//            "https://gateway-thongtinnguon.nghean.gov.vn/api/v2/oauth/token"
//        )
//        ConfigUtil.putStringEncrypt(
//            appContext,
//            ConfigUtil.URL_MQTT,
//            "tcp://thongtinnguon.mobifone.vn:8883"
//        )
//        ConfigUtil.putStringEncrypt(
//            appContext,
//            ConfigUtil.URL_MQTT_INFO,
//            "https://thongtinnguon.mobifone.vn/cmsapi/api/mqtt/dangkythietbi"
//        )
//        ConfigUtil.putStringEncrypt(
//            appContext,
//            ConfigUtil.URL_MQTT_SOURCE,
//            "https://v2-provider-data-warehouse.tek4tv.vn/Source/"
//        )
        ConfigUtil.putStringEncrypt(
            appContext,
            ConfigUtil.URL_WSS,
            "wss://websocket-thongtinnguon.nghean.gov.vn/ws/ipradio"
        )


    }

    companion object {
        lateinit var appContext: Context
        var instance: MyApp? = null
    }
}