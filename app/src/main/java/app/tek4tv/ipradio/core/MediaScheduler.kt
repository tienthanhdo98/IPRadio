package app.tek4tv.ipradio.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.Intent
import android.util.Log
import app.tek4tv.ipradio.model.ForceMedia
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.ui.MediaScheduleReceiver
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.Utils.Companion.FULL_SCREEN
import app.tek4tv.ipradio.utils.toCalendar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MediaScheduler(private val appContext: Context, private val mediaRepo: MediaRepo) {

    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val requestCodeRescheduleAllAlarm = 101
    private val requestCodeCheckHubAndResetAlarms = 102
    private val requestCodeEndLeftArea = 106
    private val requestScheduleShowListLeft = 107
    private val requestEndForce= 108
    private fun setRescheduleAllAlarms() {
        val intent = Intent(appContext, MediaScheduleReceiver::class.java).apply {
            action = MediaScheduleReceiver.ACTION_RESCHEDULE
        }
        val time = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
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

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            time.timeInMillis,
            alarmIntent
        )
    }

    private fun scheduleListAlarms() {
        val scheduledList = mediaRepo.scheduledList
        var isFirstAlarm = true
        for (i in scheduledList.keys.indices) {
            val period = scheduledList.keys.elementAt(i)
            //setAlarmsForFrame(period)
            var isNewDayStarted = false
            val requestCode = mediaRepo.timeDividers[period]!![0].id

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

            if (!isFirstAlarm) {
                start.add(Calendar.SECOND, 1)
            }
//            if (now >= start.timeInMillis && now <= end.timeInMillis ) {
//                Log.d("alarmx", "Set In Frame $requestCode ${convertTimeInMillisToHHmmss(start.timeInMillis)}")
//                val alarmIntent = getScheduleListIntent(period, requestCode!!)
//                alarmManager.setExactAndAllowWhileIdle(
//                    AlarmManager.RTC_WAKEUP, start.timeInMillis,
//                    alarmIntent
//                )
//            }
            if(now < start.timeInMillis)
            {
                Log.d("alarmx", "Set Before Frame $requestCode ${convertTimeInMillisToHHmmss(start.timeInMillis)}")
                val alarmIntent = getScheduleListIntent(period, requestCode!!)
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, start.timeInMillis,
                    alarmIntent
                )
            }
            isFirstAlarm = false
        }
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
            cancelListAlarm(period, requestCode!!)
        }
    }

    private fun cancelListAlarm(
        listKey: String, requestCode: Int
    ) {
        Log.d("alarmx", "Cancel $listKey - $requestCode")
        val alarmIntent = getScheduleListIntent(listKey, requestCode)
        alarmManager.cancel(alarmIntent)
        alarmIntent?.cancel()
    }

    private fun getScheduleListIntent(
        listKey: String, requestCode: Int
    ): PendingIntent? {

        val intent = Intent(appContext, MediaScheduleReceiver::class.java).apply {
            action = MediaScheduleReceiver.ACTION_PLAY_MEDIA_LIST
            putExtra(MediaScheduleReceiver.SCHEDULED_LIST_KEY, listKey)
        }

        return PendingIntent.getBroadcast(appContext, requestCode, intent, 0)
    }


    fun scheduleForceMediaAlarm(forceMedia: ForceMedia) {

        val intent = Intent(appContext, MediaScheduleReceiver::class.java).apply {
            action = MediaScheduleReceiver.ACTION_END_FORCE_MEDIA
        }
        val alarmIntent = PendingIntent.getBroadcast(appContext, requestEndForce, intent, FLAG_UPDATE_CURRENT)
        Log.d("alarmx", "force end time ${forceMedia.endTime}")
        val mediaEndTimeInMillis = Calendar.getInstance().apply {
            time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(forceMedia.endTime)
        }
        mediaEndTimeInMillis.add(Calendar.SECOND, 2)
        if (mediaEndTimeInMillis.timeInMillis <= Calendar.getInstance().timeInMillis) return

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            mediaEndTimeInMillis.timeInMillis, alarmIntent
        )
        Log.d("alarmx", "set end force ${convertTimeInMillisToHHmmss(mediaEndTimeInMillis.timeInMillis)}")
    }

    private fun setCheckHubAndResetAlarms() {
        val intent = Intent(appContext, MediaScheduleReceiver::class.java).apply {
            action = MediaScheduleReceiver.ACTION_CHECKHUB_RESET
        }
        val alarmIntent =
            PendingIntent.getBroadcast(appContext, requestCodeCheckHubAndResetAlarms, intent, 0)

        val cal_alarm = Calendar.getInstance()

        cal_alarm[Calendar.HOUR_OF_DAY] = 0
        cal_alarm[Calendar.MINUTE] = 3
        cal_alarm[Calendar.SECOND] = 0

        val date = Calendar.getInstance().time
        val alarmDate = cal_alarm.time
        if (alarmDate.before(date)) {
            cal_alarm.add(Calendar.DATE, 1)
        }
        cal_alarm.time

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            cal_alarm.timeInMillis,
            alarmIntent
        )

    }

    private fun scheduleEndLeftArea() {
        if (mediaRepo.scheduledList.isEmpty()) return
        val intent = Intent(appContext, MediaScheduleReceiver::class.java).apply {
            action = MediaScheduleReceiver.SCHEDULED_END_LEFT_AREA
        }

        val alarmIntent =
            PendingIntent.getBroadcast(
                appContext,
                requestCodeEndLeftArea,
                intent,
                0
            )

        val scheduledList = mediaRepo.scheduledList
        for (i in scheduledList.keys.indices) {
            val period = scheduledList.keys.elementAt(i)
            val t = period.split("-")
            val start = toCalendar(t[0])
            val end = toCalendar(t[1])
            val now = Calendar.getInstance().timeInMillis
            if (now >= start.timeInMillis && now <= end.timeInMillis) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    end.timeInMillis,
                    alarmIntent
                )
//                Log.d("alarmx", "Set scheduleEndLeftArea ${end.time}")
                break
            }
        }
    }

    fun scheduleShowListLeft() {
        if (mediaRepo.scheduledList.isEmpty()) return
        val intent = Intent(appContext, MediaScheduleReceiver::class.java).apply {
            action = MediaScheduleReceiver.SCHEDULED_SHOW_LEFT_AREA
        }

        val alarmIntent =
            PendingIntent.getBroadcast(
                appContext,
                requestScheduleShowListLeft,
                intent, FLAG_IMMUTABLE
            )

        val intentEND = Intent(appContext, MediaScheduleReceiver::class.java).apply {
            action = MediaScheduleReceiver.SCHEDULED_END_LEFT_AREA
        }

        val alarmIntentEND =
            PendingIntent.getBroadcast(
                appContext,
                requestCodeEndLeftArea,
                intentEND,
                PendingIntent.FLAG_CANCEL_CURRENT or FLAG_IMMUTABLE
            )

        val scheduledList = mediaRepo.scheduledList
        for (i in scheduledList.keys.indices) {
            val period = scheduledList.keys.elementAt(i)
            val t = period.split("-")
            val start = toCalendar(t[0])
            val end = toCalendar(t[1])
            val now = Calendar.getInstance().timeInMillis
            if (now <= end.timeInMillis) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    start.timeInMillis,
                    alarmIntent
                )
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    end.timeInMillis,
                    alarmIntentEND
                )
