package app.tek4tv.ipradio.network

import app.tek4tv.ipradio.model.*
import app.tek4tv.ipradio.model.logmodel.ExceptionLogModel
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

interface LogService {
//    @POST("https://datawarehouse.httt.tek4tv.vn/MediaDevice")
//     suspend fun postMediaInfo(@Body record: MediaRecord)//doi media id
//    @POST("https://hub-data.tek4tv.vn/DigitalDataLog")
//    suspend fun postMediaInfoStart(@Body record: MediaRecord)//doi media id

    // @POST("https://datawarehouse.httt.tek4tv.vn/MediaDevice")

    //Lai chau
//    @POST("https://hub-data.tek4tv.vn/DigitalDataLog")
////    @POST("https://v2-provider-data-warehouse.tek4tv.vn/DigitalDataLog")
//    suspend fun postMediaInfoStart(@Body record: MediaRecord)//doi media id
//
//    @POST("https://v2-provider-data-warehouse.tek4tv.vn/DigitalDataLog/digital/log")
//    suspend fun postDeviceLog(@Body dataLogModel: DataLogModel): Response<Any>

//    @POST("https://datawarehouse-truyenthanh.tek4tv.vn/DigitalDataLogConfirm")
//    suspend fun postMediaInfoEnd(@Body record: MediaRecord)//doi media id
//
//    @POST("https://datawarehouse-truyenthanh.tek4tv.vn/NetworkDevice")
//    suspend fun postTimeframeInfo(@Body record: TimeFrameRecord)
//
//    @POST("https://datawarehouse-truyenthanh.tek4tv.vn/NetworkDevice")
//    suspend fun postHubStateInfo(@Body record: HubStateRecord)//them sn,si

//    @POST("https://datawarehouse-truyenthanh.tek4tv.vn/LogDevice")
//    suspend fun postLogFile(@Body record: GetLogFile)//them sn,si


//    @POST("https://caching-truyenthanh.tek4tv.vn/api/Report/add")
//    suspend fun postReport(@Body report: Report)

    //dung cho thai nguyen, can tho,hanam
    @POST("https://hub-data.tek4tv.vn/DigitalDataLog")
    suspend fun postMediaInfoStart(@Body record: MediaRecord)//doi media id

    //binhd dinh, bac giang,binh duong,long an,dong nai
//    @POST("https://provider-data-warehouse.tek4tv.vn/DigitalDataLog")
//    suspend fun postMediaInfoStart(@Body record: MediaRecord)//doi media id

    //quang ngai api
//    @POST("https://v2-provider-data-warehouse.tek4tv.vn/DigitalDataLog")
//    suspend fun postMediaInfoStart(@Body record: MediaRecord)//doi media id

    //
    @POST("https://v2-provider-data-warehouse.tek4tv.vn/IpRadioDataLog/device/log")
    suspend fun postDeviceLog(@Body dataLogModel: DataLogModel): Response<Any>

    @POST("https://gateway-digitalsignage.tek4tv.vn/api/v2/log/device/add")
    suspend fun postExceptionLog(@Body body: ExceptionLogModel)


    @POST
    suspend fun postScreenCapture(
        @Body captureScreenModel: CaptureScreenModel,
        @Url url: String
    ): Response<Any>

    //long an
//    @POST("https://provider-data-warehouse.tek4tv.vn/DigitalDataLog")
//    suspend fun postMediaInfoStart(@Body record: MediaRecord)//doi media id
//
//    @POST("https://provider-data-warehouse.tek4tv.vn/DigitalDataLog/digital/log")
//    suspend fun postDeviceLog(@Body dataLogModel: DataLogModel): Response<Any>
}
