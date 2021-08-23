package app.tek4tv.ipradio.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import app.tek4tv.ipradio.core.PlayerManager


class MediaScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val alarmListener = MainActivity.alarmListener ?: return

        alarmListener.onAlarm(intent)
    }

    companion object {
        const val ACTION_PLAY_MEDIA_LIST = "xaction_play_media_list"
        const val ACTION_PLAY_MEDIA = "xaction_play_media"
        const val ACTION_END_MEDIA = "xaction_end_media"
        const val ACTION_END_PLAYLIST = "xaction_end_playlist"
        const val SCHEDULED_LIST_KEY = "scheduled_list_key"
        const val SCHEDULED_MEDIA_INDEX = "scheduled_media_uri"
        const val ACTION_END_FORCE_MEDIA = "ACTION_END_FORCE_MEDIA"
        const val ACTION_RESCHEDULE = "ACTION_RESCHEDULE"
    }
}

interface AlarmListener{
    fun onAlarm(intent: Intent)
}