//                Log.d("alarmx", "Set scheduleShow ${start.time}")
                break
            }
        }

    }
    private fun getScheduleForceMediaIntent(flag: Int): PendingIntent? {
        val hasForceMedia = ConfigUtil.getBool(appContext, ConfigUtil.HAVE_FORCE_MEDIA, false)
        if (!hasForceMedia) return null

        val intent = Intent(appContext, MediaScheduleReceiver::class.java).apply {
            action = MediaScheduleReceiver.ACTION_END_FORCE_MEDIA
        }

        return PendingIntent.getBroadcast(appContext, requestEndForce, intent, flag)
    }
    fun cancelForceMediaAlarm() {
        Log.d("alarmx", "cancel force")
        val alarmIntent = getScheduleForceMediaIntent(PendingIntent.FLAG_NO_CREATE) ?: return
        alarmManager.cancel(alarmIntent)
        alarmIntent.cancel()
    }

    fun scheduleAllAlarms(typeScreen : Int) {
        setRescheduleAllAlarms()
//        setCheckHubAndResetAlarms()

        scheduleListAlarms()

        if (typeScreen != FULL_SCREEN) {
//        scheduleShowListLeft()
//        scheduleEndLeftArea()
        }
    }

    fun cancelAllScheduledListAlarm() {
        if (ForceMedia.hasValidForceMedia(appContext)) {
            Log.d("alarmx", "Cancel cancelForceMediaAlarm")
            cancelForceMediaAlarm()
        }
        cancelAllListAlarm()
    }

}