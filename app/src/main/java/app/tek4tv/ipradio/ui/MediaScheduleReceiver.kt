package app.tek4tv.ipradio.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent


class MediaScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val alarmListener = MainActivity.alarmListener ?: return

        alarmListener.onAlarm(intent)
    }

    companion object {
        //bắt đầu phát khung giờ
        const val ACTION_PLAY_MEDIA_LIST = "xaction_play_media_list"
        const val ACTION_PLAY_MEDIA = "xaction_play_media"
        //dừng phát media
        const val ACTION_END_MEDIA = "xaction_end_media"
        //dừng phát khung giờ
        const val ACTION_END_PLAYLIST = "xaction_end_playlist"


        const val SCHEDULED_LIST_KEY = "scheduled_list_key"
        const val SCHEDULED_MEDIA_INDEX = "scheduled_media_uri"

        //dừng phát media khẩn cấp
        const val ACTION_END_FORCE_MEDIA = "ACTION_END_FORCE_MEDIA"
        //đặt lawij alarm
        const val ACTION_RESCHEDULE = "ACTION_RESCHEDULE"
        //check khi 00:30 co hub không, không có thì reset thiết bị
        const val ACTION_CHECKHUB_RESET= "ACTION_CHECKHUB_RESET"
        //left media
        const val SCHEDULED_END_LEFT_AREA = "SCHEDULED_END_LEFT_AREA"
        const val SCHEDULED_SHOW_LEFT_AREA = "SCHEDULED_SHOW_LEFT_AREA"
        const val ACTION_PLAY_LEFT_PLAYLIST = "xaction_play_left_media_list"
        const val ACTION_END_LEFT_PLAYLIST = "xaction_end_left_playlist"
        const val SCHEDULED_LIST_LEFT_KEY = "scheduled_list_left_key"


    }
}

interface AlarmListener{
    fun onAlarm(intent: Intent)
}