package app.tek4tv.ipradio.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Context
import android.content.Intent
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


    private fun setRescheduleAllAlarms() {
        val intent = Intent(appContext, MediaScheduleReceiver::class.java).apply {
            action = MediaScheduleReceiver.ACTION_RESCHEDULE
        }

        val time = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }

        Log.d("alarmx", "Set reschedule day ${time.get(Calendar.DAY_OF_MONTH)}")

        val alarmIntent =
            PendingIntent.getBroadcast(appContext, 101, intent, FLAG_IMMUTABLE)

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            time.timeInMillis,
            alarmIntent
        )
    }

    private fun setCheckHubAndResetAlarms() {
        val intent = Intent(appContext, MediaScheduleReceiver::class.java).apply {
            action = MediaScheduleReceiver.ACTION_CHECKHUB_RESET
        }
        val alarmIntent =
            PendingIntent.getBroadcast(appContext, 102, intent, FLAG_IMMUTABLE)

        val cal_alarm = Calendar.getInstance()

        cal_alarm[Calendar.HOUR_OF_DAY] =0
        cal_alarm[Calendar.MINUTE] = 3
        cal_alarm[Calendar.SECOND] = 0

        val date = Calendar.getInstance().time
        var alarmDate = cal_alarm.time
        if(alarmDate.before(date))
        {
            Log.d("alarmx", "yes")
            cal_alarm.add(Calendar.DATE,1)
        }
        alarmDate = cal_alarm.time

        Log.d("alarmx", "Set check no hub and reset  + ${cal_alarm.timeInMillis}")
        Log.d("alarmx", "Set check no hub and reset  + ${alarmDate}")
        Log.d("alarmx", "Set check no hub and reset  + ${date}")

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            cal_alarm.timeInMillis,
            alarmIntent
        )

    }

    private fun scheduleListAlarms() {
        val scheduledList = mediaRepo.scheduledList
        for (i in scheduledList.keys.indices) {
            val period = scheduledList.keys.elementAt(i)
            //setAlarmsForFrame(period)
            var isNewDayStarted = false
            val requestCode = mediaRepo.timeDividers[period]!![0].mediaID
            val endRequestCode = mediaRepo.timeDividers[period]!![1].mediaID

            val t = period.split("-")
            val start = toCalendar(t[0]) //start time
            val end = toCalendar(t[1]) //end time

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
                setAlarmForList(period, endRequestCode!!, end, true)
            } else if (now < start.timeInMillis) {
                setAlarmForList(period, requestCode!!, start)
                setAlarmForList(period, endRequestCode!!, end, true)
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

        Log.d("alarmx", "Set $shouldEndPlaying $listKey - $requestCode")
        val alarmIntent = getScheduleListIntent(listKey, requestCode, 0, shouldEndPlaying)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, time.timeInMillis,
            alarmIntent
        )
    }

    private fun cancelAllListAlarm() {
        val scheduledList = mediaRepo.scheduledList

        for (i in scheduledList.keys.indices) {
            val period = scheduledList.keys.elementAt(i)

            val requestCode = mediaRepo.timeDividers[period]!![0].mediaID
            val endRequestCode = mediaRepo.timeDividers[period]!![1].mediaID

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
            action = alarmAction
            putExtra(MediaScheduleReceiver.SCHEDULED_LIST_KEY, listKey)
        }

        return PendingIntent.getBroadcast(appContext, requestCode, intent, flag)
    }

    private fun getScheduleForceMediaIntent(flag: Int): PendingIntent? {
        val hasForceMedia = ConfigUtil.getBool(appContext, ConfigUtil.HAVE_FORCE_MEDIA, false)
        if (!hasForceMedia) return null

        val intent = Intent(appContext, MediaScheduleReceiver::class.java).apply {
            action = MediaScheduleReceiver.ACTION_END_FORCE_MEDIA
        }

        return PendingIntent.getBroadcast(appContext, -69, intent, flag)
    }

    fun scheduleForceMediaAlarm() {
        val alarmIntent = getScheduleForceMediaIntent(FLAG_IMMUTABLE) ?: return

        val fmValue = ConfigUtil.getString(appContext, ConfigUtil.FORCE_MEDIA, "")

        if (fmValue == null || fmValue == "") return

        val fm = ForceMedia.jsonAdapter.fromJson(fmValue) ?: return

        val mediaEndTimeInMillis = Calendar.getInstance().apply {
            time = SimpleDateFormat("dd/MM/yyyy HH:mm:ss").parse(fm.endTime)
        }.timeInMillis

        if (mediaEndTimeInMillis <= Calendar.getInstance().timeInMillis) return

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            mediaEndTimeInMillis+1000, alarmIntent
        )

        Log.d("alarmx", "set force ${mediaEndTimeInMillis+1000}")
    }

    private fun cancelForceMediaAlarm() {
        Log.d("alarmx", "cancel force")
        val alarmIntent = getScheduleForceMediaIntent(FLAG_IMMUTABLE) ?: return
        alarmManager.cancel(alarmIntent)
        alarmIntent.cancel()
    }

    fun scheduleAllAlarms() {
        scheduleListAlarms()
        setRescheduleAllAlarms()
        setCheckHubAndResetAlarms()
    }

    fun cancelAllScheduledListAlarm() {
        cancelAllListAlarm()
    }

    fun cancelAllAlarm() {
        if (ForceMedia.hasValidForceMedia(appContext)) {
            Log.d("alarmx", "Cancel cancelForceMediaAlarm")
            cancelForceMediaAlarm()
        }
        else {
            Log.d("alarmx", "Cancel cancelAllScheduledListAlarm")
            cancelAllScheduledListAlarm()
        }
    }
}