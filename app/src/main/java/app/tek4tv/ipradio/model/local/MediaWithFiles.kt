package app.tek4tv.ipradio.model.local

import androidx.room.Embedded
import androidx.room.Relation

data class MediaWithFiles(
    @Embedded val media: MediaInfoEntity,
    @Relation(
        parentColumn = "mid",
        entityColumn = "mediaOwnerId"
    )
    val files: List<MediaFileEntity>
)