package app.tek4tv.ipradio.utils.logger

import android.os.Build
import android.util.Log
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.model.LogDeviceModel
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.utils.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * DeviceLogHelper: Ghi nhận các nhóm lỗi vận hành (Player, Media, Lịch phát, Crash, Bộ nhớ, UART)
 * và log lịch phát (CAMPAIN_START / CAMPAIN_END) xuống file JSONL với cơ chế Cooldown chống spam.
 */
object DeviceLogHelper {
    private const val TAG = "DeviceLogHelper"
    private const val FROM_SERVER = "app"
    private const val PLATFORM = "api"

    // Nhóm đối tượng log (ObjectName)
    const val OBJ_MEDIA_FILE = "error_media_file"
    const val OBJ_PLAYER = "error_player"
    const val OBJ_CRASH = "app_crashed"
    const val OBJ_MEMORY = "error_memory"
    const val OBJ_STORAGE = "error_storage"
    const val OBJ_SCHEDULE = "error_schedule"
    const val OBJ_HARDWARE = "error_hardware"

    // Campaign / Schedule log
    const val OBJ_CAMPAIGN = "campain_logger"
    const val CODE_CAMPAIN_START = "CAMPAIN_START"
    const val CODE_CAMPAIN_END = "CAMPAIN_END"

    // Mã lỗi chuẩn (Code)
    const val CODE_MEDIA_EMPTY = "MEDIA_FILE_EMPTY_0_BYTE"
    const val CODE_MEDIA_CORRUPTED = "MEDIA_CONTAINER_CORRUPTED"
    const val CODE_MEDIA_UNSUPPORTED = "MEDIA_FORMAT_UNSUPPORTED"
    const val CODE_LIVE_STREAM_ERROR = "VLC_LIVE_STREAM_ERROR"
    const val CODE_MEDIA_SKIPPED = "MEDIA_SKIPPED_DUE_TO_ERROR"
    const val CODE_PLAYLIST_ALL_ITEMS_FAILED = "PLAYLIST_ALL_ITEMS_FAILED"
    const val CODE_CRASH_UNCAUGHT = "APP_CRASH_UNCAUGHT"
    const val CODE_LOW_MEMORY_CRITICAL = "SYSTEM_LOW_MEMORY_CRITICAL"
    const val CODE_STORAGE_CRITICAL = "STORAGE_SPACE_CRITICAL_BELOW_500MB"
    const val CODE_SCHEDULE_EMPTY = "SCHEDULE_PAYLOAD_EMPTY"
    const val CODE_SCHEDULE_PARSE_ERROR = "SCHEDULE_JSON_PARSE_ERROR"
    const val CODE_PLAYLIST_CORRUPTED = "PLAYLIST_FILE_CORRUPTED"
    const val CODE_MEDIA_DOWNLOAD_FAILED = "MEDIA_DOWNLOAD_FAILED"
    const val CODE_UART_ERROR = "UART_SERIAL_ERROR"

    private val cooldownMap = ConcurrentHashMap<String, Long>()
    private val campaignLogScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val campaignTimeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var testDeviceId: String? = null

    fun setDeviceIdForTest(id: String?) {
        testDeviceId = id
    }

    fun clearCooldownsForTest() {
        cooldownMap.clear()
    }

    internal fun isCooldownActive(
        key: String,
        cooldownDurationMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val lastTime = cooldownMap[key]
        if (lastTime != null && (nowMs - lastTime) < cooldownDurationMs) {
            return true
        }
        cooldownMap[key] = nowMs
        return false
    }

    private fun getDeviceId(): String {
        testDeviceId?.let { return it }
        return try {
            val ctx = MyApp.instance?.applicationContext
            if (ctx != null) Utils.getDeviceId(ctx) else (Build.SERIAL ?: "UNKNOWN_DEVICE")
        } catch (_: Exception) {
            Build.SERIAL ?: "UNKNOWN_DEVICE"
        }
    }

    private fun escapeJson(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
    }

