import app.tek4tv.ipradio.di.MoshiInst
import app.tek4tv.ipradio.model.*
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Types
import java.lang.reflect.ParameterizedType

object JsonAdapterProvider {
    private val moshi = MoshiInst.moshi

     val wssStatusAdapter: JsonAdapter<WssStatusModel> =
        moshi.adapter(WssStatusModel::class.java)
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


}