package app.tek4tv.ipradio.core.players_helper

//import org.videolan.libvlc.util.VLCVideoLayout
import android.content.Context
import android.util.Log
import android.widget.ImageView
import androidx.media3.ui.PlayerView
import app.tek4tv.ipradio.core.constants.AppConstants.FULL_SCREEN
import app.tek4tv.ipradio.core.constants.AppConstants.LEFT_AREA
import app.tek4tv.ipradio.core.constants.AppConstants.RIGHT_AREA
import app.tek4tv.ipradio.core.scheduler.MediaScheduler
import app.tek4tv.ipradio.di.JsonAdapterProvider.jsonListMediaItemAdapter
import app.tek4tv.ipradio.di.JsonAdapterProvider.mediaItemMoshiAdapter
import app.tek4tv.ipradio.model.ForceMedia
import app.tek4tv.ipradio.model.ForceMedia.Companion.saveListForceToPref
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.model.ResponseHub
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.GlobalProperties
import app.tek4tv.ipradio.utils.getDurationInSecond
import app.tek4tv.ipradio.utils.toCalendar
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    imageLayout: ImageView,
    private val mediaRepo: MediaRepo,
    exoVideoLayout: PlayerView
) : IPlayerEventListener {

    var TAG = "PlayersManager"
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val exoPlayer: IMediaPlayer = ExoMediaPlayer(appContext, this, exoVideoLayout)
    private val imagePlayer = ImagePlayer(this, imageLayout, mediaRepo)
    private var currentPlayingMedia: MediaItem? = null

    /**
     * -2 for force media
     * 0 for init
     * HH:mm:ss-HH:mm:ss for scheduled timeframe
     * **/
    var playMode = PLAY_MODE_INIT//"0"

    /**
     * List of media for a timeframe
     * **/
    private var currentPlaylist = listOf<MediaItem>()


    private var currentTextPlaylist = listOf<MediaItem>()

    var playlistIndex = 0
        private set

    var playerManagerListener: IPlayerManagerListener? = null

    /**
     * Determine if we will not play any media in >= 15s to go
     * **/
    var isTimeOver = false

    override fun onPlayerError(e: Exception, mediaItem: MediaItem) {
        Log.d(TAG, "onPlayerError: ${e.message}")
        playerManagerListener?.onMediaError(e.message!!, mediaItem)

    }

    override fun onPlayMediaSuccess(mediaItem: MediaItem?) {
        playerManagerListener?.onPlayMediaSuccess(mediaItem)
    }

    override fun onMediaEndReached(mediaItem: MediaItem?) {
        playerManagerListener?.onMediaEndReach(mediaItem!!)
        playNextMedia()
    }

    override fun onMediaForceEndReached(mediaItem: MediaItem?) {
        Log.d(TAG, "onMediaForceEndReached")
        playNextMedia()
    }

    fun stopLeftPlaylist() {
        playerManagerListener?.onEndLeftMediaPlaying()
    }


    fun startLeftTimeFrame(timeFrame: String) {
        val listMediaLeft = mediaRepo.scheduledListLeft[timeFrame] ?: listOf()
        playerManagerListener?.onLeftMediaPlaying(listMediaLeft)
    }

    /**
     * Tìm khung thơi gian hiện tại cần phát
     * **/

    fun startFirstTimeFrame() {
        var found = false
        val scheduledList = mediaRepo.scheduledList
        Log.d(TAG, "startFirstTimeFrame: list: ${scheduledList.size}")
        for (i in scheduledList.keys.indices) {
            val period = scheduledList.keys.elementAt(i)
            val t = period.split("-")
            val start = toCalendar(t[0])
            val end = toCalendar(t[1])
            val now = Calendar.getInstance().timeInMillis
            if (now >= start.timeInMillis && now <= end.timeInMillis) {
                val resumePoint = calculateResumePoint(start, period)
                setTimeFrame(period, resumePoint)
                startLeftTimeFrame(timeFrame = period)
                found = true
                Log.d(TAG, "startFirstTimeFrame: found, start $resumePoint")
                break
            }
        }
        if (!found) {
            Log.d(TAG, "startFirstTimeFrame: not found")
            playerManagerListener?.onTimeOver()
        }
    }

    fun setTimeFrame(timeFrame: String, resumePoint: Pair<Int, Int>?) {
        stopAllPlayer()
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
        if (playMode == "") return
        playlistIndex++
        if (playlistIndex < currentPlaylist.size) {
            playMediaByIndex(playlistIndex)
        } else {
            if (playMode == PLAY_MODE_FORCE) {
                stopForceMedia()
            } else {
                stopTimeFrame()
            }
        }
    }

    private fun playMediaByIndex(index: Int, timeStamp: Int = 0) {
        try {
            if (currentPlaylist.isEmpty()) return
            val mediaItem = currentPlaylist[index]

            playMedia(mediaItem, timeStamp)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun pauseMedia() {
        currentPauseTimePlayer = Calendar.getInstance().timeInMillis
        //vlcPlayer.pause()
        exoPlayer.pause()
    }

    var currentPauseTimePlayer: Long = 0
    fun resumeMedia() {
        val now = Calendar.getInstance().timeInMillis
        val mediaDuration = getDurationInSecond(currentPlayingMedia?.duration!!) * 1000
        val timeEnd = currentPauseTimePlayer + mediaDuration
        Log.d("CONTROL_MEDIA", "resume: now $now")
        Log.d("CONTROL_MEDIA", "resume: current pause$currentPauseTimePlayer")
        if (now < timeEnd) {
            Log.d("CONTROL_MEDIA", "resume: $timeEnd")
            //vlcPlayer.resume()
            exoPlayer.resume()

        } else {
            if (currentPauseTimePlayer != 0L) {
                Log.d("CONTROL_MEDIA", "re play playlist: $timeEnd")
                startFirstTimeFrame()
            }
        }
    }

    private fun playMedia(mediaItem: MediaItem, timeStamp: Int = 0) {
        try {
            currentPlayingMedia = mediaItem
            when (mediaItem.getMediaType()) {
                MediaType.FILE -> {
                    if (mediaItem.body!!.contains("m3u8")) {
                        playLiveStream(mediaItem)
                    } else {
                        when (mediaItem.contentType?.id) {
                            2 -> {
                                imagePlayer.playMedia(mediaItem, timeStamp)

                            }

                            else -> {
                                exoPlayer.playMedia(mediaItem, timeStamp)

                            }
                        }
                    }
                }

                MediaType.LIVE -> {
                    if (mediaItem.contentType?.id == 2) {
                        imagePlayer.playMedia(mediaItem, timeStamp)

                    } else {
                        playLiveStream(mediaItem)

                    }
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
        //vlcPlayer.playMedia(mediaItem, 0)
        exoPlayer.playMedia(mediaItem, 0)
    }


    fun stopAllPlayer() {
        imagePlayer.stop()
        exoPlayer.stop()

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
            Log.e(TAG, "Error calculate resume")
            e.printStackTrace()
            return Pair(0, 0)
        }
    }

    fun stopTimeFrame() {
        playerManagerListener?.onTimeOver()
    }

    fun stopForceMedia() {
        if (playMode != PLAY_MODE_FORCE) return
        stopAllPlayer()
        playerManagerListener?.onEndForceMedia()
        playerManagerListener?.onTimeOver()
    }

    fun stopPlaylist(cancelListKey: String) {
        if (cancelListKey != playMode) return
        playMode = ""
        stopAllPlayer()
        //check time over
        isTimeOver = true
        checkTimeOver()
    }


    /**
     * Nhận lệnh phát khẩn cấp từ hub
     * **/
    fun onPlayForceCommand(
        context: Context,
        responseHub: ResponseHub,
        mediaScheduler: MediaScheduler,
        isLayoutChange: Boolean? = false
    ) {
        try {
            Log.d("onPlayForceCommand", "onPlayForceCommand: ${responseHub.name}")
            //val listMediaItem = responseHub.name?.let { jsonListMediaItemAdapter.fromJson(it) }
            val mediaItem = responseHub.name?.let { mediaItemMoshiAdapter.fromJson(it) }
            val listMediaItem = listOf(mediaItem!!)
            val listForceMedia = mutableListOf<MediaItem>()
            //check xem nếu đang phát media khẩn cấp có cấp độ thấp hơn không
            //Nếu có thì bỏ qua không phát
            //Chỉ ưu tiên media khẩn cấp có cấp độ thấp hơn
            if (playMode == PLAY_MODE_FORCE) {
                if (ForceMedia.hasValidForceMedia(context)) {
                    val currentListForceMedia = ForceMedia.getLocalForceMedia(context)
                    if (currentListForceMedia != null && currentListForceMedia.first().level < responseHub.level!!) return
                }
            }
            val currentTime = Calendar.getInstance()

            listMediaItem.forEach {
                val forceMedia = ForceMedia(
                    duration = it.duration!!, type = it.type!!, level = it.level, mediaItem = it
                ).apply {
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

            currentTextPlaylist =
                listForceMedia.filter { it.broadcastRegion?.value == LEFT_AREA.toInt() }

            playerManagerListener?.onLeftMediaPlaying(currentTextPlaylist)

//            currentPlaylist =
//                listForceMedia.filter { it.broadcastRegion?.value == RIGHT_AREA.toInt() || it.broadcastRegion?.value == FULL_SCREEN }

            currentPlaylist = listForceMedia

            playlistIndex = 0

            playMediaByIndex(playlistIndex, 0)

            mediaScheduler.scheduleForceMediaAlarm(listForceMedia.lastOrNull()!!)

        } catch (e: JsonDataException) {
            Log.d(TAG, "playForceMedia error: ${e.message} ")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopAndEndTimeFrame() {
        stopAllPlayer()
        currentPlaylist = emptyList()
        playerManagerListener?.onTimeOver()
    }


    fun onActivityDestroy() {
        //vlcPlayer.release()
        imagePlayer.release()
        exoPlayer.release()
    }

    fun resetPlayMode() {
        playMode = PLAY_MODE_INIT//"0"
    }


    companion object {
        const val PLAY_MODE_FORCE = "-2"
        const val PLAY_MODE_INIT = "0"
    }

}