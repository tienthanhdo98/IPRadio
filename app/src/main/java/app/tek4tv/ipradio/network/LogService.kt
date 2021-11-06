package app.tek4tv.ipradio.network

import app.tek4tv.ipradio.model.GetLogFile
import app.tek4tv.ipradio.model.HubStateRecord
import app.tek4tv.ipradio.model.MediaRecord
import app.tek4tv.ipradio.model.TimeFrameRecord
import retrofit2.http.Body
import retrofit2.http.POST

interface LogService {
    @POST("https://caching.httt.vtcplay.vn/api/Media/add")
    suspend fun postMediaInfo(@Body record: MediaRecord)

    @POST("https://caching.httt.vtcplay.vn/api/TimeFrame/add")
    suspend fun postTimeframeInfo(@Body record: TimeFrameRecord)

    @POST("https://caching.httt.vtcplay.vn/api/Connection/add")
    suspend fun postHubStateInfo(@Body record: HubStateRecord)

    @POST("https://caching.httt.vtcplay.vn/api/Device/Synchronize")
    suspend fun postLogFile(@Body record: GetLogFile)
}