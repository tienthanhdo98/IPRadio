package app.tek4tv.ipradio.model

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

data class DataLogModel(
    val imei: String,
    val data: String
)
data class  DataValueModel(
    val idPlaylist : String,
    val updateTime : String,
    val currentDeviceTime : Long,
    val timeFrame : List<String>,
    val currentPlaylistData : String? = "",
    val deviceInfo : String? = ""
)

fun DataValueModel.toJson(): String {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val jsonAdapter = moshi.adapter(DataValueModel::class.java)
    return jsonAdapter.toJson(this)
}