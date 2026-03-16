package app.tek4tv.ipradio.di

import app.tek4tv.ipradio.model.AppData
import app.tek4tv.ipradio.model.BaseMqttMessage
import app.tek4tv.ipradio.model.BootstrapResp
import app.tek4tv.ipradio.model.McuLogData
import app.tek4tv.ipradio.model.MediaFile
import app.tek4tv.ipradio.model.MediaInfo
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.SenMLItem
import app.tek4tv.ipradio.model.ShowInfo
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.lang.reflect.ParameterizedType

object JsonAdapterProvider {
    private val moshi = MoshiInst.moshi

    val baseMqttMessageAdapter: JsonAdapter<BaseMqttMessage> =
        moshi.adapter(BaseMqttMessage::class.java)


    val mediaItemAdapter: JsonAdapter<MediaItem> = moshi.adapter(MediaItem::class.java)

    val typeMediaItem: ParameterizedType = Types.newParameterizedType(
        MutableList::class.java,
        MediaItem::class.java
    )

    val mediaItemListAdapter: JsonAdapter<List<MediaItem>> = moshi.adapter(typeMediaItem)

    val jsonMediaItemAdapter: JsonAdapter<List<MediaItem>> = moshi.adapter(typeMediaItem)

    val mediaItemMoshiAdapter: JsonAdapter<MediaItem> =
        MoshiInst.moshi.adapter(MediaItem::class.java)


    //MGW
    val bootstrapAdapter: JsonAdapter<BootstrapResp> = moshi.adapter(BootstrapResp::class.java)

    //    val appDataAdapter: JsonAdapter<AppData> = moshi.adapter(AppData::class.java)
    val listAppDataItem: ParameterizedType = Types.newParameterizedType(
        List::class.java,
        AppData::class.java
    )
    val listAppDataItemAdapter: JsonAdapter<List<AppData>> = moshi.adapter(listAppDataItem)

    val listMediaFileDataItem: ParameterizedType = Types.newParameterizedType(
        List::class.java,
        MediaFile::class.java
    )
    val listMediaFileItemAdapter: JsonAdapter<List<MediaFile>> =
        moshi.adapter(listMediaFileDataItem)

    val mapSchedulerType = Types.newParameterizedType(
        Map::class.java,
        String::class.java,
        listMediaFileDataItem
    )
    val adapterSchedulerList = moshi.adapter<Map<String, List<MediaFile>>>(mapSchedulerType)

    val listShowInfoType = Types.newParameterizedType(List::class.java, ShowInfo::class.java)
    val showInfoAdapter = moshi.adapter<List<ShowInfo>>(listShowInfoType)

    val logsType = Types.newParameterizedType(List::class.java, McuLogData::class.java)
    val logsAdapter = moshi.adapter<List<McuLogData>>(logsType)
    val senmlType = Types.newParameterizedType(List::class.java, SenMLItem::class.java)
    val senmlAdapter = moshi.adapter<List<SenMLItem>>(senmlType)

    val mediaInfoAdapter: JsonAdapter<MediaInfo> = moshi.adapter(MediaInfo::class.java)

    inline fun <reified T> Moshi.fromVs(vs: String): T? =
        this.adapter(T::class.java).fromJson(vs)

    inline fun <reified T> Moshi.toVs(data: T): String? =
        this.adapter(T::class.java).toJson(data)
}