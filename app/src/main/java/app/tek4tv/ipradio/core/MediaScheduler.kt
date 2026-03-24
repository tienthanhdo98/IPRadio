package app.tek4tv.ipradio.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Context
import android.content.Intent
import android.util.Log
import app.tek4tv.ipradio.model.ForceMedia
import app.tek4tv.ipradio.model.MediaInfo
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.ui.MediaScheduleReceiver
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.ConfigUtil.BASE_ALARM_ID
import app.tek4tv.ipradio.utils.Utils.Companion.convertSecondsToCalendar
import app.tek4tv.ipradio.utils.convertCalendarToString
import app.tek4tv.ipradio.utils.toCalendar
import java.text.SimpleDateFormat
import java.util.Calendar
import kotlin.time.Duration

class MediaScheduler(private val appContext: Context, private val mediaRepo: MediaRepo) {

    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val requestResetAndRescheduleAlarms = 1929
    private val requestEndForce = 1989

    private fun setCheckHubAndResetAlarms() {
        val intent = Intent(appContext, MediaScheduleReceiver::class.java).apply {
            action = MediaScheduleReceiver.ACTION_CHECKHUB_RESET
        }
        val alarmIntent = PendingIntent.getBroadcast(
            appContext, requestResetAndRescheduleAlarms, intent, FLAG_IMMUTABLE
        )

        val cal_alarm = Calendar.getInstance()

        cal_alarm[Calendar.HOUR_OF_DAY] = 0
        cal_alarm[Calendar.MINUTE] = 12
        cal_alarm[Calendar.SECOND] = 0

        val date = Calendar.getInstance().time
        val alarmDate = cal_alarm.time
        if (alarmDate.before(date)) {
            cal_alarm.add(Calendar.DATE, 1)
        }
        cal_alarm.time
        Log.d("alarmx", "scheduleHardResetAlarmsAndReschedule")
        val alarmClockInfo = AlarmManager.AlarmClockInfo(cal_alarm.timeInMillis, alarmIntent)
        alarmManager.setAlarmClock(alarmClockInfo, alarmIntent)
    }

    //MGW
//    private fun scheduleListAlarms() {
//        val scheduledList = mediaRepo.scheduledList
//        val baseTimestamp = (System.currentTimeMillis() % 1000000).toInt()
//        ConfigUtil.putInt(appContext, BASE_ALARM_ID, baseTimestamp)
//        for (i in scheduledList.keys.indices) {
//            val now = Calendar.getInstance().timeInMillis
//            val period = scheduledList.keys.elementAt(i)
//            val t = period.split("-")
//            if (t.size != 2) continue
//            val start = toCalendar(t[0]) //start time
//            val end = toCalendar(t[1]) //end time
//            val startReqCode = baseTimestamp + (i * 2)
//            val endReqCode = baseTimestamp + (i * 2) + 1
//
//            if (now in start.timeInMillis..end.timeInMillis) {
//                setAlarmForList(period, endReqCode, end, true)
//            } else if (now < start.timeInMillis) {
//                setAlarmForList(period, startReqCode, start)
//                setAlarmForList(period, endReqCode, end, true)
//            }
//        }
//    }

