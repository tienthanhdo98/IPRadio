package app.tek4tv.ipradio

import android.app.Application
import android.content.Context
import app.tek4tv.ipradio.utils.ConfigUtil
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()

        instance = this
        ConfigUtil.putStringEncrypt(applicationContext, ConfigUtil.USERNAME, "nghean_tek4tv")
        ConfigUtil.putStringEncrypt(
            applicationContext,
            ConfigUtil.PASSWORD,
            "5ca8ecc0-ed98-4413-95c2-b5646155b490"
        )
        ConfigUtil.putStringEncrypt(applicationContext, ConfigUtil.USERNAME_KEY, "USERNAME")
        ConfigUtil.putStringEncrypt(applicationContext, ConfigUtil.PASSWORD_KEY, "PASSWORD")
        ConfigUtil.putStringEncrypt(
            applicationContext,
            ConfigUtil.URL_TOKEN,
            "https://gateway-thongtinnguon.nghean.gov.vn/api/v2/oauth/token"
        )
        ConfigUtil.putStringEncrypt(
            applicationContext,
            ConfigUtil.URL_MQTT,
            "tcp://mqtt-thongtinnguon.nghean.gov.vn:1883"
        )
        ConfigUtil.putStringEncrypt(
            applicationContext,
            ConfigUtil.URL_MQTT_INFO,
            "https://gateway-thongtinnguon.nghean.gov.vn/v2/mqtt-ipradio/Ipradio/mqtt/dangkythietbi"
        )


    }

    companion object {
        var instance: MyApp? = null
    }
}