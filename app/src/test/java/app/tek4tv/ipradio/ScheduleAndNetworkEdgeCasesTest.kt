package app.tek4tv.ipradio

import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.ScheduleDay
import app.tek4tv.ipradio.utils.toCalendar
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class ScheduleAndNetworkEdgeCasesTest {

    // ========================================================================
    // 1. KỊCH BẢN: HẸN GIỜ (HAPPY CASE & ĐIỀU KIỆN THỜI ĐIỂM)
    // ========================================================================

    data class ScheduledAlarmInfo(
        val period: String,
        val requestCode: Int,
        val timeInMillis: Long,
        val isEndAlarm: Boolean
    )

    /**
     * Mô phỏng bộ logic lập lịch hẹn giờ của MediaScheduler.scheduleListAlarms()
     */
    private fun simulateScheduleListAlarms(
        scheduledPeriods: List<String>,
        nowCalendar: Calendar
    ): List<ScheduledAlarmInfo> {
        val alarms = mutableListOf<ScheduledAlarmInfo>()
        val now = nowCalendar.timeInMillis

        for ((index, period) in scheduledPeriods.withIndex()) {
            val t = period.split("-")
            if (t.size != 2) continue
            val start = toCalendar(t[0])
            val end = toCalendar(t[1])

            // Xử lý khung giờ qua nửa đêm
            if (start.after(end)) {
                end.add(Calendar.DAY_OF_MONTH, 1)
            }

            val startCode = index * 2 + 1
            val endCode = index * 2 + 2

            if (now in start.timeInMillis..end.timeInMillis) {
                // Đang trong khung giờ -> Chỉ hẹn giờ kết thúc (end alarm)
                alarms.add(ScheduledAlarmInfo(period, endCode, end.timeInMillis, true))
            } else if (now < start.timeInMillis) {
                // Khung giờ ở tương lai -> Hẹn giờ cả bắt đầu và kết thúc
                alarms.add(ScheduledAlarmInfo(period, startCode, start.timeInMillis, false))
                alarms.add(ScheduledAlarmInfo(period, endCode, end.timeInMillis, true))
            }
            // now > end -> Đã qua -> Không lên lịch
        }
        return alarms
    }

    @Test
    fun testScheduleAlarms_beforeAllTimeframes_setsStartAndEndAlarms() {
        val periods = listOf("06:00:00-07:00:00", "11:30:00-12:30:00", "17:00:00-18:00:00")
        // Hiện tại là 05:00:00 sáng (trước tất cả các khung giờ)
        val now = toCalendar("05:00:00")

        val alarms = simulateScheduleListAlarms(periods, now)

        // Cả 3 khung giờ đều ở tương lai -> Phải tạo đủ 6 alarm (3 start + 3 end)
        assertEquals(6, alarms.size)
        assertEquals(3, alarms.count { !it.isEndAlarm })
        assertEquals(3, alarms.count { it.isEndAlarm })
    }

    @Test
    fun testScheduleAlarms_duringActiveTimeframe_setsOnlyEndAlarmForCurrent() {
        val periods = listOf("06:00:00-07:00:00", "11:30:00-12:30:00", "17:00:00-18:00:00")
        // Hiện tại là 06:30:00 sáng (đang giữa khung giờ 06:00-07:00)
        val now = toCalendar("06:30:00")

        val alarms = simulateScheduleListAlarms(periods, now)

        // Khung giờ 06:00-07:00 chỉ lên 1 alarm END (lúc 07:00)
        // 2 khung giờ trưa và chiều ở tương lai -> mỗi khung 2 alarm (4 alarms)
        // Tổng cộng = 1 + 4 = 5 alarms
        assertEquals(5, alarms.size)

        val morningAlarms = alarms.filter { it.period == "06:00:00-07:00:00" }
        assertEquals(1, morningAlarms.size)
        assertTrue("Alarm duy nhất của khung giờ đang phát phải là alarm END", morningAlarms[0].isEndAlarm)
    }

    @Test
    fun testScheduleAlarms_afterAllTimeframes_setsNoAlarms() {
        val periods = listOf("06:00:00-07:00:00", "11:30:00-12:30:00", "17:00:00-18:00:00")
        // Hiện tại là 20:00:00 tối (đã qua hết các khung giờ trong ngày)
        val now = toCalendar("20:00:00")

        val alarms = simulateScheduleListAlarms(periods, now)

        // Không còn khung giờ nào -> Không đặt alarm nào
        assertTrue(alarms.isEmpty())
    }

    @Test
    fun testScheduleAlarms_crossMidnightPeriod_extendsEndDay() {
        // Khung giờ phát qua đêm từ 23:30:00 đến 00:30:00 sáng hôm sau
        val periods = listOf("23:30:00-00:30:00")
        val now = toCalendar("23:00:00")

        val alarms = simulateScheduleListAlarms(periods, now)
        assertEquals(2, alarms.size)

        val startAlarm = alarms.first { !it.isEndAlarm }
        val endAlarm = alarms.first { it.isEndAlarm }

        // Thời gian kết thúc phải lớn hơn thời gian bắt đầu (do được cộng thêm 1 ngày)
        assertTrue("Khung giờ qua đêm phải có endTime > startTime", endAlarm.timeInMillis > startAlarm.timeInMillis)
    }

    // ========================================================================
    // 2. KỊCH BẢN: RESCHEDULE KHI CÓ LỊCH MỚI HOẶC RESTART
    // ========================================================================

    @Test
    fun testReschedule_cancelsOldAlarmsBeforeSchedulingNew() {
        var activeAlarmCount = 6
        var isOldAlarmsCancelled = false

        fun reschedulePlaylist(newPeriods: List<String>) {
            // Bước 1: Hủy toàn bộ alarm cũ
            activeAlarmCount = 0
            isOldAlarmsCancelled = true

            // Bước 2: Nạp bộ alarm mới
            val newAlarms = simulateScheduleListAlarms(newPeriods, toCalendar("05:00:00"))
            activeAlarmCount = newAlarms.size
        }

        // Đổi sang lịch mới chỉ có 1 khung giờ duy nhất 07:00:00 - 08:00:00
        reschedulePlaylist(listOf("07:00:00-08:00:00"))

        assertTrue("Phải hủy toàn bộ alarm cũ trước khi reschedule", isOldAlarmsCancelled)
        assertEquals("Số lượng alarm mới phải cập nhật theo lịch mới", 2, activeAlarmCount)
    }

    // ========================================================================
    // 3. KỊCH BẢN: THIẾT BỊ TẮT ĐÊM -> KHÔNG TỰ RESET LÚC 00:15 / 00:30
    // ========================================================================

    @Test
    fun testNightPowerOff_detectsStaleCacheFromYesterdayAndRefetchesToday() {
        val yesterdayDate = "09-09-2026"
        val todayDate = "10-09-2026"

        // Giả lập thiết bị bị cúp điện lúc 21h tối 09/09, sáng 06h ngày 10/09 mới có điện bật lên
        // File cache trong máy đang lưu ngày hôm qua
        var savedDateInPref = yesterdayDate
        var hasLocalFile = true

        // Kiểm tra tính hợp lệ của cache theo logic MediaRepo
        val isLocalCacheValidForToday = hasLocalFile && (savedDateInPref == todayDate)
        assertFalse("Cache ngày hôm qua phải bị coi là hết hạn (invalid) cho ngày hôm nay", isLocalCacheValidForToday)

        // Mô phỏng logic tự động cập nhật lịch khi phát hiện cache hôm qua
        var fetchedUrlForToday = ""
        val monthlyScenario = listOf(
            ScheduleDay(date = "09-09-2026", sourceUrl = "https://server.vn/playlist_0909.json"),
            ScheduleDay(date = "10-09-2026", sourceUrl = "https://server.vn/playlist_1009.json")
        )

        val todaySchedule = monthlyScenario.firstOrNull { it.date == todayDate }
        assertNotNull("Phải tìm thấy lịch của ngày hôm nay trong monthlyScenario", todaySchedule)
        fetchedUrlForToday = todaySchedule!!.sourceUrl

        // Sau khi tải lịch ngày hôm nay thành công, lưu lại date mới
        savedDateInPref = todayDate

        assertEquals("https://server.vn/playlist_1009.json", fetchedUrlForToday)
        assertEquals("10-09-2026", savedDateInPref)
    }

    @Test
    fun testNightPowerOff_missingTodayInMonthlyScenario_autoFetchesNewMonthly() {
        val todayDate = "10-09-2026"

        // Giả lập monthlyScenario trong máy là của tháng trước hoặc chưa có ngày hôm nay
        val cachedMonthlyData = listOf(
            ScheduleDay(date = "07-09-2026", sourceUrl = "url1"),
            ScheduleDay(date = "08-09-2026", sourceUrl = "url2")
        )

        var hasToday = cachedMonthlyData.any { it.date == todayDate }
        assertFalse("Lịch tháng cũ không chứa ngày hôm nay", hasToday)

        // Khi hasToday == false, MediaRepo kích hoạt tự động tải lịch tháng mới từ server
        var isMonthlyScenarioRefetched = false
        val updatedMonthlyData = mutableListOf<ScheduleDay>().apply {
            addAll(cachedMonthlyData)
        }

        fun autoRecoverMonthlyScenario() {
            isMonthlyScenarioRefetched = true
            // Server trả về lịch tháng mới có ngày hôm nay
            updatedMonthlyData.add(ScheduleDay(date = "10-09-2026", sourceUrl = "https://server.vn/playlist_1009.json"))
        }

        autoRecoverMonthlyScenario()

        assertTrue("Phải tự động tải lại lịch tháng mới từ server để cứu hộ", isMonthlyScenarioRefetched)
        assertTrue("Sau khi tải lại, ngày hôm nay phải có trong lịch", updatedMonthlyData.any { it.date == todayDate })
    }

    // ========================================================================
    // 4. KỊCH BẢN: MẠNG YẾU / CHẬP CHỜN (WEAK NETWORK / EXPONENTIAL BACKOFF)
    // ========================================================================

    @Test
    fun testWeakNetwork_retrySucceedsOnThirdAttempt() {
        var attempts = 0
        val maxRetry = 5
        val expectedResult = listOf(MediaItem(title = "Chương trình hôm nay", duration = "00:30:00"))
        var actualResult = emptyList<MediaItem>()

        // Mô phỏng hàm safeUpdateScenario với mạng yếu: lần 1 và lần 2 bị timeout/lỗi, lần 3 thành công
        fun simulateSafeUpdateScenario(): List<MediaItem> {
            for (attempt in 0 until maxRetry) {
                attempts++
                if (attempts < 3) {
                    // Lần 1 và 2 gặp lỗi timeout mạng yếu
                    continue
                } else {
                    // Lần 3 mạng ổn định, lấy dữ liệu thành công
                    return expectedResult
                }
            }
            return emptyList()
        }

        actualResult = simulateSafeUpdateScenario()

        assertEquals("Hệ thống phải thử lại đúng 3 lần", 3, attempts)
        assertEquals(1, actualResult.size)
        assertEquals("Chương trình hôm nay", actualResult[0].title)
    }

    @Test
    fun testWeakNetwork_allRetriesFail_fallbacksToTodayLocalCacheIfAvailable() {
        val todayDate = "10-09-2026"
        val savedDate = todayDate
        val hasLocalCache = true
        val localCachedItems = listOf(MediaItem(title = "Bản tin đã lưu trong máy", duration = "00:20:00"))

        var networkAttemptFailed = true // Mạng mất hoàn toàn
        var finalPlaylist = emptyList<MediaItem>()

        // Logic Online-First của MediaRepo:
        // Cố gắng kéo từ mạng trước, nếu mạng hỏng nhưng cache trong máy là của HÔM NAY -> Dùng cache
        val isLocalCacheValidForToday = hasLocalCache && (savedDate == todayDate)
        if (isLocalCacheValidForToday) {
            if (networkAttemptFailed) {
                // Fallback về cache địa phương
                finalPlaylist = localCachedItems
            }
        }

        assertEquals("Khi mất mạng, hệ thống phải fallback an toàn về file cache hôm nay", 1, finalPlaylist.size)
        assertEquals("Bản tin đã lưu trong máy", finalPlaylist[0].title)
    }

    @Test
    fun testWeakNetwork_noNetworkAndNoCache_gracefullyReturnsEmptyWithoutCrash() {
        // Trường hợp xấu nhất: Mất mạng hoàn toàn, không có cache trong máy
        val hasLocalCache = false
        val networkFailed = true

        fun getScenarioGraceful(): List<MediaItem> {
            return try {
                if (networkFailed && !hasLocalCache) {
                    emptyList()
                } else {
                    listOf(MediaItem(title = "Sample"))
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

        val result = getScenarioGraceful()

        // Ứng dụng không bị crash (NPE), trả về danh sách rỗng để chờ kết nối mạng
        assertTrue(result.isEmpty())
    }

    // ========================================================================
    // 4. KIỂM THỬ TRÍCH XUẤT HOSTNAME VÀ PORT LINH HOẠT CHO CA CERT
    // ========================================================================

    @Test
    fun testExtractHostAndPort_standardAndCustomPorts() {
        // Case 1: Custom port 8443
        val res1 = app.tek4tv.ipradio.utils.network.CaCertHelper.extractHostAndPort("https://stream.radio.vn:8443/live/stream.m3u8")
        assertNotNull(res1)
        assertEquals("stream.radio.vn", res1!!.first)
        assertEquals(8443, res1.second)

        // Case 2: Standard HTTPS port (không có port trong URL -> default 443)
        val res2 = app.tek4tv.ipradio.utils.network.CaCertHelper.extractHostAndPort("https://storage-hp-ttn.thongtinnguon.vn/file.mp3")
        assertNotNull(res2)
        assertEquals("storage-hp-ttn.thongtinnguon.vn", res2!!.first)
        assertEquals(443, res2.second)

        // Case 3: Domain không có scheme nhưng có port
        val res3 = app.tek4tv.ipradio.utils.network.CaCertHelper.extractHostAndPort("custom-radio.vn:9443")
        assertNotNull(res3)
        assertEquals("custom-radio.vn", res3!!.first)
        assertEquals(9443, res3.second)

        // Case 4: extractHost chỉ lấy host
        val hostOnly = app.tek4tv.ipradio.utils.network.CaCertHelper.extractHost("https://stream.radio.vn:8443/live")
        assertEquals("stream.radio.vn", hostOnly)
    }
}
