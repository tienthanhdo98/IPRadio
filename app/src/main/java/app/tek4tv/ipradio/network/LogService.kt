package app.tek4tv.ipradio.network

import app.tek4tv.ipradio.model.MediaRecord
import retrofit2.http.Body
import retrofit2.http.POST

interface LogService {

    @POST("https://datawarehouse-truyenthanh.tek4tv.vn/IpRadioDataLog")
    suspend fun postMediaInfoStart(@Body record: MediaRecord)//doi media id

    @POST("https://datawarehouse-truyenthanh.tek4tv.vn/IpRadioDataLogConfirm")
    suspend fun postMediaInfoEnd(@Body record: MediaRecord)//doi media id


}
