package app.tek4tv.ipradio.model.local

import androidx.room.TypeConverter
import app.tek4tv.ipradio.di.MoshiInst
import app.tek4tv.ipradio.model.*

object Converters {

    private val contentTypeAdapter = MoshiInst.moshi.adapter(ContentType::class.java)
    private val priorityTypeAdapter = MoshiInst.moshi.adapter(Priority::class.java)
    private val categoryTypeAdapter = MoshiInst.moshi.adapter(Category::class.java)
    private val broadcastRegionTypeAdapter = MoshiInst.moshi.adapter(BroadcastRegion::class.java)
    private val AuthorRegionTypeAdapter = MoshiInst.moshi.adapter(Author::class.java)

    @TypeConverter
    fun fromContentType(contentType: ContentType?): String? {
        return contentType?.let { contentTypeAdapter.toJson(it) }
    }

    @TypeConverter
    fun toContentType(json: String?): ContentType? {
        return json?.let { contentTypeAdapter.fromJson(it) }
    }

    @TypeConverter
    fun fromPriority(priority: Priority?): String? {
        return priority?.let { priorityTypeAdapter.toJson(it) }
    }

    @TypeConverter
    fun toPriority(json: String?): Priority? {
        return json?.let { priorityTypeAdapter.fromJson(it) }
    }

    @TypeConverter
    fun fromCategory(category: Category?): String? {
        return category?.let { categoryTypeAdapter.toJson(it) }
    }

    @TypeConverter
    fun toCategory(json: String?): Category? {
        return json?.let { categoryTypeAdapter.fromJson(it) }
    }

    @TypeConverter
    fun fromBroadcastRegion(broadcastRegion: BroadcastRegion?): String? {
        return broadcastRegion?.let { broadcastRegionTypeAdapter.toJson(it) }
    }

    @TypeConverter
    fun toBroadcastRegion(json: String?): BroadcastRegion? {
        return json?.let { broadcastRegionTypeAdapter.fromJson(it) }
    }

    @TypeConverter
    fun fromAuthor(author: Author?): String? {
        return author?.let { AuthorRegionTypeAdapter.toJson(it) }
    }

    @TypeConverter
    fun toAuthor(json: String?): Author? {
        return json?.let { AuthorRegionTypeAdapter.fromJson(it) }
    }

}