    private fun buildFinalSegments(): List<Triple<Long, Long, String>> {
        val scheduledList = mediaRepo.scheduledList
        if (scheduledList.isEmpty()) return emptyList()

        val timePoints = mutableSetOf<Long>()
        for (period in scheduledList.keys) {
            val t = period.split("-")
            if (t.size != 2) continue
            timePoints.add(toCalendar(t[0]).timeInMillis)
            timePoints.add(toCalendar(t[1]).timeInMillis)
        }

        val sortedPoints = timePoints.toList().sorted()
        if (sortedPoints.size < 2) return emptyList()

        val rawSegments = mutableListOf<Pair<LongRange, String>>()

        for (i in 0 until sortedPoints.size - 1) {
            val segStart = sortedPoints[i]
            val segEnd = sortedPoints[i + 1]

            var bestMedia: MediaInfo? = null
            var bestPeriodKey: String? = null

            for ((period, mediaInfo) in scheduledList) {
                val t = period.split("-")
                if (t.size != 2) continue

                val originalStart = toCalendar(t[0]).timeInMillis
                val originalEnd = toCalendar(t[1]).timeInMillis

                if (segStart >= originalStart && segEnd <= originalEnd) {
                    if (bestMedia == null) {
                        bestMedia = mediaInfo
                        bestPeriodKey = period
                    } else {
                        val currentPrio = mediaInfo.prio ?: 0
                        val bestPrio = bestMedia.prio ?: 0

                        val currentVer = mediaInfo.version ?: 0
                        val bestVer = bestMedia.version ?: 0

                        val currentStart = mediaInfo.start ?: 0L
                        val bestStart = bestMedia.start ?: 0L

                        val currentCreated = mediaInfo.created ?: 0L
                        val bestCreated = bestMedia.created ?: 0L

                        val isBetter = when {
                            currentPrio > bestPrio -> true
                            currentPrio < bestPrio -> false

                            currentVer > bestVer -> true
                            currentVer < bestVer -> false

                            currentStart > bestStart -> true
                            currentStart < bestStart -> false

                            currentCreated > bestCreated -> true
                            currentCreated < bestCreated -> false
                            //neu bang nhau thi cai sau thang
                            else -> true
                        }

                        if (isBetter) {
                            bestMedia = mediaInfo
                            bestPeriodKey = period
                        }
                    }
                }
            }

            bestPeriodKey?.let {
                rawSegments.add((segStart..segEnd) to it)
            }
        }

        val finalSegments = mutableListOf<Triple<Long, Long, String>>()
        if (rawSegments.isNotEmpty()) {
            var currentStart = rawSegments[0].first.first
            var currentEnd = rawSegments[0].first.last
            var currentKey = rawSegments[0].second

            for (i in 1 until rawSegments.size) {
                val nextStart = rawSegments[i].first.first
                val nextEnd = rawSegments[i].first.last
                val nextKey = rawSegments[i].second

                if (nextKey == currentKey && nextStart == currentEnd) {
                    currentEnd = nextEnd
                } else {
                    finalSegments.add(Triple(currentStart, currentEnd, currentKey))
                    currentStart = nextStart
                    currentEnd = nextEnd
                    currentKey = nextKey
                }
            }
            finalSegments.add(Triple(currentStart, currentEnd, currentKey))
        }

        return finalSegments
    }

    private fun cancelAllListAlarm() {
        val baseTimestamp = ConfigUtil.getInt(appContext, BASE_ALARM_ID, -1)
        if (baseTimestamp == -1) return

        val finalSegments = buildFinalSegments()

        finalSegments.forEachIndexed { index, segment ->
            val periodKey = segment.third
            val startReqCode = baseTimestamp + (index * 2)
            val endReqCode = baseTimestamp + (index * 2) + 1

            cancelListAlarm(periodKey, startReqCode)
            cancelListAlarm(periodKey, endReqCode, true)
        }

        ConfigUtil.putInt(appContext, BASE_ALARM_ID, -1)
    }

    private fun cancelListAlarm(
        listKey: String,
        requestCode: Int,
        shouldEndPlaying: Boolean = false
    ) {
        val alarmIntent = getScheduleListIntent(
            listKey,
            requestCode,
            PendingIntent.FLAG_NO_CREATE,
            shouldEndPlaying
        ) ?: return

        Log.d("MediaInfo", "Cancel alarm $listKey - $requestCode")
        alarmManager.cancel(alarmIntent)
        alarmIntent.cancel()
    }

