package app.tek4tv.ipradio.network

import app.tek4tv.ipradio.model.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface LogService {
    //hai phong va cac ban cu hon
//    @POST("https://datawarehouse-truyenthanh.tek4tv.vn/IpRadioDataLog")
//    suspend fun postMediaInfoStart(@Body record: MediaRecord)//doi media id//

  // @POST("https://hub-data.tek4tv.vn/IpRadioDataLog/device/log")
    @POST("https://provider-data-warehouse.tek4tv.vn/IpRadioDataLog/device/log")
    suspend fun postDeviceLog(@Body dataLogModel: DataLogModel) : Response<Any>

    //dung cho thai nguyen, can tho,hanam
    //     @POST("https://hub-data.tek4tv.vn/IpRadioDataLog")
//    suspend fun postMediaInfoStart(@Body record: MediaRecord)//doi media id

    // binhd dinh, bac giang,binh duong
    @POST("https://provider-data-warehouse.tek4tv.vn/IpRadioDataLog")
    suspend fun postMediaInfoStart(@Body record: MediaRecord)//doi media id

//    @POST("https://datawarehouse-truyenthanh.tek4tv.vn/IpRadioDataLogConfirm")
//    suspend fun postMediaInfoEnd(@Body record: MediaRecord)//doi media id

}