package app.tek4tv.ipradio.network

import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.api.ScheduleDay
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

interface PlaylistService {
    @GET
    suspend fun getPlaylist(@Url listUrl: String): Response<List<MediaItem>>

    @GET
    suspend fun getMonthlyPlaylist(@Url sourceUrl: String): Response<List<ScheduleDay>>
}