    private fun writeLog(
        objectName: String,
        code: String,
        message: String,
        metadata: String = "",
        actionName: String = ""
    ) {
        try {
            val id = getDeviceId()
            val model = LogDeviceModel(
                serviceName = id,
                userName = id,
                actionName = actionName,
                objectName = objectName,
                metadata = metadata,
                message = message,
                fromServer = FROM_SERVER,
                platform = PLATFORM,
                code = code
            )
            LogFileUtils.writeLogDeviceErrorDirect(model, objectName)
        } catch (e: Exception) {
            try {
                Log.e(TAG, "writeLog error: ${e.message}", e)
            } catch (_: Throwable) {
            }
        }
    }

    // ── Nhóm 1: Lỗi Media & Player ──

    fun logMediaFileCorrupted(
        mediaItem: MediaItem?,
        filePath: String,
        fileSize: Long,
        reason: String
    ) {
        if (isCooldownActive("MEDIA_FILE_$filePath", 60_000L)) return
        val code = if (fileSize == 0L) CODE_MEDIA_EMPTY else CODE_MEDIA_CORRUPTED
        val meta =
            "{\"filePath\":\"${escapeJson(filePath)}\",\"fileSize\":$fileSize,\"reason\":\"${escapeJson(reason)}\",\"mediaUrl\":\"${escapeJson(mediaItem?.pathBackup ?: mediaItem?.body ?: "")}\",\"title\":\"${escapeJson(mediaItem?.title ?: "")}\"}"
        writeLog(
            objectName = OBJ_MEDIA_FILE,
            code = code,
            message = "Media file error ($fileSize bytes): $reason",
            metadata = meta
        )
    }

    fun logLiveStreamError(mediaItem: MediaItem?, reason: String, retryCount: Int = 0) {
        val url = mediaItem?.pathBackup?.ifBlank { mediaItem.body ?: "" } ?: ""
        if (isCooldownActive("LIVE_ERROR_$url", 60_000L)) return
        val meta =
            "{\"retryCount\":$retryCount,\"mediaUrl\":\"${escapeJson(url)}\",\"title\":\"${escapeJson(mediaItem?.title ?: "")}\"}"
        writeLog(
            objectName = OBJ_PLAYER,
            code = CODE_LIVE_STREAM_ERROR,
            message = "Lỗi luồng Live (${mediaItem?.title ?: url}): $reason",
            metadata = meta
        )
    }

    fun logMediaSkippedDueToError(mediaItem: MediaItem?, failCount: Int, reason: String) {
        val key = mediaItem?.pathBackup?.ifBlank { mediaItem.body ?: "unknown" } ?: "unknown"
        if (isCooldownActive("MEDIA_SKIPPED_$key", 60_000L)) return
        val meta = "{\"failCount\":$failCount,\"url\":\"${escapeJson(key)}\",\"reason\":\"${escapeJson(reason)}\"}"
        writeLog(
            objectName = OBJ_PLAYER,
            code = CODE_MEDIA_SKIPPED,
            message = "Bỏ qua media do lỗi ($failCount lần): $key ($reason)",
            metadata = meta
        )
    }

    fun logPlaylistAllItemsFailed(playMode: String, reason: String, itemCount: Int) {
        if (isCooldownActive("PLAYLIST_ALL_FAILED_$playMode", 60_000L)) return
        val meta = "{\"playMode\":\"${escapeJson(playMode)}\",\"reason\":\"${escapeJson(reason)}\",\"itemCount\":$itemCount}"
        writeLog(
            objectName = OBJ_SCHEDULE,
            code = CODE_PLAYLIST_ALL_ITEMS_FAILED,
            message = "Toàn bộ $itemCount items trong khung $playMode bị lỗi: $reason",
            metadata = meta
        )
    }

    // ── Nhóm 2: Lỗi Kịch bản & Tải Media ──

