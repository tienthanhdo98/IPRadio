package app.tek4tv.ipradio.utils

import app.tek4tv.ipradio.model.MediaFile
import app.tek4tv.ipradio.model.MediaInfo
import app.tek4tv.ipradio.model.local.MediaFileEntity
import app.tek4tv.ipradio.model.local.MediaInfoEntity
import app.tek4tv.ipradio.model.local.MediaInfoLiveHistoryEntity
import app.tek4tv.ipradio.model.local.MediaWithFiles

fun MediaInfo.toEntity(): MediaInfoEntity {
    return MediaInfoEntity(
        mid = this.mid ?: 0L,
        version = this.version,
        prio = this.prio,
        summary = this.summary,
        mode = this.mode,
        repeat = this.repeat,
        days = this.days,
        ts = this.ts,
        ds = this.ds,
        created = this.created,
        start = this.start,
        expired = this.expired,
        action = this.action
    )
}

fun MediaInfo.toLiveEntity(): MediaInfoLiveHistoryEntity {
    return MediaInfoLiveHistoryEntity(
        mid = this.mid ?: 0L,
        version = this.version,
        prio = this.prio,
        summary = this.summary,
        mode = this.mode,
        repeat = this.repeat,
        days = this.days,
        ts = this.ts,
        ds = this.ds,
        created = this.created,
        start = this.start,
        expired = this.expired,
        action = this.action
    )
}

fun MediaFile.toEntity(ownerId: Long): MediaFileEntity {
    return MediaFileEntity(
        id = this.id,
        mediaOwnerId = ownerId,
        index = this.index,
        type = this.type,
        size = this.size,
        cs = this.cs,
        duration = this.duration,
        url = this.url
    )
}

fun MediaWithFiles.toModel(): MediaInfo {
    return MediaInfo(
        mid = this.media.mid,
        version = this.media.version,
        prio = this.media.prio,
        summary = this.media.summary,
        mode = this.media.mode,
        repeat = this.media.repeat,
        days = this.media.days,
        ts = this.media.ts,
        ds = this.media.ds,
        created = this.media.created,
        start = this.media.start,
        expired = this.media.expired,
        // Chuyển danh sách MediaFileEntity sang MediaFile
        files = this.files.map { fileEntity ->
            MediaFile(
                id = fileEntity.id,
                index = fileEntity.index,
                type = fileEntity.type,
                size = fileEntity.size,
                cs = fileEntity.cs,
                duration = fileEntity.duration,
                url = fileEntity.url
            )
        }
    )
}