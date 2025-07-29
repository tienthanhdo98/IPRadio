package app.tek4tv.ipradio.ui

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat


class MediaScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val alarmListener = MainActivity.alarmListener ?: return

        alarmListener.onAlarm(intent)


    }

    companion object {
        //bắt đầu phát khung giờ
        const val ACTION_PLAY_MEDIA_LIST = "xaction_play_media_list"

        const val SCHEDULED_LIST_KEY = "scheduled_list_key"

        //dừng phát media khẩn cấp
        const val ACTION_END_FORCE_MEDIA = "ACTION_END_FORCE_MEDIA"

        //Cap nhat lai lich va reset lai thiet bi
        const val ACTION_CHECKHUB_RESET = "ACTION_CHECKHUB_RESET"
    }
}

interface AlarmListener {
    fun onAlarm(intent: Intent)
}
