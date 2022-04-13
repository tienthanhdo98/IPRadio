package app.tek4tv.ipradio.network

import app.tek4tv.ipradio.model.GetLogFile
import app.tek4tv.ipradio.model.HubStateRecord
import app.tek4tv.ipradio.model.MediaRecord
import app.tek4tv.ipradio.model.TimeFrameRecord
import retrofit2.http.Body
import retrofit2.http.POST

interface LogService {
//    @POST("https://datawarehouse.httt.tek4tv.vn/MediaDevice")
//    suspend fun postMediaInfo(@Body record: MediaRecord)//doi media id
    @POST("https://datawarehouse-truyenthanh.tek4tv.vn/IpRadioDataLog")
    suspend fun postMediaInfoStart(@Body record: MediaRecord)//doi media id

    @POST("https://datawarehouse-truyenthanh.tek4tv.vn/IpRadioDataLogConfirm")
    suspend fun postMediaInfoEnd(@Body record: MediaRecord)//doi media id

    @POST("https://datawarehouse-truyenthanh.tek4tv.vn/NetworkDevice")
    suspend fun postTimeframeInfo(@Body record: TimeFrameRecord)

    @POST("https://datawarehouse-truyenthanh.tek4tv.vn/NetworkDevice")
    suspend fun postHubStateInfo(@Body record: HubStateRecord)//them sn,si

    @POST("https://datawarehouse-truyenthanh.tek4tv.vn/LogDevice")
    suspend fun postLogFile(@Body record: GetLogFile)//them sn,si

//    @POST("https://caching-truyenthanh.tek4tv.vn/api/Report/add")
//    suspend fun postReport(@Body report: Report)
}
