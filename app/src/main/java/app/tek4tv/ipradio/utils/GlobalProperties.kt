package app.tek4tv.ipradio.utils

import android.content.Context
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.model.ResponseHub

class GlobalProperties {

    private val appContext : Context = MyApp.instance!!.applicationContext

    val DEFAULT_VOLUME = "DEFAULT_VOLUME"
    val DEFAULT_PI_CODE = "DEFAULT_PI_CODE"
    val DEFAULT_PS_NAME = "DEFAULT_PS_NAME"
    val DEFAULT_LOG_ENABLE = "DEFAULT_LOG_ENABLE"
    val DEFAULT_RESUME_FROM_START = "DEFAULT_RESUME_FROM_START"

    var volume = "60"
    var piCode = "0"
    var psName = "AAA"
    var logEnabled = false
    var resumeFromStart = false

    init {
        volume = ConfigUtil.getString(appContext, DEFAULT_VOLUME, "60") ?: "60"
        piCode = ConfigUtil.getString(appContext, DEFAULT_PI_CODE, "0") ?: "0"
        psName = ConfigUtil.getString(appContext, DEFAULT_PS_NAME, "AAA") ?: "AAA"
        logEnabled = ConfigUtil.getBool(appContext, DEFAULT_LOG_ENABLE, false)
        resumeFromStart = ConfigUtil.getBool(appContext, DEFAULT_RESUME_FROM_START, false)
    }

    fun saveProperties(responseHub: ResponseHub)
    {
        if(responseHub.volume != "")
        {
            saveVolume(responseHub.volume)
        }

        if(responseHub.piCode != "")
        {
            ConfigUtil.putString(appContext, DEFAULT_PI_CODE, responseHub.piCode)
            piCode = responseHub.piCode
        }

        if(responseHub.psName != "")
        {
            ConfigUtil.putString(appContext, DEFAULT_PS_NAME, responseHub.psName)
            psName = responseHub.psName
        }

        if(responseHub.logEnabled != null)
        {
            ConfigUtil.putBool(appContext, DEFAULT_LOG_ENABLE, responseHub.logEnabled!!)
            logEnabled = responseHub.logEnabled!!
        }

        if(responseHub.resumeFromStart != null)
        {
            ConfigUtil.putBool(appContext, DEFAULT_RESUME_FROM_START, responseHub.resumeFromStart!!)
            resumeFromStart = responseHub.resumeFromStart!!
        }
    }

    fun saveVolume(vol : String)
    {
        ConfigUtil.putString(appContext, DEFAULT_VOLUME, vol)
        volume = vol
    }

    companion object
    {
        private var instance : GlobalProperties? = null

        fun getInst() : GlobalProperties{
            if(instance == null)
                instance = GlobalProperties()

            return instance!!
        }
    }
}