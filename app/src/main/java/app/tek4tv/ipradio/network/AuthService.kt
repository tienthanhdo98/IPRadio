package app.tek4tv.ipradio.network

import app.tek4tv.ipradio.model.TokenDto
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthService {
    @POST("https://api.gateway.httt.tek4tv.vn/api/Auth/Login")
    fun getToken(@Body body: Map<String, String>) : Call<TokenDto>
}