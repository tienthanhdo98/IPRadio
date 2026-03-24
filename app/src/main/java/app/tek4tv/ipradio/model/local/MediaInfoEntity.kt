package app.tek4tv.ipradio.model.local

import androidx.room.Entity
import androidx.room.PrimaryKey

open class BaseMediaInfo(
    open val mid: Long,
    open val version: Int?,
    open val prio: Int?,
    open val summary: String?,
    open val mode: Int?,
    open val repeat: Int?,
    open val days: Int?,
    open val ts: List<Long>?,
    open val ds: List<Long>?,
    open val created: Long?,
    open val start: Long?,
    open val expired: Long?,
    open val action: Int?
)

// Lưu lịch mediainfo
@Entity(tableName = "media_info")
data class MediaInfoEntity(
    @PrimaryKey override val mid: Long,
    override val version: Int? = null,
    override val prio: Int? = null,
    override val summary: String? = null,
    override val mode: Int? = null,
    override val repeat: Int? = null,
    override val days: Int? = null,
    override val ts: List<Long>? = null,
    override val ds: List<Long>? = null,
    override val created: Long? = null,
    override val start: Long? = null,
    override val expired: Long? = null,
    override val action: Int? = null
) : BaseMediaInfo(
    mid,
    version,
    prio,
    summary,
    mode,
    repeat,
    days,
    ts,
    ds,
    created,
    start,
    expired,
    action
)

//Lưu lịch sử mediainfo live đã phát
@Entity(tableName = "live_media_info")
data class MediaInfoLiveHistoryEntity(
    @PrimaryKey override val mid: Long, // mid vẫn là khóa chính để so sánh
    override val version: Int? = null,
    override val prio: Int? = null,
    override val summary: String? = null,
    override val mode: Int? = null,
    override val repeat: Int? = null,
    override val days: Int? = null,
    override val ts: List<Long>? = null,
    override val ds: List<Long>? = null,
    override val created: Long? = null,
    override val start: Long? = null,
    override val expired: Long? = null,
    override val action: Int? = null
) : BaseMediaInfo(
    mid,
    version,
    prio,
    summary,
    mode,
    repeat,
    days,
    ts,
    ds,
    created,
    start,
    expired,
    action
)