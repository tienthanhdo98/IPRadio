package app.tek4tv.ipradio.repo

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.network.PlaylistService
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.GlobalProperties.Companion.TYPE_SCREEN_KEY
import app.tek4tv.ipradio.utils.PLAYLIST_FILE_NAME
import app.tek4tv.ipradio.utils.Utils.Companion.FULL_SCREEN
import app.tek4tv.ipradio.utils.Utils.Companion.LEFT_AREA
import app.tek4tv.ipradio.utils.Utils.Companion.RIGHT_AREA
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

    override val jsonAdapter: JsonAdapter<List<MediaItem>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, MediaItem::class.java)
    )
    var broadcastList = mutableListOf<MediaItem>()

    var scheduledList = LinkedHashMap<String, List<MediaItem>>()

    var scheduledListLeft = LinkedHashMap<String, List<MediaItem>>()

    var timeDividers = LinkedHashMap<String, List<MediaItem>>()

    val downloadHelper = DownloadHelper2.getInstance()

    val mediaStoragePath: String = appContext.filesDir.path

    var isRescheduleSuccess = false;
    val type: Type = Types.newParameterizedType(
        List::class.java,
        MediaItem::class.java
    )

    suspend fun getMonthlyScenario(url: String) {
        isRescheduleSuccess = false
        try {
            val fetchResult = retryWithBackoff { playlistService.getMonthlyPlaylist(url) }
            if (fetchResult == null || !fetchResult.isSuccessful) {
                Log.e("getMonthlyScenario", "API request failed after retries")
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
            Log.e("updateDailyScenario", "Cannot update daily scenario ${e.message}")
        }
    }

    private suspend fun getDayScenario(urlDatSchedule: String): List<MediaItem> {
        val result = listOf<MediaItem>()
        try {
            val res = retryWithBackoff { playlistService.getPlaylist(urlDatSchedule) }
            if (res == null || !res.isSuccessful) {
                isRescheduleSuccess = false
                Log.e("getDayScenario", "API request failed after retries")
                return emptyList()
            }
            val resBody = res.body()
            if (resBody != null) {
                //dung khi moi ban tin 1 lich
//                val listOnlyMedia =
//                    resBody.filter { it.body != null && it.body != "start" && it.body != "end" }
//                result = convertToTimeFrame(listOnlyMedia)

                saveItemsToFile(resBody, "getDayScenario")
                isRescheduleSuccess = true
            } else {
                isRescheduleSuccess = false
                Log.e("yee", "Lịch rỗng rồi")
            }
        } catch (e: Exception) {
            isRescheduleSuccess = false
            Log.e("yee", e.toString())
        }

        return result
    }

    suspend fun getDataDayScenario(): List<MediaItem> {
        scheduledList.clear()
        scheduledListLeft.clear()

        val result = if (isFileExisted(mediaStoragePath, PLAYLIST_FILE_NAME)) {
            Log.d("readplaylist", "local ")
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
                Log.d("alarmx", "Set $key")
                scheduledList[key] = mediaList.subList(startIndex + 1, endIndex)
                    .filter { mediaItem -> mediaItem.broadcastRegion?.value == RIGHT_AREA.toInt() || mediaItem.broadcastRegion?.value == FULL_SCREEN.toInt() }

                timeDividers[key] = listOf(broadcastList[startIndex], broadcastList[endIndex])

                scheduledListLeft[key] = mediaList.subList(startIndex + 1, endIndex)
                    .filter { mediaItem -> mediaItem.broadcastRegion?.value == LEFT_AREA.toInt() }
            }
        }

    }

    ///model v1 to v2
    private fun convertToTimeFrame(listItem: List<MediaItem>): List<MediaItem> {
        val newList = listItem.toMutableList()
        val res = mutableListOf<MediaItem>()
        val listTimeFrame: MutableList<Map<Int, Int>> = mutableListOf()
        val starts: MutableList<String> = mutableListOf()
        var startTime = 0
        var endTime: Int
        for (i in 0..listItem.size - 2) {
            if (i == 0) {
                startTime = 0
            }

            if (listItem[i].end != listItem[i + 1].start) {
                endTime = i
                starts.add(listItem[i].end!!)
                val map = mapOf(startTime to endTime)
                listTimeFrame.add(map)
                startTime = i + 1
            }
            if (i == listItem.size - 2) {
                endTime = i + 1
                val map = mapOf(startTime to endTime)
                listTimeFrame.add(map)
            }
        }
        //Chỉ có 1 khung
        if (starts.size == 0) {
            val firstId: Int = listItem.first().id?.minus(1) ?: 0
            Log.d("wssTImeFrame", "convertToTimeFrame: 1 khung")
            val tObjStart = MediaItem(
                id = firstId,
                mediaID = firstId,
                projectID = firstId.toString(),
                title = "Khung ${listItem.first().start}",
                body = "start",
                startTime = listItem.first().startTime,
                start = listItem.first().startTime,
                fixTime = listItem.first().startTime,
                end = listItem.first().end,
            )
            newList.add(0, tObjStart)
            val lastId: Int = listItem.last().id?.plus(1) ?: 0
            val tObEnd = MediaItem(
                id = lastId,
                mediaID = lastId,
                projectID = lastId.toString(),
                title = "End Khung ${listItem.first().start}",
                body = "end",
                startTime = listItem.last().startTime,
                fixTime = listItem.last().end,
                end = listItem.last().end,
                start = listItem.last().start
            )
            newList.add(tObEnd)

            return newList
        }
        //Có nhiều khung
        else {
            Log.d("wssTImeFrame", "convertToTimeFrame: Nhiều khung")
            listTimeFrame.forEach {
                val list: MutableList<MediaItem> =
                    listItem.subList(it.keys.first(), it.values.first() + 1).toMutableList()
                val firstId: Int = listItem[it.keys.first()].id?.minus(1) ?: 0
                val tObjStart = MediaItem(
                    id = firstId,
                    mediaID = firstId,
                    projectID = firstId.toString(),
                    title = "Khung ${listItem[it.keys.first()].start}",
                    body = "start",
                    startTime = listItem[it.keys.first()].startTime,
                    start = listItem[it.keys.first()].startTime,
                    fixTime = listItem[it.keys.first()].startTime,
                    end = listItem[it.keys.first()].end,
                )
                list.add(0, tObjStart)
                val lastId: Int = listItem[it.values.first()].id?.plus(1) ?: 0
                val tObEnd = MediaItem(
                    id = lastId,
                    mediaID = lastId,
                    projectID = lastId.toString(),
                    title = "End Khung ${listItem[it.values.first()].start}",
                    body = "end",
                    startTime = listItem[it.values.first()].startTime,
                    fixTime = listItem[it.values.first()].end,
                    end = listItem[it.values.first()].end,
                    start = listItem[it.values.first()].start
                )
                list.add(list.size, tObEnd)
                res.addAll(list)
            }
            return res

        }

    }

    suspend fun clearPlaylist() {
        saveItemsToFile(emptyList())
    }

    fun startDownloadMedia() {
        val needDownloadList = broadcastList.filter { it.shouldDownload() }.toList()


        if (needDownloadList.isNotEmpty()) {
            needDownloadList.forEach { downloadMediaItem(it) }
        }
    }


    private fun getConfigExtension(): String? {
        return ConfigUtil.getString(appContext, "KEY_EXTEND_FILE", "mp4,jpg,png")
    }

    fun downloadMediaItem(it: MediaItem) {

        //xác định có là file hay không, lấy đuôi file
        val extMedia = File(it.body ?: "").extension

        if (extMedia.isEmpty()) return
        val isFile = getConfigExtension()!!.contains(extMedia)

        if (!isFile) return

        val url = it.body

        val fileName = getFileNameFromUrl(url!!)
        if (isFileExisted(mediaStoragePath, fileName)) {
            //handle broken path
            if (it.body != "$mediaStoragePath/$fileName") {
                Log.d("ĐÂSDF", "${mediaStoragePath}/${fileName}")
                Log.d("ĐÂSDF", "filterMedia: ${it.body}")
                it.body = "$mediaStoragePath/$fileName"
                coroutineScope.launch {
                    saveItemsToFile(broadcastList, "downloadMediaItem check")
                    Log.e("SavefileLog", "Save in downloadAMediaItem: Case handle broken path")
                }
            }
            return
        }

        Log.d("downloaditem", url)

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
                        Log.e("SavefileLog", "Save in onDownloadComplete: onDownloadComplete")
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
}