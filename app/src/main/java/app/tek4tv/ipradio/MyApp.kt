package app.tek4tv.ipradio

import android.app.Application
import android.content.Context
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        instance = this
        //
    // Firebase.database.setPersistenceEnabled(true)
    }

    companion object {
        lateinit var appContext: Context
        var instance: MyApp? = null
    }
}