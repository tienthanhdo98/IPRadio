//package app.tek4tv.ipradio.model.local.log
//
//import androidx.room.Dao
//import androidx.room.Insert
//import androidx.room.OnConflictStrategy
//import androidx.room.Query
//
//@Dao
//interface MgwLogDao {
//
//    @Insert(onConflict = OnConflictStrategy.REPLACE)
//    suspend fun insert(log: MgwLogEntity)
//
//    @Query(
//        """
//        SELECT * FROM mgw_logs
//        WHERE sent = 0 AND sending = 0
//        ORDER BY time ASC
//        LIMIT :limit
//    """
//    )
//    suspend fun getPendingLogs(limit: Int): List<MgwLogEntity>
//
//    @Query(
//        """
//        UPDATE mgw_logs
//        SET sending = 1
//        WHERE localId IN (:ids)
//    """
//    )
//    suspend fun markSending(ids: List<Long>)
//
//    @Query(
//        """
//        UPDATE mgw_logs
//        SET sent = 1, sending = 0
//        WHERE localId IN (:ids)
//    """
//    )
//    suspend fun markSent(ids: List<Long>)
//
//    @Query(
//        """
//        UPDATE mgw_logs
//        SET sending = 0
//        WHERE localId IN (:ids)
//    """
//    )
//    suspend fun unmarkSending(ids: List<Long>)
//
//    @Query("DELETE FROM mgw_logs WHERE sent = 1")
//    suspend fun deleteSentLogs()
//}