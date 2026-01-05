package app.tek4tv.ipradio.core.scheduler

import android.app.AlarmManager
import android.app.AlarmManager.AlarmClockInfo
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.Intent
import android.util.Log
import app.tek4tv.ipradio.model.ForceMedia
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.toCalendar
import java.text.SimpleDateFormat
import java.util.*

class MediaScheduler(private val appContext: Context, private val mediaRepo: MediaRepo) {

    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val requestResetAndRescheduleAlarms = 2056
    private val requestEndForce = 2036

    private fun scheduleListAlarms() {
        val scheduledList = mediaRepo.scheduledList
        for (i in scheduledList.keys.indices) {
            val period = scheduledList.keys.elementAt(i)

            val requestCode = mediaRepo.timeDividers[period]!![0].id
            val endRequestCode = mediaRepo.timeDividers[period]!![1].id

            val turnOnPaRequestCode = requestCode!! + 20000
            val t = period.split("-")
            val start = toCalendar(t[0])
            val end = toCalendar(t[1]) //end time

            val now = Calendar.getInstance().timeInMillis

            when {
                // Đang trong time frame
                now in start.timeInMillis..end.timeInMillis -> {
                    setAlarmForList(period, endRequestCode!!, end, true)
                }
                // Chưa đến time frame
                now < start.timeInMillis -> {
                    setAlarmForList(period, requestCode, start)
                    setAlarmForList(period, endRequestCode!!, end, true)

                    // Schedule Turn On PA cho time frame này
                    val turnOnTime = ConfigUtil.getInt(
                        context = appContext,
                        key = ConfigUtil.TIME_TURN_ON_PA,
                        defaultValue = 120
                    )
                    scheduleTurnOnPA(turnOnPaRequestCode, start, turnOnTime)
                }
            }

        }

    }

    private fun setAlarmForList(
        listKey: String, requestCode: Int, time: Calendar, shouldEndPlaying: Boolean = false
    ) {
        val isAlarmWorking = getScheduleListIntent(
            listKey, requestCode,
            PendingIntent.FLAG_NO_CREATE, shouldEndPlaying
        )
        if (isAlarmWorking != null) return

        Log.d(
            "alarmx",
            "Set Alarm ${if (shouldEndPlaying) "End" else "Start"} : $listKey - $requestCode"
        )
        val alarmIntent =
            getScheduleListIntent(listKey, requestCode, FLAG_IMMUTABLE, shouldEndPlaying)

        val alarmClockInfo = AlarmClockInfo(time.timeInMillis, alarmIntent)
        alarmManager.setAlarmClock(alarmClockInfo, alarmIntent!!)

    }


    private fun cancelAllListAlarm() {
        val scheduledList = mediaRepo.scheduledList

        for (i in scheduledList.keys.indices) {
            val period = scheduledList.keys.elementAt(i)

            val requestCode = mediaRepo.timeDividers[period]!![0].id
            val endRequestCode = mediaRepo.timeDividers[period]!![1].id
            val turnOnPaRequestCode = requestCode!! + 20000

            cancelListAlarm(period, requestCode!!)
            cancelListAlarm(period, endRequestCode!!, true)
            cancelTurnOnPaAlarm(turnOnPaRequestCode)
        }
    }


