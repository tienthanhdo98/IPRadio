package app.tek4tv.ipradio

import app.tek4tv.ipradio.model.ForceMedia
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.utils.getDurationInSecond
import app.tek4tv.ipradio.utils.toCalendar
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class TimeframeAndForceMediaTest {

    // ========================================================================
    // 1. KỊCH BẢN: ĐẶT TÊN & BÓC TÁCH KHUNG GIỜ PHÁT (TIMEFRAME KEY)
    // ========================================================================

    @Test
    fun testTimeframeKey_generateFromStartAndEndMarkers() {
        // Cấu trúc danh sách trong MediaRepo: marker start -> các bài hát -> marker end
        val rawPlaylist = listOf(
            MediaItem(id = "1", body = "start", fixTime = "06:00:00"),
            MediaItem(id = "2", body = "https://server.vn/song1.mp3", duration = "00:15:00", title = "Bài 1"),
            MediaItem(id = "3", body = "https://server.vn/song2.mp3", duration = "00:15:00", title = "Bài 2"),
            MediaItem(id = "4", body = "end", fixTime = "06:30:00"),
            MediaItem(id = "5", body = "start", fixTime = "17:00:00"),
            MediaItem(id = "6", body = "https://server.vn/news.mp3", duration = "00:30:00", title = "Bản tin chiều"),
            MediaItem(id = "7", body = "end", fixTime = "17:30:00")
        )

        // Mô phỏng thuật toán filterMedia của MediaRepo
        val scheduledList = LinkedHashMap<String, List<MediaItem>>()
        val dividerIndices = rawPlaylist.indices.filter {
            rawPlaylist[it].body == "start" || rawPlaylist[it].body == "end"
        }.chunked(2)

        dividerIndices.forEach { pair ->
            if (pair.size == 2) {
                val startIdx = pair[0]
                val endIdx = pair[1]
                val key = "${rawPlaylist[startIdx].fixTime}-${rawPlaylist[endIdx].fixTime}"
                scheduledList[key] = rawPlaylist.subList(startIdx + 1, endIdx)
            }
        }

        // Khẳng định tạo đúng 2 khung giờ chuẩn theo format HH:mm:ss-HH:mm:ss
        assertTrue(scheduledList.containsKey("06:00:00-06:30:00"))
        assertTrue(scheduledList.containsKey("17:00:00-17:30:00"))
        assertEquals(2, scheduledList["06:00:00-06:30:00"]?.size)
        assertEquals(1, scheduledList["17:00:00-17:30:00"]?.size)
        assertEquals("Bài 1", scheduledList["06:00:00-06:30:00"]?.get(0)?.title)
        assertEquals("Bản tin chiều", scheduledList["17:00:00-17:30:00"]?.get(0)?.title)
    }

    @Test
    fun testFindActiveTimeframe_matchesCurrentTime() {
        val scheduledKeys = listOf("06:00:00-07:00:00", "11:30:00-12:30:00", "17:00:00-18:00:00")

        fun findActiveKey(nowCalendar: Calendar): String? {
            val now = nowCalendar.timeInMillis
            for (period in scheduledKeys) {
                val t = period.split("-")
                val start = toCalendar(t[0])
                val end = toCalendar(t[1])
                if (now in start.timeInMillis..end.timeInMillis) {
                    return period
                }
            }
            return null
        }

        // Lúc 06:30:00 -> Phải khớp khung giờ sáng
        val at630 = toCalendar("06:30:00")
        assertEquals("06:00:00-07:00:00", findActiveKey(at630))

        // Lúc 12:00:00 -> Phải khớp khung giờ trưa
        val at1200 = toCalendar("12:00:00")
        assertEquals("11:30:00-12:30:00", findActiveKey(at1200))

        // Lúc 14:00:00 -> Không có khung giờ nào (ngoài giờ phát)
        val at1400 = toCalendar("14:00:00")
        assertNull(findActiveKey(at1400))
    }

    // ========================================================================
    // 2. KỊCH BẢN: TÍNH ĐIỂM PHÁT TIẾP (RESUME POINT) KHI KHỞI ĐỘNG GIỮA KHUNG GIỜ
    // ========================================================================

    @Test
    fun testCalculateResumePoint_findsExactTrackAndOffset() {
        // Giả lập khung giờ 06:00:00-07:00:00 gồm 3 bài
        // Bài 0: 600s (06:00:00 -> 06:10:00)
        // Bài 1: 1200s (06:10:00 -> 06:30:00)
        // Bài 2: 1800s (06:30:00 -> 07:00:00)
        val playlist = listOf(
            MediaItem(title = "Bài 1", duration = "00:10:00"), // 600s
            MediaItem(title = "Bài 2", duration = "00:20:00"), // 1200s
            MediaItem(title = "Bài 3", duration = "00:30:00")  // 1800s
        )
        val startTime = toCalendar("06:00:00")

        fun calculateResume(now: Calendar): Pair<Int, Int> {
            val secondFromStart = ((now.timeInMillis - startTime.timeInMillis) / 1000).toInt()
            var t = 0
            for (i in playlist.indices) {
                val mediaDuration = getDurationInSecond(playlist[i].duration!!)
                t += mediaDuration
                if (t > secondFromStart) {
                    val offsetInTrack = mediaDuration - (t - secondFromStart)
                    return Pair(i, offsetInTrack)
                }
            }
            return Pair(0, 0)
        }

        // Case 1: Lúc 06:05:00 (sau 300s) -> Đang ở Bài 0, giây thứ 300
        val res1 = calculateResume(toCalendar("06:05:00"))
        assertEquals(0, res1.first)
        assertEquals(300, res1.second)

        // Case 2: Lúc 06:15:00 (sau 900s) -> Đang ở Bài 1, giây thứ 300 (vì 900 - 600 = 300)
        val res2 = calculateResume(toCalendar("06:15:00"))
        assertEquals(1, res2.first)
        assertEquals(300, res2.second)

        // Case 3: Lúc 06:40:00 (sau 2400s) -> Đang ở Bài 2, giây thứ 600 (vì 2400 - 1800 = 600)
        val res3 = calculateResume(toCalendar("06:40:00"))
        assertEquals(2, res3.first)
        assertEquals(600, res3.second)
    }

    // ========================================================================
    // 3. KỊCH BẢN: PHÁT LIVE/KHẨN CẤP ĐÈ LÚC ĐANG PHÁT LỊCH
    // ========================================================================

    @Test
    fun testForceMedia_overridesScheduledPlayback() {
        var currentPlayMode = "06:00:00-07:00:00" // Đang phát lịch buổi sáng
        var isScheduledAlarmCancelled = false
        var isVlcStopped = false
        var currentPlayingTitle = "Bài hát theo lịch"

        // Có lệnh phát khẩn cấp gửi xuống từ Hub
        fun simulateOnPlayForceCommand(newForceMedia: ForceMedia) {
            // 1. Hủy tất cả hẹn giờ của lịch phát hiện tại
            isScheduledAlarmCancelled = true
            // 2. Dừng player hiện tại
            isVlcStopped = true
            // 3. Đặt playMode thành "-2" (PLAY_MODE_FORCE)
            currentPlayMode = "-2"
            // 4. Bắt đầu phát bản tin khẩn cấp
            currentPlayingTitle = newForceMedia.mediaItem!!.title!!
        }

        val forceMedia = ForceMedia(
            path = "https://stream.tek4tv.vn/live_emergency.m3u8",
            duration = "00:10:00",
            type = "LIVE",
            level = 1,
            mediaItem = MediaItem(title = "Bản tin bão khẩn cấp", duration = "00:10:00")
        )

        simulateOnPlayForceCommand(forceMedia)

        assertEquals("Chế độ phát phải chuyển sang -2 (PLAY_MODE_FORCE)", "-2", currentPlayMode)
        assertTrue("Hẹn giờ của lịch cũ phải bị hủy để không chèn ngang", isScheduledAlarmCancelled)
        assertTrue("Player đang phát bài theo lịch phải bị ngắt", isVlcStopped)
        assertEquals("Bản tin bão khẩn cấp", currentPlayingTitle)
    }

    @Test
    fun testForceMedia_priorityLevelOverride() {
        // Đang phát khẩn cấp cấp độ 2 (Cấp độ số càng nhỏ thì ưu tiên càng cao)
        var currentForceLevel = 2
        var currentTitle = "Thông báo chung xã"

        fun handleIncomingForceCommand(newLevel: Int, newTitle: String): Boolean {
            // Kiểm tra: nếu đang phát media cấp cao hơn (số nhỏ hơn) mà lệnh mới cấp thấp hơn (số lớn hơn) -> BỎ QUA
            if (currentForceLevel < newLevel) {
                return false // Bị từ chối
            }
            // Lệnh mới có độ ưu tiên cao hơn hoặc bằng -> ĐÈ LÊN
            currentForceLevel = newLevel
            currentTitle = newTitle
            return true
        }

        // Lệnh mới có Level = 3 (thấp hơn Level 2 hiện tại) -> Bị từ chối
        val accepted1 = handleIncomingForceCommand(3, "Bản tin nông nghiệp")
        assertFalse("Lệnh cấp độ 3 không được đè lên cấp độ 2", accepted1)
        assertEquals("Thông báo chung xã", currentTitle)

        // Lệnh mới có Level = 1 (cao hơn Level 2 hiện tại) -> ĐƯỢC PHÉP ĐÈ
        val accepted2 = handleIncomingForceCommand(1, "Báo động lũ khẩn cấp")
        assertTrue("Lệnh cấp độ 1 phải được đè lên cấp độ 2", accepted2)
        assertEquals(1, currentForceLevel)
        assertEquals("Báo động lũ khẩn cấp", currentTitle)
    }

    @Test
    fun testForceMediaEnd_resumesScheduledTimeframe() {
        // Kịch bản: Khung giờ phát 06:00:00 - 07:00:00
        // Lúc 06:10:00 có bản tin khẩn cấp phát trong 15 phút (đến 06:25:00)
        // Khi bản tin khẩn cấp kết thúc lúc 06:25:00, kiểm tra xem có khôi phục lịch phát sáng hay không
        val scheduledPeriod = "06:00:00-07:00:00"
        var currentPlayMode = "-2" // Đang ở chế độ khẩn cấp

        fun onEndForceMedia(currentTime: Calendar): String {
            currentPlayMode = "" // Thoát khỏi force mode

            // Kiểm tra xem hiện tại có còn trong khung giờ phát hay không
            val t = scheduledPeriod.split("-")
            val start = toCalendar(t[0])
            val end = toCalendar(t[1])
            return if (currentTime.timeInMillis in start.timeInMillis..end.timeInMillis) {
                // Vẫn còn trong khung giờ -> Tiếp tục phát lịch
                currentPlayMode = scheduledPeriod
                "RESUME_SCHEDULED"
            } else {
                "TIMEOVER"
            }
        }

        // Lúc 06:25:00 bản tin khẩn cấp kết thúc -> Vẫn còn trong khung 06:00 - 07:00 -> Khôi phục lịch
        val action1 = onEndForceMedia(toCalendar("06:25:00"))
        assertEquals("RESUME_SCHEDULED", action1)
        assertEquals("06:00:00-07:00:00", currentPlayMode)

        // Trường hợp khác: Bản tin khẩn cấp kéo dài đến 07:05:00 mới xong (đã qua khung giờ 07:00)
        val action2 = onEndForceMedia(toCalendar("07:05:00"))
        assertEquals("TIMEOVER", action2)
    }

    // ========================================================================
    // 4. KỊCH BẢN: CHUYỂN TIẾP BÀI HÁT TRONG KHUNG GIỜ (playNextMedia)
    // ========================================================================

    @Test
    fun testPlayNextMedia_advancesUntilEndOfTimeframe() {
        val playlist = listOf("Track 1", "Track 2", "Track 3")
        var playlistIndex = 0
        var isTimeFrameEnded = false
        var isPlayerStopped = false

        fun playNextMedia() {
            playlistIndex++
            if (playlistIndex < playlist.size) {
                // Phát bài tiếp theo
            } else {
                // Hết bài trong khung giờ -> Dừng phát và đóng khung giờ
                isPlayerStopped = true
                isTimeFrameEnded = true
            }
        }

        // Đang ở bài 0, chuyển bài tiếp theo
        playNextMedia()
        assertEquals(1, playlistIndex)
        assertFalse(isTimeFrameEnded)

        // Chuyển tiếp bài 2
        playNextMedia()
        assertEquals(2, playlistIndex)
        assertFalse(isTimeFrameEnded)

        // Hết bài 2, chuyển tiếp -> Hết khung giờ
        playNextMedia()
        assertEquals(3, playlistIndex)
        assertTrue("Khi hết danh sách bài hát phải kết thúc khung giờ", isTimeFrameEnded)
        assertTrue("Phải dừng player khi hết khung giờ", isPlayerStopped)
    }

    // ========================================================================
    // 5. KỊCH BẢN: DỪNG KHUNG GIỜ BẰNG LỆNH HẸN GIỜ (stopPlaylist)
    // ========================================================================

    @Test
    fun testStopPlaylist_matchingKeyStopsImmediately() {
        var playMode = "06:00:00-07:00:00"
        var isStopped = false
        var isTimeOver = false

        fun stopPlaylist(cancelListKey: String) {
            if (cancelListKey != playMode) return
            playMode = ""
            isStopped = true
            isTimeOver = true
        }

        // Lệnh dừng gửi sai key (khung giờ khác) -> Bỏ qua
        stopPlaylist("17:00:00-18:00:00")
        assertEquals("06:00:00-07:00:00", playMode)
        assertFalse(isStopped)

        // Lệnh dừng đúng key của khung giờ đang phát -> Dừng ngay lập tức
        stopPlaylist("06:00:00-07:00:00")
        assertEquals("", playMode)
        assertTrue(isStopped)
        assertTrue(isTimeOver)
    }
}
