package app.tek4tv.ipradio.core

import android.app.AlarmManager
import android.app.AlarmManager.AlarmClockInfo
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import app.tek4tv.ipradio.model.ForceMedia
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.ui.MediaScheduleReceiver
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.toCalendar
import java.text.SimpleDateFormat
import java.util.*

class MediaScheduler(private val appContext: Context, private val mediaRepo: MediaRepo) {

    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val requestCodeRescheduleAllAlarm = 1010
    private val requestCodeCheckHubAndResetAlarms = 1020
    private val requestEndForce = 1080
    private fun setRescheduleAllAlarms() {
        val intent = Intent(appContext, MediaScheduleReceiver::class.java).apply {
            action = MediaScheduleReceiver.ACTION_RESCHEDULE
        }
        val time = Calendar.getInstance().apply {
//            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 3)
            set(Calendar.MINUTE, 5)
            set(Calendar.SECOND, 0)
        }
        Log.d("alarmx", "Set reschedule day ${time.get(Calendar.DAY_OF_MONTH)}")
        val alarmIntent =
            PendingIntent.getBroadcast(
                appContext,
                requestCodeRescheduleAllAlarm,
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT or FLAG_IMMUTABLE
            )
        val alarmClockInfo = AlarmClockInfo( time.timeInMillis, alarmIntent)
        alarmManager.setAlarmClock(alarmClockInfo, alarmIntent)
    }

    private fun scheduleListAlarms() {
        val scheduledList = mediaRepo.scheduledList
        for (i in scheduledList.keys.indices) {
            val period = scheduledList.keys.elementAt(i)
            val listMediaInPeriod = scheduledList.values.elementAt(i)
            //setAlarmsForFrame(period)
            var isNewDayStarted = false
            val requestCode = mediaRepo.timeDividers[period]!![0].id
            val endRequestCode = mediaRepo.timeDividers[period]!![1].id

            val t = period.split("-")
            val start = toCalendar(t[0])
            val end = toCalendar(t[1])

            if (start.get(Calendar.HOUR_OF_DAY) > end.get(Calendar.HOUR_OF_DAY)
            ) isNewDayStarted = true

            if (isNewDayStarted) {
                if (start.get(Calendar.HOUR_OF_DAY) > end.get(Calendar.HOUR_OF_DAY)) end.add(
                    Calendar.DAY_OF_MONTH, 1
                )
                else {
                    start.add(Calendar.DAY_OF_MONTH, 1)
                    end.add(Calendar.DAY_OF_MONTH, 1)
                }
            }

            val now = Calendar.getInstance().timeInMillis

            if (now in start.timeInMillis..end.timeInMillis) {
                setAlarmForList(
                    period,
                    endRequestCode!!,
                    end.timeInMillis + (listMediaInPeriod.size + 1) * 1000,
                    true
                )
            } else if (now < start.timeInMillis) {
                setAlarmForList(period, requestCode!!, start.timeInMillis)
                setAlarmForList(
                    period,
                    endRequestCode!!,
                    end.timeInMillis + (listMediaInPeriod.size + 1) * 1000,
                    true
                )
            }
        }
    }

    private fun setAlarmForList(
        listKey: String, requestCode: Int, time: Long, shouldEndPlaying: Boolean = false
    ) {
        val isAlarmWorking = getScheduleListIntent(
            listKey, requestCode,
            PendingIntent.FLAG_NO_CREATE, shouldEndPlaying
        )

        if (isAlarmWorking != null) return

        Log.d("alarmx", "Set $shouldEndPlaying $listKey - $requestCode")
        val alarmIntent = getScheduleListIntent(listKey, requestCode, 0, shouldEndPlaying)

        val alarmClockInfo = AlarmClockInfo(time, alarmIntent)
        alarmManager.setAlarmClock(alarmClockInfo, alarmIntent)

    }

