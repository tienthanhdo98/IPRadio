package app.tek4tv.ipradio.model.local

import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

class RoomConverters {
    private val moshi = Moshi.Builder().build()
    private val listLongAdapter = moshi.adapter<List<Long>>(
        Types.newParameterizedType(List::class.java, Long::class.javaObjectType)
    )

    @TypeConverter
    fun fromListLong(value: List<Long>?): String? = listLongAdapter.toJson(value)

    @TypeConverter
    fun toListLong(value: String?): List<Long>? = value?.let { listLongAdapter.fromJson(it) }
}