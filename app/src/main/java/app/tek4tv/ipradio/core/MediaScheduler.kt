package app.tek4tv.ipradio.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.ui.MediaScheduleReceiver
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.getDurationInSecond
import app.tek4tv.ipradio.utils.toCalendar
import java.text.SimpleDateFormat
import java.util.*

class MediaScheduler(private val appContext: Context, private val mediaRepo: MediaRepo) {

    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun setRescheduleMediasAlarm() {
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
            PendingIntent.getBroadcast(appContext, 1, intent, PendingIntent.FLAG_CANCEL_CURRENT)

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time.timeInMillis, alarmIntent)
    }

    private fun scheduleListAlarms() {
        val scheduledList = mediaRepo.scheduledList
        for (i in scheduledList.keys.indices) {
            val period = scheduledList.keys.elementAt(i)
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
                setAlarmForList(period, endRequestCode, end, true)
            } else if (now < start.timeInMillis) {
                setAlarmForList(period, requestCode, start)
                setAlarmForList(period, endRequestCode, end, true)
            }
        }
    }

    /*fun setAlarmsForFrame(listKey: String, setForNextDay : Boolean = false)
    {
        val requestCode = mediaRepo.timeDividers[listKey]!![0].id
        val endRequestCode = mediaRepo.timeDividers[listKey]!![1].id

        var isNewDayStarted = false

        val t = listKey.split("-")
        val start = toCalendar(t[0])
        val end = toCalendar(t[1])

        if (start.get(Calendar.HOUR_OF_DAY) > end.get(Calendar.HOUR_OF_DAY)
        ) isNewDayStarted = true

        if(setForNextDay)
        {
            start.add(Calendar.DAY_OF_MONTH, 1)
            end.add(Calendar.DAY_OF_MONTH, 1)
        }

        if (isNewDayStarted) {
            end.add(Calendar.DAY_OF_MONTH, 1)
        }

        val now = Calendar.getInstance().timeInMillis

        if (now in start.timeInMillis..end.timeInMillis) {
            setAlarmForList(listKey, endRequestCode, end, true)
        } else if (now < start.timeInMillis) {
            setAlarmForList(listKey, requestCode, start)
            setAlarmForList(listKey, endRequestCode, end, true)
        }
    }*/

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

            val requestCode = mediaRepo.timeDividers[period]!![0].id
            val endRequestCode = mediaRepo.timeDividers[period]!![1].id

            cancelListAlarm(period, requestCode)
            cancelListAlarm(period, endRequestCode, true)
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

    private fun getScheduleMediaIntent(
        mediaIndex: Int, requestCode: Int, flag: Int
    ): PendingIntent? {
        val intent = Intent(appContext, MediaScheduleReceiver::class.java).apply {
            action = MediaScheduleReceiver.ACTION_PLAY_MEDIA
            putExtra(MediaScheduleReceiver.SCHEDULED_MEDIA_INDEX, mediaIndex)
        }

        return PendingIntent.getBroadcast(appContext, requestCode, intent, flag)
    }

    private fun scheduleMediaAlarms() {
        mediaRepo.scheduledMediaItems.forEachIndexed { index, mediaItem ->
            val now = Calendar.getInstance()
            val scheduleTime = toCalendar(mediaItem.fixTime)
            val duration = getDurationInSecond(mediaItem.duration ?: "")
            val limit = toCalendar(mediaItem.fixTime).apply {
                add(Calendar.SECOND, duration.toInt())
            }

            if (now > limit) return@forEachIndexed

            val isAlarmWorking = getScheduleMediaIntent(
                index, mediaItem.id,
                PendingIntent.FLAG_NO_CREATE
            ) != null

            if (!isAlarmWorking) {
                Log.d("alarmx", "set media ${mediaItem.path}")

                val alarmIntent = getScheduleMediaIntent(index, mediaItem.id, 0)
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    scheduleTime.timeInMillis, alarmIntent
                )
            }
        }
    }

    private fun cancelMediaAlarms() {
        mediaRepo.scheduledMediaItems.forEachIndexed { index, mediaItem ->
            val now = Calendar.getInstance()
            val scheduleTime = toCalendar(mediaItem.fixTime)

            if (now > scheduleTime) return@forEachIndexed

            val isAlarmWorking = getScheduleMediaIntent(
                index, mediaItem.id,
                PendingIntent.FLAG_NO_CREATE
            ) != null

            if (isAlarmWorking) {
                Log.d("alarmx", "cancel media ${mediaItem.path}")

                val alarmIntent = getScheduleMediaIntent(index, mediaItem.id, 0)

                alarmManager.cancel(alarmIntent)
                alarmIntent?.cancel()
            }
        }
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
        Log.d("alarmx", "set force")

        val alarmIntent = getScheduleForceMediaIntent(PendingIntent.FLAG_UPDATE_CURRENT) ?: return

        val mediaEndTime = ConfigUtil.getString(appContext, ConfigUtil.FORCE_MEDIA_END_TIME, "")
        if (mediaEndTime == null || mediaEndTime == "") return

        val mediaEndTimeInMillis = Calendar.getInstance().apply {
            time = SimpleDateFormat("dd/MM/yyyy HH:mm:ss").parse(mediaEndTime)
        }.timeInMillis

        if (mediaEndTimeInMillis <= Calendar.getInstance().timeInMillis) return

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            mediaEndTimeInMillis, alarmIntent
        )
    }

    fun cancelForceMediaAlarm() {
        Log.d("alarmx", "cancel force")
        val alarmIntent = getScheduleForceMediaIntent(PendingIntent.FLAG_NO_CREATE) ?: return
        alarmManager.cancel(alarmIntent)
        alarmIntent.cancel()
    }

    fun scheduleAllAlarms() {
        scheduleListAlarms()
        scheduleMediaAlarms()
    }

    fun cancelAllAlarm() {
        cancelAllListAlarm()
        cancelMediaAlarms()
    }
}