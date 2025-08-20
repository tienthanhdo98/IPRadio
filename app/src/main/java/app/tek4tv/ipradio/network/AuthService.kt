package app.tek4tv.ipradio.network

import app.tek4tv.ipradio.model.*
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.*

interface AuthService {
    @POST
    //@GET("https://hub.gateway.httt.tek4tv.vn/api/Device/Token")
    fun getToken(@Body body: Map<String, String>, @Url url: String): Call<TokenDto>

    @POST("https://v2-provider-data-warehouse.tek4tv.vn/DigitalDataLog/digital/log")
    suspend fun postDeviceLog(@Body dataLogModel: DataLogModel): Response<Any>

    @POST
    fun getMQTTInfo(
        @Body mqttModelInfo: MQTTInfoModel,
        @Header("Authorization") token: String,
        @Url url: String
    ): Call<MQTTInfoValueModel>

    @GET
    fun getSourceMQTT(@Url url: String): Call<SourceMQTT>

    //
    @POST
    suspend fun getDigitalToken(
        @Url url: String,
        @Body body: Map<String, String>
    ): Response<DigitalToken>

    @POST
    suspend fun postScreenCapture(
        @Url url: String,
        @Header("Authorization") token: String,
        @Body captureScreenModel: CaptureScreenModel,
    ): Response<Any>

    @POST("https://gateway-digitalsignage.tek4tv.vn/api/v2/log/device/add")
    suspend fun postExceptionLog(
        @Header("Authorization") token: String,
        @Body body: ExceptionLogModel
    ): Response<Any>
}