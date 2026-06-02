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
import app.tek4tv.ipradio.utils.ConfigUtil.MONTHLY_SCENARIO_URL
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
import java.util.Date
import java.util.Locale
import java.util.UUID
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

    val numberFileDownloadAtOnce = 2

    private val mediaStoragePath = appContext.filesDir.path


    private val scheduleMonthlyAdapter: JsonAdapter<List<ScheduleDay>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, ScheduleDay::class.java)
    )

    suspend fun safeUpdateMonthlyScenario(
        url: String,
        retryCount: Int = 15,
        delaySeconds: Long = 10L // Giá trị x(s) cấu hình mặc định là 10 giây
    ): Boolean {
        val baseDelayMillis = delaySeconds * 1000L
        val maxDelayMillis = 30 * 1000L // Giới hạn tối đa 1 phút (60,000ms)

        for (attempt in 0 until retryCount) {
            try {
                val isSuccess = updateMonthlyScenario(url)
                if (isSuccess) return true

                Log.e(TAG, "Tải lịch thất bại (lần ${attempt + 1})")
                if (attempt < retryCount - 1) {
                    // Tính toán thời gian delay dựa trên số lần thử (attempt chạy từ 0 đến 14)
                    val currentDelay = if (attempt < 10) {
                        baseDelayMillis // 10 lần đầu: delay cố định x(s)
                    } else {
                        maxDelayMillis // Từ lần thứ 11 trở đi (attempt >= 10): delay 1 phút
                    }

                    // Thêm một chút jitter ngẫu nhiên (0 - 1s) để tránh trùng lặp request
                    val jitter = (Math.random() * 1000).toLong()
                    val finalDelay = currentDelay + jitter

                    Log.d(TAG, "Chờ ${finalDelay / 1000f} giây trước khi thử lại...")
                    delay(finalDelay)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Lỗi hệ thống khi tải lịch (lần ${attempt + 1}): ${e.message}")
                if (attempt < retryCount - 1) {
                    val currentDelay = if (attempt < 10) baseDelayMillis else maxDelayMillis
                    val jitter = (Math.random() * 1000).toLong()
                    delay(currentDelay + jitter)
                }
            }
        }
        return false
    }

    private suspend fun updateMonthlyScenario(url: String): Boolean {
        try {
            Log.e(TAG, "updateMonthlyScenario")
            val fetchResult = playlistService.getMonthlyPlaylist(url)

            if (!fetchResult.isSuccessful) return false

            val monthScenario = fetchResult.body() ?: return false

            ConfigUtil.putString(
                appContext, KEY_MODIFY_TIME_PLAYLIST, monthScenario.first().ppdatedAt
            )
            //4.1.6e luu du lieu cua lich thang
            val monthlyScenarioData = scheduleMonthlyAdapter.toJson(monthScenario)

            ConfigUtil.putString(
                appContext, MONTHLY_SCENARIO_DATA, monthlyScenarioData
            )

            val currentDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

            val dayScenario = monthScenario.firstOrNull { it.date == currentDate }

            if (dayScenario != null) {
                Log.e(TAG, "có lịch trong ngày: update")
                updateScenario(dayScenario.sourceUrl)
            } else {
                //In case there are no scenario for today, don't play anythings
                saveItemsToFile(listOf())
                Log.e(TAG, "Không có lịch trong ngày")
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Cannot update daily scenario ${e.message}")
            return false
        }
    }

    private suspend fun updateScenario(scheduleDayUrl: String): List<MediaItem> {
        Log.e(TAG, "updateScenario: start")
        var result = listOf<MediaItem>()

        try {
            val res = playlistService.getPlaylist(scheduleDayUrl)
            Log.e(TAG, "updateScenario: ${res.body()}")
            if (res.isSuccessful) {
                val resBody = res.body()
                if (resBody != null) {
                    result = resBody

                    saveItemsToFile(result)

                    val currentDate =
                        SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
                    ConfigUtil.putString(appContext, ConfigUtil.PLAYLIST_LOCAL_DATE, currentDate)
                }
            } else {
                Log.e(TAG, "updateScenario: Lịch rỗng rồi")
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateScenario error ${e.toString()}")
        }

        return result
    }

    suspend fun getScenario(): List<MediaItem> {
        scheduledList.clear()
        timeDividers.clear()

        val currentDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

        var jsonMonthlyListData = ConfigUtil.getString(appContext, MONTHLY_SCENARIO_DATA, "") ?: ""

        var monthlyDataList = if (jsonMonthlyListData.isNotEmpty()) {
            scheduleMonthlyAdapter.fromJson(jsonMonthlyListData) ?: emptyList()
        } else {
            emptyList()
        }

        // Tự động khôi phục / cập nhật nếu lịch tháng chưa có lịch cho hôm nay
        val hasToday = monthlyDataList.any { it.date == currentDate }
        if (!hasToday) {
            val monthlyUrl = ConfigUtil.getString(appContext, MONTHLY_SCENARIO_URL, "") ?: ""
            if (monthlyUrl.isNotEmpty()) {
                Log.d(
                    TAG, "No schedule for today found in cache. Fetching latest monthly scenario..."
                )
                try {
                    updateMonthlyScenario(monthlyUrl)
                    // Update lại dữ liệu cache sau khi tải mới
                    jsonMonthlyListData =
                        ConfigUtil.getString(appContext, MONTHLY_SCENARIO_DATA, "") ?: ""
                    if (jsonMonthlyListData.isNotEmpty()) {
                        monthlyDataList =
                            scheduleMonthlyAdapter.fromJson(jsonMonthlyListData) ?: emptyList()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching monthly scenario for recovery: ${e.message}")
                }
            }
        }

        val res = if (monthlyDataList.isEmpty()) {
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
                    val savedDate =
                        ConfigUtil.getString(appContext, ConfigUtil.PLAYLIST_LOCAL_DATE, "")
                    if (savedDate == currentDate) {
                        Log.d(TAG, "Reading playlist from local file (Date match)")
                        val localList = readItemsFromFile()
                        localList.ifEmpty {
                            Log.d(TAG, "Local file is empty, fallback to network")
                            updateScenario(todayScenario.sourceUrl)
                        }
                    } else {
                        Log.d(
                            TAG,
                            "Local file date mismatch ($savedDate != $currentDate), fallback to network"
                        )
                        Log.d(TAG, "Update from url: ${todayScenario.sourceUrl}")
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


    suspend fun clearPlaylist() {
        saveItemsToFile(listOf())
    }

    fun clearMonthlyLocalData() {
        ConfigUtil.removeSharef(appContext, MONTHLY_SCENARIO_DATA)
    }


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

        //Group by URL để tránh download trùng và tránh đè file khác URL nhưng trùng tên
        val indicesByUrl = indices.groupBy { idx ->
            broadcastList[idx].body ?: UUID.randomUUID().toString()
        }
        // Log warning nếu có duplicate
        indicesByUrl.forEach { (url, idxList) ->
            if (idxList.size > 1) {
                Log.w(TAG, "Duplicate URL detected: $url (${idxList.size} items)")
            }
        }

        val sem = kotlinx.coroutines.sync.Semaphore(parallelism)

        // Chỉ download MỖI URL MỘT LẦN, sau đó apply cho tất cả items trùng
        val resultsByUrl = indicesByUrl.map { (url, idxList) ->
            async {
                sem.withPermit {
                    // Lấy item đầu tiên để download
                    val firstIdx = idxList.first()
                    val item = broadcastList[firstIdx]

                    val guessedName = URLUtil.guessFileName(url, null, null)
                    val safeFileName = "${url.md5()}_$guessedName"

                    val updated = downloadAndTransform(
                        item = item,
                        mediaStoragePath = mediaStoragePath,
                        isAllowedExt = isAllowedExtension,
                        safeFileName = safeFileName
                    )
                    // Trả về tất cả indices với cùng kết quả
                    idxList.map { it to updated }
                }
            }
        }.awaitAll().flatten()

        // Ghi lại tuần tự để tránh race condition
        try {
            for ((idx, updated) in resultsByUrl) {
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
        safeFileName: String
    ): MediaItem {
        val url = item.body ?: return item
        if (!URLUtil.isNetworkUrl(url)) return item

        val localPath = File(mediaStoragePath, safeFileName).path

        // Lọc extension
        val ext = safeFileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
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
        val ok = prDownloadOnce(url, mediaStoragePath, safeFileName)

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
                    if (targetFile.exists() && System.currentTimeMillis() - targetFile.lastModified() < 5000) {
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

    private fun String.md5(): String {
        val md = java.security.MessageDigest.getInstance("MD5")
        val digested = md.digest(toByteArray())
        return digested.joinToString("") { "%02x".format(it) }
    }
}