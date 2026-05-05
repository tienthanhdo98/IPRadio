package app.tek4tv.ipradio

import android.app.Application
import android.content.Context
import app.tek4tv.ipradio.utils.su.NetworkShellUtils

import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MyApp : Application() {
    val TAG = "HomeViewModel"
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        instance = this
        //a7
        val globalProperties = app.tek4tv.ipradio.utils.GlobalProperties.getInst()
        val gateway = if (globalProperties.settingsTxType == 0) globalProperties.gateway else ""
        NetworkShellUtils.setUniversalStaticIp(
            globalProperties.localIp,
            globalProperties.subnet,
            gateway,
            "8.8.8.8",
            "1.1.1.1"
        )

        //a9 b02
//        val globalProperties = app.tek4tv.ipradio.utils.GlobalProperties.getInst()
//        EthernetStaticIpUtils.forceStaticIp(
//            globalProperties.localIp,
//            globalProperties.gateway,
//            "8.8.8.8",
//            "1.1.1.1"
//        )
    }

    companion object {
        lateinit var appContext: Context
        var instance: MyApp? = null
    }
}