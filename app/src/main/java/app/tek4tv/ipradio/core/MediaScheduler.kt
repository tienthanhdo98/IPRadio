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


    private fun setCheckHubAndResetAlarms() {
        val intent = Intent(appContext, MediaScheduleReceiver::class.java).apply {
            action = MediaScheduleReceiver.ACTION_CHECKHUB_RESET
        }
        val alarmIntent =
            PendingIntent.getBroadcast(appContext, 1902, intent, FLAG_IMMUTABLE)

        val cal_alarm = Calendar.getInstance()

        cal_alarm[Calendar.HOUR_OF_DAY] = 0
        cal_alarm[Calendar.MINUTE] = 15
        cal_alarm[Calendar.SECOND] = 0

        val date = Calendar.getInstance().time
        var alarmDate = cal_alarm.time
        if (alarmDate.before(date)) {
            Log.d("alarmx", "Set check hub reset once day: tomorrow")
            cal_alarm.add(Calendar.DATE, 1)
        }
        alarmDate = cal_alarm.time

        Log.d("alarmx", "Set check hub reset once day ${alarmDate}")

        val alarmClockInfo = AlarmManager.AlarmClockInfo(cal_alarm.timeInMillis, alarmIntent)
        alarmManager.setAlarmClock(alarmClockInfo, alarmIntent)

    }

    private fun scheduleListAlarms() {
        val scheduledList = mediaRepo.scheduledList
        for (i in scheduledList.keys.indices) {
            val period = scheduledList.keys.elementAt(i)
            //setAlarmsForFrame(period)
            var isNewDayStarted = false
            val requestCode = mediaRepo.timeDividers[period]!![0].id
            val endRequestCode = mediaRepo.timeDividers[period]!![1].id

            val requestCodeInt = requestCode.toString().toIntOrNull()
            val endRequestCodeInt = endRequestCode.toString().toIntOrNull()

            if (requestCodeInt == null || endRequestCodeInt == null) {
                // Bỏ qua period này nếu id không phải số
                continue
            }

            val t = period.split("-")
            if (t.size != 2) continue
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
                setAlarmForList(period, endRequestCodeInt, end, true)
            } else if (now < start.timeInMillis) {
                setAlarmForList(period, requestCodeInt, start)
                setAlarmForList(period, endRequestCodeInt, end, true)
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

        val alarmClockInfo = AlarmManager.AlarmClockInfo(time.timeInMillis, alarmIntent)
        alarmManager.setAlarmClock(alarmClockInfo, alarmIntent!!)
    }

    private fun cancelAllListAlarm() {
        val scheduledList = mediaRepo.scheduledList

        for (i in scheduledList.keys.indices) {
            val period = scheduledList.keys.elementAt(i)

            val requestCode = mediaRepo.timeDividers[period]!![0].id
            val endRequestCode = mediaRepo.timeDividers[period]!![1].id
            val requestCodeInt = requestCode.toString().toIntOrNull()
            val endRequestCodeInt = endRequestCode.toString().toIntOrNull()

            if (requestCodeInt != null) {
                cancelListAlarm(period, requestCodeInt)
            }
            if (endRequestCodeInt != null) {
                cancelListAlarm(period, endRequestCodeInt, true)
            }

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

    private fun getScheduleForceMediaIntent(): PendingIntent? {
        val hasForceMedia = ConfigUtil.getBool(appContext, ConfigUtil.HAVE_FORCE_MEDIA, false)
        if (!hasForceMedia) return null

        val intent = Intent(appContext, MediaScheduleReceiver::class.java).apply {
            action = MediaScheduleReceiver.ACTION_END_FORCE_MEDIA
        }

        return PendingIntent.getBroadcast(appContext, -69, intent, FLAG_IMMUTABLE)
    }

    fun scheduleForceMediaAlarm() {
        val alarmIntent = getScheduleForceMediaIntent() ?: return

        val fmValue = ConfigUtil.getString(appContext, ConfigUtil.FORCE_MEDIA, "")

        if (fmValue == null || fmValue == "") return

        val fm = ForceMedia.jsonAdapter.fromJson(fmValue) ?: return

        val mediaEndTimeInMillis = Calendar.getInstance().apply {
            time = SimpleDateFormat("dd/MM/yyyy HH:mm:ss").parse(fm.endTime)
        }.timeInMillis

        if (mediaEndTimeInMillis <= Calendar.getInstance().timeInMillis) return

        val alarmClockInfo = AlarmManager.AlarmClockInfo(mediaEndTimeInMillis + 1000, alarmIntent)
        alarmManager.setAlarmClock(alarmClockInfo, alarmIntent)

        Log.d("alarmx", "set force ${mediaEndTimeInMillis + 1000}")
    }

    fun turnOnPAAlarms(mediaRepo: MediaRepo, alarmValue: Int) {
        Log.i("alarm", "turnOnPAAlarms: start")
        val scheduledList = mediaRepo.scheduledList
        val intent = Intent(appContext, MediaScheduleReceiver::class.java).apply {
            action = MediaScheduleReceiver.ACTION_TURN_ON_PA
        }
        for (i in scheduledList.keys.indices) {
            val period = scheduledList.keys.elementAt(i)
            val t = period.split("-")
            val start = toCalendar(t[0]).timeInMillis - (alarmValue * 60 * 1000)

            val alarmIntent =
                PendingIntent.getBroadcast(appContext, i, intent, FLAG_IMMUTABLE)

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                start,
                alarmIntent
            )

            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
            Log.i("ádsadsad", "turnOnPAAlarms: ${dateFormat.format(start)}")
        }
    }

    private fun cancelForceMediaAlarm() {
        Log.d("alarmx", "cancel force")
        val alarmIntent = getScheduleForceMediaIntent() ?: return
        alarmManager.cancel(alarmIntent)
        alarmIntent.cancel()
    }

    fun scheduleAllAlarms() {
        scheduleListAlarms()
        setCheckHubAndResetAlarms()
    }


    fun cancelAllAlarm() {
        cancelForceMediaAlarm()
        cancelAllListAlarm()

    }
}