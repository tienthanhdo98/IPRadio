package app.tek4tv.ipradio.repo

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.network.PlaylistService
import app.tek4tv.ipradio.utils.*
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
    var scheduledList = LinkedHashMap<String, List<MediaItem>>()
    var timeDividers = LinkedHashMap<String, List<MediaItem>>()
    var unscheduledList = mutableListOf<MediaItem>()
    var scheduledMediaItems = mutableListOf<MediaItem>()

    private val downloadHelper = DownloadHelper.getInstance(coroutineScope)

    private val mediaStoragePath = appContext.filesDir.path

    suspend fun updateScenario(url: String) {
        val fetchResult = playlistService.getMonthlyPlaylist(url)

        if (fetchResult.isSuccessful) {
            val monthScenario = fetchResult.body() ?: return

            val singleScenario = monthScenario.size == 1 && monthScenario[0].date == ""

            if (singleScenario && isFileExisted(appContext.filesDir.path, PLAYLIST_FILE_NAME)
            ) {
                Log.d("aaa","return update")
                return
            }

            val currentDate = SimpleDateFormat("dd-MM-yyyy").format(Date())

            val dayScenario = if(singleScenario) monthScenario[0]
            else monthScenario.firstOrNull { it.date == currentDate }

            if (dayScenario != null) {
                //getBroadcastList(true, dayScenarioUrl.sourceUrl)
                updatePlaylist(dayScenario.sourceUrl)
            }
        }
    }

    suspend fun getBroadcastList(needUpdate: Boolean, listUrl: String): List<MediaItem> {
        scheduledList.clear()
        unscheduledList.clear()
        scheduledMediaItems.clear()

        val res = if (needUpdate) {
            updatePlaylist(listUrl)
        } else {
            val result = if (isFileExisted(mediaStoragePath, PLAYLIST_FILE_NAME)) {
                Log.d("readplaylist", "local")
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
            mediaList[it].path == "start" || mediaList[it].path == "end"
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
            it.path != "start" && it.path != "end" && it.fixTime.isNotEmpty() && it.fixTime != "00:00:00"
        })
    }

    private suspend fun updatePlaylist(listUrl: String): List<MediaItem> {
        var result = listOf<MediaItem>()

        try {
            val res = playlistService.getPlaylist(listUrl)
            if (res.isSuccessful) {
                val resBody = res.body()
                if (resBody != null) {
                    var i = 1
                    result = resBody.onEach { it.id = i++ }
                    saveItemsToFile(result)
                }
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
        if (it.getMediaType() != MediaType.VOD) return

        val url = it.path
        //if (url.isEmpty() || !url.startsWith("http")) return

        Log.d("downloadx", url)

        val fileName = getFileNameFromUrl(url)

        if (isFileExisted(mediaStoragePath, fileName)) {
            it.path = "$mediaStoragePath/$fileName"
            coroutineScope.launch {
                saveItemsToFile(broadcastList)
            }
            return
        }

        downloadHelper.addToQueue(DownloadItem().apply {
            itDownloadUrl = url
            itStoragePath = mediaStoragePath
            itFileName = fileName
            priority = 0
            downloadListener = object : OnDownloadListener {
                override fun onDownloadComplete() {
                    it.pathBackup = it.path
                    it.path = "$mediaStoragePath/$fileName"

                    coroutineScope.launch {
                        saveItemsToFile(broadcastList)
                    }

                    Log.d("downloadcomplete", it.path)
                }

                override fun onError(error: Error?) {
                    Log.d("downloaderror", it.path)
                }
            }
        })
    }
}