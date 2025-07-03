package app.tek4tv.ipradio.di

import app.tek4tv.ipradio.model.*
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Types
import java.lang.reflect.Type

object JsonAdapterProvider {
    private val moshi = MoshiInst.moshi

    val wssStatusAdapter: JsonAdapter<StatusModel> =
        moshi.adapter(StatusModel::class.java)
    val wssLiveAdapter: JsonAdapter<ReceivedMessageModel> =
        moshi.adapter(ReceivedMessageModel::class.java)
    val wssPayloadScheduleAdapter: JsonAdapter<ReceivedMessageScheduleModel> =
        moshi.adapter(ReceivedMessageScheduleModel::class.java)
    val wssPayloadLiveAdapter: JsonAdapter<ReceivedMessageLiveModel> =
        moshi.adapter(ReceivedMessageLiveModel::class.java)
    val wssPayloadRemovePlaylistAdapter: JsonAdapter<ReceivedMessageRemovePlaylistModel> =
        moshi.adapter(ReceivedMessageRemovePlaylistModel::class.java)
    val wssPayloadControlDeviceAdapter: JsonAdapter<ReceivedMessageControlDeviceModel> =
        moshi.adapter(ReceivedMessageControlDeviceModel::class.java)
    val mediaItemMoshiAdapter: JsonAdapter<MediaItem> =
        MoshiInst.moshi.adapter(MediaItem::class.java)
    val typeListMediaItem = Types.newParameterizedType(List::class.java, MediaItem::class.java)
    val jsonListMediaItemAdapter = MoshiInst.moshi.adapter<List<MediaItem>>(typeListMediaItem)
    val typeMap =
        Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
    val jsonMapAdapter: JsonAdapter<Map<String, String>> =
        MoshiInst.moshi.adapter(typeMap)
    val type: Type = Types.newParameterizedType(
        List::class.java,
        MediaItem::class.java
    )


}