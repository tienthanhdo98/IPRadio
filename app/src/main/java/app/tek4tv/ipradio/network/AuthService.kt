package app.tek4tv.ipradio.network

import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.Secrets
import app.tek4tv.ipradio.model.TokenDto
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

interface AuthService {
    //@POST("https://api.gateway.httt.tek4tv.vn/api/Auth/Login")
    @POST
    fun getToken(@Body body: Map<String, String>, @Url url: String) : Call<TokenDto>
}