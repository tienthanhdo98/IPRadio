package app.tek4tv.ipradio.core.players_helper

import android.content.Context
import android.os.Handler
import android.util.Log
import app.tek4tv.ipradio.core.MediaScheduler
import app.tek4tv.ipradio.di.RetrofitInstance.moshi
import app.tek4tv.ipradio.model.ForceMedia
import app.tek4tv.ipradio.model.MediaItemModel
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.model.ResponseHub
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.GlobalProperties
import app.tek4tv.ipradio.utils.Utils.Companion.FULL_SCREEN
import app.tek4tv.ipradio.utils.Utils.Companion.LEFT_AREA
import app.tek4tv.ipradio.utils.Utils.Companion.RIGHT_AREA
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
    appContext: Context,
    vlcVideoLayout: VLCVideoLayout,
    private val mediaRepo: MediaRepo
) : IPlayerEventListener {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val vlcPlayer: IMediaPlayer = VlcMediaPlayer(appContext, this,vlcVideoLayout)
    private var currentPlayer: IMediaPlayer? = null

    private var currentPlayingMedia: MediaItemModel? = null

    /**
     * The timestamp when a media begin to play
     * **/
    private var mediaStartTime = 0L

    /**
     * -2 for force media
     * 0 for init
     * HH:mm:ss-HH:mm:ss for scheduled timeframe
     * **/
    var playMode = PLAY_MODE_INIT//"0"

    /**
     * List of media for a timeframe
     * **/


    var playlistIndex = 0
        private set

    var playerManagerListener: IPlayerManagerListener? = null

    /**
     * Determine if we will not play any media in >= 15s to go
     * **/
    private var isTimeOver = false

    var played = false

    private var handlerFMPlayer: Handler? = null

    var currentTextPlaylist = listOf<MediaItemModel>()

    override fun onPlayerError(e: Exception?, isLiveError: Boolean,mediaItem: MediaItemModel) {
        if (isLiveError) {
            //To log stream played duration
            onMediaStopPlaying()
            playerManagerListener?.onLiveError(mediaItem)
        }
    }

    override fun onPlayMediaSuccess(mediaItem: MediaItemModel?) {
        playerManagerListener?.onPlayMediaSuccess(mediaItem)
        mediaStartTime = Calendar.getInstance().timeInMillis
        played = true
    }

    override fun onMediaEndReached(mediaItem: MediaItemModel?) {
        onMediaStopPlaying()
        playNextMedia()
    }

    override fun onMediaForceEndReached(mediaItem: MediaItemModel?) {
        stopForceMedia()
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
        val now = Calendar.getInstance().timeInMillis
        Log.d(LOGTAG, "onTimeOver: play mode: ${scheduledList.keys}")
        Log.d(LOGTAG, "onTimeOver: play mode: ${scheduledList.size}")
        for (i in scheduledList.keys.indices) {
            val period = scheduledList.keys.elementAt(i)
            Log.d(LOGTAG, "onTimeOver: $period")
            val t = period.split("-")
            val start = toCalendar(t[0])
            val end = toCalendar(t[1])
            if (now in start.timeInMillis..end.timeInMillis) {
                val resumePoint = calculateResumePoint(start, period)
                setTimeFrame(period, resumePoint)
                found = true
                isInTimeFrame = true
                break
            }
        }
        if (!found) {
            isInTimeFrame = false
            playerManagerListener?.onTimeOver()
            Log.d(LOGTAG, "onTimeOver: not time frame found")
        }
    }

    fun setTimeFrame(timeFrame: String, resumePoint: Pair<Int, Int>?) {

        stopAllPlayer()

        playMode = timeFrame

        currentPlaylist =
            mediaRepo.scheduledList[timeFrame]?.filter { it.broadcastRegion?.value == RIGHT_AREA || it.broadcastRegion?.value == FULL_SCREEN }
                ?: listOf()
        currentTextPlaylist =
            mediaRepo.scheduledList[timeFrame]?.filter { it.broadcastRegion?.value == LEFT_AREA }
                ?: listOf()
        Log.d("monthlyinfo", "show right ${currentPlaylist.size}")
        Log.d("monthlyinfo", "show left ${currentTextPlaylist.size}")

        if (resumePoint != null) {
            playlistIndex = resumePoint.first
            playMediaByIndex(playlistIndex, resumePoint.second)
            Log.d(LOGTAG, "resumePoint index : ${resumePoint.first}")
            Log.d(LOGTAG, "resumePoint second: ${resumePoint.second}")
        } else {
            playlistIndex = 0
            playMediaByIndex(playlistIndex)
        }

        isTimeOver = false
    }

    fun playNextMedia() {
        if (playMode == "") return
        Log.d(LOGTAG, "Play media start ")
        Log.d(LOGTAG, "Play media size ${currentPlaylist.size} ")
        playlistIndex++

        if (playlistIndex < currentPlaylist.size) {
            Log.d(LOGTAG, "Play media phat tiep")
            playMediaByIndex(playlistIndex)
        }
//        else
//        {
//            Log.d(LOGTAG, "Play media phat tiep cham dich")
//            stopAllPlayer()
//            stopTimeFrame()
//        }
        else
        {
            Log.d(LOGTAG, "Play media loop")
            playlistIndex = 0
            playMediaByIndex(playlistIndex)
        }
    }

    fun resumeMedia()
    {
        vlcPlayer.resume()
//        exoPlayer.resume()
    }

    private fun playMediaByIndex(index: Int, timeStamp: Int = -1) {
        try {
            Log.d(LOGTAG, "currentPlaylist ${currentPlaylist.size}")
            if (currentPlaylist.isEmpty()) return
            val mediaItem = currentPlaylist[index]
            Log.d(LOGTAG, "play ${mediaItem.title}")
            playMedia(mediaItem, timeStamp)


        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playMedia(mediaItem: MediaItemModel, timeStamp: Int = -1) {
        try {

            currentPlayingMedia = mediaItem
            when (mediaItem.getMediaType()) {
                MediaType.FILE -> {
                    if (mediaItem.body!!.contains("m3u8")) {
                        playLiveStream(mediaItem, false, timeStamp)
                    } else {
                        // play live o trong lich
//                        if (mediaItem.contentType?.value == "5") {
//
//                            vlcPlayer.playMedia(mediaItem, true, timeStamp)
//                        } else {
//                            vlcPlayer.playMedia(mediaItem, false, timeStamp)
//                        }
//                        currentPlayer = vlcPlayer

                        if (mediaItem.contentType?.value == "5") {
                            vlcPlayer.playMedia(mediaItem, true, timeStamp)
                        } else {
                            vlcPlayer.playMedia(mediaItem, false, timeStamp)
                        }
                        currentPlayer = vlcPlayer
                    }
                }
                MediaType.LIVE -> {
                    playLiveStream(mediaItem, true, timeStamp)
                }
                else -> {
                    Log.e(LOGTAG, "Player not in live and file")
                    playNextMedia()
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playLiveStream(mediaItem: MediaItemModel,isForce : Boolean,timeStamp: Int ) {
        val resumeLiveTime = if(isForce) calculateResumeLivePoint(mediaItem) else timeStamp
        Log.d(LOGTAG, "playLiveStream resume time: $resumeLiveTime")
//        currentPlayer = if (mediaItem.body!!.contains("m3u8")) {
//            exoPlayer.playMedia(mediaItem, isForce, resumeLiveTime)
//            exoPlayer
//        } else {
//            vlcPlayer.playMedia(mediaItem,isForce, resumeLiveTime)
//            vlcPlayer
//        }
        vlcPlayer.playMedia(mediaItem,isForce, resumeLiveTime)
        currentPlayer = vlcPlayer
    }

    fun   stopAllPlayer() {
        handlerFMPlayer?.removeCallbacksAndMessages(null)
        vlcPlayer.stop()
//        exoPlayer.stop()
        played = false
    }


    /**
     * If there are no timeframe to play in the next 15s,
     * fire an timeover event
     * **/
    private fun checkTimeOver() {
        coroutineScope.launch {
            delay(15000)
            if (isTimeOver) playerManagerListener?.onTimeOver()
            Log.d("turnPA", "onTimeOver: check time over")
        }
    }

    /**
     * Find media and the second from start which it should be played at the moment
     * trả về index của media cần phát tịa thời điểm hiện tại và nếu là file thì thời gian tính từ lúc bắt đầu
     * **/
    private fun calculateResumePoint(start: Calendar, timeFrame: String): Pair<Int, Int> {
        try {
            val mediaList = mediaRepo.scheduledList[timeFrame]?.filter { it.broadcastRegion?.value == RIGHT_AREA || it.broadcastRegion?.value == FULL_SCREEN } ?: return Pair(0, 0)
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
            Log.e(LOGTAG, "Error calculate resume")
            e.printStackTrace()
            return Pair(0, 0)
        }
    }

    private fun calculateResumeLivePoint(mediaItem: MediaItemModel): Int {
        //với force media, fixtime dc gán là start time
        val startTimeOfMedia = toCalendar(mediaItem.fixTime!!)
        val now = Calendar.getInstance()
        return ((now.timeInMillis - startTimeOfMedia.timeInMillis) / 1000).toInt()

    }

    fun stopPlaylist(cancelListKey: String) {
        if (cancelListKey != playMode) return
        playMode = ""
        stopAllPlayer()

        //check time over
        isTimeOver = true
        checkTimeOver()
    }

    private fun stopTimeFrame()
    {
        playerManagerListener?.onTimeOver()
        Log.d("turnPA", "onTimeOver: stop time frame")
    }
    fun stopForceMedia() {
        if (playMode != PLAY_MODE_FORCE) return

        stopAllPlayer()
        onMediaStopPlaying()
        playerManagerListener?.onEndForceMedia()
//        playerManagerListener?.onTimeOver()
    }

    /**
     * Nhận lệnh phát khẩn cấp từ hub, chuyen json "name" sang media va convert sang force media, tính thời điểm bắt đầu và kết thúc của force media
     * **/

    fun  onPlayForceCommand(
        context: Context,
        responseHub: ResponseHub,
        mediaScheduler: MediaScheduler
    ) {
        try {
            val responseAdapter = moshi.adapter(MediaItemModel::class.java)
            val duration = responseHub.duration
            val type = responseHub.type
            val level = responseHub.level
            val mediaItemObject = responseAdapter.fromJson(responseHub.name)
            //check xem nếu đang phát media khẩn cấp có cấp độ thấp hơn không
            //Nếu có thì bỏ qua không phát
            //Chỉ ưu tiên media khẩn cấp có cấp độ thấp hơn
            Log.d("alarmx", "Duration $duration")
            if (playMode == PLAY_MODE_FORCE) {
                val value = ConfigUtil.getString(context, ConfigUtil.FORCE_MEDIA, "")
                if (value != null && value != "") {
                    val currentForceMedia = ForceMedia.jsonAdapter.fromJson(value)

                    if (currentForceMedia != null && currentForceMedia.level < level) {
                        Log.d("alarmx", " ko đủ level ${currentForceMedia.level} - $level")
                        return}
                    Log.d("alarmx", " đủ level ${currentForceMedia!!.level} - $level")
                }

            }


            val forceMedia =
                ForceMedia(responseHub.message!!, duration, type, level, mediaItemObject).apply {
                    val listDur = duration.split(":")
                    val h = listDur[0].toInt()
                    val m = listDur[1].toInt()
                    val s = listDur[2].toInt()
                    val mediaEndTime = Calendar.getInstance().apply {
                        add(Calendar.HOUR, h)
                        add(Calendar.MINUTE, m)
                        add(Calendar.SECOND, s)
                    }

                    endTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(mediaEndTime.time)
                    fixTime = SimpleDateFormat("HH:mm:ss").format(Date())
                }
            playForceMedia(context, forceMedia, mediaScheduler)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    fun playForceMedia(context: Context, forceMedia: ForceMedia, mediaScheduler: MediaScheduler) {
        try {
            Log.d("aaa", "force")
            //Hủy hẹn giờ của kịch bản đang phát
            mediaScheduler.cancelAllAlarm()

            forceMedia.saveItemToPref(context)
            //hẹn giờ dừng media khẩn cấp
            mediaScheduler.scheduleForceMediaAlarm()

            playMode = PLAY_MODE_FORCE
            stopAllPlayer()
            playMedia(forceMedia.toMediaItem())

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun onActivityStop() {
        stopAllPlayer()
    }

    fun onActivityDestroy() {
        vlcPlayer.release()
//        exoPlayer.release()
    }

    fun resetPlayMode() {
        playMode = PLAY_MODE_INIT//"0"
    }

    companion object {
        const val PLAY_MODE_FORCE = "-2"
        const val PLAY_MODE_INIT = "0"
        const val LOGTAG = "player"
        var currentPlaylist = listOf<MediaItemModel>()
        var handlerLivePlayer: Handler? = null
    }
}