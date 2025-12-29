package app.tek4tv.ipradio.di

import app.tek4tv.ipradio.model.BaseMqttMessage
import app.tek4tv.ipradio.model.CancelSchedulePayload
import app.tek4tv.ipradio.model.ControllerPayload
import app.tek4tv.ipradio.model.LivePayload
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.SchedulePayload
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Types
import java.lang.reflect.ParameterizedType

object JsonAdapterProvider {
    private val moshi = MoshiInst.moshi

    val baseMqttMessageAdapter: JsonAdapter<BaseMqttMessage> =
        moshi.adapter(BaseMqttMessage::class.java)

    val schedulePayloadAdapter: JsonAdapter<SchedulePayload> =
        moshi.adapter(SchedulePayload::class.java)
    val livePayloadAdapter: JsonAdapter<LivePayload> =
        moshi.adapter(LivePayload::class.java)
    val cancelPayloadAdapter: JsonAdapter<CancelSchedulePayload> =
        moshi.adapter(CancelSchedulePayload::class.java)
    val controllerPayloadAdapter: JsonAdapter<ControllerPayload> =
        moshi.adapter(ControllerPayload::class.java)


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