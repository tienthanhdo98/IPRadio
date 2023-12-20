package app.tek4tv.ipradio.repo

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.network.PlaylistService
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.GlobalProperties.Companion.TYPE_SCREEN_KEY
import app.tek4tv.ipradio.utils.PLAYLIST_FILE_NAME
import app.tek4tv.ipradio.utils.Utils.Companion.FULL_SCREEN
import app.tek4tv.ipradio.utils.Utils.Companion.LEFT_AREA
import app.tek4tv.ipradio.utils.Utils.Companion.RIGHT_AREA
import app.tek4tv.ipradio.utils.Utils.Companion.TOP_AREA
import app.tek4tv.ipradio.utils.getFileNameFromUrl
import app.tek4tv.ipradio.utils.isFileExisted
import app.tek4tv.ipradio.utils.media.DownloadHelper2
import app.tek4tv.ipradio.utils.media.DownloadItem
import com.downloader.Error
import com.downloader.OnDownloadListener
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.LinkedHashMap

@Singleton
class MediaRepo @Inject constructor(
    private val playlistService: PlaylistService,
    moshi: Moshi,
    @ApplicationContext private val appContext: Context
) : BaseRepo<MediaItem>(fileStoragePath = "${appContext.filesDir.path}/$PLAYLIST_FILE_NAME") {

    override val jsonAdapter: JsonAdapter<List<MediaItem>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, MediaItem::class.java)
    )
    var broadcastList = mutableListOf<MediaItem>()
    //right scheduledList
    var scheduledList = LinkedHashMap<String, List<MediaItem>>()
    //left scheduledList
    var leftScheduledList = listOf<MediaItem>()
    //top scheduledList
    var topScheduledList =  listOf<MediaItem>()
    var timeDividers = LinkedHashMap<String, List<MediaItem>>()
    var unscheduledList = mutableListOf<MediaItem>()
    var scheduledMediaItems = mutableListOf<MediaItem>()

    private val downloadHelper = DownloadHelper2.getInstance()

    private val mediaStoragePath = appContext.filesDir.path

    suspend fun updateMonthlyScenario(url: String, forceNewScenario: Boolean) {
        try {
            val fetchResult = playlistService.getMonthlyPlaylist(url)

            if (!fetchResult.isSuccessful) return
            val monthScenario = fetchResult.body() ?: return

            //if there are only a single scenario for every day
            val isSingleScenario = monthScenario.size == 1 && monthScenario[0].date == ""

            //in case of single scenario, continue to use old scenario
            if (isSingleScenario && !forceNewScenario) {
                Log.d("updateDailyScenario", "return update")
                return
            }

            val currentDate = SimpleDateFormat("dd-MM-yyyy").format(Date())

            val dayScenario = if (isSingleScenario) monthScenario[0]
            else monthScenario.firstOrNull { it.date == currentDate }

            if (dayScenario != null) {
                ConfigUtil.putLong(appContext, TYPE_SCREEN_KEY, dayScenario.broadcastRegionDigital.toLong())
                updateScenario(dayScenario.sourceUrl)
            } else {
                //In case there are no scenario for today, don't play anythings
                saveItemsToFile(listOf())
            }

        } catch (e: Exception) {
            Log.e("updateDailyScenario", "Cannot update daily scenario")
        }
    }

    suspend fun getScenario(needUpdate: Boolean, listUrl: String): List<MediaItem> {
        scheduledList.clear()
        unscheduledList.clear()
        scheduledMediaItems.clear()

        val res = if (needUpdate) {
            updateScenario(listUrl)
        } else {
            val result = if (isFileExisted(mediaStoragePath, PLAYLIST_FILE_NAME)) {
                Log.d("readplaylist", "local ")
                readItemsFromFile()
            } else listOf()
            result

        }

        broadcastList.clear()
        broadcastList.addAll(res)

        filterMedia(broadcastList)
        return broadcastList
    }

    private fun filterMedia(mediaList: List<MediaItem>) {
        val dividerIndices = mediaList.indices.filter {
            mediaList[it].body == "start" || mediaList[it].body == "end"
        }.chunked(2)

        val scheduledIndex = mutableListOf<Int>()

        dividerIndices.forEach {
            if (it.size == 2) {
                val startIndex = it[0]
                val endIndex = it[1]

                val key = "${mediaList[startIndex].fixTime}-${mediaList[endIndex].fixTime}"
                scheduledList[key] = mediaList.subList(startIndex + 1, endIndex)

                timeDividers[key] = listOf(broadcastList[startIndex], broadcastList[endIndex])

                //adding the index of scheduled media
                scheduledIndex.addAll((startIndex..endIndex))
            }
        }

        mediaList.indices.filter { !scheduledIndex.contains(it) }.forEach {
            unscheduledList.add(mediaList[it])
        }

        scheduledMediaItems.addAll(mediaList.filter {
            it.body != "start" && it.body != "end" && it.fixTime!!.isNotEmpty() && it.fixTime != "00:00:00"
        })
    }



    private suspend fun updateScenario(listUrl: String): List<MediaItem> {
        var result = listOf<MediaItem>()

        try {
            val res = playlistService.getPlaylist(listUrl)
            if (res.isSuccessful) {
                val resBody = res.body()
                if (resBody != null) {

                    //result = resBody.onEach { it.mediaID = i++ }
                    result =  resBody.filter {it.broadcastRegion?.value == RIGHT_AREA || it.broadcastRegion?.value == FULL_SCREEN  }

                    leftScheduledList =  resBody.filter { it.broadcastRegion?.value == LEFT_AREA }
                    topScheduledList  =  resBody.filter { it.broadcastRegion?.value == TOP_AREA }


                    Log.e("yee", "Lịch rỗng rồi ${result.size}")
                    Log.e("yee", "Lịch rỗng rồi ${leftScheduledList.size}")
                    Log.e("yee", "Lịch rỗng rồi ${topScheduledList.size}")
                    saveItemsToFile(result)
                }
            } else {
                Log.e("yee", "Lịch rỗng rồi")
            }
        } catch (e: Exception) {
            Log.e("yee", e.toString())
        }

        return result
    }

    fun startDownloadMedia() {
        if (broadcastList.isNullOrEmpty()) return

        broadcastList.forEach {
            downloadAMediaItem(it)
        }
    }

    fun downloadAMediaItem(it: MediaItem) {
        Log.i("download", "downloadAMediaItem: ${it.getMediaType()}")
        if (it.getMediaType() != MediaType.FILE) return

        val url = it.body
        //if (url.isEmpty() || !url.startsWith("http")) return
        Log.i("download", "downloadAMediaItem: ${url}")
        val fileName = getFileNameFromUrl(url!!)
        Log.i("download", "downloadAMediaItem: ${fileName}")
        if (isFileExisted(mediaStoragePath, fileName)) {
            //handle broken path
            if (it.body != "$mediaStoragePath/$fileName") {
                it.body = "$mediaStoragePath/$fileName"
                coroutineScope.launch {
                    saveItemsToFile(broadcastList)
                }
            }

            return
        }

        Log.d("download item", url)

        downloadHelper.addToQueue(DownloadItem().apply {
            itDownloadUrl = url
            itStoragePath = mediaStoragePath
            itFileName = fileName
            priority = 0
            downloadListener = object : OnDownloadListener {
                override fun onDownloadComplete() {
                    it.pathBackup = it.body!!
                    it.body = "$mediaStoragePath/$fileName"

                    coroutineScope.launch {
                        saveItemsToFile(broadcastList)
                    }

                    Log.d("downloadcomplete", it.body!!)
                }

                override fun onError(error: Error?) {
                    Log.d("downloaderror", it.body!!)
                }
            }
        })
    }
}