package app.tek4tv.voicestream.model

import app.tek4tv.ipradio.model.encoder_model.Device
import com.squareup.moshi.Json

data class DeviceResponse(
    @Json(name = "Data")
    var deviceList : List<Device>
)
