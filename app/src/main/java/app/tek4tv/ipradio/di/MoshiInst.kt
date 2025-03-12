package app.tek4tv.ipradio.di

import androidx.room.TypeConverter
import app.tek4tv.ipradio.model.*
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.lang.reflect.ParameterizedType

object MoshiInst {
    val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    val adapterTacGia: JsonAdapter<TacGia> = moshi.adapter(TacGia::class.java)


    val typeListMediaItem: ParameterizedType =
        Types.newParameterizedType(List::class.java, MediaItem::class.java)
    val jsonListMediaItemAdapter: JsonAdapter<List<MediaItem>> = moshi.adapter(typeListMediaItem)

    val typeMap: ParameterizedType =
        Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
    val jsonMapAdapter: JsonAdapter<Map<String, String>> =
        moshi.adapter(typeMap)

    //
    @TypeConverter
    fun fromContentType(value: ContentType?): String? {
        return value?.let { moshi.adapter(ContentType::class.java).toJson(it) }
    }

    @TypeConverter
    fun toContentType(value: String?): ContentType? {
        return value?.let { moshi.adapter(ContentType::class.java).fromJson(it) }
    }

    @TypeConverter
    fun fromCategory(value: Category?): String? {
        return value?.let { moshi.adapter(Category::class.java).toJson(it) }
    }

    @TypeConverter
    fun toCategory(value: String?): Category? {
        return value?.let { moshi.adapter(Category::class.java).fromJson(it) }
    }
    @TypeConverter
    fun fromPriority(value: Priority?): String? {
        return value?.let { moshi.adapter(Priority::class.java).toJson(it) }
    }

    @TypeConverter
    fun toPriority(value: String?): Priority? {
        return value?.let { moshi.adapter(Priority::class.java).fromJson(it) }
    }

    @TypeConverter
    fun fromAuthor(value: Author?): String? {
        return value?.let { moshi.adapter(Author::class.java).toJson(it) }
    }

    @TypeConverter
    fun toAuthor(value: String?): Author? {
        return value?.let { moshi.adapter(Author::class.java).fromJson(it) }
    }
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