    fun logScheduleError(source: String, code: String, message: String, metadata: String = "") {
        if (isCooldownActive("SCHEDULE_${code}_$source", 60_000L)) return
        val meta = if (metadata.isNotBlank()) metadata else "{\"source\":\"${escapeJson(source)}\"}"
        writeLog(
            objectName = OBJ_SCHEDULE,
            code = code,
            message = message,
            metadata = meta
        )
    }

    fun logPlaylistCorrupted(fileName: String, reason: String) {
        if (isCooldownActive("PLAYLIST_CORRUPTED_$fileName", 60_000L)) return
        writeLog(
            objectName = OBJ_SCHEDULE,
            code = CODE_PLAYLIST_CORRUPTED,
            message = "File kịch bản $fileName bị hỏng: $reason",
            metadata = "{\"fileName\":\"${escapeJson(fileName)}\",\"reason\":\"${escapeJson(reason)}\"}"
        )
    }

    fun logMediaDownloadFailed(url: String, title: String, reason: String) {
        if (isCooldownActive("DOWNLOAD_FAIL_$url", 60_000L)) return
        val meta = "{\"url\":\"${escapeJson(url)}\",\"title\":\"${escapeJson(title)}\",\"reason\":\"${escapeJson(reason)}\"}"
        writeLog(
            objectName = OBJ_MEDIA_FILE,
            code = CODE_MEDIA_DOWNLOAD_FAILED,
            message = "Tải file âm thanh thất bại ($title): $reason",
            metadata = meta
        )
    }

    // ── Nhóm 3: Lỗi Hệ thống, Crash & Phần cứng ──

    fun logCrash(thread: Thread, ex: Throwable) {
        val sw = StringWriter()
        ex.printStackTrace(PrintWriter(sw))
        val stacktrace = sw.toString().take(4000)
        val meta = "{\"thread\":\"${escapeJson(thread.name)}\",\"exception\":\"${escapeJson(ex.javaClass.name)}\"}"
        writeLog(
            objectName = OBJ_CRASH,
            code = CODE_CRASH_UNCAUGHT,
            message = "${ex.message ?: ex.javaClass.simpleName}\n$stacktrace",
            metadata = meta
        )
    }

    fun logStorageCritical(freeBytes: Long, totalBytes: Long) {
        if (isCooldownActive("STORAGE_CRITICAL", 30 * 60_000L)) return
        val freeMb = freeBytes / (1024 * 1024)
        val totalMb = totalBytes / (1024 * 1024)
        writeLog(
            objectName = OBJ_STORAGE,
            code = CODE_STORAGE_CRITICAL,
            message = "Dung lượng bộ nhớ trong thấp nghiêm trọng: còn ${freeMb}MB / ${totalMb}MB",
            metadata = "{\"freeMB\":$freeMb,\"totalMB\":$totalMb}"
        )
    }

    fun logLowMemory(availMb: Long, totalMb: Long) {
        if (isCooldownActive("LOW_MEMORY", 15 * 60_000L)) return
        writeLog(
            objectName = OBJ_MEMORY,
            code = CODE_LOW_MEMORY_CRITICAL,
            message = "RAM khả dụng thấp nghiêm trọng: ${availMb}MB / ${totalMb}MB",
            metadata = "{\"availMB\":$availMb,\"totalMB\":$totalMb}"
        )
    }

    fun logUartError(port: String, reason: String) {
        if (isCooldownActive("UART_ERR_$port", 5 * 60_000L)) return
        writeLog(
            objectName = OBJ_HARDWARE,
            code = CODE_UART_ERROR,
            message = "Lỗi giao tiếp UART ($port): $reason",
            metadata = "{\"port\":\"${escapeJson(port)}\",\"reason\":\"${escapeJson(reason)}\"}"
        )
    }

    // ── Nhóm 4: Log Lịch phát (Campaign / Schedule Logger) ──

