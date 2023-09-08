package app.tek4tv.voicestream.model

import com.squareup.moshi.Json

data class DeviceResponse(
    @Json(name = "Data")
    var deviceList : List<Device>
)
