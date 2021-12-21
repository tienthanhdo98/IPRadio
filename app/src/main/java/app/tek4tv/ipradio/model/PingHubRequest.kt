package app.tek4tv.ipradio.model

/*import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName*/
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class PingHubRequest {
    @Json(name = "ConnectionId")

    var connectionId: String? = null

    @Json(name = "IMEI")

    var imei: String? = null

    @Json(name = "StartTime")

    var startTime: String? = null

    @Json(name = "Status")

    var status: String? = null

    @Json(name = "Volume")

    var volume: String? = null

    @Json(name = "Message")

    var message: String? = null

    @Json(name = "Video")

    var video: String? = null

    @Json(name = "NetworkInfo")
    var networkInfo = ""

    @Json(name = "Version")
    var version = ""

    @Json(name = "DestinationID")
    var destinationId: String? = ""

    @Json(name = "DestinationName")
    var destinationName: String? = ""

    //
    @Json(name = "Lat")
    var lat: Float? = null

    @Json(name = "Lng")
    var lng: Float? = null

    @Json(name = "DeviceName")
    var deviceName: String? = ""

    @Json(name = "Start")
    var start: String? = null

    @Json(name = "Duration")
    var duration: Int = 0

    @Json(name = "PI")
    var piCode = ""

    @Json(name = "PS")
    var psName = ""

    @Json(name = "Log")
    var logEnabled: Boolean? = null

    @Json(name = "Type")
    var type: String = ""

    @Json(name = "Level")
    var level: Int = 0

    @Json(name = "Resume")
    var resumeFromStart: Boolean? = null

    @Json(name = "Name")
    var name: String = ""


}