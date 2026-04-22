package app.tek4tv.ipradio.network

import app.tek4tv.ipradio.model.BootstrapResp
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

interface ApiService {

    @GET("things/bootstrap/{externalId}")
    fun getBootstrapInfo(
        @Path("externalId") externalId: String,
        @Header("Authorization") externalKey: String
    ): Call<BootstrapResp>


}