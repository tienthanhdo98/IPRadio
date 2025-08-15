package app.tek4tv.ipradio.network

import app.tek4tv.ipradio.model.LogToken
import app.tek4tv.ipradio.model.TokenDto
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

interface AuthService {
    @POST
    //@GET("https://hub.gateway.httt.tek4tv.vn/api/Device/Token")
    fun getToken(@Body body: Map<String, String>, @Url url: String): Call<TokenDto>

    @POST
    fun getTokenLog(@Body body: Map<String, String>, @Url url: String): Call<LogToken>
}