    private fun convertTimeInMillisToHHmmss(timeInMillis: Long): String {
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return dateFormat.format(Date(timeInMillis))
    }

    private fun cancelAllListAlarm() {
        val scheduledList = mediaRepo.scheduledList

        for (i in scheduledList.keys.indices) {
            val period = scheduledList.keys.elementAt(i)

            val requestCode = mediaRepo.timeDividers[period]!![0].id
            val endRequestCode = mediaRepo.timeDividers[period]!![1].id

            cancelListAlarm(period, requestCode!!)
            cancelListAlarm(period, endRequestCode!!, true)
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
        val alarmIntent = getScheduleListIntent(listKey, requestCode, 0, shouldEndPlaying)
        alarmManager.cancel(alarmIntent)
        alarmIntent?.cancel()
    }

    private fun getScheduleListIntent(
        listKey: String, requestCode: Int, flag: Int, shouldEndPlaying: Boolean = false
    ): PendingIntent? {

        val alarmAction = if (shouldEndPlaying) MediaScheduleReceiver.ACTION_END_PLAYLIST
        else MediaScheduleReceiver.ACTION_PLAY_MEDIA_LIST

        val intent = Intent(appContext, MediaScheduleReceiver::class.java).apply {
            Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            action = alarmAction
            putExtra(MediaScheduleReceiver.SCHEDULED_LIST_KEY, listKey)
        }

        return PendingIntent.getBroadcast(appContext, requestCode, intent, flag)
    }


    fun scheduleForceMediaAlarm(forceMedia: ForceMedia) {

        val intent = Intent(appContext, MediaScheduleReceiver::class.java).apply {
            action = MediaScheduleReceiver.ACTION_END_FORCE_MEDIA
        }
        val alarmIntent =
            PendingIntent.getBroadcast(appContext, requestEndForce, intent, FLAG_UPDATE_CURRENT)
        Log.d("alarmx", "force end time ${forceMedia.endTime}")
        val mediaEndTimeInMillis = Calendar.getInstance().apply {
            time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(forceMedia.endTime)
        }
        mediaEndTimeInMillis.add(Calendar.SECOND, 2)
        if (mediaEndTimeInMillis.timeInMillis <= Calendar.getInstance().timeInMillis) return

        val alarmClockInfo = AlarmClockInfo(mediaEndTimeInMillis.timeInMillis, alarmIntent)
        alarmManager.setAlarmClock(alarmClockInfo, alarmIntent)
        Log.d(
            "alarmx",
            "set end force ${convertTimeInMillisToHHmmss(mediaEndTimeInMillis.timeInMillis)}"
        )
    }

    private fun setCheckHubAndResetAlarms() {
        val intent = Intent(appContext, MediaScheduleReceiver::class.java).apply {
            action = MediaScheduleReceiver.ACTION_CHECKHUB_RESET
        }
        val alarmIntent =
            PendingIntent.getBroadcast(appContext, requestCodeCheckHubAndResetAlarms, intent, 0)

        val cal_alarm = Calendar.getInstance()

        cal_alarm[Calendar.HOUR_OF_DAY] = 0
        cal_alarm[Calendar.MINUTE] = 15
        cal_alarm[Calendar.SECOND] = 0

        val date = Calendar.getInstance().time
        val alarmDate = cal_alarm.time
        if (alarmDate.before(date)) {
            cal_alarm.add(Calendar.DATE, 1)
        }
        cal_alarm.time

        val alarmClockInfo = AlarmClockInfo( cal_alarm.timeInMillis, alarmIntent)
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
        setRescheduleAllAlarms()
        setCheckHubAndResetAlarms()
        scheduleListAlarms()

    }

    fun cancelAllScheduledListAlarm() {
        if (ForceMedia.hasValidForceMedia(appContext)) {
            Log.d("alarmx", "Cancel cancelForceMediaAlarm")
            cancelForceMediaAlarm()
        }
        cancelAllListAlarm()
    }

}