    internal fun extractMediaFileInfo(mediaItem: MediaItem?): Pair<String, String> {
        if (mediaItem == null) return Pair("", "")
        val url = when {
            !mediaItem.pathBackup.isNullOrBlank() && mediaItem.pathBackup != "start" && mediaItem.pathBackup != "end" ->
                mediaItem.pathBackup
            !mediaItem.body.isNullOrBlank() && mediaItem.body != "start" && mediaItem.body != "end" ->
                mediaItem.body!!
            else -> ""
        }
        val fileName = when {
            !mediaItem.title.isNullOrBlank() -> mediaItem.title!!
            url.isNotBlank() -> url.substringAfterLast('/').substringBefore('?')
            else -> ""
        }
        return Pair(fileName, url)
    }

    fun logCampaignStart(
        listKey: String,
        from: String,
        mediaItem: MediaItem? = null,
        items: List<MediaItem>? = null,
        syncWrite: Boolean = false
    ) {
        val nowMs = System.currentTimeMillis()
        val timeStr = synchronized(campaignTimeFmt) { campaignTimeFmt.format(Date(nowMs)) }
        val (fileName, url) = if (!items.isNullOrEmpty() && items.size > 1) {
            val fNames = items.map { extractMediaFileInfo(it).first }.filter { it.isNotBlank() }.joinToString("; ")
            val urls = items.map { extractMediaFileInfo(it).second }.filter { it.isNotBlank() }.joinToString("; ")
            Pair(fNames, urls)
        } else {
            extractMediaFileInfo(mediaItem ?: items?.firstOrNull())
        }

        val writeBlock = {
            try {
                val id = getDeviceId()
                val message =
                    "{\"listkey\":\"${escapeJson(listKey)}\",\"from\":\"${escapeJson(from)}\",\"timestart\":\"$timeStr\",\"filename\":\"${escapeJson(fileName)}\",\"url\":\"${escapeJson(url)}\"}"
                val model = LogDeviceModel(
                    serviceName = id,
                    userName = id,
                    actionName = "",
                    objectName = OBJ_CAMPAIGN,
                    metadata = "",
                    message = message,
                    fromServer = FROM_SERVER,
                    platform = PLATFORM,
                    code = CODE_CAMPAIN_START
                )
                LogFileUtils.writeCampaignLogDirect(model, nowMs)
            } catch (e: Exception) {
                try {
                    Log.e(TAG, "logCampaignStart error: ${e.message}", e)
                } catch (_: Throwable) {
                }
            }
        }

        if (syncWrite) writeBlock() else campaignLogScope.launch { writeBlock() }
    }

    fun logCampaignEnd(
        lastListKey: String,
        mediaItem: MediaItem? = null,
        syncWrite: Boolean = false
    ) {
        val nowMs = System.currentTimeMillis()
        val timeStr = synchronized(campaignTimeFmt) { campaignTimeFmt.format(Date(nowMs)) }
        val (fileName, url) = extractMediaFileInfo(mediaItem)

        val writeBlock = {
            try {
                val id = getDeviceId()
                val message = if (fileName.isNotBlank() || url.isNotBlank()) {
                    "{\"listkey\":\"${escapeJson(lastListKey)}\",\"timeend\":\"$timeStr\",\"filename\":\"${escapeJson(fileName)}\",\"url\":\"${escapeJson(url)}\"}"
                } else {
                    "{\"listkey\":\"${escapeJson(lastListKey)}\",\"timeend\":\"$timeStr\"}"
                }
                val model = LogDeviceModel(
                    serviceName = id,
                    userName = id,
                    actionName = "",
                    objectName = OBJ_CAMPAIGN,
                    metadata = "",
                    message = message,
                    fromServer = FROM_SERVER,
                    platform = PLATFORM,
                    code = CODE_CAMPAIN_END
                )
                LogFileUtils.writeCampaignLogDirect(model, nowMs)
            } catch (e: Exception) {
                try {
                    Log.e(TAG, "logCampaignEnd error: ${e.message}", e)
                } catch (_: Throwable) {
                }
            }
        }

        if (syncWrite) writeBlock() else campaignLogScope.launch { writeBlock() }
    }
}
