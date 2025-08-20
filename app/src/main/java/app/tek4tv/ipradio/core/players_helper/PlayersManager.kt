package app.tek4tv.ipradio.core.players_helper

import android.content.Context
import android.util.Log
import android.widget.ImageView
import androidx.media3.ui.PlayerView
import app.tek4tv.ipradio.MyApp.Companion.appContext
import app.tek4tv.ipradio.core.SchedulerManager.MediaScheduler
import app.tek4tv.ipradio.di.RetrofitInstance.moshi
import app.tek4tv.ipradio.model.ForceMedia
import app.tek4tv.ipradio.model.MediaItemModel
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.model.ResponseHub
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.toCalendar
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
    private val mediaRepo: MediaRepo,
    exoVideoLayout: PlayerView,
    imageLayout: ImageView,
) : IPlayerEventListener {
    companion object {
        val PLAY_MODE_FORCE = "-2"
        val PLAY_MODE_INIT = "0"
    }

    private val LOGTAG = "player"
    var currentPlaylist = listOf<MediaItemModel>()
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    //    private val vlcPlayer: IMediaPlayer = VlcMediaPlayer(this, vlcVideoLayout)
    private val exoPlayer: IMediaPlayer = ExoMediaPlayer(appContext, this, exoVideoLayout)
    private val imagePlayer = ImagePlayer(this, imageLayout, mediaRepo)
    private var currentPlayingMedia: MediaItemModel? = null

    /**
     * -2 for force media
     * 0 for init
     * HH:mm:ss-HH:mm:ss for scheduled timeframe
     * **/
    var playMode = PLAY_MODE_INIT//"0"

    var playlistIndex = 0
        private set

    var playerManagerListener: IPlayerManagerListener? = null

    /**
     * Determine if we will not play any media in >= 15s to go
     * **/
    private var isTimeOver = false


    override fun onPlayerError(e: Exception?, isLiveError: Boolean, mediaItem: MediaItemModel) {
        playerManagerListener?.onMediaError(e?.message!!, mediaItem)
    }

    override fun onPlayMediaSuccess(mediaItem: MediaItemModel?) {
        playerManagerListener?.onPlayMediaSuccess(mediaItem)
    }

    override fun onMediaEndReached(mediaItem: MediaItemModel?) {
        playNextMedia()
    }

    override fun onMediaForceEndReached(mediaItem: MediaItemModel?) {
        stopForceMedia()
    }

    /**
     * Tìm khung thơi gian hiện tại cần phát
     * **/
    fun startFirstTimeFrame() {
        val now = Calendar.getInstance().timeInMillis
        if (mediaRepo.isHaveScheduler) {
            val scheduledList = mediaRepo.scheduledList
            Log.d(LOGTAG, "onTimeOver: play mode: ${scheduledList.keys}")
            Log.d(LOGTAG, "onTimeOver: play mode: ${scheduledList.size}")
            for (i in scheduledList.keys.indices) {
                val period = scheduledList.keys.elementAt(i)
                Log.d(LOGTAG, "onTimeOver: $period")
                val t = period.split("-")
                val start = toCalendar(t[0])
                val end = toCalendar(t[1])
                if (now in start.timeInMillis..end.timeInMillis) {
                    setTimeFrame(period)
                    break
                } else {
                    val startDefault = toCalendar(mediaRepo.startTimeDefaultScenario)
                    val endDefault = toCalendar(mediaRepo.endTimeDefaultScenario)
                    if (now in startDefault.timeInMillis..endDefault.timeInMillis) {
                        currentPlaylist = mediaRepo.listDefaultScenario
                        playlistIndex = 0
                        playMediaByIndex(playlistIndex)
                    }
                }
            }
        } else {
            val start = toCalendar(mediaRepo.startTimeDefaultScenario)
            val end = toCalendar(mediaRepo.endTimeDefaultScenario)
            if (now in start.timeInMillis..end.timeInMillis) {
                currentPlaylist = mediaRepo.listDefaultScenario
                playlistIndex = 0
                playMediaByIndex(playlistIndex)
            }

        }
    }

    fun playDefault() {
        currentPlaylist = mediaRepo.listDefaultScenario
        playlistIndex = 0
        playMediaByIndex(playlistIndex)
    }

    fun setTimeFrame(timeFrame: String) {

        stopAllPlayer()

        playMode = timeFrame

        currentPlaylist =
            mediaRepo.scheduledList[timeFrame]
                ?: listOf()

        playlistIndex = 0
        playMediaByIndex(playlistIndex)

        isTimeOver = false

    }

    fun playNextMedia() {
        if (playMode == "") return

        playlistIndex++
        Log.d(LOGTAG, "playNextMedia $playlistIndex - ${currentPlaylist.size}")
        if (playlistIndex < currentPlaylist.size) {
            Log.d(LOGTAG, "Play media phat tiep")
            playMediaByIndex(playlistIndex)
        } else {
            Log.d(LOGTAG, "Play media loop")
            playlistIndex = 0
            playMediaByIndex(playlistIndex)
        }
    }

    fun resumeMedia() {
        exoPlayer.resume()
    }

    private fun playMediaByIndex(index: Int, timeStamp: Int = 0) {
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


    private fun playMedia(mediaItem: MediaItemModel, timeStamp: Int = 0) {
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

    private fun playLiveStream(mediaItem: MediaItemModel) {
        //vlcPlayer.playMedia(mediaItem, 0)
        exoPlayer.playMedia(mediaItem, 0)
    }

    fun stopAllPlayer() {
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
            Log.d("turnPA", "onTimeOver: check time over")
        }
    }


    private fun calculateResumeLivePoint(mediaItem: MediaItemModel): Int {
        //với force media, fixtime dc gán là start time
        val startTimeOfMedia = toCalendar(mediaItem.fixTime!!)
        val now = Calendar.getInstance()
        return ((now.timeInMillis - startTimeOfMedia.timeInMillis) / 1000).toInt()
    }

    fun stopPlaylist(cancelListKey: String) {
        Log.d("alarmx", "stopPlaylist")
//        if (cancelListKey != playMode) return
//        playMode = ""
        stopAllPlayer()
        //check time over
        isTimeOver = true
//        checkTimeOver()
        //
        currentPlaylist = mediaRepo.listDefaultScenario
        playlistIndex = 0
        playMediaByIndex(playlistIndex)
        Log.d("alarmx", "stopPlaylist: continue play default")
    }

    private fun stopTimeFrame() {
        playerManagerListener?.onTimeOver()
        Log.d("turnPA", "onTimeOver: stop time frame")
    }

    fun stopForceMedia() {
        if (playMode != PLAY_MODE_FORCE) return

        stopAllPlayer()
        playerManagerListener?.onEndForceMedia()
//        playerManagerListener?.onTimeOver()
    }

    /**
     * Nhận lệnh phát khẩn cấp từ hub, chuyen json "name" sang media va convert sang force media, tính thời điểm bắt đầu và kết thúc của force media
     * **/

    fun onPlayForceCommand(
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
                        return
                    }
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
            mediaScheduler.cancelAllScheduledListAlarm()

            forceMedia.saveItemToPref(context)
            //hẹn giờ dừng media khẩn cấp
            mediaScheduler.scheduleForceMediaAlarm(forceMedia)

            playMode = PLAY_MODE_FORCE
            stopAllPlayer()
            playMedia(forceMedia.toMediaItem())

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun onActivityDestroy() {
        exoPlayer.release()
    }

    fun resetPlayMode() {
        playMode = PLAY_MODE_INIT//"0"
    }


}