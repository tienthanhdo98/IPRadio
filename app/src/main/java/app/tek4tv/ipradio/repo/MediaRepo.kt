package app.tek4tv.ipradio.repo

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.network.PlaylistService
import app.tek4tv.ipradio.ui.MainActivity.Companion.jsonListMediaItemAdapter
import app.tek4tv.ipradio.ui.MainActivity.Companion.jsonMapAdapter
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.ConfigUtil.MONTHLY_SCENARIO_WSS_DATA
import app.tek4tv.ipradio.utils.GlobalProperties.Companion.TYPE_SCREEN_KEY
import app.tek4tv.ipradio.utils.PLAYLIST_FILE_NAME
import app.tek4tv.ipradio.utils.Utils.Companion.FULL_SCREEN
import app.tek4tv.ipradio.utils.Utils.Companion.LEFT_AREA
import app.tek4tv.ipradio.utils.Utils.Companion.RIGHT_AREA
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
import java.lang.reflect.Type
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
    val TAG = "MEDIA_REPO"
    override val jsonAdapter: JsonAdapter<List<MediaItem>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, MediaItem::class.java)
    )
    var broadcastList = mutableListOf<MediaItem>()

    var scheduledList = LinkedHashMap<String, List<MediaItem>>()

    var leftScheduledList = mutableListOf<MediaItem>()
    //top scheduledList
    var timeDividers = LinkedHashMap<String, List<MediaItem>>()
    var unscheduledList = mutableListOf<MediaItem>()
    var scheduledMediaItems = mutableListOf<MediaItem>()

    private val downloadHelper = DownloadHelper2.getInstance()

    private val mediaStoragePath = appContext.filesDir.path

    suspend fun updateMonthlyScenarioWss(strListMonthSchedule: String) {
        try {
            Log.e(TAG, "updateMonthlyScenario")

            val listMonthSchedule = jsonMapAdapter.fromJson(strListMonthSchedule)
            listMonthSchedule?.forEach{
                Log.e(TAG, "updateMonthlyScenarioWss ${it.key}")
                val list = jsonListMediaItemAdapter.fromJson(it.value)
                list?.forEach {
                    Log.e(TAG, "updateMonthlyScenarioWss ${it.title}")
                }

            }
            ConfigUtil.putString(
                appContext,
                MONTHLY_SCENARIO_WSS_DATA,
                strListMonthSchedule
            )

            val currentDate = SimpleDateFormat("dd-MM-yyyy").format(Date())

//            val dayScenario = listMonthSchedule?.firstOrNull { it.date == currentDate }
            var dayScenario : String? = null

            if (listMonthSchedule != null) {
                for(i in listMonthSchedule) {
                    if (i.key == currentDate) {
                        Log.e(TAG, "get scenario co data ngay ${i.key}")
                        dayScenario = i.value
                        break
                    } else {
                        dayScenario = null
                    }
                }
            }
            if (dayScenario != null) {
                Log.e(TAG, "có lịch trong ngày: update ${dayScenario}")
                updateScenarioWss(dayScenario)
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

    fun getScenarioWss(): List<MediaItem> {
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

                for(i in monthlyListData)
                {
                    if (i.key == currentDate) {
                        Log.e(TAG, "get scenario co data ngay ${i.key}")
                        Log.e(TAG, "get scenario co data ngay $currentDate")
                        dayScenario = i.value
                        break
                    } else {
                        dayScenario = null
                    }
                }
                dayScenario?.let {

                    res = jsonListMediaItemAdapter.fromJson(dayScenario)!!

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
                ConfigUtil.putInt(appContext, TYPE_SCREEN_KEY, dayScenario.broadcastRegionDigital.toInt())
               // layoutType = dayScenario.broadcastRegionDigital.toInt()
                Log.i("ChangeLayout", "Mess: get type ${dayScenario.broadcastRegionDigital.toInt()}")
                updateScenario(dayScenario.sourceUrl)
            } else {
                //In case there are no scenario for today, don't play anythings
                saveItemsToFile(listOf())
            }

        } catch (e: Exception) {
            Log.e("updateDailyScenario", "Cannot update daily scenario ${e.message}")
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

    val type: Type = Types.newParameterizedType(
        List::class.java,
        MediaItem::class.java
    )
    val mediaJsonAdapter: JsonAdapter<List<MediaItem>> = moshi.adapter<List<MediaItem>>(type)

//    val mediaJsonAdapter = MoshiInst.moshi.adapter<List<MediaItem>>( List::class.java, MediaItem::class.java)
    private suspend fun updateScenario(listUrl: String): List<MediaItem> {
        var result = listOf<MediaItem>()

        try {
            val res = playlistService.getPlaylist(listUrl)
            if (res.isSuccessful) {
                val resBody = res.body()
                if (resBody != null) {

//                    result =  resBody
                    result =  resBody.filter {it.broadcastRegion?.value == RIGHT_AREA || it.broadcastRegion?.value == FULL_SCREEN  }

                    leftScheduledList = resBody.filter { it.broadcastRegion?.value == LEFT_AREA  && it.body != null && it.body != "start" && it.body != "end"} as MutableList<MediaItem>

                    val srt = mediaJsonAdapter.toJson(leftScheduledList)
                    ConfigUtil.putString(appContext,ConfigUtil.KEY_LIST_LEFT_PREF,srt)

//                    var i = 1
//                    result.onEach{ mediaItem ->
//                        mediaItem.mediaID = i++
//                    }
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