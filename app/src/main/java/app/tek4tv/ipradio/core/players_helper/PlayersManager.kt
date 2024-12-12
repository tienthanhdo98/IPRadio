package app.tek4tv.ipradio.core.players_helper

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.core.HubEventObserver
import app.tek4tv.ipradio.core.MediaScheduler
import app.tek4tv.ipradio.di.MoshiInst.moshi
import app.tek4tv.ipradio.model.ForceMedia
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.model.ResponseHub
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.GlobalProperties
import app.tek4tv.ipradio.utils.getDurationInSecond
import app.tek4tv.ipradio.utils.toCalendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.util.VLCVideoLayout
import java.text.SimpleDateFormat
import java.util.*


/**
 * Manager all media playback
 * 2 Mode: Scheduled and force media
 * Scheduled: Play media in a fixed scheduled
 * Force Media: Play media by command from server
 * **/
class PlayersManager(
    private val appContext: Context,
    private val vlcVideoLayout: VLCVideoLayout,
    private val mediaRepo: MediaRepo
) : IPlayerEventListener, HubEventObserver {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val vlcPlayer: IMediaPlayer = VlcMediaPlayer(appContext, this,vlcVideoLayout)
    private var currentPlayingMedia: MediaItem? = null

    /**
     * The timestamp when a media begin to play
     * **/
    private var mediaStartTime = 0L

    /**
     * -2 for force media
     * 0 for init
     * HH:mm:ss-HH:mm:ss for scheduled timeframe
     * **/
    private var playMode = PLAY_MODE_INIT//"0"

    /**
     * List of media for a timeframe
     * **/
    private var currentPlaylist = listOf<MediaItem>()

    var playlistIndex = 0
        private set

    var playerManagerListener: IPlayerManagerListener? = null

    /**
     * Determine if we will not play any media in >= 15s to go
     * **/
    var isTimeOver = false

    var played = false



    override fun onPlayerError(e: Exception?, isLiveError: Boolean,mediaItem: MediaItem) {
        if (isLiveError) {
            //To log stream played duration
            onMediaStopPlaying()
            playerManagerListener?.onLiveError(mediaItem)
        }
    }

    override fun onPlayMediaSuccess(mediaItem: MediaItem?) {
        playerManagerListener?.onPlayMediaSuccess(mediaItem)
        mediaStartTime = Calendar.getInstance().timeInMillis
        played = true
    }

    override fun onMediaEndReached(mediaItem: MediaItem?) {
        playerManagerListener?.onMediaEndReach(mediaItem!!)
        onMediaStopPlaying()
//        stopAllPlayer()
        playNextMedia()
    }


    override fun onLostHubConnection() {
        vlcPlayer.disconnectedHub = true
    }

    override fun onReconnectedToHub() {
        vlcPlayer.disconnectedHub = false
    }

    private fun onMediaStopPlaying() {
        if (played) {
            Log.e("AAA", "Đừng vào đây lần 2")
            val duration = Calendar.getInstance().timeInMillis - mediaStartTime
            playerManagerListener?.onMediaStopPlaying(currentPlayingMedia!!, duration)
        }

        played = false
    }

    /**
     * Tìm khung thơi gian hiện tại cần phát
     * **/
    var isInTimeFrame = false
    fun startFirstTimeFrame() {
        var found = false
        val scheduledList = mediaRepo.scheduledList
        for (i in scheduledList.keys.indices) {
            val period = scheduledList.keys.elementAt(i)
            val t = period.split("-")
            val start = toCalendar(t[0])
            val end = toCalendar(t[1])
            val now = Calendar.getInstance().timeInMillis
            if (now in start.timeInMillis..end.timeInMillis) {
                val resumePoint = calculateResumePoint(start, period)
                setTimeFrame(period, resumePoint)
                isInTimeFrame = true
                found = true
                Log.d("PMLOG", "startFirstTimeFrame: found, start")
                break
            }
        }
        if (!found) {
            Log.d("PMLOG", "startFirstTimeFrame: not found")
            playerManagerListener?.onTimeOver()
        }
    }

    fun setTimeFrame(timeFrame: String, resumePoint: Pair<Int, Int>?) {
        stopAllPlayer()
        playerManagerListener?.onStartTimeFrame(timeFrame)
        playMode = timeFrame

        currentPlaylist = mediaRepo.scheduledList[timeFrame] ?: listOf()

        if (resumePoint != null) {
            playlistIndex = resumePoint.first
            playMediaByIndex(playlistIndex, resumePoint.second)
        } else {
            playlistIndex = 0
            playMediaByIndex(playlistIndex)
        }
        isTimeOver = false
    }

    fun playNextMedia() {
        Log.d("alarmx", "Play media next $isInTimeFrame" )
        if (playMode == "") return

        playlistIndex++
            if (playlistIndex < currentPlaylist.size) {
                Log.d("PlayerLog", "Play media next")
                playMediaByIndex(playlistIndex)
            }
//            else {
//                if (isInTimeFrame) {
//                    Log.d("PlayerLog", "Play media loop")
//                    playlistIndex = 0
//                    playMediaByIndex(playlistIndex)
//                }
////                else
////                {
////                    stopAllPlayer()
////                }
//            }


    }

    private fun playMediaByIndex(index: Int, timeStamp: Int = -1) {
        try {
            if (currentPlaylist.isEmpty()) return
            val mediaItem = currentPlaylist[index]

            playMedia(mediaItem, timeStamp)

            Log.d("PlayerLog", "Play media $index")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    fun pauseMedia()
    {
        vlcPlayer.pause()
    }
    fun resumeMedia()
    {
        vlcPlayer.resume()
    }
    fun reset(){
        vlcPlayer.reset()
    }

    private fun playMedia(mediaItem: MediaItem, timeStamp: Int = -1) {
        try {
            Log.d("alarmx", "Play media next $isInTimeFrame" )
            currentPlayingMedia = mediaItem
            when (mediaItem.getMediaType()) {
                MediaType.FILE -> {
                    vlcPlayer.playMedia(mediaItem, timeStamp)
                    vlcPlayer
                }
                MediaType.LIVE -> {
                    playLiveStream(mediaItem)
                }
                MediaType.FM -> {
                    mediaStartTime = Calendar.getInstance().timeInMillis
                    playerManagerListener?.onPlayTuner(mediaItem)
                }
                else -> {
                    playNextMedia()
                }

            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playLiveStream(mediaItem: MediaItem) {
     vlcPlayer.playMedia(mediaItem, -1)

    }


    fun stopAllPlayer() {
        vlcPlayer.stop()
    }




    /**
     * If there are no timeframe to play in the next 15s,
     * fire an timeover event
     * **/
    private fun checkTimeOver() {
        coroutineScope.launch {
            delay(15000)
            if (isTimeOver) playerManagerListener?.onTimeOver()
        }
    }

    /**
     * Find media and the second from start which it should be played at the moment
     * trả về index của media cần phát tịa thời điểm hiện tại và nếu là file thì thời gian tính từ lúc bắt đầu
     * **/
    private fun calculateResumePoint(start: Calendar, timeFrame: String): Pair<Int, Int> {
        try {
            val mediaList = mediaRepo.scheduledList[timeFrame] ?: return Pair(0, 0)
            val now = Calendar.getInstance()
            val secondFromStart = ((now.timeInMillis - start.timeInMillis) / 1000).toInt()

            var t = 0

            for (i in mediaList.indices) {
                val media = mediaList[i]
                val mediaDuration = getDurationInSecond(media.duration!!)

                t += mediaDuration

                if (t > secondFromStart) {
                    return if (GlobalProperties.getInst().resumeFromStart) {
                        Pair(i, 0)
                    } else {
                        val startTimeStamp = mediaDuration - (t - secondFromStart)
                        Pair(i, startTimeStamp)
                    }
                }
            }

            return Pair(0, 0)
        } catch (e: Exception) {
            Log.e("PlayerLog", "Error calculate resume")
            e.printStackTrace()
            return Pair(0, 0)
        }
    }

    fun stopPlaylist(cancelListKey: String) {
        if (cancelListKey != playMode) return
        playMode = ""
        stopAllPlayer()

        playerManagerListener?.onEndTimeFrame(cancelListKey)

        //check time over
        isTimeOver = true
        checkTimeOver()
    }

    fun endScheduledMediaByIndex(mediaIndex: Int) {
        val scheduledItems = mediaRepo.scheduledMediaItems
        if (mediaIndex >= scheduledItems.size) return

        val itemToEnd = scheduledItems[mediaIndex]
        if (itemToEnd.mediaID != currentPlayingMedia?.mediaID) return

        stopAllPlayer()
        onMediaStopPlaying()

        playNextMedia()
    }
    fun stopTimeFrame()
    {
        playerManagerListener?.onTimeOver()
    }

    fun stopForceMedia() {
        if (playMode != PLAY_MODE_FORCE) return
        stopAllPlayer()
        onMediaStopPlaying()
        playerManagerListener?.onEndForceMedia()
        playerManagerListener?.onTimeOver()
    }

    /**
     * Nhận lệnh phát khẩn cấp từ hub
     * **/

    fun onPlayForceCommand(
        context: Context,
        responseHub: ResponseHub,
        mediaScheduler: MediaScheduler
    ) {
        try {
            val responseAdapter = moshi.adapter(MediaItem::class.java)
            val duration = responseHub.duration
            val type = responseHub.type
            val level = responseHub.level
            val mediaItemObject = responseAdapter.fromJson(responseHub.name)

            //check xem nếu đang phát media khẩn cấp có cấp độ thấp hơn không
            //Nếu có thì bỏ qua không phát
            //Chỉ ưu tiên media khẩn cấp có cấp độ thấp hơn
            if (playMode == PLAY_MODE_FORCE) {
                val value = ConfigUtil.getString(context, ConfigUtil.FORCE_MEDIA, "")
                if (value != null && value != "") {
                    val currentForceMedia = ForceMedia.jsonAdapter.fromJson(value)

                    if (currentForceMedia != null && currentForceMedia.level < level) return
                }
            }
            val forceMedia =
                ForceMedia(responseHub.message!!, duration, type, level, mediaItemObject).apply {

                    val mediaEndTime = Calendar.getInstance().apply {
                        add(Calendar.MINUTE, convertStringToInt(duration))
                    }
                    endTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(mediaEndTime.time)
                    fixTime = SimpleDateFormat("HH:mm:ss").format(Date())
                }
                forceMedia.saveItemToPref(context)
                playForceMedia(forceMedia, mediaScheduler,false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun convertStringToInt(time: String): Int {
        val listDur = time.split(":")
        val h = listDur[0].toInt()
        val m = listDur[1].toInt()
        val s = listDur[2].toInt()
        return (h * 3600 + m * 60 + s) / 60
    }

    fun playForceMedia( forceMedia: ForceMedia, mediaScheduler: MediaScheduler,isPlayAtStart : Boolean) {
        try {
            Log.d("aaa", "force")
            //Hủy hẹn giờ của kịch bản đang phát
            mediaScheduler.cancelAllAlarm()

//           forceMedia.saveItemToPref()
            //hẹn giờ dừng media khẩn cấp
            mediaScheduler.scheduleForceMediaAlarm()

            playMode = PLAY_MODE_FORCE
            stopAllPlayer()
//            if(isPlayAtStart) {
//                playMedia(forceMedia.toMediaItem())
//            }
           playMedia(forceMedia.toMediaItem())

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }



    fun onActivityDestroy() {
        vlcPlayer.release()
    }

    fun resetPlayMode() {
        playMode = PLAY_MODE_INIT//"0"
    }


    companion object {
        const val PLAY_MODE_FORCE = "-2"
        const val PLAY_MODE_INIT = "0"
        const val LOGTAG = "player"
    }

}