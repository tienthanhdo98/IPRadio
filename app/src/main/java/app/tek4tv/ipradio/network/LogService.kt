package app.tek4tv.ipradio.network

import app.tek4tv.ipradio.model.*
import retrofit2.http.Body
import retrofit2.http.POST

interface LogService {
    @POST("https://caching.httt.tek4tv.vn/api/Media/add")
    suspend fun postMediaInfo(@Body record: MediaRecord)

    @POST("https://caching.httt.tek4tv.vn/api/TimeFrame/add")
    suspend fun postTimeframeInfo(@Body record: TimeFrameRecord)

    @POST("https://caching.httt.tek4tv.vn/api/Connection/add")
    suspend fun postHubStateInfo(@Body record: HubStateRecord)

    @POST("https://caching.httt.tek4tv.vn/api/Device/Synchronize")
    suspend fun postLogFile(@Body record: GetLogFile)
    @POST("https://caching.httt.tek4tv.vn/api/Report/add")
    suspend fun postReport(@Body report: Report)
}