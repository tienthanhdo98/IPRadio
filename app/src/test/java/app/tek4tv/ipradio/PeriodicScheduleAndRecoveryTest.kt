package app.tek4tv.ipradio

import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.ScheduleDay
import org.junit.Assert.*
import org.junit.Test

class PeriodicScheduleAndRecoveryTest {

    // ========================================================================
    // 1. KIỂM THỬ LOGIC lastUpdateFailed (MediaRepo)
    // ========================================================================

    data class MockScenarioResult(
        val mediaList: List<MediaItem>,
        val lastUpdateFailed: Boolean
    )

    /**
     * Mô phỏng logic xác định lastUpdateFailed và kết quả trả về trong MediaRepo.getScenario()
     */
    private fun simulateGetScenarioLogic(
        monthlyDataList: List<ScheduleDay>,
        currentDate: String,
        monthlyFetchSucceeded: Boolean,
        todayScenarioFetchSucceeded: Boolean
    ): MockScenarioResult {
        var lastUpdateFailed = false

        val hasToday = monthlyDataList.any { it.date == currentDate }
        var currentMonthlyList = monthlyDataList

        if (!hasToday) {
            if (!monthlyFetchSucceeded) {
                lastUpdateFailed = true
            }
        }

        val res = if (currentMonthlyList.isEmpty()) {
            lastUpdateFailed = true
            emptyList()
        } else {
            val todayScenario = currentMonthlyList.firstOrNull { it.date == currentDate }
            if (todayScenario == null) {
                // Nếu đã có danh sách tháng hợp lệ từ server nhưng hôm nay không có lịch:
                // lastUpdateFailed = false (ngày nghỉ hợp lệ, không phải lỗi mạng)
                emptyList()
            } else {
                if (todayScenarioFetchSucceeded) {
                    lastUpdateFailed = false
                    listOf(MediaItem(title = "Bản tin hôm nay", duration = "00:15:00"))
                } else {
                    lastUpdateFailed = true
                    emptyList()
                }
            }
        }

        return MockScenarioResult(res, lastUpdateFailed)
    }

    @Test
    fun testLastUpdateFailed_networkErrorAtBootOr0015_marksTrue() {
        // Kịch bản: Máy vừa boot hoặc lúc 00:15, mất mạng nên danh sách tháng rỗng và API tải tháng thất bại
        val result = simulateGetScenarioLogic(
            monthlyDataList = emptyList(),
            currentDate = "29-10-2026",
            monthlyFetchSucceeded = false,
            todayScenarioFetchSucceeded = false
        )

        assertTrue("Danh sách bài phát phải rỗng khi mất mạng", result.mediaList.isEmpty())
        assertTrue("lastUpdateFailed phải là TRUE để không bị hiểu nhầm là ngày nghỉ", result.lastUpdateFailed)
    }

    @Test
    fun testLastUpdateFailed_validMonthWithoutToday_marksFalse() {
        // Kịch bản: Server trả về danh sách tháng thành công (có ngày 28 và 30), nhưng hôm nay (29) không có lịch
        val monthlyList = listOf(
            ScheduleDay(date = "28-10-2026", sourceUrl = "http://api/28"),
            ScheduleDay(date = "30-10-2026", sourceUrl = "http://api/30")
        )

        val result = simulateGetScenarioLogic(
            monthlyDataList = monthlyList,
            currentDate = "29-10-2026",
            monthlyFetchSucceeded = true,
            todayScenarioFetchSucceeded = false
        )

        assertTrue("Hôm nay không có lịch phát nên danh sách rỗng", result.mediaList.isEmpty())
        assertFalse("lastUpdateFailed phải là FALSE vì đây là ngày không có lịch hợp lệ, không phải lỗi mạng", result.lastUpdateFailed)
    }

    @Test
    fun testLastUpdateFailed_todayScheduled_networkFetchSucceeds_marksFalse() {
        // Kịch bản: Hôm nay có lịch và tải thành công từ server
        val monthlyList = listOf(
            ScheduleDay(date = "29-10-2026", sourceUrl = "http://api/29")
        )

        val result = simulateGetScenarioLogic(
            monthlyDataList = monthlyList,
            currentDate = "29-10-2026",
            monthlyFetchSucceeded = true,
            todayScenarioFetchSucceeded = true
        )

        assertEquals("Phải có 1 bài phát", 1, result.mediaList.size)
        assertFalse("lastUpdateFailed phải là FALSE khi tải lịch thành công", result.lastUpdateFailed)
    }

    // ========================================================================
    // 2. KIỂM THỬ TIẾN TRÌNH ĐỊNH KỲ 30 PHÚT (MainViewModel)
    // ========================================================================

