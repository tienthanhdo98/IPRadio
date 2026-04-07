package app.tek4tv.ipradio.model.local.log

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mgw_logs")
data class MgwLogEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,
    val logId: Long,
    val time: Long,
    val type: Int,
    val detail: String,
    val sent: Boolean = false,
    val sending: Boolean = false
)
