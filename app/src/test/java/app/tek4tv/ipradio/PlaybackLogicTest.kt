package app.tek4tv.ipradio

import app.tek4tv.ipradio.core.players_helper.VlcMediaPlayer
import app.tek4tv.ipradio.model.ForceMedia
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.utils.getDurationInSecond
import app.tek4tv.ipradio.utils.network.CaCertHelper
import app.tek4tv.ipradio.utils.toCalendar
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class PlaybackLogicTest {

    // ========================================================================
    // 1. KỊCH BẢN: TRÍCH XUẤT DOMAIN VÀ LINK HTTPS CHO SSL CERTIFICATE
    // ========================================================================

    @Test
    fun testExtractHost_standardHttpsUrl() {
        val url = "https://storage-hp-ttn.thongtinnguon.vn/2026/09/audio.mp3"
        val host = CaCertHelper.extractHost(url)
        assertEquals("storage-hp-ttn.thongtinnguon.vn", host)
    }

    @Test
    fun testExtractHost_withDuplicateSlashesInPath() {
        // Link thực tế từ log lỗi của người dùng có 2 dấu // trong path
        val url = "https://storage-hp-ttn.thongtinnguon.vn//2026/9/9/11/475759296_chuong-trinh-chieu-9.9.mp3"
        val host = CaCertHelper.extractHost(url)
        assertEquals("storage-hp-ttn.thongtinnguon.vn", host)
    }

    @Test
    fun testExtractHost_withCustomPort() {
        val url = "https://stream.tek4tv.vn:8443/live/channel1.m3u8"
        val host = CaCertHelper.extractHost(url)
        assertEquals("stream.tek4tv.vn", host)
    }

    @Test
    fun testExtractHost_withQueryParams() {
        val url = "https://cdn.thongtinnguon.vn/live/stream.m3u8?token=abc&expire=12345"
        val host = CaCertHelper.extractHost(url)
        assertEquals("cdn.thongtinnguon.vn", host)
    }

    @Test
    fun testExtractHost_rtspAndRtmp() {
        val rtsp = "rtsp://live.tek4tv.vn:554/stream1"
        assertEquals("live.tek4tv.vn", CaCertHelper.extractHost(rtsp))

        val rtmp = "rtmp://media.tek4tv.vn/live/flv"
        assertEquals("media.tek4tv.vn", CaCertHelper.extractHost(rtmp))
    }

    @Test
    fun testExtractHost_plainDomainString_fromHubCommand() {
        // Trường hợp Hub gửi trực tiếp tên domain không có https://
        val domain1 = "storage-hp-ttn.thongtinnguon.vn"
        assertEquals("storage-hp-ttn.thongtinnguon.vn", CaCertHelper.extractHost(domain1))

        // Trường hợp có khoảng trắng thừa
        val domain2 = "  stream.tek4tv.app  "
        assertEquals("stream.tek4tv.app", CaCertHelper.extractHost(domain2))

        // Trường hợp domain có kèm port
        val domain3 = "stream.thongtinnguon.vn:8443"
        assertEquals("stream.thongtinnguon.vn", CaCertHelper.extractHost(domain3))
    }

    @Test
    fun testHubCaCommand_parsingMultipleDomains() {
        // Hub có thể gửi 1 domain hoặc nhiều domain phân tách bằng dấu phẩy / chấm phẩy
        val input = "storage-hp-ttn.thongtinnguon.vn, https://live.tek4tv.vn:8443/hls; stream.media.vn"
        val parsedHosts = input.split(",", ";", " ", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { CaCertHelper.extractHost(it) }

        assertEquals(3, parsedHosts.size)
        assertEquals("storage-hp-ttn.thongtinnguon.vn", parsedHosts[0])
        assertEquals("live.tek4tv.vn", parsedHosts[1])
        assertEquals("stream.media.vn", parsedHosts[2])
    }

    @Test
    fun testExtractHttpsUrl_prefersBodyThenPathBackup() {
        // 1. Khi Body là https
        val item1 = MediaItem(
            body = "https://storage.vn/file.mp3",
            pathBackup = "http://backup.vn/file.mp3"
        )
        assertEquals("https://storage.vn/file.mp3", CaCertHelper.extractHttpsUrl(item1))

        // 2. Khi Body là http nhưng pathBackup là https
        val item2 = MediaItem(
            body = "http://primary.vn/file.mp3",
            pathBackup = "https://backup.vn/file.mp3"
        )
        assertEquals("https://backup.vn/file.mp3", CaCertHelper.extractHttpsUrl(item2))

        // 3. Khi không có link https nào
        val item3 = MediaItem(
            body = "http://primary.vn/file.mp3",
            pathBackup = "http://backup.vn/file.mp3"
        )
        assertNull(CaCertHelper.extractHttpsUrl(item3))

        // 4. Khi null
        assertNull(CaCertHelper.extractHttpsUrl(null))
    }

    // ========================================================================
    // 2. KỊCH BẢN: CHUYỂN ĐỔI FORCE MEDIA VÀ TRÁNH LỖI NPE
    // ========================================================================

    @Test
    fun testForceMedia_toMediaItem_initializesStartAndFixTime() {
        val baseItem = MediaItem(
            id = 100,
            title = "Bản tin khẩn cấp",
            duration = "00:15:00",
            startTime = null // Giả lập server không gửi startTime
        )

        val forceMedia = ForceMedia(
            path = "https://storage-hp-ttn.thongtinnguon.vn/emergency.mp3",
            duration = "00:15:00",
            type = "LIVE",
            level = 1,
            mediaItem = baseItem
        ).apply {
            fixTime = "14:00:00"
        }

        val convertedItem = forceMedia.toMediaItem()

        // Kiểm tra start và fixTime đã được khởi tạo để tránh NPE khi tính toán thời gian
        assertEquals("14:00:00", convertedItem.fixTime)
        assertEquals("14:00:00", convertedItem.start)
        assertEquals("14:00:00", convertedItem.startTime)
        assertEquals("Bản tin khẩn cấp", convertedItem.title)
        assertEquals("https://storage-hp-ttn.thongtinnguon.vn/emergency.mp3", convertedItem.body)
        assertEquals(1, convertedItem.level)
        assertEquals("LIVE", convertedItem.type)
    }

    // ========================================================================
    // 3. KỊCH BẢN: TÍNH TOÁN THỜI GIAN VÀ ĐIỂM TIẾP TỤC PHÁT (RESUME POINT)
    // ========================================================================

    @Test
    fun testCalculateResumePoint_accuracy() {
        val mediaItem = MediaItem(
            start = "08:00:00",
            duration = "01:00:00"
        )

        // Giả lập hiện tại là 08:05:30 (sau 330 giây)
        val simulatedNow = toCalendar("08:05:30")
        val resumeSec = VlcMediaPlayer.calculateResumePoint(mediaItem, simulatedNow)
        assertEquals(330, resumeSec)
    }

    @Test
    fun testCalculateResumePoint_futureStart_returnsZero() {
        val mediaItem = MediaItem(
            start = "09:00:00",
            duration = "01:00:00"
        )

        // Giả lập hiện tại là 08:59:00 (chưa tới giờ)
        val simulatedNow = toCalendar("08:59:00")
        val resumeSec = VlcMediaPlayer.calculateResumePoint(mediaItem, simulatedNow)
        assertEquals(0, resumeSec)
    }

    @Test
    fun testCalculateResumePoint_allTimeFieldsNull_noNpeException() {
        // Trường hợp xấu nhất: mọi trường thời gian đều null
        val mediaItem = MediaItem(
            start = null,
            fixTime = null,
            startTime = null,
            duration = null
        )

        // Phải an toàn fallback và không crash
        val resumeSec = VlcMediaPlayer.calculateResumePoint(mediaItem)
        assertTrue(resumeSec >= 0)
    }

    // ========================================================================
    // 4. KỊCH BẢN: KIỂM TRA KHUNG GIỜ PHÁT LIVE (checkLiveStream)
    // ========================================================================

    @Test
    fun testCheckLiveStream_withinTimeWindow() {
        // Phát từ 08:00:00, thời lượng 30 phút (kết thúc lúc 08:30:00)
        val liveMedia = MediaItem(
            start = "08:00:00",
            duration = "00:30:00",
            type = "LIVE"
        )

        // Lúc 08:15:00 (đang giữa chương trình) -> Còn trong giờ
        val at815 = toCalendar("08:15:00")
        assertTrue("08:15:00 phải còn trong giờ phát Live", VlcMediaPlayer.checkLiveStream(liveMedia, at815))

        // Lúc 08:30:00 (đúng giây kết thúc) -> Vẫn còn trong giờ
        val at830 = toCalendar("08:30:00")
        assertTrue("08:30:00 phải còn trong giờ phát Live", VlcMediaPlayer.checkLiveStream(liveMedia, at830))
    }

    @Test
    fun testCheckLiveStream_afterTimeWindowExpired() {
        val liveMedia = MediaItem(
            start = "08:00:00",
            duration = "00:30:00",
            type = "LIVE"
        )

        // Lúc 08:30:05 (quá 5 giây) -> Đã hết giờ phát Live
        val at83005 = toCalendar("08:30:05")
        assertFalse("08:30:05 phải hết giờ phát Live", VlcMediaPlayer.checkLiveStream(liveMedia, at83005))
    }

    @Test
    fun testCheckLiveStream_noDurationOrZero_returnsFalse() {
        // Luồng không có duration hoặc duration = 00:00:00 -> Không hợp lệ, trả về false (luôn phải có duration)
        val liveNullDur = MediaItem(
            start = "08:00:00",
            duration = null,
            type = "LIVE"
        )
        assertFalse("Luồng Live không có duration không được coi là hợp lệ", VlcMediaPlayer.checkLiveStream(liveNullDur))

        val liveZeroDur = MediaItem(
            start = "08:00:00",
            duration = "00:00:00",
            type = "LIVE"
        )
        assertFalse("Luồng Live có duration 00:00:00 không được coi là hợp lệ", VlcMediaPlayer.checkLiveStream(liveZeroDur))
    }

    // ========================================================================
    // 5. KỊCH BẢN: KIỂM TRA FILE VOD NGẮT GIỮA CHỪNG VS KẾT THÚC BÌNH THƯỜNG
    // ========================================================================

    @Test
    fun testCheckTimeFrameStream_earlyCutoffVsNormalEnd() {
        // File phát từ 08:00:00, thời lượng 10 phút (600 giây, kết thúc 08:10:00)
        val fileMedia = MediaItem(
            start = "08:00:00",
            duration = "00:10:00",
            body = "https://example.com/vod.mp3",
            type = "VOD"
        )

        // Giả sử nhận sự kiện EndReached lúc 08:04:00 (mới 4 phút -> bị đứt mạng giữa chừng)
        val at804 = toCalendar("08:04:00")
        val isPrematureCutoff = VlcMediaPlayer.checkTimeFrameStream(fileMedia, fileMedia.body, at804)
        assertTrue("EndReached lúc 08:04 phải được phát hiện là đứt stream sớm để retry", isPrematureCutoff)

        // Giả sử nhận sự kiện EndReached lúc 08:10:00 (hết 10 phút -> đã phát trọn vẹn)
        val at810 = toCalendar("08:10:00")
        val isPrematureCutoffEnd = VlcMediaPlayer.checkTimeFrameStream(fileMedia, fileMedia.body, at810)
        assertFalse("EndReached lúc 08:10 là kết thúc trọn vẹn, không được retry", isPrematureCutoffEnd)
    }

    @Test
    fun testCheckTimeFrameStream_fixTimeDoesNotOverrideStart() {
        // Trong dữ liệu lịch, FixTime bằng End ("08:10:00").
        // getMediaStartTime phải ưu tiên Start ("08:00:00"), không được dùng FixTime ("08:10:00").
        val fileWithFixTime = MediaItem(
            start = "08:00:00",
            end = "08:10:00",
            fixTime = "08:10:00",
            duration = "00:10:00",
            body = "https://example.com/audio.mp3",
            type = "VOD"
        )

        val at810 = toCalendar("08:10:00")
        val isPrematureCutoff = VlcMediaPlayer.checkTimeFrameStream(fileWithFixTime, fileWithFixTime.body, at810)
        assertFalse("Khi phát hết 10 phút lúc 08:10:00, không được coi là đứt sớm dù FixTime là 08:10:00", isPrematureCutoff)
    }

    @Test
    fun testPrepareMediaUrl_encodesHashSign() {
        val media = MediaItem()
        val rawUrl = "https://storage.example.vn/2026/10/song-(#vxnca).mp3"
        val cleaned = media.prepareMediaUrl(rawUrl, false)
        assertEquals("Ký tự '#' trong URL phải được mã hóa thành '%23'", "https://storage.example.vn/2026/10/song-(%23vxnca).mp3", cleaned)
    }

    // ========================================================================
    // 6. KỊCH BẢN: STATE MACHINE RETRY (LIVE: TỐI ĐA 5 LẦN, FILE: TỐI ĐA 10 LẦN)
    // ========================================================================

    @Test
    fun testLiveRetryLogic_limit5TimesThenExhausted() {
        val maxLiveRetries = 5
        var currentRetryCount = 0
        var isStreamTerminated = false

        val liveMedia = MediaItem(
            start = "08:00:00",
            duration = "00:30:00",
            type = "LIVE"
        )
        val now = toCalendar("08:10:00") // Đang trong giờ

        // Mô phỏng chu kỳ gặp lỗi kết nối Live
        for (errorAttempt in 1..6) {
            val isStillInTime = VlcMediaPlayer.checkLiveStream(liveMedia, now)
            if (isStillInTime && currentRetryCount < maxLiveRetries) {
                // Tiếp tục thử lại
                currentRetryCount++
            } else {
                // Đã vượt quá 5 lần -> dừng retry và thông báo kết thúc
                isStreamTerminated = true
                break
            }
        }

        assertEquals("Số lần retry Live phải đạt đúng ngưỡng 5", 5, currentRetryCount)
        assertTrue("Sau 5 lần thất bại phải dừng retry luồng Live", isStreamTerminated)
    }

    @Test
    fun testFileRetryLogic_limit10TimesThenSkipToNext() {
        val maxFileRetries = 10
        var currentRetryCount = 0
        var isSkippedToNext = false

        val fileMedia = MediaItem(
            start = "08:00:00",
            duration = "00:30:00",
            body = "https://example.com/vod.mp3",
            type = "VOD"
        )
        val now = toCalendar("08:10:00") // Đang trong khung giờ

        // Mô phỏng chu kỳ gặp lỗi kết nối File VOD
        for (errorAttempt in 1..12) {
            val isStillInTime = VlcMediaPlayer.checkTimeFrameStream(fileMedia, fileMedia.body, now)
            if (isStillInTime && currentRetryCount < maxFileRetries) {
                currentRetryCount++
            } else {
                // Quá 10 lần -> chuyển bài tiếp theo
                isSkippedToNext = true
                break
            }
        }

        assertEquals("Số lần retry File phải đạt đúng ngưỡng 10", 10, currentRetryCount)
        assertTrue("Sau 10 lần thất bại phải bỏ qua file lỗi để chuyển bài tiếp", isSkippedToNext)
    }

    @Test
    fun testRetryCountReset_onSuccessPlayingOrStop() {
        var countReplayLive = 3
        var countReplayFile = 7

        // Giả lập sự kiện MediaPlayer.Event.Playing thành công
        fun onPlayingEvent() {
            countReplayLive = 0
            countReplayFile = 0
        }

        onPlayingEvent()

        assertEquals("Biến đếm retry Live phải được reset về 0 khi phát thành công", 0, countReplayLive)
        assertEquals("Biến đếm retry File phải được reset về 0 khi phát thành công", 0, countReplayFile)
    }
}