    private fun cancelListAlarm(
        listKey: String, requestCode: Int, shouldEndPlaying: Boolean = false
    ) {
        val isAlarmWorking = getScheduleListIntent(
            listKey, requestCode,
            PendingIntent.FLAG_NO_CREATE, shouldEndPlaying
        ) != null

        if (!isAlarmWorking) return

        Log.d("alarmx", "Cancel $listKey - $requestCode")
        val alarmIntent =
            getScheduleListIntent(listKey, requestCode, FLAG_IMMUTABLE, shouldEndPlaying)
        alarmManager.cancel(alarmIntent!!)
        alarmIntent.cancel()
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


    private fun scheduleHardResetAlarmsAndReschedule() {
        val intent = Intent(appContext, MediaScheduleReceiver::class.java).apply {
            action = MediaScheduleReceiver.ACTION_CHECKHUB_RESET
        }
        val alarmIntent =
            PendingIntent.getBroadcast(
                appContext,
                requestResetAndRescheduleAlarms,
                intent,
                FLAG_IMMUTABLE
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
        val alarmClockInfo = AlarmClockInfo(cal_alarm.timeInMillis, alarmIntent)
        alarmManager.setAlarmClock(alarmClockInfo, alarmIntent)
    }

    private fun scheduleTurnOnPA(requestCode: Int, startTimeFrame: Calendar, timeTurnOn: Int) {
        val intent = Intent(appContext, MediaScheduleReceiver::class.java).apply {
            action = MediaScheduleReceiver.ACTION_TURN_ON_PA
        }
        val alarmIntent =
            PendingIntent.getBroadcast(
                appContext,
                requestCode,
                intent,
                FLAG_IMMUTABLE
            )

        val cal_alarm = startTimeFrame.clone() as Calendar
        // Trừ đi timeTurnOn giây để alarm chạy trước startTimeFrame
        cal_alarm.add(Calendar.SECOND, -timeTurnOn)

        // Kiểm tra nếu thời gian alarm đã qua (trong quá khứ)
        val currentTime = Calendar.getInstance()
        if (cal_alarm.before(currentTime)) {
            // Nếu đã qua, chuyển sang ngày hôm sau
            cal_alarm.add(Calendar.DATE, 1)
        }
        Log.d("alarmx", "scheduleTurnOnPA $requestCode")
        Log.d("alarmx", "scheduleTurnOnPA at: ${cal_alarm.time}")
        val alarmClockInfo = AlarmClockInfo(cal_alarm.timeInMillis, alarmIntent)
        alarmManager.setAlarmClock(alarmClockInfo, alarmIntent)
    }

    private fun cancelTurnOnPaAlarm(requestCode: Int) {
        val intent = Intent(appContext, MediaScheduleReceiver::class.java).apply {
            action = MediaScheduleReceiver.ACTION_TURN_ON_PA
        }
        val alarmIntent = PendingIntent.getBroadcast(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE
        )

        if (alarmIntent != null) {
            Log.d("alarmx", "Cancel Turn On PA - $requestCode")
            alarmManager.cancel(alarmIntent)
            alarmIntent.cancel()
        }
    }

    fun scheduleForceMediaAlarm(forceMedia: MediaItem) {

        val intent = Intent(appContext, MediaScheduleReceiver::class.java).apply {
            action = MediaScheduleReceiver.ACTION_END_FORCE_MEDIA
        }
        val alarmIntent =
            PendingIntent.getBroadcast(appContext, requestEndForce, intent, FLAG_UPDATE_CURRENT)
        Log.d("alarmx", "schedule force end : ${forceMedia.end}")
        val mediaEndTimeInMillis = Calendar.getInstance().apply {
            time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(forceMedia.end)
        }
        mediaEndTimeInMillis.add(Calendar.SECOND, 2)
        //if (mediaEndTimeInMillis.timeInMillis <= Calendar.getInstance().timeInMillis) return

        val alarmClockInfo = AlarmClockInfo(mediaEndTimeInMillis.timeInMillis, alarmIntent)
        alarmManager.setAlarmClock(alarmClockInfo, alarmIntent)
    }

    private fun getScheduleForceMediaIntent(flag: Int): PendingIntent? {
        val hasForceMedia = ConfigUtil.getBool(appContext, ConfigUtil.HAVE_FORCE_MEDIA, false)
        if (!hasForceMedia) return null

        val intent = Intent(appContext, MediaScheduleReceiver::class.java).apply {
            action = MediaScheduleReceiver.ACTION_END_FORCE_MEDIA
        }

        return PendingIntent.getBroadcast(appContext, requestEndForce, intent, flag)
    }

    private fun cancelForceMediaAlarm() {
        Log.d("alarmx", "cancel force")
        val alarmIntent = getScheduleForceMediaIntent(PendingIntent.FLAG_NO_CREATE) ?: return
        alarmManager.cancel(alarmIntent)
        alarmIntent.cancel()
    }

    fun scheduleAllAlarms() {
        scheduleHardResetAlarmsAndReschedule()
        scheduleListAlarms()
    }

    fun cancelAllScheduledListAlarm() {
        if (ForceMedia.hasValidForceMedia(appContext)) {
            Log.d("alarmx", "Cancel ForceMediaAlarm")
            cancelForceMediaAlarm()
        }
        cancelAllListAlarm()
    }

}