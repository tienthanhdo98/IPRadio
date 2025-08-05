package app.tek4tv.ipradio.di

import androidx.room.TypeConverter
import app.tek4tv.ipradio.model.ContentType
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object MoshiInst {
    val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val contentTypeAdapter = moshi.adapter(ContentType::class.java)

    @TypeConverter
    fun fromContentType(contentType: ContentType?): String? {
        return contentType?.let { contentTypeAdapter.toJson(it) }
    }

    @TypeConverter
    fun toContentType(json: String?): ContentType? {
        return json?.let { contentTypeAdapter.fromJson(it) }
    }
}