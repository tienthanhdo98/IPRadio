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
        ConfigUtil.putString(appContext, ConfigUtil.USERNAME, "123433232323")
        ConfigUtil.putString(
            appContext,
            ConfigUtil.PASSWORD,
            "123433232323"
        )
        ConfigUtil.putString(appContext, ConfigUtil.USERNAME_KEY, "USERNAME")
        ConfigUtil.putString(appContext, ConfigUtil.PASSWORD_KEY, "PASSWORD")
        ConfigUtil.putString(
            appContext,
            ConfigUtil.URL_TOKEN,
            "https://thongtinnguon.mobifone.vn/cmsapi/api/oauth/token"
        )
        ConfigUtil.putString(
            appContext,
            ConfigUtil.URL_MQTT,
            "tcp://thongtinnguon.mobifone.vn:8883"
        )
        ConfigUtil.putString(
            appContext,
            ConfigUtil.URL_MQTT_INFO,
            "https://thongtinnguon.mobifone.vn/cmsapi/api/mqtt/dangkythietbi"
        )

    }

    companion object {
        lateinit var appContext: Context
        var instance: MyApp? = null
    }
}