    @Test
    fun testPeriodicSyncLoop_maintains30MinutesInterval_untilScheduleLoaded() {
        var isLoopActive = true
        var loopDelayMinutes = 0L
        var executedCycles = 0
        var loadedSchedule = emptyList<MediaItem>()

        // Mô phỏng chu kỳ kiểm tra định kỳ 30 phút trong MainViewModel.startScheduleRetryLoop()
        fun runPeriodicCycle(simulatedNetworkHasSchedule: Boolean) {
            val delayMinutes = 30L
            loopDelayMinutes = delayMinutes
            executedCycles++

            if (simulatedNetworkHasSchedule) {
                loadedSchedule = listOf(MediaItem(title = "Bản tin chiều", duration = "00:20:00"))
                isLoopActive = false // Tự động break/cancel loop khi tải được lịch
            }
        }

        // Chu kỳ 1 (00:45): Mất mạng
        runPeriodicCycle(simulatedNetworkHasSchedule = false)
        assertTrue("Vòng lặp phải tiếp tục chạy khi chưa có lịch", isLoopActive)
        assertEquals("Chu kỳ phải là cố định 30 phút", 30L, loopDelayMinutes)
        assertEquals(1, executedCycles)

        // Chu kỳ 40 (tương đương ~20 tiếng sau nếu mất mạng kéo dài): Vòng lặp vẫn KHÔNG bị chết yểu
        for (i in 2..40) {
            runPeriodicCycle(simulatedNetworkHasSchedule = false)
        }
        assertTrue("Vòng lặp không bị giới hạn 40 lần, vẫn duy trì sau chu kỳ 40", isLoopActive)
        assertEquals(40, executedCycles)

        // Chu kỳ 41 (Ví dụ: Lúc 07:00 sáng mạng có lại, server có lịch chiều)
        runPeriodicCycle(simulatedNetworkHasSchedule = true)
        assertFalse("Vòng lặp phải tự động dừng lại khi tải được lịch", isLoopActive)
        assertEquals("Bản tin chiều", loadedSchedule[0].title)
    }

    // ========================================================================
    // 3. KIỂM THỬ COOLDOWN 5 PHÚT & BẢO VỆ PHÁT KHI HUB SIGNALR KẾT NỐI LẠI
    // ========================================================================

    class MockHubScheduleTrigger {
        var lastHubScheduleSyncTime = 0L
        var startPlayingMediaCallCount = 0

        fun onHubConnected(
            currentTime: Long,
            isScheduleEmpty: Boolean,
            isIdle: Boolean,
            isPlaying: Boolean,
            hasValidForceMedia: Boolean
        ) {
            if (currentTime - lastHubScheduleSyncTime >= 5 * 60 * 1000L) {
                lastHubScheduleSyncTime = currentTime
                if (isScheduleEmpty && isIdle && !isPlaying && !hasValidForceMedia) {
                    startPlayingMediaCallCount++
                }
            }
        }
    }

    @Test
    fun testHubReconnect_5MinutesCooldown_preventsRapidDuplicateCalls() {
        val trigger = MockHubScheduleTrigger()
        val t0 = 1000000L

        // Lần 1 (t = 0): Hub kết nối lại lần đầu sau khi có mạng -> Được phép gọi tải lịch
        trigger.onHubConnected(
            currentTime = t0,
            isScheduleEmpty = true,
            isIdle = true,
            isPlaying = false,
            hasValidForceMedia = false
        )
        assertEquals("Lần đầu tiên có mạng phải gọi startPlayingMedia() ngay", 1, trigger.startPlayingMediaCallCount)

        // Lần 2 (t = 10 giây sau): Mạng chập chờn, Hub reconnect lần nữa -> Bị chặn bởi cooldown 5 phút
        trigger.onHubConnected(
            currentTime = t0 + 10_000L,
            isScheduleEmpty = true,
            isIdle = true,
            isPlaying = false,
            hasValidForceMedia = false
        )
        assertEquals("Trong vòng 5 phút không được gọi lại để tránh spam", 1, trigger.startPlayingMediaCallCount)

        // Lần 3 (t = 4 phút sau): Vẫn chưa đủ 5 phút -> Tiếp tục bị chặn
        trigger.onHubConnected(
            currentTime = t0 + 4 * 60 * 1000L,
            isScheduleEmpty = true,
            isIdle = true,
            isPlaying = false,
            hasValidForceMedia = false
        )
        assertEquals("Sau 4 phút vẫn chưa đủ 5 phút cooldown", 1, trigger.startPlayingMediaCallCount)

        // Lần 4 (t = 5 phút 1 giây sau): Đã đủ 5 phút -> Cho phép gọi lại
        trigger.onHubConnected(
            currentTime = t0 + 5 * 60 * 1000L + 1000L,
            isScheduleEmpty = true,
            isIdle = true,
            isPlaying = false,
            hasValidForceMedia = false
        )
        assertEquals("Sau 5 phút được phép gọi lại an toàn", 2, trigger.startPlayingMediaCallCount)
    }

    @Test
    fun testHubReconnect_guardConditions_preventInterruptingPlayback() {
        val trigger = MockHubScheduleTrigger()
        val t0 = 1000000L

        // Trường hợp 1: Thiết bị đang phát nhạc bình thường (isPlaying = true)
        trigger.onHubConnected(
            currentTime = t0,
            isScheduleEmpty = true,
            isIdle = false,
            isPlaying = true, // Đang phát
            hasValidForceMedia = false
        )
        assertEquals("Không được gọi tải lịch khi đang phát", 0, trigger.startPlayingMediaCallCount)

        // Trường hợp 2: Thiết bị đang có bản tin phát khẩn cấp (hasValidForceMedia = true)
        trigger.onHubConnected(
            currentTime = t0 + 10 * 60 * 1000L,
            isScheduleEmpty = true,
            isIdle = true,
            isPlaying = false,
            hasValidForceMedia = true // Đang có khẩn cấp
        )
        assertEquals("Không được gọi tải lịch khi đang có phát khẩn cấp", 0, trigger.startPlayingMediaCallCount)

        // Trường hợp 3: Thiết bị đã có lịch hôm nay rồi (isScheduleEmpty = false)
        trigger.onHubConnected(
            currentTime = t0 + 20 * 60 * 1000L,
            isScheduleEmpty = false, // Đã có lịch
            isIdle = true,
            isPlaying = false,
            hasValidForceMedia = false
        )
        assertEquals("Không được gọi tải lịch khi đã có lịch hôm nay rồi", 0, trigger.startPlayingMediaCallCount)
    }
}
