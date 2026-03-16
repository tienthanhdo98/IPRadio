package app.tek4tv.ipradio.model.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_files", foreignKeys = [ForeignKey(
        entity = MediaInfoEntity::class,
        parentColumns = ["mid"],
        childColumns = ["mediaOwnerId"],
        onDelete = ForeignKey.CASCADE // Xóa MediaInfo thì xóa luôn Files
    )], indices = [Index("mediaOwnerId")]
)
data class MediaFileEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val id: Long? = null, // ID từ server
    val mediaOwnerId: Long, // Khóa ngoại liên kết với MediaEntity.mid
    val index: Int? = null,
    val type: Int? = null,
    val size: Long? = null,
    val cs: Long? = null,
    val duration: Int? = null,
    val url: String? = null
)