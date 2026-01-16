package app.tek4tv.ipradio.repo

import android.content.Context
import android.util.Log
import android.webkit.URLUtil
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.ScheduleDay
import app.tek4tv.ipradio.network.PlaylistService
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.ConfigUtil.KEY_MODIFY_TIME_PLAYLIST
import app.tek4tv.ipradio.utils.ConfigUtil.MONTHLY_SCENARIO_DATA
import app.tek4tv.ipradio.utils.media.DownloadHelper2
import app.tek4tv.ipradio.utils.storage.PLAYLIST_FILE_NAME
import app.tek4tv.ipradio.utils.storage.isFileExisted
import com.downloader.Error
import com.downloader.OnDownloadListener
import com.downloader.PRDownloader
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class MediaRepo @Inject constructor(
    private val playlistService: PlaylistService,
    moshi: Moshi,
    @ApplicationContext private val appContext: Context
) : BaseRepo<MediaItem>(fileStoragePath = "${appContext.filesDir.path}/$PLAYLIST_FILE_NAME") {

    override val jsonAdapter: JsonAdapter<List<MediaItem>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, MediaItem::class.java)
    )

    private val TAG = "MEDIA_REPO"

    var broadcastList = mutableListOf<MediaItem>()
    var scheduledList = LinkedHashMap<String, List<MediaItem>>()
    var timeDividers = LinkedHashMap<String, List<MediaItem>>()

    private val downloadHelper = DownloadHelper2.getInstance()
    val numberFileDownloadAtOnce = 2

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
            Log.e(TAG, "updateMonthlyScenario")
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
                Log.e(TAG, "có lịch trong ngày: update")
                updateScenario(dayScenario.sourceUrl)
            } else {
                //In case there are no scenario for today, don't play anythings
                saveItemsToFile(listOf())
                Log.e(TAG, "Không có lịch trong ngày")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Cannot update daily scenario ${e.message}")
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
                Log.d(TAG, "No monthly scenario data")
                emptyList()
            } else {
                val todayScenario = monthlyDataList.firstOrNull { it.date == currentDate }
                when {
                    todayScenario == null -> {
                        Log.d(TAG, "No schedule for today")
                        emptyList()
                    }

                    isFileExisted(mediaStoragePath, PLAYLIST_FILE_NAME) -> {
                        Log.d(TAG, "Reading playlist from local file")
                        val localList = readItemsFromFile()
                        localList.ifEmpty {
                            Log.d(TAG, "Local file is empty, fallback to network")
                            updateScenario(todayScenario.sourceUrl)
                        }
                    }

                    else -> {
                        Log.d(TAG, "No local file, fallback to network")
                        updateScenario(todayScenario.sourceUrl)
                    }
                }
            }

            broadcastList.clear()
            broadcastList.addAll(res)
            Log.d(TAG, "local ${res.size}")
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


