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
    private fun scheduleListAlarms() {
        val scheduledList = mediaRepo.scheduledList
        val baseTimestamp = (System.currentTimeMillis() % 1000000).toInt()
        ConfigUtil.putInt(appContext, BASE_ALARM_ID, baseTimestamp)
        for (i in scheduledList.keys.indices) {
            val now = Calendar.getInstance().timeInMillis
            val period = scheduledList.keys.elementAt(i)
            val t = period.split("-")
            if (t.size != 2) continue
            val start = toCalendar(t[0]) //start time
            val end = toCalendar(t[1]) //end time
            val startReqCode = baseTimestamp + (i * 2)
            val endReqCode = baseTimestamp + (i * 2) + 1

            if (now in start.timeInMillis..end.timeInMillis) {
                setAlarmForList(period, endReqCode, end, true)
            } else if (now < start.timeInMillis) {
                setAlarmForList(period, startReqCode, start)
                setAlarmForList(period, endReqCode, end, true)
            }
        }
    }

    private fun setAlarmForList(
        listKey: String, requestCode: Int, time: Calendar, shouldEndPlaying: Boolean = false
    ) {
        val isAlarmWorking = getScheduleListIntent(
            listKey, requestCode, PendingIntent.FLAG_NO_CREATE, shouldEndPlaying
        )

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

    private fun cancelAllListAlarm() {
        val scheduledList = mediaRepo.scheduledList
        val baseTimestamp = ConfigUtil.getInt(appContext, "BASE_ALARM_ID", -1)
        if (baseTimestamp == -1) return // Không có alarm nào để hủy
        for (i in scheduledList.keys.indices) {

            val period = scheduledList.keys.elementAt(i)
            val startReqCode = baseTimestamp + (i * 2)
            val endReqCode = baseTimestamp + (i * 2) + 1
            cancelListAlarm(period, startReqCode)
            cancelListAlarm(period, endReqCode, true)
        }
    }

    private fun cancelListAlarm(
        listKey: String, requestCode: Int, shouldEndPlaying: Boolean = false
    ) {
        val isAlarmWorking = getScheduleListIntent(
            listKey, requestCode, PendingIntent.FLAG_NO_CREATE, shouldEndPlaying
        ) != null

        if (!isAlarmWorking) return

        Log.d("MediaInfo", "Cancel alarm $listKey - $requestCode")
        val alarmIntent = getScheduleListIntent(listKey, requestCode, 0, shouldEndPlaying)
        alarmManager.cancel(alarmIntent)
        alarmIntent?.cancel()
    }
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