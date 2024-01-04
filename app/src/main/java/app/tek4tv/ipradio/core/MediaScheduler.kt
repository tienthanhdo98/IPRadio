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
import app.tek4tv.ipradio.utils.getDurationInSecond
import app.tek4tv.ipradio.utils.toCalendar
import java.text.SimpleDateFormat
import java.util.*

class MediaScheduler(private val appContext: Context, private val mediaRepo: MediaRepo) {

    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val requestCodeRescheduleAllAlarm = 101
    private val requestCodeCheckHubAndResetAlarms = 102
    private val requestCodeEndLeftArea = 106
    private val requestScheduleShowListLeft = 107
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
            PendingIntent.getBroadcast(
                appContext,
                requestCodeRescheduleAllAlarm,
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT
            )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            time.timeInMillis,
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

    private fun getScheduleMediaIntent(
        mediaIndex: Int, action: String, requestCode: Int, flag: Int
    ): PendingIntent? {
        val intent = Intent(appContext, MediaScheduleReceiver::class.java).apply {
            this.action = action//MediaScheduleReceiver.ACTION_PLAY_MEDIA
            putExtra(MediaScheduleReceiver.SCHEDULED_MEDIA_INDEX, mediaIndex)
        }

        return PendingIntent.getBroadcast(appContext, requestCode, intent, flag)
    }


    private fun scheduleMediaAlarms() {
        mediaRepo.scheduledMediaItems.forEachIndexed { index, mediaItem ->
            val now = Calendar.getInstance()
            val scheduleTime = toCalendar(mediaItem.fixTime!!)
            val limit = toCalendar(mediaItem.fixTime).apply {
                add(Calendar.SECOND, getDurationInSecond(mediaItem.duration!!))
            }
            Log.d("alarmx", "scheduleMediaAlarms limit ${limit}")


            if (now > limit) return@forEachIndexed

            val isAlarmWorking =
                /*getScheduleMediaIntent(
                    index,
                    MediaScheduleReceiver.ACTION_PLAY_MEDIA,
                    mediaItem.id,
                    PendingIntent.FLAG_NO_CREATE
                ) != null
                        &&*/ getScheduleMediaIntent(
                index,
                MediaScheduleReceiver.ACTION_END_MEDIA,
                mediaItem.mediaID!!,
                PendingIntent.FLAG_NO_CREATE
            ) != null

            if (!isAlarmWorking) {
                Log.d("alarmx", "set media ${mediaItem.body}")

                /*val startIntent = getScheduleMediaIntent(
                    index,
                    MediaScheduleReceiver.ACTION_PLAY_MEDIA,
                    mediaItem.id,
                    0
                )*/
                val endIntent = getScheduleMediaIntent(
                    index,
                    MediaScheduleReceiver.ACTION_END_MEDIA,
                    mediaItem.mediaID!!,
                    0
                )

                /*alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    scheduleTime.timeInMillis, startIntent
                )*/

                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    limit.timeInMillis, endIntent
                )
            }
        }
    }

    private fun cancelMediaAlarms() {
        mediaRepo.scheduledMediaItems.forEachIndexed { index, mediaItem ->
            val isAlarmWorking =
                /*getScheduleMediaIntent(
                    index,
                    MediaScheduleReceiver.ACTION_PLAY_MEDIA,
                    mediaItem.id,
                    PendingIntent.FLAG_NO_CREATE
                ) != null
                        &&*/ getScheduleMediaIntent(
                index,
                MediaScheduleReceiver.ACTION_END_MEDIA,
                mediaItem.mediaID!!,
                PendingIntent.FLAG_NO_CREATE
            ) != null

            if (isAlarmWorking) {
                /*val startIntent = getScheduleMediaIntent(
                    index,
                    MediaScheduleReceiver.ACTION_PLAY_MEDIA,
                    mediaItem.id,
                    0
                )*/
                val endIntent = getScheduleMediaIntent(
                    index,
                    MediaScheduleReceiver.ACTION_END_MEDIA,
                    mediaItem.mediaID!!,
                    0
                )
                //alarmManager.cancel(startIntent)
                //startIntent?.cancel()
                alarmManager.cancel(endIntent)
                endIntent?.cancel()

                Log.d("alarmx", "cancel media ${mediaItem.body}")
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
        val alarmIntent = getScheduleForceMediaIntent(PendingIntent.FLAG_UPDATE_CURRENT) ?: return

        val fmValue = ConfigUtil.getString(appContext, ConfigUtil.FORCE_MEDIA, "")

        if (fmValue == null || fmValue == "") return

        val fm = ForceMedia.jsonAdapter.fromJson(fmValue) ?: return

        val mediaEndTimeInMillis = Calendar.getInstance().apply {
            time = SimpleDateFormat("dd/MM/yyyy HH:mm:ss").parse(fm.endTime)
        }.timeInMillis

        if (mediaEndTimeInMillis <= Calendar.getInstance().timeInMillis) return

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            mediaEndTimeInMillis, alarmIntent
        )
        Log.d("alarmx", "set force")
    }

    fun setCheckHubAndResetAlarms() {
        val intent = Intent(appContext, MediaScheduleReceiver::class.java).apply {
            action = MediaScheduleReceiver.ACTION_CHECKHUB_RESET
        }
        val alarmIntent =
            PendingIntent.getBroadcast(appContext, requestCodeCheckHubAndResetAlarms, intent, 0)

        val cal_alarm = Calendar.getInstance()

        cal_alarm[Calendar.HOUR_OF_DAY] =0
        cal_alarm[Calendar.MINUTE] = 3
        cal_alarm[Calendar.SECOND] = 0

        val date = Calendar.getInstance().time
        var alarmDate = cal_alarm.time
        if(alarmDate.before(date))
        {
            cal_alarm.add(Calendar.DATE,1)
        }
        alarmDate = cal_alarm.time

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            cal_alarm.timeInMillis,
            alarmIntent
        )

    }

    private fun scheduleEndLeftArea() {
        if(mediaRepo.scheduledList.isEmpty()) return
        val intent = Intent(appContext, MediaScheduleReceiver::class.java).apply {
            action = MediaScheduleReceiver.SCHEDULED_END_LEFT_AREA
        }

        val alarmIntent =
            PendingIntent.getBroadcast(
                appContext,
                requestCodeEndLeftArea,
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT
            )

        val scheduledList = mediaRepo.scheduledList
        val t = scheduledList.keys.last().split("-")
        val endTime = toCalendar(t[1])
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            endTime.timeInMillis,
            alarmIntent
        )
        Log.d("alarmx", "Set scheduleEndLeftArea ${endTime.time}")


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
                intent,FLAG_IMMUTABLE
            )

        val scheduledList = mediaRepo.scheduledList
        val t = scheduledList.keys.first().split("-")
        val endTime = toCalendar(t[0])
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            endTime.timeInMillis,
            alarmIntent
        )
        Log.d("alarmx", "Set scheduleShowListLeft ${endTime.time}")
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
        setRescheduleAllAlarms()
        scheduleEndLeftArea()
    }

    fun cancelAllScheduledListAlarm() {
        cancelAllListAlarm()
        cancelMediaAlarms()
    }

    fun cancelAllAlarm() {
        if (ForceMedia.hasValidForceMedia(appContext)) cancelForceMediaAlarm()
        else cancelAllScheduledListAlarm()
    }
}