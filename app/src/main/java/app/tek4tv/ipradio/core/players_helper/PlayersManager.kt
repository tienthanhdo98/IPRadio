package app.tek4tv.ipradio.core.players_helper

import android.content.Context
import android.util.Log
import android.widget.ImageView
import app.tek4tv.ipradio.core.MediaScheduler
import app.tek4tv.ipradio.di.JsonAdapterProvider.jsonListMediaItemAdapter
import app.tek4tv.ipradio.model.ForceMedia
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.model.ResponseHub
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.utils.GlobalProperties
import app.tek4tv.ipradio.utils.TimeUtils.getDurationInSecond
import app.tek4tv.ipradio.utils.TimeUtils.toCalendar
import app.tek4tv.ipradio.utils.Utils.Companion.FULL_SCREEN
import app.tek4tv.ipradio.utils.Utils.Companion.LEFT_AREA
import app.tek4tv.ipradio.utils.Utils.Companion.RIGHT_AREA
import app.tek4tv.ipradio.utils.Utils.Companion.saveListForceToPref
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
    imageLayout: ImageView,
    private val mediaRepo: MediaRepo
) : IPlayerEventListener {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val vlcPlayer = VlcMediaPlayer(appContext, this, vlcVideoLayout)
    private val exoPlayer = ExoMediaPlayer(appContext, this)
    private val imagePlayer = ImagePlayer(this, imageLayout, mediaRepo)
    private var currentPlayer: BaseMediaPlayer? = null
    var playerManagerListener: IPlayerManagerListener? = null
    private val PLAY_MODE_FORCE = "-2"
    private val PLAY_MODE_INIT = "0"
    private val LOGTAG = "player"
    private var currentPlaylist = listOf<MediaItem>()
    private var currentTextPlaylist = listOf<MediaItem>()


    /**
     * -2 for force media
     * 0 for init
     * HH:mm:ss-HH:mm:ss for scheduled timeframe
     * **/
    private var playMode = PLAY_MODE_INIT//"0"

    /**
     * List of media for a timeframe
     * **/

    var playlistIndex = 0
        private set

    /**
     * Determine if we will not play any media in >= 15s to go
     * **/
    var isTimeOver = false


    override fun onPlayerError(e: Exception?, isLiveError: Boolean, mediaItem: MediaItem) {
        if (isLiveError) {
            playerManagerListener?.onLiveError(mediaItem)
        }
    }

    override fun onPlayMediaInit(mediaItem: MediaItem?) {
        playerManagerListener?.onMediaInit(mediaItem)
        Log.d(LOGTAG, "onPlayMediaInit")
    }

    override fun onPlayMediaSuccess(mediaItem: MediaItem?) {
        playerManagerListener?.onPlayMediaSuccess(mediaItem)
    }

    override fun onMediaEndReached(mediaItem: MediaItem?) {
        Log.d(LOGTAG, "onMediaEndReached")
        playNextMedia()
    }

    override fun onMediaForceEndReached(mediaItem: MediaItem?) {
        Log.d(LOGTAG, "onMediaForceEndReached")
        playNextMedia()
    }


    private fun stopLeftPlaylist() {
        playerManagerListener?.onEndLeftMediaPlaying()
    }

    fun startLeftTimeFrame(timeFrame: String) {
        val listMediaLeft = mediaRepo.scheduledListLeft[timeFrame] ?: emptyList()
        playerManagerListener?.onLeftMediaPlaying(listMediaLeft)
    }

    /**
     * Tìm khung thơi gian hiện tại cần phát
     * **/
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
                startLeftTimeFrame(timeFrame = period)
                found = true
                break
            }
        }
        if (!found) {
            playerManagerListener?.onTimeOver()
            Log.d(LOGTAG, "onTimeOver: not time frame found")
        }
    }

    fun setTimeFrame(timeFrame: String, resumePoint: Pair<Int, Int>?) {

        stopAllPlayer()

        playMode = timeFrame

        currentPlaylist =
            mediaRepo.scheduledList[timeFrame]?.filter { it.broadcastRegion?.id == RIGHT_AREA || it.broadcastRegion?.id == FULL_SCREEN }
                ?: emptyList()
        currentTextPlaylist =
            mediaRepo.scheduledList[timeFrame]?.filter { it.broadcastRegion?.id == LEFT_AREA }
                ?: emptyList()

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
        playlistIndex++
        if (playlistIndex < currentPlaylist.size) {
            Log.d(LOGTAG, "Play media phat tiep")
            playMediaByIndex(playlistIndex)
        } else {
            Log.d(LOGTAG, "Play media phat tiep cham dich")
            if (playMode == PLAY_MODE_FORCE) {
                stopForceMedia()
            } else {
                stopTimeFrame()
            }
        }
    }

    var currentPauseTimePlayer: Long = 0
    fun pauseMedia() {
        currentPauseTimePlayer = Calendar.getInstance().timeInMillis
        vlcPlayer.pause()
    }

    fun resumeMedia() {
        vlcPlayer.resume()
    }

    private fun playMediaByIndex(index: Int, timeStamp: Int = -1) {
        try {
            Log.d(LOGTAG, "currentPlaylist ${currentPlaylist.size}")
            Log.d(LOGTAG, "currentPlaylist index $index")
            if (currentPlaylist.isEmpty()) return
            val mediaItem = currentPlaylist[index]
            Log.d(LOGTAG, "play ${mediaItem.title}")
            playMedia(mediaItem, timeStamp)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playMedia(mediaItem: MediaItem, timeStamp: Int = -1) {
        try {
            when (mediaItem.getMediaType()) {
                MediaType.FILE -> {
                    if (mediaItem.body!!.contains("m3u8")) {
                        playLiveStream(mediaItem, false, timeStamp)
                    } else {
                        currentPlayer = when (mediaItem.contentType?.id) {
                            2 -> {
                                imagePlayer.playMedia(mediaItem, false, 0)
                                imagePlayer
                            }
                            5 -> {
                                vlcPlayer.playMedia(mediaItem, true, timeStamp)
                                vlcPlayer
                            }
                            else -> {
                                vlcPlayer.playMedia(mediaItem, false, timeStamp)
                                vlcPlayer
                            }
                        }
                    }
                }
                MediaType.LIVE -> {
                    if (mediaItem.contentType?.id == 2) {
                        imagePlayer.playMedia(mediaItem, true, 0)
                        currentPlayer = imagePlayer
                    } else {
                        playLiveStream(mediaItem, true, timeStamp)
                    }
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


    private fun playLiveStream(mediaItem: MediaItem, isForce: Boolean, timeStamp: Int) {
//        val resumeLiveTime = if (isForce) calculateResumeLivePoint(mediaItem) else timeStamp
//        Log.d(LOGTAG, "playLiveStream resume time: $resumeLiveTime")
        val resumeLiveTime = 0
        vlcPlayer.playMedia(mediaItem, isForce, resumeLiveTime)
        currentPlayer = vlcPlayer
    }

    fun stopAllPlayer() {
        if (currentPlayer == vlcPlayer) {
            vlcPlayer.stop()
        }
        if (currentPlayer == exoPlayer) {
            exoPlayer.stop()
        }
        if (currentPlayer == imagePlayer) {
            imagePlayer.stop()
        }
    }

    fun stopAndEndTimeFrame(type: String) {
        stopAllPlayer()
        playerManagerListener?.onTimeOver()
        if (type != FULL_SCREEN) {
            stopLeftPlaylist()
        }

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
            val mediaList =
                mediaRepo.scheduledList[timeFrame]?.filter { it.broadcastRegion?.id == RIGHT_AREA || it.broadcastRegion?.id == FULL_SCREEN }
                    ?: return Pair(0, 0)
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

    private fun calculateResumeLivePoint(mediaItem: MediaItem): Int {
        //với force media, fixtime dc gán là start time
        val startTimeOfMedia = toCalendar(mediaItem.fixTime!!)
        val now = Calendar.getInstance()
        return ((now.timeInMillis - startTimeOfMedia.timeInMillis) / 1000).toInt()
    }

    fun stopTimeFrame() {
        playerManagerListener?.onTimeOver()
        Log.d("turnPA", "onTimeOver: stop time frame")
    }

    fun stopForceMedia() {
        if (playMode != PLAY_MODE_FORCE) return

        stopAllPlayer()
        playerManagerListener?.onEndForceMedia()

    }

    /**
     * Nhận lệnh phát khẩn cấp từ hub, chuyen json "name" sang media va convert sang force media, tính thời điểm bắt đầu và kết thúc của force media
     * **/

    fun onPlayForceCommand(
        context: Context,
        responseHub: ResponseHub,
        mediaScheduler: MediaScheduler,
        isLayoutChange: Boolean? = false
    ) {
        try {
            val listMediaItem = jsonListMediaItemAdapter.fromJson(responseHub.name)
            val listForceMedia = mutableListOf<MediaItem>()
            //check xem nếu đang phát media khẩn cấp có cấp độ thấp hơn không
            //Nếu có thì bỏ qua không phát
            //Chỉ ưu tiên media khẩn cấp có cấp độ thấp hơn
//            if (playMode == PLAY_MODE_FORCE) {
//                val value = ConfigUtil.getString(context, ConfigUtil.FORCE_MEDIA, "")
//                if (value != null && value != "") {
//                    val currentForceMedia = ForceMedia.jsonAdapter.fromJson(value)
//                    if (currentForceMedia != null && currentForceMedia.level < level) return
//                }
//            }
            val currentTime = Calendar.getInstance()

            listMediaItem?.forEach {
                val forceMedia = ForceMedia(it.duration, it.type!!, it.level, it).apply {
                    val listDur = duration!!.split(":")
                    val h = listDur[0].toInt()
                    val m = listDur[1].toInt()
                    val s = listDur[2].toInt()
                    val mediaEndTime = currentTime.apply {
                        add(Calendar.HOUR, h)
                        add(Calendar.MINUTE, m)
                        add(Calendar.SECOND, s)
                    }
                    endTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(mediaEndTime.time)
                }.toMediaItem()
                listForceMedia.add(forceMedia)
            }
            Log.d(
                LOGTAG, "live: ${jsonListMediaItemAdapter.toJson(listForceMedia)}"
            )
            saveListForceToPref(context, jsonListMediaItemAdapter.toJson(listForceMedia))
            if (isLayoutChange == false) {
                playForceMedia(listForceMedia, mediaScheduler = mediaScheduler)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playForceMedia(
        listForceMedia: List<MediaItem>, mediaScheduler: MediaScheduler
    ) {
        try {
            //Hủy hẹn giờ của kịch bản đang phát
            mediaScheduler.cancelAllScheduledListAlarm()
            //hẹn giờ dừng media khẩn cấp
            playMode = PLAY_MODE_FORCE

            currentTextPlaylist = listForceMedia.filter { it.broadcastRegion?.id == LEFT_AREA }
        
            playerManagerListener?.onLeftMediaPlaying(currentTextPlaylist)


            currentPlaylist =
                listForceMedia.filter { it.broadcastRegion?.id == RIGHT_AREA || it.broadcastRegion?.id == FULL_SCREEN }
            playlistIndex = 0

            playMediaByIndex(playlistIndex, 0)

            //mediaScheduler.scheduleForceMediaAlarm(listForceMedia.lastOrNull()!!)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun onActivityDestroy() {
        vlcPlayer.release()
        exoPlayer.release()
    }

    fun resetPlayMode() {
        playMode = PLAY_MODE_INIT//"0"
    }


}