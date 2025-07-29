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
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.GlobalProperties
import app.tek4tv.ipradio.utils.Utils.Companion.FULL_SCREEN
import app.tek4tv.ipradio.utils.Utils.Companion.LEFT_AREA
import app.tek4tv.ipradio.utils.Utils.Companion.RIGHT_AREA
import app.tek4tv.ipradio.utils.Utils.Companion.saveListForceToPref
import app.tek4tv.ipradio.utils.getDurationInSecond
import app.tek4tv.ipradio.utils.toCalendar
import com.squareup.moshi.JsonDataException
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

    var TAG = "PlayersManager"
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val vlcPlayer: IMediaPlayer = VlcMediaPlayer(appContext, this, vlcVideoLayout)
    private val imagePlayer = ImagePlayer(this, imageLayout, mediaRepo)
    private var currentPlayingMedia: MediaItem? = null
    private var currentPlayer: IMediaPlayer? = null

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


    private var currentTextPlaylist = listOf<MediaItem>()

    var playlistIndex = 0
        private set

    var playerManagerListener: IPlayerManagerListener? = null

    /**
     * Determine if we will not play any media in >= 15s to go
     * **/
    var isTimeOver = false

    override fun onPlayerError(e: Exception?, isLiveError: Boolean, mediaItem: MediaItem) {
        if (isLiveError) {
            //To log stream played duration
            playerManagerListener?.onLiveError(mediaItem)
        }
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

    fun startLeftTimeFrame(timeFrame: String) {
        val listMediaLeft = mediaRepo.scheduledListLeft[timeFrame] ?: listOf()
        playerManagerListener?.onLeftMediaPlaying(listMediaLeft)
    }

    fun stopLeftPlaylist() {
        playerManagerListener?.onEndLeftMediaPlaying()
    }

    /**
     * Tìm khung thơi gian hiện tại cần phát
     * **/

    fun startFirstTimeFrame() {
        var found = false
        val scheduledList = mediaRepo.scheduledList
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
//        if (playMode == "") return
//
//        playlistIndex++
//        try {
//            if (playlistIndex < currentPlaylist.size) {
//                Log.d(TAG, "Play media next")
//                playMediaByIndex(playlistIndex)
//            } else {
//                Log.d(TAG, "Play media phat tiep cham dich")
//                stopAllPlayer()
//                stopTimeFrame()
//            }
//        } catch (e: Exception) {
//
//        }
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
        vlcPlayer.pause()
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
            vlcPlayer.resume()

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
                        currentPlayer = when (mediaItem.contentType?.id) {
                            2 -> {
                                imagePlayer.playMedia(mediaItem)
                                imagePlayer
                            }
                            5 -> {
                                vlcPlayer.playMedia(mediaItem)
                                vlcPlayer
                            }
                            else -> {
                                vlcPlayer.playMedia(mediaItem)
                                vlcPlayer
                            }
                        }
                    }
                }

                MediaType.LIVE -> {
                    if (mediaItem.contentType?.id == 2) {
                        imagePlayer.playMedia(mediaItem)
                        currentPlayer = imagePlayer
                    } else {
                        playLiveStream(mediaItem)
                        currentPlayer = vlcPlayer
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
        vlcPlayer.playMedia(mediaItem, 0)
    }


    fun stopAllPlayer() {
        if (currentPlayer == vlcPlayer) {
            vlcPlayer.stop()
        }
        if (currentPlayer == imagePlayer) {
            imagePlayer.stop()
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
                val forceMedia = ForceMedia(
                    duration = it.duration!!,
                    type = it.type!!,
                    level = it.level,
                    mediaItem = it
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
            Log.d(
                "wssLive", "live: ${jsonListMediaItemAdapter.toJson(listForceMedia)}"
            )
            saveListForceToPref(context, jsonListMediaItemAdapter.toJson(listForceMedia))
            if (isLayoutChange == false) {
                playForceMedia(listForceMedia, mediaScheduler = mediaScheduler)
            }
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

//    fun playForceMedia(
//        forceMedia: ForceMedia,
//        mediaScheduler: MediaScheduler,
//        isPlayAtStart: Boolean
//    ) {
//        try {
//            Log.d(TAG, "Play force")
//            //Hủy hẹn giờ của kịch bản đang phát
//            mediaScheduler.cancelAllScheduledListAlarm()
//
////           forceMedia.saveItemToPref()
//            //hẹn giờ dừng media khẩn cấp
//            playMode = PLAY_MODE_FORCE
//            stopAllPlayer()
//            if (isPlayAtStart) {
//                playMedia(forceMedia.toMediaItem())
//            }
////            playMedia(forceMedia.toMediaItem())
//            mediaScheduler.scheduleForceMediaAlarm(forceMedia)
//
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
//    }

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

            currentPlaylist =
                listForceMedia.filter { it.broadcastRegion?.value == RIGHT_AREA.toInt() || it.broadcastRegion?.value == FULL_SCREEN }
            playlistIndex = 0

            playMediaByIndex(playlistIndex, 0)

            //mediaScheduler.scheduleForceMediaAlarm(listForceMedia.lastOrNull()!!)

        } catch (e: JsonDataException) {
            Log.d(TAG, "playForceMedia error: ${e.message} ")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopAndEndTimeFrame(type: Int) {
        vlcPlayer.stop()
        playerManagerListener?.onTimeOver()
        if (type != FULL_SCREEN) {
            stopLeftPlaylist()
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
    }

}