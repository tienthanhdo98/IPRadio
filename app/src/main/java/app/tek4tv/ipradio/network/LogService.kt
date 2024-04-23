package app.tek4tv.ipradio.network

import app.tek4tv.ipradio.model.*
import retrofit2.http.Body
import retrofit2.http.POST

interface LogService {

//    @POST("https://datawarehouse-truyenthanh.tek4tv.vn/IpRadioDataLog")
//    suspend fun postMediaInfoStart(@Body record: MediaRecord)//doi media id//

    //dung cho thai nguyen, binh dinh
    @POST("https://hub-data.tek4tv.vn/IpRadioDataLog")
    suspend fun postMediaInfoStart(@Body record: MediaRecord)//doi media id

    @POST("https://datawarehouse-truyenthanh.tek4tv.vn/IpRadioDataLogConfirm")
    suspend fun postMediaInfoEnd(@Body record: MediaRecord)//doi media id





}