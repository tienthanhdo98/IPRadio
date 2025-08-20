package app.tek4tv.ipradio.di

import app.tek4tv.ipradio.model.*
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Types
import java.lang.reflect.Type

object JsonAdapterProvider {
    val moshi = RetrofitInstance.moshi

    val typeThongTinKhac = Types.newParameterizedType(List::class.java, ThongTinKhac::class.java)
    val adapterThongTinKhac = RetrofitInstance.moshi.adapter<List<ThongTinKhac>>(typeThongTinKhac)

    val wssStatusAdapter: JsonAdapter<WssStatusModel> =
        moshi.adapter(WssStatusModel::class.java)
    val wssLiveAdapter: JsonAdapter<ReceivedMessageModel> =
        moshi.adapter(ReceivedMessageModel::class.java)
    val wssPayloadLiveAdapter: JsonAdapter<ReceivedMessageLiveModel> =
        moshi.adapter(ReceivedMessageLiveModel::class.java)
    val wssPayloadRemovePlaylistAdapter: JsonAdapter<ReceivedMessageRemovePlaylistModel> =
        moshi.adapter(ReceivedMessageRemovePlaylistModel::class.java)
    val wssPayloadControlDeviceAdapter: JsonAdapter<ReceivedMessageControlDeviceModel> =
        moshi.adapter(ReceivedMessageControlDeviceModel::class.java)
    val mediaItemMoshiAdapter: JsonAdapter<MediaItemModel> =
        moshi.adapter(MediaItemModel::class.java)

    val typeListMediaItem =
        Types.newParameterizedType(List::class.java, MediaItemModel::class.java)
    val jsonListMediaItemAdapter: JsonAdapter<List<MediaItemModel>> =
        moshi.adapter(typeListMediaItem)

    val typeMap =
        Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
    val jsonMapAdapter: JsonAdapter<Map<String, String>> = moshi.adapter(typeMap)

}