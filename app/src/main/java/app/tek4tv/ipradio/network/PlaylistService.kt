package app.tek4tv.ipradio.network

import app.tek4tv.ipradio.model.MediaItem
import retrofit2.Response
import retrofit2.http.*


interface PlaylistService {
    /*@Headers("Content-Type: application/json")
    @POST("api/device/imei")
    suspend fun getPlaylist(@Body body: Map<String, String>): Response<ResponseList>*/

    @GET
    suspend fun getPlaylist(@Url listUrl: String): Response<List<MediaItem>>

    @GET
    suspend fun getAudioList(@Url url: String): Response<List<String>>

    @Headers("Content-Type: application/json")
    @POST("api/device/image/save")
    suspend fun postScreenshot(@Body body: Map<String, String>): Response<Boolean>
}