package app.tek4tv.ipradio

import app.tek4tv.ipradio.di.MoshiInst
import app.tek4tv.ipradio.model.LogDeviceModel
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.utils.logger.DeviceLogHelper
import app.tek4tv.ipradio.utils.logger.LogFileUtils
import app.tek4tv.ipradio.utils.logger.LogManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LoggerLogicTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        LogFileUtils.setBaseFilesDirForTest(tempFolder.root)
        LogFileUtils.initFolders()
        DeviceLogHelper.setDeviceIdForTest("AA:BB:CC:DD:EE:FF")
        DeviceLogHelper.clearCooldownsForTest()
    }

    @After
    fun tearDown() {
        LogFileUtils.setBaseFilesDirForTest(null)
        DeviceLogHelper.setDeviceIdForTest(null)
        DeviceLogHelper.clearCooldownsForTest()
    }

    @Test
    fun testInitFoldersCreatesSubdirectories() {
        assertTrue(File(LogFileUtils.deviceErrorLogFolderPath).isDirectory)
        assertTrue(File(LogFileUtils.campaignLogFolderPath).isDirectory)
    }

    @Test
    fun testWriteDeviceErrorLogJsonlFormat() {
        DeviceLogHelper.logMediaFileCorrupted(
            mediaItem = MediaItem(title = "Ban tin sang", body = "https://cdn.example.vn/a.mp3"),
            filePath = "/data/a.mp3",
            fileSize = 0L,
            reason = "Empty 0 byte file"
        )

        val logFile = File(LogFileUtils.getDeviceErrorLogFilePath())
        assertTrue(logFile.exists())
        val lines = logFile.readLines().filter { it.isNotBlank() }
        assertEquals(1, lines.size)

        val parsed = MoshiInst.moshi.adapter(LogDeviceModel::class.java).fromJson(lines[0])
        assertNotNull(parsed)
        assertEquals("AA:BB:CC:DD:EE:FF", parsed!!.serviceName)
        assertEquals(DeviceLogHelper.OBJ_MEDIA_FILE, parsed.objectName)
        assertEquals(DeviceLogHelper.CODE_MEDIA_EMPTY, parsed.code)
        assertTrue(lines[0].contains("\"Timestamp\":\""))
    }

    @Test
    fun testCooldownPreventsDuplicateSpam() {
        assertFalse(DeviceLogHelper.isCooldownActive("KEY_A", 60_000L, nowMs = 1000L))
        assertTrue(DeviceLogHelper.isCooldownActive("KEY_A", 60_000L, nowMs = 30_000L))
        assertFalse(DeviceLogHelper.isCooldownActive("KEY_A", 60_000L, nowMs = 62_000L))
    }

    @Test
    fun testLogRotationWhenExceedingMaxSize() {
        val dateStr = LogFileUtils.formatDateLog()
        val firstFile = File(LogFileUtils.deviceErrorLogFolderPath, "device_errors_${dateStr}.jsonl")
        firstFile.writeBytes(ByteArray(LogFileUtils.MAX_LOG_FILE_SIZE_BYTES.toInt() + 10))

        val nextPath = LogFileUtils.getDeviceErrorLogFilePath()
        assertTrue(nextPath.endsWith("device_errors_${dateStr}_part2.jsonl"))
    }

    @Test
    fun testCampaignStartAndEndLogging() {
        val item = MediaItem(
            title = "Ban tin thoi su",
            body = "/files/thoisu.mp3",
            pathBackup = "https://cdn.example.vn/thoisu.mp3"
        )
        DeviceLogHelper.logCampaignStart(
            listKey = "06:00:00-06:30:00",
            from = "0",
            mediaItem = item,
            items = listOf(item),
            syncWrite = true
        )
        DeviceLogHelper.logCampaignEnd(
            lastListKey = "06:00:00-06:30:00",
            mediaItem = item,
            syncWrite = true
        )

        val campaignFile = File(LogFileUtils.getCampaignLogFilePath())
        assertTrue(campaignFile.exists())
        val lines = campaignFile.readLines().filter { it.isNotBlank() }
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("\"Code\":\"CAMPAIN_START\""))
        assertTrue(lines[0].contains("Ban tin thoi su"))
        assertTrue(lines[0].contains("https://cdn.example.vn/thoisu.mp3"))
        assertTrue(lines[1].contains("\"Code\":\"CAMPAIN_END\""))
    }

    @Test
    fun testCleanupExpiredLogsRemovesFilesOlderThan7Days() {
        val dir = File(LogFileUtils.deviceErrorLogFolderPath)
        val oldFile = File(dir, "device_errors_01-01-2026.jsonl").apply { writeText("old\n") }
        val freshFile = File(dir, "device_errors_25-11-2026.jsonl").apply { writeText("new\n") }

        val now = 1_795_600_000_000L
        oldFile.setLastModified(now - LogManager.MAX_LOG_AGE_MS - 10_000L)
        freshFile.setLastModified(now - 10_000L)

        LogManager.cleanupExpiredLogs(dir, nowMs = now)

        assertFalse(oldFile.exists())
        assertTrue(freshFile.exists())
    }

    @Test
    fun testMultipleErrorLogTypesAppendAndParseCleanly() {
        DeviceLogHelper.logLiveStreamError(
            mediaItem = MediaItem(title = "VOV1 \"Live\"", body = "http://stream.example.vn/vov1"),
            reason = "Timeout\tafter 10s\nretry",
            retryCount = 2
        )
        DeviceLogHelper.logScheduleError(
            source = "MQTT",
            code = DeviceLogHelper.CODE_SCHEDULE_PARSE_ERROR,
            message = "Invalid JSON payload"
        )
        DeviceLogHelper.logPlaylistCorrupted("playlist.json", "Unexpected EOF")
        DeviceLogHelper.logMediaDownloadFailed("https://cdn.example.vn/a.mp3", "Ban tin", "HTTP 404")
        DeviceLogHelper.logCrash(Thread.currentThread(), RuntimeException("Crash test\nline2"))
        DeviceLogHelper.logUartError("/dev/ttyS1", "Permission denied")
        DeviceLogHelper.logStorageCritical(100L * 1024 * 1024, 8L * 1024 * 1024 * 1024)
        DeviceLogHelper.logLowMemory(50L, 1024L)

        val logFile = File(LogFileUtils.getDeviceErrorLogFilePath())
        assertTrue(logFile.exists())
        val lines = logFile.readLines().filter { it.isNotBlank() }
        assertEquals(8, lines.size)

        val adapter = MoshiInst.moshi.adapter(LogDeviceModel::class.java)
        val parsed = lines.map { line ->
            adapter.fromJson(line).also { assertNotNull("Failed to parse JSONL line: $line", it) }!!
        }

        assertEquals(
            listOf(
                DeviceLogHelper.CODE_LIVE_STREAM_ERROR,
                DeviceLogHelper.CODE_SCHEDULE_PARSE_ERROR,
                DeviceLogHelper.CODE_PLAYLIST_CORRUPTED,
                DeviceLogHelper.CODE_MEDIA_DOWNLOAD_FAILED,
                DeviceLogHelper.CODE_CRASH_UNCAUGHT,
                DeviceLogHelper.CODE_UART_ERROR,
                DeviceLogHelper.CODE_STORAGE_CRITICAL,
                DeviceLogHelper.CODE_LOW_MEMORY_CRITICAL
            ),
            parsed.map { it.code }
        )
        assertTrue(parsed[0].message.contains("VOV1 \"Live\""))
        assertTrue(parsed[4].message.contains("Crash test\nline2"))
    }

    @Test
    fun testCampaignMultiItemPlaylistAndInnerJsonFormat() {
        val items = listOf(
            MediaItem(title = "Mo dau", body = "/files/1.mp3", pathBackup = "https://cdn.example.vn/1.mp3"),
            MediaItem(title = "Tin \"Chinh\"", body = "/files/2.mp3", pathBackup = "https://cdn.example.vn/2.mp3")
        )
        DeviceLogHelper.logCampaignStart(
            listKey = "07:00:00-07:30:00",
            from = "1",
            mediaItem = items[0],
            items = items,
            syncWrite = true
        )
        DeviceLogHelper.logCampaignEnd(
            lastListKey = "07:00:00-07:30:00",
            mediaItem = items[1],
            syncWrite = true
        )

        val campaignFile = File(LogFileUtils.getCampaignLogFilePath())
        val lines = campaignFile.readLines().filter { it.isNotBlank() }
        assertEquals(2, lines.size)

        val adapter = MoshiInst.moshi.adapter(LogDeviceModel::class.java)
        val mapAdapter = MoshiInst.moshi.adapter(Map::class.java)
        val startModel = adapter.fromJson(lines[0])!!
        val endModel = adapter.fromJson(lines[1])!!

        assertEquals(DeviceLogHelper.OBJ_CAMPAIGN, startModel.objectName)
        assertEquals(DeviceLogHelper.CODE_CAMPAIN_START, startModel.code)
        val startMsg = mapAdapter.fromJson(startModel.message)!!
        assertEquals("07:00:00-07:30:00", startMsg["listkey"])
        assertEquals("1", startMsg["from"])
        assertEquals("Mo dau; Tin \"Chinh\"", startMsg["filename"])
        assertEquals("https://cdn.example.vn/1.mp3; https://cdn.example.vn/2.mp3", startMsg["url"])

        assertEquals(DeviceLogHelper.CODE_CAMPAIN_END, endModel.code)
        val endMsg = mapAdapter.fromJson(endModel.message)!!
        assertEquals("07:00:00-07:30:00", endMsg["listkey"])
        assertNotNull(endMsg["timeend"])
        assertEquals("Tin \"Chinh\"", endMsg["filename"])
    }
}