    private fun scheduleListAlarms() {
        cancelAllListAlarm()

        val finalSegments = buildFinalSegments()
        if (finalSegments.isEmpty()) return

        val baseTimestamp = (System.currentTimeMillis() % 1000000).toInt()
        val now = Calendar.getInstance().timeInMillis
        ConfigUtil.putInt(appContext, BASE_ALARM_ID, baseTimestamp)

        finalSegments.forEachIndexed { index, segment ->
            val segStart = segment.first
            val segEnd = segment.second
            val periodKey = segment.third

            val startCal = Calendar.getInstance().apply { timeInMillis = segStart }
            val endCal = Calendar.getInstance().apply { timeInMillis = segEnd }

            val startReqCode = baseTimestamp + (index * 2)
            val endReqCode = baseTimestamp + (index * 2) + 1

            if (now in segStart until segEnd) {
                setAlarmForList(periodKey, endReqCode, endCal, true)
            } else if (now < segStart) {
                setAlarmForList(periodKey, startReqCode, startCal)
                setAlarmForList(periodKey, endReqCode, endCal, true)
            }
        }
    }

//    private fun scheduleListAlarms() {
//        val scheduledList = mediaRepo.scheduledList
//        if (scheduledList.isEmpty()) return
//
//        // 1. Thu thập tất cả mốc thời gian (bắt đầu & kết thúc) và sắp xếp tăng dần
//        val timePoints = mutableSetOf<Long>()
//        for (period in scheduledList.keys) {
//            val t = period.split("-")
//            if (t.size != 2) continue
//            timePoints.add(toCalendar(t[0]).timeInMillis)
//            timePoints.add(toCalendar(t[1]).timeInMillis)
//        }
//        val sortedPoints = timePoints.toList().sorted()
//
//        // 2. Chia thành các đoạn nhỏ và tìm bản tin "thắng cuộc" cho mỗi đoạn
//        val rawSegments = mutableListOf<Pair<LongRange, String>>() // <Khoảng thời gian, PeriodKey>
//
//        for (i in 0 until sortedPoints.size - 1) {
//            val segStart = sortedPoints[i]
//            val segEnd = sortedPoints[i + 1]
//
//            var bestMedia: MediaInfo? = null
//            var bestPeriodKey: String? = null
//
//            for ((period, mediaInfo) in scheduledList) {
//                val t = period.split("-")
//                val originalStart = toCalendar(t[0]).timeInMillis
//                val originalEnd = toCalendar(t[1]).timeInMillis
//
//                // Kiểm tra xem đoạn [segStart, segEnd] có nằm trong khung giờ gốc không
//                if (segStart >= originalStart && segEnd <= originalEnd) {
//                    if (bestMedia == null) {
//                        bestMedia = mediaInfo
//                        bestPeriodKey = period
//                    } else {
//                        // --- LOGIC SO SÁNH ƯU TIÊN ---
//                        val currentPrio = mediaInfo.prio ?: 0
//                        val bestPrio = bestMedia.prio ?: 0
//
//                        val currentVer = mediaInfo.version ?: 0
//                        val bestVer = bestMedia.version ?: 0
//
//                        val currentStart = mediaInfo.start ?: 0L
//                        val bestStart = bestMedia.start ?: 0L
//
//                        val currentCreated = mediaInfo.created ?: 0L
//                        val bestCreated = bestMedia.created ?: 0L
//
//                        // Ưu tiên: Prio > Version > Start Time
////                        val isBetter = when {
////                            currentPrio > bestPrio -> true
////                            currentPrio < bestPrio -> false
////                            currentVer > bestVer -> true
////                            currentVer < bestVer -> false
////                            currentStart > bestStart -> true
////                            else -> false
////                        }
////                        val isBetter = when {
////                            currentPrio > bestPrio -> true
////                            currentPrio < bestPrio -> false
////                            currentVer > bestVer -> true
////                            currentVer < bestVer -> false
////                            currentStart > bestStart -> true
////                            currentStart < bestStart -> false
////                            else -> false // tất cả bằng nhau -> cái sau đè cái trước
////                        }
//                        val isBetter = when {
//                            currentPrio > bestPrio -> true
//                            currentPrio < bestPrio -> false
//
//                            currentVer > bestVer -> true
//                            currentVer < bestVer -> false
//
//                            currentStart > bestStart -> true
//                            currentStart < bestStart -> false
//
//                            currentCreated > bestCreated -> true
//                            currentCreated < bestCreated -> false
//
//                            else -> true // bằng sạch thì cái duyệt sau thắng
//                        }
//
//
//                        if (isBetter) {
//                            bestMedia = mediaInfo
//                            bestPeriodKey = period
//                        }
//                    }
//                }
//            }
//
//            bestPeriodKey?.let {
//                rawSegments.add(Pair(segStart..segEnd, it))
//            }
//        }
//
//        // 3. GỘP CÁC ĐOẠN LIÊN TIẾP (Nếu cùng 1 PeriodKey thì gộp lại cho mượt)
//        val finalSegments = mutableListOf<Triple<Long, Long, String>>() // <Start, End, PeriodKey>
//        if (rawSegments.isNotEmpty()) {
//            var currentStart = rawSegments[0].first.first
//            var currentEnd = rawSegments[0].first.last
//            var currentKey = rawSegments[0].second
//
//            for (i in 1 until rawSegments.size) {
//                val nextStart = rawSegments[i].first.first
//                val nextEnd = rawSegments[i].first.last
//                val nextKey = rawSegments[i].second
//
//                if (nextKey == currentKey && nextStart == currentEnd) {
//                    // Nếu cùng bản tin và nối tiếp nhau -> Kéo dài thời gian End
//                    currentEnd = nextEnd
//                } else {
//                    // Khác bản tin -> Lưu đoạn cũ, bắt đầu đoạn mới
//                    finalSegments.add(Triple(currentStart, currentEnd, currentKey))
//                    currentStart = nextStart
//                    currentEnd = nextEnd
//                    currentKey = nextKey
//                }
//            }
//            finalSegments.add(Triple(currentStart, currentEnd, currentKey))
//        }
//
//        // 4. ĐẶT ALARM DỰA TRÊN DANH SÁCH ĐÃ GỘP
//        val baseTimestamp = (System.currentTimeMillis() % 1000000).toInt()
//        val now = Calendar.getInstance().timeInMillis
//        ConfigUtil.putInt(appContext, BASE_ALARM_ID, baseTimestamp)
//
//        finalSegments.forEachIndexed { index, segment ->
//            val segStart = segment.first
//            val segEnd = segment.second
//            val periodKey = segment.third
//
//            val startCal = Calendar.getInstance().apply { timeInMillis = segStart }
//            val endCal = Calendar.getInstance().apply { timeInMillis = segEnd }
//
//            val startReqCode = baseTimestamp + (index * 2)
//            val endReqCode = baseTimestamp + (index * 2) + 1
//
//            if (now in segStart until segEnd) {
//                // Đang trong giờ phát: Đặt báo thức kết thúc
//                setAlarmForList(periodKey, endReqCode, endCal, true)
//            } else if (now < segStart) {
//                // Tương lai: Đặt cả báo thức bắt đầu và kết thúc
//                setAlarmForList(periodKey, startReqCode, startCal)
//                setAlarmForList(periodKey, endReqCode, endCal, true)
//            }
//        }
//    }

