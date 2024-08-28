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
import java.io.File
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

    var timeDividers = LinkedHashMap<String, List<MediaItem>>()


    private val downloadHelper = DownloadHelper2.getInstance()

    private val mediaStoragePath = appContext.filesDir.path

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
        var result = listOf<MediaItem>()

        try {
            result = jsonListMediaItemAdapter.fromJson(jsonListMediaByDay)!!


            if (result.isNotEmpty()) {
                Log.e(TAG, "Save ${result.size} to local")
                saveItemsToFile(result)

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
        Log.d(TAG, "filterMedia: $scheduledList")
        Log.d(TAG, "filterMedia: $timeDividers")


    }

    val type: Type = Types.newParameterizedType(
        List::class.java,
        MediaItem::class.java
    )


    fun startDownloadMedia() {
        if (broadcastList.isNullOrEmpty()) return

        broadcastList.forEach {
            downloadAMediaItem(it)
        }
    }

    private fun getConfigExtension() : String?
    {
        return ConfigUtil.getStringEncrypt(appContext,ConfigUtil.KEY_EXTEND_FILE,"mp4")
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
        if (it.body == "start" || it.body == "end") return
        if (it.contentType?.value != "4" && it.contentType?.value != "3" && it.contentType?.value != "2" ) return

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