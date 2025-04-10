package app.tek4tv.ipradio.di

import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.WssLiveModel
import app.tek4tv.ipradio.model.WssStatusModel
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Types
import java.lang.reflect.ParameterizedType

object JsonAdapterProvider {
    private val moshi = MoshiInst.moshi

    val wssStatusAdapter: JsonAdapter<WssStatusModel> = moshi.adapter(WssStatusModel::class.java)
    val wssLiveAdapter: JsonAdapter<WssLiveModel> = moshi.adapter(WssLiveModel::class.java)
    val mediaItemAdapter: JsonAdapter<MediaItem> = moshi.adapter(MediaItem::class.java)

    val typeMediaItem: ParameterizedType = Types.newParameterizedType(
        MutableList::class.java,
        MediaItem::class.java
    )

    val mediaItemListAdapter: JsonAdapter<List<MediaItem>> = moshi.adapter(typeMediaItem)

    val jsonMediaItemAdapter: JsonAdapter<List<MediaItem>> = moshi.adapter(typeMediaItem)

    val mediaItemMoshiAdapter: JsonAdapter<MediaItem> =
        MoshiInst.moshi.adapter(MediaItem::class.java)


}