package app.tek4tv.ipradio

import android.app.Application
import android.content.Context
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.network.CaCertHelper
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()

        instance = this
        CaCertHelper.setupGlobalConfiguredSsl(this)
        ConfigUtil.putStringEncrypt(applicationContext, ConfigUtil.USERNAME, "danang_tek4tv_mqtt")
        ConfigUtil.putStringEncrypt(
            applicationContext,
            ConfigUtil.PASSWORD,
            "8cf8cd99-5460-41e2-8e40-1129c534037a"
        )
        ConfigUtil.putStringEncrypt(applicationContext, ConfigUtil.USERNAME_KEY, "USERNAME")
        ConfigUtil.putStringEncrypt(applicationContext, ConfigUtil.PASSWORD_KEY, "PASSWORD")
        ConfigUtil.putStringEncrypt(
            applicationContext,
            ConfigUtil.URL_TOKEN,
            "https://gateway-ttn.danang.gov.vn/api/v2/oauth/token"
        )
        ConfigUtil.putStringEncrypt(
            applicationContext,
            ConfigUtil.URL_MQTT,
            "tcp://deviceApi-ttn.danang.gov.vn:1883"
        )
        ConfigUtil.putStringEncrypt(
            applicationContext,
            ConfigUtil.URL_MQTT_INFO,
            "https://gateway-ttn.danang.gov.vn/api/v2/mqtt-ipradio/mqtt/dangkythietbi"
        )


    }

    companion object {
        var instance: MyApp? = null
    }
}