package app.tek4tv.ipradio.model

import java.util.*

data class FbMediaReport(
    var name : String = "",
    var privateKey : Int,
    var volume : String = "",
    var paState : String = "",
    var start : Long = Date().time
)

data class HubConnectState(
    var state : String = "",
    var networkType : String? = null,
    var start : Long = Date().time
)

data class FbTimeFrameReport(
    var timeframeKey : String? = null,
    var status : String? = null,
    var time: Long = Date().time
)
{
    companion object
    {
        const val STATUS_START = "START"
        const val STATUS_STOP = "STOP"
    }
}

data class FbDeviceStatusReport(
    var status : String? = null,
    var time: Long = Date().time
){
    companion object
    {
        const val STATUS_ON = "ON"
    }
}