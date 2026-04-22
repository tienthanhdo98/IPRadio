package app.tek4tv.ipradio

import android.app.Application
import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.utils.su.EthernetStaticIpUtils
import app.tek4tv.ipradio.utils.su.NetworkShellUtils

import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class MyApp : Application() {
    val TAG = "HomeViewModel"
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        instance = this
        //a7
//        NetworkShellUtils.setStaticIp(
//            "192.168.1.231", 24, "192.168.1.1", "8.8.8.8", "1.1.1.1"
//        )

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