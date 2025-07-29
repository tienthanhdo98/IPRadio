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
    private val requestResetAndRescheduleAlarms = 1929
    private val requestEndForce = 1989

    private fun scheduleListAlarms() {
        val scheduledList = mediaRepo.scheduledList
        for (i in scheduledList.keys.indices) {
            val period = scheduledList.keys.elementAt(i)

            val requestCode = mediaRepo.timeDividers[period]!![0].id

            val t = period.split("-")
            val start = toCalendar(t[0])

            val now = Calendar.getInstance().timeInMillis

            if (now < start.timeInMillis) {
                setAlarmForList(period, requestCode!!, start.timeInMillis)
            }
        }
    }

    private fun setAlarmForList(
        listKey: String, requestCode: Int, time: Long
    ) {
        val isAlarmWorking = getScheduleListIntent(
            listKey, requestCode,
            PendingIntent.FLAG_NO_CREATE
        )

        if (isAlarmWorking != null) return

        Log.d("alarmx", "Set start frame $listKey - $requestCode")
        val alarmIntent = getScheduleListIntent(listKey, requestCode, 0)

        val alarmClockInfo = AlarmClockInfo(time, alarmIntent)
        alarmManager.setAlarmClock(alarmClockInfo, alarmIntent)

    }


    private fun cancelAllListAlarm() {
        val scheduledList = mediaRepo.scheduledList

        for (i in scheduledList.keys.indices) {
            val period = scheduledList.keys.elementAt(i)

            val requestCode = mediaRepo.timeDividers[period]!![0].id
            val endRequestCode = mediaRepo.timeDividers[period]!![1].id

            cancelListAlarm(period, requestCode!!)
            cancelListAlarm(period, endRequestCode!!)
        }
    }

    private fun cancelListAlarm(
        listKey: String, requestCode: Int
    ) {
        val isAlarmWorking = getScheduleListIntent(
            listKey, requestCode,
            PendingIntent.FLAG_NO_CREATE
        ) != null

        if (!isAlarmWorking) return

        Log.d("alarmx", "Cancel $listKey - $requestCode")
        val alarmIntent = getScheduleListIntent(listKey, requestCode, 0)
        alarmManager.cancel(alarmIntent)
        alarmIntent?.cancel()
    }

    private fun getScheduleListIntent(
        listKey: String, requestCode: Int, flag: Int
    ): PendingIntent? {


        val intent = Intent(appContext, MediaScheduleReceiver::class.java).apply {
            Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            action = MediaScheduleReceiver.ACTION_PLAY_MEDIA_LIST
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
            Log.d("alarmx", "Cancel cancelForceMediaAlarm")
            cancelForceMediaAlarm()
        }
        cancelAllListAlarm()
    }

}