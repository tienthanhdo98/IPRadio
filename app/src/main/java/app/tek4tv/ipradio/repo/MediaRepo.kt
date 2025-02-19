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

    val LOG_TAG = "MEDIA_REPO"

    var broadcastList = mutableListOf<MediaItem>()
    var scheduledList = LinkedHashMap<String, List<MediaItem>>()
    var timeDividers = LinkedHashMap<String, List<MediaItem>>()
    var unscheduledList = mutableListOf<MediaItem>()
    var scheduledMediaItems = mutableListOf<MediaItem>()

    private val downloadHelper = DownloadHelper2.getInstance()

    private val mediaStoragePath = appContext.filesDir.path


    private val scheduleMonthlyAdapter: JsonAdapter<List<ScheduleDay>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, ScheduleDay::class.java)
    )

    suspend fun updateMonthlyScenario(url: String) {
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

            //if there are only a single scenario for every day
            //trường hợp là lịch tháng nhưng chỉ
//            val isSingleScenario = monthScenario.size == 1 && monthScenario[0].date == ""

            //in case of single scenario, continue to use old scenario
//            if (isSingleScenario && !forceNewScenario) {
//                Log.d("updateDailyScenario", "return update")
//                return
//            }

            val currentDate = SimpleDateFormat("dd-MM-yyyy").format(Date())

//            val dayScenario = if (isSingleScenario) monthScenario[0]
//            else monthScenario.firstOrNull { it.date == currentDate }

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
    suspend fun clearPlaylist()
    {
        saveItemsToFile(listOf())
    }

    suspend fun getScenario(needUpdate: Boolean, listUrl: String): List<MediaItem> {
        scheduledList.clear()
        unscheduledList.clear()
        scheduledMediaItems.clear()
        timeDividers.clear()

        val res = when {
            needUpdate -> updateScenario(listUrl)
            else -> {
                val jsonMonthlyListData = ConfigUtil.getString(appContext, MONTHLY_SCENARIO_DATA, "") ?: ""
                if (jsonMonthlyListData.isNotEmpty()) {
                    val monthlyScenarioDataList = scheduleMonthlyAdapter.fromJson(jsonMonthlyListData)
                    val currentDate = SimpleDateFormat("dd-MM-yyyy").format(Date())

                    if (monthlyScenarioDataList?.isNotEmpty() == true) {
                        val dayScenario = monthlyScenarioDataList.firstOrNull { it.date == currentDate }

                        dayScenario?.let {
                            if (isFileExisted(mediaStoragePath, PLAYLIST_FILE_NAME)) {
                                Log.d("readplaylist", "local")
                                val list = readItemsFromFile()
                                if (list.isNotEmpty()) {
                                    Log.d(LOG_TAG, "local not null")
                                    list
                                } else {
                                    Log.d(LOG_TAG, "local null: recall monthly url")
                                    updateScenario(it.sourceUrl)
                                }
                            } else {
                                Log.d(LOG_TAG, "local not file in local")
                                listOf()
                            }
                        } ?: run {
                            Log.d(LOG_TAG, "Today does not have a playing schedule")
                            listOf()
                        }
                    } else {
                        Log.d(LOG_TAG, "List Nothing to play")
                        listOf()
                    }
                } else {
                    Log.d(LOG_TAG, "Nothing str list to play")
                    listOf()
                }
            }
        }

        broadcastList.clear()
        broadcastList.addAll(res)
        Log.d(LOG_TAG, "local ${res.size}")
        filterMedia(broadcastList)
        return broadcastList
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
//                    var i = 1
//                    result = resBody.onEach { it.mediaID = i++ }
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
        if (broadcastList.isNullOrEmpty()) return

        broadcastList.forEach {
            downloadAMediaItem(it)
        }
    }
    private fun getConfigExtension() : String?
    {
        return ConfigUtil.getString(appContext,ConfigUtil.KEY_EXTEND_FILE,"mp3#aac#wav#wma#ac3")

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
        if (it.contentType?.value != "4" && it.contentType?.value != "3" ) return

        //xác định có là file hay không, lấy đuôi file
        val extMedia = File(it.body).extension

        if(extMedia.isEmpty()) return
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