package app.tek4tv.ipradio.repo

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.ScheduleDay
import app.tek4tv.ipradio.network.PlaylistService
import app.tek4tv.ipradio.ui.MainActivity.Companion.jsonListMediaItemAdapter
import app.tek4tv.ipradio.ui.MainActivity.Companion.jsonMapAdapter
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.ConfigUtil.KEY_MODIFY_TIME_PLAYLIST
import app.tek4tv.ipradio.utils.ConfigUtil.MONTHLY_SCENARIO_DATA
import app.tek4tv.ipradio.utils.ConfigUtil.MONTHLY_SCENARIO_WSS_DATA
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

    val TAG = "MEDIA_REPO"

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

    suspend fun updateMonthlyScenarioWss(strListMonthSchedule: String) {
        try {
            Log.e(TAG, "updateMonthlyScenario")

            val listMonthSchedule = jsonMapAdapter.fromJson(strListMonthSchedule)

            ConfigUtil.putString(
                appContext,
                MONTHLY_SCENARIO_WSS_DATA,
                strListMonthSchedule
            )

            val currentDate = SimpleDateFormat("dd-MM-yyyy").format(Date())

//            val dayScenario = listMonthSchedule?.firstOrNull { it.date == currentDate }
            var dayScenario : String? = null
            listMonthSchedule?.forEach { item ->
                dayScenario = if(item.key == currentDate) {
                    item.value
                } else {
                    null
                }
            }
                if (dayScenario != null) {
                Log.e(TAG, "có lịch trong ngày: update")
                updateScenarioWss(dayScenario!!)
            } else {
                //In case there are no scenario for today, don't play anythings
                saveItemsToFile(listOf())
                Log.e(TAG, "Không có lịch trong ngày")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Cannot update daily scenario ${e.message}")
        }
    }

    private suspend fun updateScenarioWss(jsonListMediaByDay: String): List<MediaItem> {
        val result = listOf<MediaItem>()

        try {
            val res = jsonListMediaItemAdapter.fromJson(jsonListMediaByDay)
            if (res?.isNotEmpty() == true) {
                Log.e(TAG, "Save ${res.size} to local")
                saveItemsToFile(res)

            } else {
                Log.e(TAG, "Lịch rỗng rồi")
            }
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        }

        return result
    }

    suspend fun getScenarioWss(): List<MediaItem> {
        scheduledList.clear()
        unscheduledList.clear()
        scheduledMediaItems.clear()
        timeDividers.clear()

        var res = listOf<MediaItem>()

        val jsonMonthlyListData = ConfigUtil.getString(appContext, MONTHLY_SCENARIO_WSS_DATA, "") ?: ""
        Log.e(TAG, "get scenario ${jsonMonthlyListData}")
        if (jsonMonthlyListData.isNotEmpty()) {
            val monthlyListData = jsonMapAdapter.fromJson(jsonMonthlyListData)

            if (monthlyListData?.isNotEmpty() == true) {

                val currentDate = SimpleDateFormat("dd-MM-yyyy").format(Date())

                var dayScenario: String? = null
                monthlyListData.forEach { item ->
                    dayScenario = if (item.key == currentDate) {
                        item.value
                    } else {
                        null
                    }
                }
                dayScenario?.let {
                    if (isFileExisted(mediaStoragePath, PLAYLIST_FILE_NAME)) {
                        Log.d("readplaylist", "local")
                        val list = readItemsFromFile()
                        if (list.isNotEmpty()) {
                            Log.d(TAG, "local not null")
                            res = list
                        } else {
                            Log.d(TAG, "local null: recall monthly url")
                            updateMonthlyScenarioWss(jsonMonthlyListData)
                        }
                    } else {
                        Log.d(TAG, "local not file in local")
                        res = listOf()
                    }
                } ?: run {
                    Log.d(TAG, "Today does not have a playing schedule")
                    res = listOf()
                }

            } else {
                    Log.d(TAG, "Nothing str list to play")
                    res = listOf()
                }
            }
                else {
                    Log.d(TAG, "Nothing str list to play")
                    res = listOf()
                }

        broadcastList.clear()
        broadcastList.addAll(res)
        Log.d(TAG, "local ${res.size}")
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


    fun startDownloadMedia() {
        Log.d("startDownloadMedia", "start")
        if (broadcastList.isEmpty()) return
        Log.d("startDownloadMedia", "stilll")
        broadcastList.forEach {
            downloadAMediaItem(it)
        }
    }
    private fun getConfigExtension() : String?
    {
        return ConfigUtil.getStringEncrypt(appContext,ConfigUtil.KEY_EXTEND_FILE,"mp3#aac#wav#wma#ac3")

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
        Log.d("startDownloadMedia", "type : ${it.contentType?.value}")
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