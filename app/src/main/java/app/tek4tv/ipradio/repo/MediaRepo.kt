package app.tek4tv.ipradio.repo

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.ScheduleDay
import app.tek4tv.ipradio.network.PlaylistService
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.ConfigUtil.KEY_MODIFY_TIME_PLAYLIST
import app.tek4tv.ipradio.utils.ConfigUtil.MONTHLY_SCENARIO_DATA
import app.tek4tv.ipradio.utils.media.DownloadHelper2
import app.tek4tv.ipradio.utils.media.DownloadItem
import app.tek4tv.ipradio.utils.storage.PLAYLIST_FILE_NAME
import app.tek4tv.ipradio.utils.storage.getFileNameFromUrl
import app.tek4tv.ipradio.utils.storage.isFileExisted
import com.downloader.OnDownloadListener
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
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

    override val jsonAdapter: JsonAdapter<List<MediaItem>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, MediaItem::class.java)
    )

    private val LOG_TAG = "MEDIA_REPO"

    var broadcastList = mutableListOf<MediaItem>()
    var scheduledList = LinkedHashMap<String, List<MediaItem>>()
    var timeDividers = LinkedHashMap<String, List<MediaItem>>()

    private val downloadHelper = DownloadHelper2.getInstance()

    private val mediaStoragePath = appContext.filesDir.path


    private val scheduleMonthlyAdapter: JsonAdapter<List<ScheduleDay>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, ScheduleDay::class.java)
    )

    suspend fun safeUpdateMonthlyScenario(
        url: String,
        retryCount: Int = 3,
        delayMillis: Long = 5 * 60 * 1000
    ) {
        repeat(retryCount) { attempt ->
            try {
                updateMonthlyScenario(url)
                return // thành công → thoát luôn
            } catch (e: Exception) {
                if (attempt < retryCount - 1) delay(delayMillis)
            }
        }
    }

    private suspend fun updateMonthlyScenario(url: String) {
        try {
            Log.e(LOG_TAG, "updateMonthlyScenario")
            val fetchResult = playlistService.getMonthlyPlaylist(url)

            if (!fetchResult.isSuccessful) return
            val monthScenario = fetchResult.body() ?: return
            ConfigUtil.putString(
                appContext,
                KEY_MODIFY_TIME_PLAYLIST,
                monthScenario.first().ppdatedAt
            )
            //4.1.6e luu du lieu cua lich thang
            val monthlyScenarioData = scheduleMonthlyAdapter.toJson(fetchResult.body())
            ConfigUtil.putString(
                appContext,
                MONTHLY_SCENARIO_DATA,
                monthlyScenarioData
            )

            val currentDate = SimpleDateFormat("dd-MM-yyyy").format(Date())

            val dayScenario = monthScenario.firstOrNull { it.date == currentDate }

            if (dayScenario != null) {
                Log.e(LOG_TAG, "có lịch trong ngày: update")
                updateScenario(dayScenario.sourceUrl)
            } else {
                //In case there are no scenario for today, don't play anythings
                saveItemsToFile(listOf())
                Log.e(LOG_TAG, "Không có lịch trong ngày")
            }

        } catch (e: Exception) {
            Log.e(LOG_TAG, "Cannot update daily scenario ${e.message}")
        }
    }

    suspend fun clearPlaylist() {
        saveItemsToFile(listOf())
    }

    suspend fun getScenario(): List<MediaItem> {
        scheduledList.clear()
        timeDividers.clear()

        val currentDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        val jsonMonthlyListData = ConfigUtil.getString(appContext, MONTHLY_SCENARIO_DATA, "") ?: ""
        if (jsonMonthlyListData != "") {
            val monthlyDataList = scheduleMonthlyAdapter.fromJson(jsonMonthlyListData)

            val res = if (monthlyDataList.isNullOrEmpty()) {
                Log.d(LOG_TAG, "No monthly scenario data")
                emptyList()
            } else {
                val todayScenario = monthlyDataList.firstOrNull { it.date == currentDate }
                when {
                    todayScenario == null -> {
                        Log.d(LOG_TAG, "No schedule for today")
                        emptyList()
                    }
                    isFileExisted(mediaStoragePath, PLAYLIST_FILE_NAME) -> {
                        Log.d(LOG_TAG, "Reading playlist from local file")
                        val localList = readItemsFromFile()
                        localList.ifEmpty {
                            Log.d(LOG_TAG, "Local file is empty, fallback to network")
                            updateScenario(todayScenario.sourceUrl)
                        }
                    }
                    else -> {
                        Log.d(LOG_TAG, "No local file, fallback to network")
                        updateScenario(todayScenario.sourceUrl)
                    }
                }
            }

            broadcastList.clear()
            broadcastList.addAll(res)
            Log.d(LOG_TAG, "local ${res.size}")
            filterMedia(broadcastList)
            return broadcastList
        } else {
            return emptyList()
        }
    }

    private fun filterMedia(mediaList: List<MediaItem>) {
        //chia ta thanh cac cap [0,4], [5,8]
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

    }

    private suspend fun updateScenario(listUrl: String): List<MediaItem> {
        var result = listOf<MediaItem>()

        try {
            val res = playlistService.getPlaylist(listUrl)
            if (res.isSuccessful) {
                val resBody = res.body()
                if (resBody != null) {
                    result = resBody
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
        if (broadcastList.isEmpty()) return

        broadcastList.forEach {
            downloadAMediaItem(it)
        }
    }

    private fun getConfigExtension(): String? {
        return ConfigUtil.getString(appContext, ConfigUtil.KEY_EXTEND_FILE, "mp3#aac#wav#wma#ac3")

    }

    fun downloadAMediaItem(it: MediaItem) {
        //1 - bản tin ký tự
        //2 - bản tin hình ảnh
        //3 - bản tin âm thanh
        //4 - Bản tin video
        //5 - Bản tin trực tiếp
        //6 - Bản tin FM
        //7 - Bản tin ghi âm trực tiếp
        //không phải files thì không tải, chỉ tải type 3 , type 4
        if (it.contentType?.value != "4" && it.contentType?.value != "3") return

        //xác định có là file hay không, lấy đuôi file
        val extMedia = File(it.body).extension

        if (extMedia.isEmpty()) return
        val isFile = getConfigExtension()!!.contains(extMedia)

        if (!isFile) return

        val url = it.body
        //if (url.isEmpty() || !url.startsWith("http")) return

        val fileName = getFileNameFromUrl(url!!)
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

                override fun onError(error: com.downloader.Error?) {
                    Log.d("downloaderror", it.body!!)
                }

            }
        })
    }
}