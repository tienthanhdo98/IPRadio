package app.tek4tv.ipradio.model.encoder_model

/*import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName*/
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class PingHubRequestEncoder {
    @Json(name = "ConnectionId")
     var connectionId: String? = null

    @Json(name = "IMEI")
     var imei: String? = null

    @Json(name = "Message")
     var message: String? = null

    @Json(name ="Type")
    var type :String? = null

    @Json(name = "Level")
    var level = 0

    @Json(name = "Duration")
    var duration : Int = 30

    @Json(name = "Name")
    var name : String = ""
    @Json(name = "StartTime")

    var startTime: String? = null

    @Json(name = "Status")

    var status: String? = null

    @Json(name = "Volume")

    var volume: String? = null

    @Json(name = "Video")

    var video: String? = null

    @Json(name = "NetworkInfo")
    var networkInfo : String? = null

    @Json(name = "Version")
    var version:String ?= null

    @Json(name = "DestinationID")
    var destinationId :String ? = null

    @Json(name = "DestinationName")
    var destinationName:String ? = null
    @Json(name = "AppName")
    var appName: String = ""
    @Json(name = "Provider")
    var provider: String = ""
}