    private fun setAlarmForList(
        listKey: String,
        requestCode: Int,
        time: Calendar,
        shouldEndPlaying: Boolean = false
    ) {
        val isAlarmWorking = getScheduleListIntent(
            listKey, requestCode, PendingIntent.FLAG_NO_CREATE, shouldEndPlaying
        )

//        Log.d(
//            "MediaInfo",
//            "check alarm ${if (shouldEndPlaying) "End" else "Start"} $listKey - $requestCode, existed=${isAlarmWorking != null}, trigger=${time.timeInMillis}"
//        )

        if (isAlarmWorking != null) return

        Log.d(
            "MediaInfo",
            "Alarm Set ${if (shouldEndPlaying) "End" else "Start"} $listKey - $requestCode"
        )

        val alarmIntent = getScheduleListIntent(listKey, requestCode, 0, shouldEndPlaying)
        val alarmClockInfo = AlarmManager.AlarmClockInfo(time.timeInMillis, alarmIntent)
        alarmManager.setAlarmClock(alarmClockInfo, alarmIntent)
    }

    private fun getScheduleListIntent(
        listKey: String, requestCode: Int, flag: Int, shouldEndPlaying: Boolean = false
    ): PendingIntent? {

        val alarmAction = if (shouldEndPlaying) MediaScheduleReceiver.ACTION_END_PLAYLIST
        else MediaScheduleReceiver.ACTION_PLAY_MEDIA_LIST

        val intent = Intent(appContext, MediaScheduleReceiver::class.java).apply {
            action = alarmAction
            putExtra(MediaScheduleReceiver.SCHEDULED_LIST_KEY, listKey)
        }

        return PendingIntent.getBroadcast(appContext, requestCode, intent, flag)
    }

//    private fun cancelAllListAlarm() {
//        val scheduledList = mediaRepo.scheduledList
//        val baseTimestamp = ConfigUtil.getInt(appContext, "BASE_ALARM_ID", -1)
//        if (baseTimestamp == -1) return // Không có alarm nào để hủy
//        for (i in scheduledList.keys.indices) {
//
//            val period = scheduledList.keys.elementAt(i)
//            val startReqCode = baseTimestamp + (i * 2)
//            val endReqCode = baseTimestamp + (i * 2) + 1
//            cancelListAlarm(period, startReqCode)
//            cancelListAlarm(period, endReqCode, true)
//        }
//    }

//    private fun cancelListAlarm(
//        listKey: String, requestCode: Int, shouldEndPlaying: Boolean = false
//    ) {
//        val isAlarmWorking = getScheduleListIntent(
//            listKey, requestCode, PendingIntent.FLAG_NO_CREATE, shouldEndPlaying
//        ) != null
//
//        if (!isAlarmWorking) return
//
//        Log.d("MediaInfo", "Cancel alarm $listKey - $requestCode")
//        val alarmIntent = getScheduleListIntent(listKey, requestCode, 0, shouldEndPlaying)
//        alarmManager.cancel(alarmIntent)
//        alarmIntent?.cancel()
//    }
    ///

