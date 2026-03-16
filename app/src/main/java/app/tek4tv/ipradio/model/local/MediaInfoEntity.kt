package app.tek4tv.ipradio.model.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_info")
data class MediaInfoEntity(
    @PrimaryKey val mid: Long,
    val version: Int? = null,
    val prio: Int? = null,
    val summary: String? = null,
    val mode: Int? = null,
    val repeat: Int? = null,
    val days: Int? = null,
    val ts: List<Long>? = null, // Cần TypeConverter
    val ds: List<Long>? = null, // Cần TypeConverter
    val created: Long? = null,
    val start: Long? = null,
    val expired: Long? = null
)