//    fun startDownloadMedia() {
//        if (broadcastList.isEmpty()) return
//
//        broadcastList.forEach {
//            downloadAMediaItem(it)
//        }
//    }
//
//    private fun getConfigExtension(): String? {
//        return ConfigUtil.getString(appContext, ConfigUtil.KEY_EXTEND_FILE, "mp3#aac#wav#wma#ac3")
//    }
//
//    fun downloadAMediaItem(it: MediaItem) {
//        //1 - bản tin ký tự
//        //2 - bản tin hình ảnh
//        //3 - bản tin âm thanh
//        //4 - Bản tin video
//        //5 - Bản tin trực tiếp
//        //6 - Bản tin FM
//        //7 - Bản tin ghi âm trực tiếp
//        //không phải files thì không tải, chỉ tải type 3 , type 4
//        if (it.contentType?.value != "4" && it.contentType?.value != "3") return
//
//        //xác định có là file hay không, lấy đuôi file
//        val extMedia = File(it.body).extension
//
//        if (extMedia.isEmpty()) return
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
//
//    /


    private val allowedExt by lazy {
        setOf(
            "mp3",
            "aac",
            "wav",
            "wma",
            "ac3",
        )
    }

    private fun isAllowedExtension(ext: String) =
        allowedExt.any { it.equals(ext, ignoreCase = true) }


    suspend fun startDownloadMedia() {
        Log.d(TAG, "startDownloadMedia: ${broadcastList.size} items")
        downloadAllAndSaveOnce(
            broadcastList = broadcastList,
            mediaStoragePath = mediaStoragePath,
            isAllowedExtension = ::isAllowedExtension,
            saveItemsToFile = { list -> saveItemsToFile(list) },
            parallelism = numberFileDownloadAtOnce
        )
    }

    suspend fun downloadAllAndSaveOnce(
        broadcastList: MutableList<MediaItem>,
        mediaStoragePath: String,
        isAllowedExtension: (String) -> Boolean,
        saveItemsToFile: suspend (List<MediaItem>) -> Unit,
        parallelism: Int = numberFileDownloadAtOnce
    ) = withContext(Dispatchers.IO) {
        val indices = broadcastList.indices.filter { broadcastList[it].shouldDownload() }

        if (indices.isEmpty()) return@withContext

        //Group by filename để tránh download trùng
        val indicesByFilename = indices.groupBy { idx ->
            val url = broadcastList[idx].body ?: ""
            URLUtil.guessFileName(url, null, null)
        }
        // Log warning nếu có duplicate
        indicesByFilename.forEach { (filename, idxList) ->
            if (idxList.size > 1) {
                Log.w(TAG, "Duplicate filename detected: $filename (${idxList.size} items)")
            }
        }

        val sem = kotlinx.coroutines.sync.Semaphore(parallelism)

        // Chỉ download MỖI FILE MỘT LẦN, sau đó apply cho tất cả items trùng
        val resultsByFilename = indicesByFilename.map { (filename, idxList) ->
            async {
                sem.withPermit {
                    // Lấy item đầu tiên để download
                    val firstIdx = idxList.first()
                    val item = broadcastList[firstIdx]

                    val updated = downloadAndTransform(
                        item = item,
                        mediaStoragePath = mediaStoragePath,
                        isAllowedExt = isAllowedExtension,
                    )
                    // Trả về tất cả indices với cùng kết quả
                    idxList.map { it to updated }
                }
            }
        }.awaitAll().flatten()

        // Ghi lại tuần tự để tránh race condition
        try {
            for ((idx, updated) in resultsByFilename) {
                broadcastList[idx] = updated
            }
        } catch (e: Exception) {
            Log.d(TAG, "downloadAllAndSaveOnce: ${e.message}")
        }

        // Chỉ save 1 lần sau khi xong hết
        saveItemsToFile(broadcastList)

    }

    private suspend fun downloadAndTransform(
        item: MediaItem,
        mediaStoragePath: String,
        isAllowedExt: (String) -> Boolean,
    ): MediaItem {
        val url = item.body ?: return item
        if (!URLUtil.isNetworkUrl(url)) return item

        val fileName = URLUtil.guessFileName(url, null, null)
        val localPath = File(mediaStoragePath, fileName).path

        // Lọc extension
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext.isBlank() || !isAllowedExt(ext)) return item

        val originalBody = item.body // giữ URL gốc để set pathBackup
        val file = File(localPath)

        // 2. Kiểm tra file tồn tại + kích thước hợp lệ
        if (file.exists() && file.length() > 100) {
            val newItem = item.copy(body = localPath)
            if (!originalBody.isNullOrBlank()) newItem.pathBackup =
                originalBody // setter chỉ nhận http
            return newItem
        }
        // 3. Xóa file rác/corrupt
        if (file.exists()) {
            try {
                file.delete()
            } catch (_: Throwable) {
            }
        }

        // 4. Tải file
        val ok = prDownloadOnce(url, mediaStoragePath, fileName)

        // 5. Đợi một chút để file được flush (workaround PRDownloader timing issue)
        if (ok) {
            delay(200) // 100ms buffer
        }
        // 6. Verify file sau khi tải
        val isValid = file.exists() && file.length() > 100

        return if (ok && isValid) {
            val newItem = item.copy(body = localPath)
            if (!originalBody.isNullOrBlank()) newItem.pathBackup = originalBody
            newItem
        } else {
            item // giữ item gốc khi lỗi
        }
    }

    private suspend fun prDownloadOnce(
        url: String, dirPath: String, fileName: String
    ): Boolean = suspendCancellableCoroutine { cont ->
        val request = PRDownloader.download(url, dirPath, fileName).build()
        val targetFile = File(dirPath, fileName)
        Log.e(TAG, "start download $url")
        // Bắt đầu tải và lấy downloadId để có thể cancel
        val downloadId = request.start(object : OnDownloadListener {
            override fun onDownloadComplete() {
                Log.e(TAG, "download $url success")
                if (cont.isActive) cont.resume(true)
            }

            override fun onError(error: Error?) {
                Log.e(
                    TAG,
                    "download error $url: ${error?.connectionException?.message ?: error?.serverErrorMessage ?: "unknown"}"
                )

                // Chỉ xóa file nếu là file của download này (check bằng timestamp gần đây)
                try {
                    if (targetFile.exists() &&
                        System.currentTimeMillis() - targetFile.lastModified() < 5000
                    ) {
                        targetFile.delete()
                        Log.e(TAG, "deleted corrupted file: ${targetFile.absolutePath}")
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "failed to delete file: ${e.message}")
                }

                if (cont.isActive) cont.resume(false)
            }
        })

        // Cho phép huỷ khi coroutine bị cancel
        cont.invokeOnCancellation {
            try {
                Log.e(TAG, "start download $url cancel")
                PRDownloader.cancel(downloadId)
                // Xóa file nếu bị cancel
                if (targetFile.exists()) {
                    targetFile.delete()
                }
            } catch (_: Throwable) {
            }
        }
    }


    suspend fun checkPlaylistBroken() {
        checkPlaylist(broadcastList, { list -> saveItemsToFile(list) })
    }

    suspend fun checkPlaylist(
        mediaList: MutableList<MediaItem>,
        saveItemsToFile: suspend (List<MediaItem>) -> Unit,
    ) {
        val needDownload = mediaList.filter { item ->
            (shouldRedownload(item)) && (item.body != "start" && item.body != "end") && (item.shouldDownload())
        }.toMutableList()
        Log.d(TAG, "checkPlaylist media broken: ${needDownload.size}")
        if (needDownload.isNotEmpty()) {
            needDownload.forEach {
                it.body = it.pathBackup
            }
            downloadAllAndSaveOnce(
                broadcastList = mediaList,
                mediaStoragePath = mediaStoragePath,
                isAllowedExtension = ::isAllowedExtension,
                saveItemsToFile = saveItemsToFile, // ghi JSON 1 lần
                parallelism = numberFileDownloadAtOnce // tùy máy/IO
            )
        }
    }

    private fun shouldRedownload(item: MediaItem): Boolean {
        val body = item.body.orEmpty()
        val isUrl = URLUtil.isNetworkUrl(body)

        //body là URL -> cần tải file về (trường hợp file local chưa có)
        if (isUrl) return true
        //body là local path nhưng file đã mất -> nếu có pathBackup là URL thì tải lại
        val missingLocal = body.isNotBlank() && !File(body).exists()
        if (missingLocal) {
            item.body = item.pathBackup
            return true
        }

        return false
    }

    fun stopDownloadMedia() {
        try {
            Log.e(TAG, "Stop all downloads manually")
            PRDownloader.cancelAll()
        } catch (e: Exception) {
            Log.e(TAG, "Error while stopping downloads: ${e.message}")
        }
    }
}