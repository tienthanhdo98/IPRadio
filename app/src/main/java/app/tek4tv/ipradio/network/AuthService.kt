package app.tek4tv.ipradio.network

import app.tek4tv.ipradio.model.*
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface AuthService {
    @POST
    //@GET("https://hub.gateway.httt.tek4tv.vn/api/Device/Token")
    fun getToken(@Body body: Map<String, String>, @Url url: String) : Call<TokenDto>

    @POST("https://v2-provider-data-warehouse.tek4tv.vn/DigitalDataLog/digital/log")
    suspend fun postDeviceLog(@Body dataLogModel: DataLogModel) : Response<Any>

    @POST
    fun getMQTTInfo(@Body mqttModelInfo : MQTTInfoModel, @Header("Authorization") token : String, @Url url: String) : Call<MQTTInfoValueModel>

    @POST
    suspend fun postScreenCapture(@Body captureScreenModel : CaptureScreenModel, @Url url: String) : Response<Any>


}