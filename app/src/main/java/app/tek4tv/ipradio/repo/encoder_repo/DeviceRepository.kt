package app.tek4tv.ipradio.repo.encoder_repo

import android.content.Context
import app.tek4tv.ipradio.MyApp


class DeviceRepository(
    private val appContext: Context
) {


    companion object {
        private var instance: DeviceRepository? = null

        fun getInstance(): DeviceRepository {
            if (instance == null)
                instance = DeviceRepository(MyApp.instance!!.applicationContext)

            return instance!!
        }
    }
}