    private fun getScheduleForceMediaIntent(flag: Int): PendingIntent? {
        val hasForceMedia = ConfigUtil.getBool(appContext, ConfigUtil.HAVE_FORCE_MEDIA, false)
        if (!hasForceMedia) return null

        val intent = Intent(appContext, MediaScheduleReceiver::class.java).apply {
            action = MediaScheduleReceiver.ACTION_END_FORCE_MEDIA
        }

        return PendingIntent.getBroadcast(appContext, requestEndForce, intent, flag)
    }

    fun scheduleForceMediaAlarm(endTime: Int) {
        val intent = Intent(appContext, MediaScheduleReceiver::class.java).apply {
            action = MediaScheduleReceiver.ACTION_END_FORCE_MEDIA
        }
        val alarmIntent =
            PendingIntent.getBroadcast(appContext, requestEndForce, intent, FLAG_IMMUTABLE)

        val end = convertSecondsToCalendar(endTime).timeInMillis

        val alarmClockInfo = AlarmManager.AlarmClockInfo(end + 1000, alarmIntent)
        alarmManager.setAlarmClock(alarmClockInfo, alarmIntent)
        Log.d(
            "MediaInfo",
            "Live set force end ${convertCalendarToString(convertSecondsToCalendar(endTime))}"
        )
    }

    private fun cancelForceMediaAlarm() {
        Log.d("alarmx", "cancel force")
        val alarmIntent = getScheduleForceMediaIntent(FLAG_IMMUTABLE) ?: return
        alarmManager.cancel(alarmIntent)
        alarmIntent.cancel()
    }

    fun scheduleAllAlarms() {
        scheduleListAlarms()
        setCheckHubAndResetAlarms()
    }

    fun cancelAllScheduledListAlarm() {
        cancelAllListAlarm()
    }

    fun cancelAllAlarm() {
        if (ForceMedia.hasValidForceMedia(appContext)) {
            Log.d("alarmx", "Cancel cancelForceMediaAlarm")
            cancelForceMediaAlarm()
        } else {
            Log.d("alarmx", "Cancel cancelAllScheduledListAlarm")
            cancelAllScheduledListAlarm()
        }
    }
}