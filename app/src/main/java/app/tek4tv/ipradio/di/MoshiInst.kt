package app.tek4tv.ipradio.di

import androidx.room.TypeConverter
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.lang.reflect.ParameterizedType

object MoshiInst {
    val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()


//    val typeListMediaItem: ParameterizedType =
//        Types.newParameterizedType(List::class.java, MediaItem::class.java)
//    val jsonListMediaItemAdapter: JsonAdapter<List<MediaItem>> = moshi.adapter(typeListMediaItem)

    val typeMap: ParameterizedType =
        Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
    val jsonMapAdapter: JsonAdapter<Map<String, String>> =
        moshi.adapter(typeMap)

    //

    @TypeConverter
    fun fromMetadataList(metadata: List<Any?>?): String? {
        if (metadata == null) return null
        val type = Types.newParameterizedType(List::class.java, Any::class.java)
        val adapter: JsonAdapter<List<Any?>> = moshi.adapter(type)
        return adapter.toJson(metadata)
    }

    @TypeConverter
    fun toMetadataList(metadataJson: String?): List<Any?>? {
        if (metadataJson.isNullOrEmpty()) return null
        val type = Types.newParameterizedType(List::class.java, Any::class.java)
        val adapter: JsonAdapter<List<Any?>> = moshi.adapter(type)
        return adapter.fromJson(metadataJson)
    }
}