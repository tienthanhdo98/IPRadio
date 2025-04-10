package app.tek4tv.ipradio.repo

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.di.MoshiInst.jsonListMediaItemAdapter
import app.tek4tv.ipradio.di.MoshiInst.jsonMapAdapter
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.utils.ConfigUtil
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
    private var completedDownloads = 0
    private val lock = Any()

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


    private fun getConfigExtension(): String? {
        return ConfigUtil.getString(
            appContext,
            ConfigUtil.KEY_EXTEND_FILE,
            "mp3#aac#wav#wma#ac3"
        )

    }
    suspend fun clearPlaylist()
    {
        saveItemsToFile(listOf())
    }
        fun startDownloadMedia() {
        if (broadcastList.isEmpty()) return
        broadcastList.forEach {
            downloadAMediaItem(it)
        }
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
        if (it.body == "start" || it.body == "end") return
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


//    fun startDownloadMedia() {
//        if (broadcastList.isEmpty()) return
//
//        val validItems = broadcastList.filter {
//            it.body != "start" && it.body != "end" &&
//                    (it.contentType?.value == "3" || it.contentType?.value == "4") &&
//                    File(it.body).extension.isNotEmpty() &&
//                    getConfigExtension()?.contains(File(it.body).extension) == true
//        }
//
//        if (validItems.isEmpty()) return  // No valid items to download
//
//        completedDownloads = 0  // Reset counter
//        val totalDownloads = validItems.size
//
//        validItems.forEach { item ->
//            downloadMediaItem(item, totalDownloads)
//        }
//    }
//
//
//    // Separate function for downloading a media item
//    fun downloadMediaItem(item: MediaItem, totalDownloads: Int) {
//        val fileName = getFileNameFromUrl(item.body ?: return)
//
//        // Check if file already exists
//        if (isFileExisted(mediaStoragePath, fileName)) {
//            val updatedPath = "$mediaStoragePath/$fileName"
//            if (item.body != updatedPath) {
//                item.body = updatedPath
//            }
//            return
//        }
//
//        Log.d("download item", item.body ?: "Invalid URL")
//
//        // Queue download
//        downloadHelper.addToQueue(DownloadItem().apply {
//            itDownloadUrl = item.body!!
//            itStoragePath = mediaStoragePath
//            itFileName = fileName
//            priority = 0
//            downloadListener = object : OnDownloadListener {
//                override fun onDownloadComplete() {
//                    item.pathBackup = item.body!!
//                    item.body = "$mediaStoragePath/$fileName"
//
//                    synchronized(lock) {  // Thread-safe update
//                        completedDownloads++
//                        if (completedDownloads == totalDownloads) {
//                            coroutineScope.launch {
//                                saveItemsToFile(broadcastList)  // Save only once after all downloads
//                                Log.d("writrr","$completedDownloads - $totalDownloads")
//                            }
//                        }
//                    }
//                    Log.d("downloadcomplete", item.body!!)
//                }
//
//                override fun onError(error: com.downloader.Error?) {
//                    Log.d("downloaderror", item.body!!)
//                }
//            }
//        })
//    }



//    fun downloadAMediaItem(it: MediaItem) {
//        //1 - bản tin ký tự
//        //2 - bản tin hình ảnh
//        //3 - bản tin âm thanh
//        //4 - Bản tin video
//        //5 - Bản tin trực tiếp
//        //6 - Bản tin FM
//        //7 - Bản tin ghi âm trực tiếp
//        //không phải files thì không tải, chỉ tải type 3 , type 4
//        Log.d("startDownloadMedia", "type : ${it.contentType?.value}")
//        if (it.body == "start" || it.body == "end") return
//        if (it.contentType?.value != "4" && it.contentType?.value != "3" ) return
//
//        //xác định có là file hay không, lấy đuôi file
//        val extMedia = File(it.body).extension
//
//        if(extMedia.isEmpty()) return
//        val isFile = getConfigExtension()!!.contains(extMedia)
//
//        if (!isFile) return
//
//        val url = it.body
//        //if (url.isEmpty() || !url.startsWith("http")) return
//
//        val fileName = getFileNameFromUrl(url!!)
//        if (isFileExisted(mediaStoragePath, fileName)) {
//            //handle broken path
//            if (it.body != "$mediaStoragePath/$fileName") {
//                it.body = "$mediaStoragePath/$fileName"
//                coroutineScope.launch {
//                    saveItemsToFile(broadcastList)
//                }
//            }
//
//            return
//        }
//
//        Log.d("download item", url)
//
//        downloadHelper.addToQueue(DownloadItem().apply {
//            itDownloadUrl = url
//            itStoragePath = mediaStoragePath
//            itFileName = fileName
//            priority = 0
//            downloadListener = object : OnDownloadListener {
//                override fun onDownloadComplete() {
//                    it.pathBackup = it.body!!
//                    it.body = "$mediaStoragePath/$fileName"
//
//                    coroutineScope.launch {
//                        saveItemsToFile(broadcastList)
//                    }
//
//                    Log.d("downloadcomplete", it.body!!)
//                }
//
//                override fun onError(error: com.downloader.Error?) {
//                    Log.d("downloaderror", it.body!!)
//                }
//
//            }
//        })
//    }
}
//    fun startDownloadMedia() {
//        if (broadcastList.isEmpty()) return
//        broadcastList.forEach {
//            downloadAMediaItem(it)
//        }
//    }

