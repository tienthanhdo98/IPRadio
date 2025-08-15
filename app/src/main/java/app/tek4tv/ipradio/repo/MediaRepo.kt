package app.tek4tv.ipradio.repo

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.core.constants.AppConstants.FULL_SCREEN
import app.tek4tv.ipradio.core.constants.AppConstants.LEFT_AREA
import app.tek4tv.ipradio.core.constants.AppConstants.RIGHT_AREA
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.Tag
import app.tek4tv.ipradio.network.PlaylistService
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.GlobalProperties.Companion.TYPE_SCREEN_KEY
import app.tek4tv.ipradio.utils.PLAYLIST_FILE_NAME
import app.tek4tv.ipradio.utils.getFileNameFromUrl
import app.tek4tv.ipradio.utils.isFileExisted
import app.tek4tv.ipradio.utils.media.DownloadHelper2
import app.tek4tv.ipradio.utils.media.DownloadItem
import app.tek4tv.ipradio.utils.network.retryWithBackoff
import com.downloader.Error
import com.downloader.OnDownloadListener
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepo @Inject constructor(
    private val playlistService: PlaylistService,
    moshi: Moshi,
    @ApplicationContext private val appContext: Context
) : BaseRepo<MediaItem>(fileStoragePath = "${appContext.filesDir.path}/$PLAYLIST_FILE_NAME") {
    val TAG = "MediaRepo"
    override val jsonAdapter: JsonAdapter<List<MediaItem>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, MediaItem::class.java)
    )
    var broadcastList = mutableListOf<MediaItem>()

    var scheduledList = LinkedHashMap<String, List<MediaItem>>()

    var scheduledListLeft = LinkedHashMap<String, List<MediaItem>>()

    var timeDividers = LinkedHashMap<String, List<MediaItem>>()

    private val downloadHelper = DownloadHelper2.getInstance()

    val mediaStoragePath: String = appContext.filesDir.path

    var isRescheduleSuccess = false


    suspend fun getMonthlyScenario(url: String) {
        isRescheduleSuccess = false
        try {
            val fetchResult =
                retryWithBackoff { playlistService.getMonthlyPlaylist(url) }
            if (fetchResult == null || !fetchResult.isSuccessful) {
                Log.e(TAG, "API request failed after retries")
                return
            }

            val monthScenario = fetchResult.body() ?: return


            val currentDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

            val dayScenario = monthScenario.firstOrNull { it.date == currentDate }

            if (dayScenario != null) {
                ConfigUtil.putInt(
                    appContext,
                    TYPE_SCREEN_KEY,
                    dayScenario.broadcastRegionDigital.toInt()
                )
                getDayScenario(dayScenario.sourceUrl)
            } else {
                //In case there are no scenario for today, don't play anythings
                saveItemsToFile(emptyList())
                isRescheduleSuccess = true
            }

        } catch (e: Exception) {
            Log.e(TAG, "Cannot update daily scenario ${e.message}")
        }
    }

    private suspend fun getDayScenario(urlDatSchedule: String): List<MediaItem> {
        val result = listOf<MediaItem>()
        try {
            val res = retryWithBackoff { playlistService.getPlaylist(urlDatSchedule) }
            if (res == null || !res.isSuccessful) {
                isRescheduleSuccess = false
                Log.e(TAG, "API request failed after retries")
                return emptyList()
            }
            val resBody = res.body()
            if (resBody != null) {
                saveItemsToFile(resBody, "getDayScenario")
                isRescheduleSuccess = true
            } else {
                isRescheduleSuccess = false
                Log.e(TAG, "Lịch rỗng rồi")
            }
        } catch (e: Exception) {
            isRescheduleSuccess = false
            Log.e(TAG, e.toString())
        }

        return result
    }

    suspend fun getDataDayScenario(): List<MediaItem> {
        scheduledList.clear()
        scheduledListLeft.clear()

        val result = if (isFileExisted(mediaStoragePath, PLAYLIST_FILE_NAME)) {
            Log.d(TAG, "read local ")
            readItemsFromFile()
        } else listOf()

        broadcastList.clear()

        broadcastList.addAll(result)

        filterMedia(broadcastList)

        return broadcastList
    }

    private fun filterMedia(mediaList: List<MediaItem>) {
        val dividerIndices = mediaList.indices.filter {
            mediaList[it].body == "start" || mediaList[it].body == "end"
        }.chunked(2)

        dividerIndices.forEach {
            if (it.size == 2) {
                val startIndex = it[0]
                val endIndex = it[1]
                val key = "${mediaList[startIndex].fixTime}-${mediaList[endIndex].fixTime}"
                scheduledList[key] = mediaList.subList(startIndex + 1, endIndex)
                    .filter { mediaItem -> mediaItem.broadcastRegion?.value == RIGHT_AREA.toInt() || mediaItem.broadcastRegion?.value == FULL_SCREEN }

                timeDividers[key] = listOf(broadcastList[startIndex], broadcastList[endIndex])

                scheduledListLeft[key] = mediaList.subList(startIndex + 1, endIndex)
                    .filter { mediaItem -> mediaItem.broadcastRegion?.value == LEFT_AREA.toInt() }
            }
        }

    }


    suspend fun clearPlaylist() {
        broadcastList.clear()
        scheduledListLeft.clear()
        scheduledList.clear()
        timeDividers.clear()
        saveItemsToFile(emptyList())
    }

    fun startDownloadMedia() {
        val needDownloadList = broadcastList.filter { it.shouldDownload() }

        if (needDownloadList.isNotEmpty()) {
            needDownloadList.forEach { downloadMediaItem(it) }
        }
    }

    fun downloadMediaItem(it: MediaItem) {

        //xác định có là file hay không, lấy đuôi file
        val extMedia = File(it.body ?: "").extension

        if (extMedia.isEmpty()) return
        val isFile = getConfigExtension()?.contains(extMedia.lowercase()) ?: false

        if (!isFile) return

        val url = it.body

        val fileName = getFileNameFromUrl(url!!)

        if (isFileExisted(mediaStoragePath, fileName)) {
            //handle broken path
            if (it.body != "$mediaStoragePath/$fileName") {
                Log.d(TAG, "${mediaStoragePath}/${fileName}")
                Log.d(TAG, "filterMedia: ${it.body}")
                it.body = "$mediaStoragePath/$fileName"
                coroutineScope.launch {
                    saveItemsToFile(broadcastList, "downloadMediaItem check")
                    Log.e("TAG", "Save in downloadAMediaItem: Case handle broken path")
                }
            }
            return
        }

        Log.d(TAG, "Down load $url")

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
                        saveItemsToFile(broadcastList, "downloadMediaItem")
                        Log.e(TAG, "Save in onDownloadComplete: onDownloadComplete")
                    }
                    Log.d("downloadcomplete", it.body!!)
                }

                override fun onError(error: Error?) {
                    Log.d("downloaderror", it.body!!)
                }

            }
        })
    }

    fun downloadImage(url: String) {
        val fileName = getFileNameFromUrl(url)
        downloadHelper.addToQueue(DownloadItem().apply {
            itDownloadUrl = url
            itStoragePath = mediaStoragePath
            itFileName = fileName
            priority = 0
            downloadListener = object : OnDownloadListener {
                override fun onDownloadComplete() {

                    Log.d("downloadcomplete", url)
                }

                override fun onError(error: Error?) {
                    Log.d("downloaderror", url)
                }

            }
        })
    }


    private fun getConfigExtension(): String? {
        return ConfigUtil.getString(appContext, "KEY_EXTEND_FILE", "mp4,jpg,png")
    }

}