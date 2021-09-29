package app.tek4tv.ipradio

import android.app.Application
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        //Firebase.database.setPersistenceEnabled(true)
    }

    companion object {
        var instance: MyApp? = null
    }
}