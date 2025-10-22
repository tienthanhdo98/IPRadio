package app.tek4tv.ipradio

import android.app.Application
import android.content.Context
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.ConfigUtil.URL_HUB
import app.tek4tv.ipradio.utils.ConfigUtil.URL_TOKEN
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        instance = this


    }

    companion object {
        lateinit var appContext: Context
        var instance: MyApp? = null
    }
}