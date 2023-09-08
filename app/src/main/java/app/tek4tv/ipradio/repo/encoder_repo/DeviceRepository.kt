package app.tek4tv.ipradio.repo.encoder_repo

import android.content.Context
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.model.encoder_model.Device


class DeviceRepository(
    private val appContext: Context
) {

    var latestDeviceList = listOf<Device>()
    val itemCheckArr = mutableMapOf<Int, Boolean>()



    companion object
    {
        private var instance : DeviceRepository? = null

        fun getInstance() : DeviceRepository
        {
            if(instance == null)
                instance = DeviceRepository(MyApp.instance!!.applicationContext)

            return instance!!
        }
    }
}