package app.tek4tv.ipradio.network

import app.tek4tv.ipradio.model.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface LogService {

    @POST("https://provider-data-warehouse-alpha.tek4tv.vn/IpRadioDataLog")
    suspend fun postMediaInfoStart(@Body record: MediaRecord): Response<Any>//doi media id


}