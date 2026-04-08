package app.tek4tv.ipradio.repo

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import app.tek4tv.ipradio.model.MediaFile
import app.tek4tv.ipradio.model.MediaInfo
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.Utils.Companion.convertSecondsToCalendar
import app.tek4tv.ipradio.utils.convertCalendarToString
import app.tek4tv.ipradio.utils.decodeRepeatDays
import app.tek4tv.ipradio.utils.isTodayAllowed
import app.tek4tv.ipradio.utils.media.DownloadHelper2
import app.tek4tv.ipradio.utils.media.DownloadItem
import app.tek4tv.ipradio.utils.repeatDaysToText
import app.tek4tv.ipradio.utils.storage.getFileNameFromUrl
import app.tek4tv.ipradio.utils.unixSecondsToCalendar
import com.downloader.OnDownloadListener
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepo @Inject constructor(
    @ApplicationContext private val appContext: Context
) {

    val TAG = "MEDIA_REPO"


    private val downloadHelper = DownloadHelper2.getInstance()

    //MGW
    //var scheduledList = LinkedHashMap<String, List<MediaFile>>()
    var scheduledList = LinkedHashMap<String, MediaInfo>()
    var broadcastList = mutableListOf<MediaFile>()
    var listMediaInfoId = mutableListOf<Long>()
    private val mediaStoragePath = appContext.filesDir.path

    fun updateScheduler(mediaInfo: MediaInfo) {
        try {
            val mid = mediaInfo.mid ?: 0
            val now = Calendar.getInstance()
            val repeat = mediaInfo.repeat ?: 0
            val days = mediaInfo.days ?: 0
            val start = mediaInfo.start ?: 0L
            val expired = mediaInfo.expired ?: 0L
            val mode = mediaInfo.mode
            listMediaInfoId.add(mid)
            //Chỉ lưu không phát
            if (mode == 0) {
                Log.d(
                    "MediaInfo", "Mode: Chỉ lưu - Không phát"
                )
            }
            //Lưu và phát
            else {
                Log.d(
                    "MediaInfo",
                    "Repeat Type: Scheduler ${if (mediaInfo.repeat == 0) "tuần" else "tháng"}"
                )
                Log.d(
                    "MediaInfo", "Name: ${mediaInfo.summary} - ${mediaInfo.mid}"
                )
                val r1 = decodeRepeatDays(repeat, days)
                val res = repeatDaysToText(r1)
                Log.d("MediaInfo", "Chạy vào: $res")
                val isAllowed = isTodayAllowed(now, r1)
                Log.d("MediaInfo", "Hôm nay có lịch: $isAllowed")
                if (isAllowed) {
                    val startTime = unixSecondsToCalendar(start)
                    val expiredTime = unixSecondsToCalendar(expired)
                    val timeSlot = mediaInfo.ts
                    val durationSlot = mediaInfo.ds
                    Log.d(
                        "MediaInfo", "Thời gian phát: ${convertCalendarToString(startTime)}"
                    )
                    Log.d(
                        "MediaInfo", "Thời gian kết thúc: ${
                            convertCalendarToString(
                                expiredTime
                            )
                        }"
                    )
                    timeSlot!!.forEachIndexed { index, ts ->
                        if (durationSlot == null || index >= durationSlot.size) return@forEachIndexed
                        val slotStart = convertSecondsToCalendar(ts.toInt())
                        val slotEnd =
                            convertSecondsToCalendar(ts.toInt() + durationSlot[index].toInt())
                        val peStart = convertCalendarToString(slotStart, "HH:mm:ss")
                        val peEnd = convertCalendarToString(slotEnd, "HH:mm:ss")
                        val period = "$peStart-$peEnd"
                        Log.d(
                            "MediaInfo", "Khung phát: $period"
                        )
                        scheduledList[period] = mediaInfo
                    }

                }
            }
            broadcastList.addAll(mediaInfo.files)

        } catch (e: Exception) {
            Log.e(TAG, "Cannot update daily scenario ${e.message}")
        }
    }


    // Trong MediaRepo.kt
    fun replaceAllSchedules(newList: List<MediaInfo>) {
        val tempMap = LinkedHashMap<String, MediaInfo>()
        val now = Calendar.getInstance()

        newList.forEach { mediaInfo ->
            if (mediaInfo.mode != 0) { // Chỉ xử lý loại có lịch phát
                val r1 = decodeRepeatDays(mediaInfo.repeat ?: 0, mediaInfo.days ?: 0)
                if (isTodayAllowed(now, r1)) {
                    val timeSlot = mediaInfo.ts
                    val durationSlot = mediaInfo.ds
                    timeSlot?.forEachIndexed { index, ts ->
                        if (durationSlot != null && index < durationSlot.size) {
                            val slotStart = convertSecondsToCalendar(ts.toInt())
                            val slotEnd =
                                convertSecondsToCalendar(ts.toInt() + durationSlot[index].toInt())
                            val period = "${
                                convertCalendarToString(
                                    slotStart,
                                    "HH:mm:ss"
                                )
                            }-${convertCalendarToString(slotEnd, "HH:mm:ss")}"
                            tempMap[period] = mediaInfo
                        }
                    }
                }
            }
        }

        // Ghi đè an toàn bằng synchronized
        synchronized(scheduledList) {
            scheduledList.clear()
            scheduledList.putAll(tempMap)
        }
        Log.d(TAG, "Đã cập nhật nguyên khối danh sách phát mới (Size: ${tempMap.size})")
    }

    fun syncListMediaInfoId(mid: Long) {
        listMediaInfoId.remove(mid)
    }

    fun clearAllScheduled() {
        scheduledList.clear()
        broadcastList.clear()
        listMediaInfoId.clear()
        Log.d(TAG, "Đã làm sạch toàn bộ lịch phát trong bộ nhớ")
    }

    private fun getConfigExtension(): String? {
        return ConfigUtil.getString(
            appContext, ConfigUtil.KEY_EXTEND_FILE, "mp3#aac#wav#wma#ac3#wav"
        )

    }


    fun startDownloadMedia() {
        if (broadcastList.isEmpty()) return
        broadcastList.forEach {
            downloadAMediaItem(it)
        }
    }

    fun downloadAMediaItem(it: MediaFile) {
//        0 -> MediaFileType.SILENT
//        1 -> MediaFileType.FILE
//        2 -> MediaFileType.FM
//        3 -> MediaFileType.LIVE
//        Log.d("startDownloadMedia", "type : ${it.type}")
//        if (it.body == "start" || it.body == "end") return
        if (it.type != 1) return
        val url = it.url
        if (url.isNullOrBlank()) return
        if (!url.startsWith("http")) return
        //xác định có là file hay không, lấy đuôi file
        val extMedia =
            url.toUri().lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()

        if (extMedia.isEmpty()) return
        val extensions = getConfigExtension().orEmpty()
        val isFile = extensions.contains(extMedia)

        if (!isFile) return

        val fileName = getFileNameFromUrl(url)
        if (fileName.isBlank()) return
        val localFile = File(appContext.filesDir.path, fileName)
        if (localFile.exists()) {
//            Log.d("download item", "Item existed")
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
                    it.pathBackup = url
                    it.url = "$mediaStoragePath/$fileName"

                    Log.d("downloadcomplete", url)
                }

                override fun onError(error: com.downloader.Error?) {
                    Log.d("downloaderror", url)
                }

            }
        })
    }

}


