package app.tek4tv.ipradio.core.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.tek4tv.ipradio.ui.MainActivity


class MediaScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val alarmListener = MainActivity.alarmListener ?: return

        alarmListener.onAlarm(intent)


    }

    companion object {
        //bắt đầu phát khung giờ
        const val ACTION_PLAY_MEDIA_LIST = "xaction_play_media_list"
        const val ACTION_END_PLAYLIST = "xaction_end_playlist"

        const val SCHEDULED_LIST_KEY = "scheduled_list_key"

        //dừng phát media khẩn cấp
        const val ACTION_END_FORCE_MEDIA = "ACTION_END_FORCE_MEDIA"

        //Cap nhat lai lich va reset lai thiet bi
        const val ACTION_CHECKHUB_RESET = "ACTION_CHECKHUB_RESET"
        const val ACTION_TURN_ON_PA = "ACTION_TURN_ON_PA"
    }
}

interface AlarmListener {
    fun onAlarm(intent: Intent)
}
