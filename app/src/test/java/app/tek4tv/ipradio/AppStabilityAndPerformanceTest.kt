package app.tek4tv.ipradio

import app.tek4tv.ipradio.core.players_helper.VlcMediaPlayer
import app.tek4tv.ipradio.model.ForceMedia
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.utils.getDurationInSecond
import app.tek4tv.ipradio.utils.secToReadable
import app.tek4tv.ipradio.utils.toCalendar
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class AppStabilityAndPerformanceTest {

    // ========================================================================
    // 1. KIỂM THỬ TÍNH TOÁN VÀ LOGIC VlcMediaPlayer (CHỐNG RECURSION / CRASH)
    // ========================================================================

    @Test
    fun testVlcMediaPlayer_calculateResumePoint_exactCalculations() {
        val mediaItem = MediaItem(
            title = "Bản tin 10h",
            startTime = "10:00:00",
            duration = "01:00:00"
        )

        // Case 1: Thời điểm hiện tại sau giờ bắt đầu 15 phút (900 giây)
        val now1 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 15)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val resumeSec1 = VlcMediaPlayer.calculateResumePoint(mediaItem, now1)
        assertEquals("Sau 15 phút thì resume point phải là 900s", 900, resumeSec1)

        // Case 2: Thời điểm hiện tại trước giờ bắt đầu -> trả về 0s
        val now2 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 50)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val resumeSec2 = VlcMediaPlayer.calculateResumePoint(mediaItem, now2)
        assertEquals("Chưa đến giờ bắt đầu thì resume point phải là 0s", 0, resumeSec2)

        // Case 3: Thời điểm hiện tại trùng đúng giờ bắt đầu -> trả về 0s
        val now3 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val resumeSec3 = VlcMediaPlayer.calculateResumePoint(mediaItem, now3)
        assertEquals("Đúng giờ bắt đầu thì resume point phải là 0s", 0, resumeSec3)
    }

    @Test
    fun testVlcMediaPlayer_checkLiveStream_boundaryConditions() {
        val liveMedia = MediaItem(
            title = "Tiếp sóng VOV",
            startTime = "06:00:00",
            duration = "00:30:00" // 30 phút -> kết thúc lúc 06:30:00
        )

        // Case 1: Trong khung giờ (06:15:00) -> true
        val inside = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 15)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertTrue("06:15 nằm trong khung 06:00 - 06:30", VlcMediaPlayer.checkLiveStream(liveMedia, inside))

        // Case 2: Đúng mốc kết thúc (06:30:00) -> true (now <= endTime)
        val edge = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertTrue("06:30:00 vẫn còn hợp lệ trong khung giờ", VlcMediaPlayer.checkLiveStream(liveMedia, edge))

        // Case 3: Sau mốc kết thúc 1 giây (06:30:01) -> false
        val outside = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 1)
            set(Calendar.MILLISECOND, 0)
        }
        assertFalse("06:30:01 đã hết giờ phát", VlcMediaPlayer.checkLiveStream(liveMedia, outside))

        // Case 4: Luồng không có duration (null hoặc rỗng hoặc "00:00:00") -> an toàn trả về false không crash
        val invalidDurationMedia = MediaItem(title = "Lỗi duration", startTime = "06:00:00", duration = null)
        assertFalse("Duration null phải trả về false an toàn", VlcMediaPlayer.checkLiveStream(invalidDurationMedia, inside))
    }

    @Test
    fun testVlcMediaPlayer_checkTimeFrameStream_vodFileContinuity() {
        val fileMedia = MediaItem(
            title = "Bản tin thời sự chiều",
            startTime = "17:00:00",
            duration = "00:45:00",
            body = "https://domain.vn/audio_chieu.mp3"
        )

        // Case 1: Đang phát đúng file đó trong khung giờ -> true
        val inside = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 17)
            set(Calendar.MINUTE, 20)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val isStillSame = VlcMediaPlayer.checkTimeFrameStream(fileMedia, "https://domain.vn/audio_chieu.mp3", inside)
        assertTrue("Cùng file và trong giờ phát -> tiếp tục phát", isStillSame)

        // Case 2: File hiện tại đã đổi sang bài khác -> false (dừng luồng cũ ngay)
        val isChanged = VlcMediaPlayer.checkTimeFrameStream(fileMedia, "https://domain.vn/audio_khac.mp3", inside)
        assertFalse("Đã chuyển sang bài khác -> phải trả về false", isChanged)
    }

    // ========================================================================
    // 2. KIỂM THỬ CHỐNG CRASH CHO ForceMedia (NULL-SAFETY)
    // ========================================================================

    @Test
    fun testForceMedia_toMediaItem_nullMediaItemSafe() {
        // Trường hợp Hub gửi lệnh khẩn cấp nhưng trường Name là chuỗi văn bản thường (không parse được thành MediaItem)
        val forceMedia = ForceMedia(
            path = "https://stream.radio.vn/live.m3u8",
            duration = "00:15:00",
            type = "LIVE",
            level = 1,
            mediaItem = null // mediaItem null!
        ).apply {
            name = "Thông báo bão khẩn cấp"
            fixTime = "14:30:00"
        }

        // Trước đây đoạn này ném NullPointerException tại mediaItem!!.title
        // Giờ đây phải chuyển đổi an toàn và không bị crash
        val resultItem = forceMedia.toMediaItem()

        assertNotNull(resultItem)
        assertEquals("Thông báo bão khẩn cấp", resultItem.title)
        assertEquals("https://stream.radio.vn/live.m3u8", resultItem.body)
        assertEquals("LIVE", resultItem.type)
        assertEquals("00:15:00", resultItem.duration)
        assertEquals("14:30:00", resultItem.startTime)
        assertEquals(1, resultItem.level)
    }

    // ========================================================================
    // 3. KIỂM THỬ AN TOÀN DỮ LIỆU MediaItem (NULL-SAFETY & ENUM MAPPING)
    // ========================================================================

    @Test
    fun testMediaItem_nullSafety() {
        // Body null
        val itemWithNullBody = MediaItem(body = null, type = "LIVE")
        assertEquals(MediaType.LIVE, itemWithNullBody.getMediaType())

        // getFileName với body null không được ném NPE
        val fileName = itemWithNullBody.getFileName()
        assertNotNull(fileName)

        // getMediaType với các kiểu khác nhau
        assertEquals(MediaType.FILE, MediaItem(type = "VOD").getMediaType())
        assertEquals(MediaType.LIVE, MediaItem(type = "LIVE").getMediaType())
        assertEquals(MediaType.FM, MediaItem(type = "FM").getMediaType())
        assertEquals(MediaType.AM, MediaItem(type = "AM").getMediaType())
        assertEquals(MediaType.UNKNOWN, MediaItem(type = "UNKNOWN_TYPE").getMediaType())
    }

    // ========================================================================
    // 4. KIỂM THỬ TimeUtils CHỊU LỖI DỮ LIỆU BẤT THƯỜNG
    // ========================================================================

    @Test
    fun testTimeUtils_toCalendar_malformedInputsDoNotCrash() {
        // Chuỗi rỗng
        val calEmpty = toCalendar("")
        assertNotNull(calEmpty)

        // Chuỗi chỉ có 2 phần (giờ:phút thiếu giây)
        val calTwoParts = toCalendar("08:30")
        assertNotNull(calTwoParts)
        assertEquals(8, calTwoParts.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, calTwoParts.get(Calendar.MINUTE))
        assertEquals(0, calTwoParts.get(Calendar.SECOND))

        // Chuỗi chỉ có giờ
        val calOnePart = toCalendar("12")
        assertNotNull(calOnePart)
        assertEquals(12, calOnePart.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, calOnePart.get(Calendar.MINUTE))

        // Chuỗi chứa chữ không hợp lệ -> fallback an toàn, không ném NPE từ e.message!!
        val calInvalid = toCalendar("invalid_string")
        assertNotNull(calInvalid)
    }

    @Test
    fun testTimeUtils_getDurationInSecond_andSecToReadable() {
        assertEquals(3600, getDurationInSecond("01:00:00"))
        assertEquals(900, getDurationInSecond("00:15:00"))
        assertEquals(45, getDurationInSecond("00:00:45"))
        assertEquals(0, getDurationInSecond("invalid"))

        assertEquals("01:00:00", secToReadable(3600))
        assertEquals("15:00", secToReadable(900))
        assertEquals("00:45", secToReadable(45))
    }

    // ========================================================================
    // 5. KIỂM THỬ HIỆU NĂNG & CHỊU TẢI (PERFORMANCE & STRESS TEST)
    // ========================================================================

    @Test
    fun testPerformance_10k_calculations_under1Second() {
        val testItem = MediaItem(
            title = "Benchmark Item",
            startTime = "08:00:00",
            duration = "01:00:00",
            body = "https://server.vn/stream.mp3"
        )
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
        }

        val startTime = System.currentTimeMillis()

        // Chạy 10.000 phép tính liên tục
        for (i in 0 until 10_000) {
            val resume = VlcMediaPlayer.calculateResumePoint(testItem, now)
            val isLive = VlcMediaPlayer.checkLiveStream(testItem, now)
            val isSame = VlcMediaPlayer.checkTimeFrameStream(testItem, testItem.body, now)

            assertEquals(1800, resume)
            assertTrue(isLive)
            assertTrue(isSame)
        }

        val durationMs = System.currentTimeMillis() - startTime
        println("=== Performance Benchmark: 10,000 iterations completed in ${durationMs}ms ===")

        // Kiểm tra hiệu năng: 10.000 vòng tính toán phải hoàn thành dưới 1000ms (1 giây)
        assertTrue("10.000 phép tính phải chạy dưới 1000ms (thực tế: ${durationMs}ms)", durationMs < 1000)
    }
}
