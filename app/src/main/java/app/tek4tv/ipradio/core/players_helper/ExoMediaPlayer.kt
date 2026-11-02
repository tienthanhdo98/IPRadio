//package app.tek4tv.ipradio.core.players_helper
//
//import android.content.Context
//import android.os.Handler
//import android.os.Looper
//import android.util.Log
//import androidx.media3.common.PlaybackException
//import androidx.media3.common.Player
//import androidx.media3.datasource.okhttp.OkHttpDataSource
//import androidx.media3.exoplayer.ExoPlayer
//import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
//import app.tek4tv.ipradio.model.MediaItem
//import app.tek4tv.ipradio.utils.getDurationInSecond
//import app.tek4tv.ipradio.utils.network.OkHttpClientUtil.ignoreAllSSLErrors
//import app.tek4tv.ipradio.utils.toCalendar
//import okhttp3.OkHttpClient
//import java.util.*
//
//
////Mainly for playing m3u8 stream
//class ExoMediaPlayer(
//    appContext: Context,
//    private val eventListener: IPlayerEventListener
//) : IMediaPlayer {
//
//    private val TAG = "ExoMediaPlayer"
//
//    private val exoPlayer: ExoPlayer
//
//    private var currentPlayingMedia: MediaItem? = null
//
//    private var isPlaying = false
//    private var isForceValue = false
//
//    override fun isPlaying(): Boolean = isPlaying
//
//    var handler: Handler? = null
//
//    var countReplayLive = 0
//
//    private val exoEventListener = object : Player.Listener {
//
//        override fun onIsPlayingChanged(isPlaying: Boolean) {
//            super.onIsPlayingChanged(isPlaying)
//
//            this@ExoMediaPlayer.isPlaying = isPlaying
//
//            if (isPlaying) {
//                eventListener.onPlayMediaSuccess(currentPlayingMedia)
//                Log.i(TAG, "onPlaybackStateChanged: onIsPlayingChanged")
//            }
//        }
//
//        override fun onPlaybackStateChanged(state: Int) {
//            super.onPlaybackStateChanged(state)
//            if (state == Player.STATE_ENDED) {
//                Log.i(TAG, "onPlaybackStateChanged: end reach")
//            }
//            if (state == Player.STATE_BUFFERING) {
//                Log.i(TAG, "onPlaybackStateChanged: Buff")
//            }
//
//            if (state == Player.STATE_READY) {
//                Log.i(TAG, "onPlaybackStateChanged: play")
//            }
//        }
//
//        override fun onPlayerError(error: PlaybackException) {
//            super.onPlayerError(error)
//            Log.d(TAG, "exoerror: ${error.message ?: ""}")
//            isPlaying = false
//            try {
//                val isStreamInTimeFrame = checkHls(currentPlayingMedia!!)
//                Log.d(TAG, "onLiveError: Chay lai live $isForceValue")
//                if (isForceValue) {
//                    if (isStreamInTimeFrame) {
//                        eventListener.onPlayerError(error, true, currentPlayingMedia!!)
//                        Handler(Looper.getMainLooper()).postDelayed({
//                            countReplayLive++;
//                            Log.d(TAG, "onLiveError: Chay lai live")
//                            playMedia(currentPlayingMedia!!, isForceValue)
//                        }, 5000)
//                    } else {
//                        Log.d(TAG, "onPlayerError. Stopping from reconnect")
//                        stop()
//                    }
//                } else {
//                    if (isStreamInTimeFrame) {
//                        val resumeTime = calculateResumeLivePoint(currentPlayingMedia!!)
//                        playMedia(currentPlayingMedia!!, isForceValue, resumeTime)
//                    }
//
//                }
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//        }
//    }
//
//    init {
//        //tắt xác thực ssl để phát luồng m3u8
//        val okHttpClient = OkHttpClient.Builder().apply {
//            ignoreAllSSLErrors()
//        }.build()
//
//        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
//
//        // Khởi tạo ExoPlayer với MediaSourceFactory hỗ trợ đa dạng nguồn (HLS, RTSP, v.v.)
//        exoPlayer = ExoPlayer.Builder(appContext)
//            .setMediaSourceFactory(
//                DefaultMediaSourceFactory(appContext).setDataSourceFactory(
//                    dataSourceFactory
//                )
//            )
//            .build()
//
//        exoPlayer.addListener(exoEventListener)
//
//    }
//
//    override fun playMedia(mediaItem: MediaItem, isForce: Boolean, timeStamp: Int) {
//        if (isPlaying) stop()
//        try {
////            if (NetworkUtils.isNetworkConnected(MyApp.appContext)) {
////                if (isInRangeTimePlay()) {
////                    currentPlayingMedia = mediaItem
////                    isForceValue = isForce
////                    Log.e(TAG, "Exoplayer is force $isForceValue")
////                    playHlsExo(mediaItem, timeStamp)
////                }
////            }
//            currentPlayingMedia = mediaItem
//            isForceValue = isForce
//            playHlsExo(mediaItem, timeStamp)
//        } catch (e: Exception) {
//            Log.e(TAG, "Error playing media using Exo")
//            e.printStackTrace()
//        }
//    }
//
//
//    private fun playHlsExo(mediaItem: MediaItem, timeStamp: Int) {
//        var currentTimeStamp = 0
//        if (timeStamp >= 0) currentTimeStamp = timeStamp
//        val em = androidx.media3.common.MediaItem.fromUri(mediaItem.pathBackup)
//
//        exoPlayer.setMediaItem(em)
//        exoPlayer.playWhenReady = true
//        exoPlayer.prepare()
//
//        Log.e(TAG, "Player exo start")
//        Log.e(TAG, "Player exo start is seek ${exoPlayer.isCurrentMediaItemSeekable}")
//        if (!isForceValue) {
//            handler?.removeCallbacksAndMessages(null)
//            handler = Handler(Looper.getMainLooper())
//            handler?.postDelayed(
//                {
//                    exoPlayer.stop()
//                    Log.d(TAG, "stop")
//                    eventListener.onMediaEndReached(currentPlayingMedia)
//                },
//                (getDurationInSecond(mediaItem.duration!!) * 1000).toLong() - currentTimeStamp * 1000
//            )
//        }
//    }
//
//    private fun calculateResumeLivePoint(mediaItem: MediaItem): Int {
//
//        val startTimeOfMedia = toCalendar(mediaItem.start!!)
//        val now = Calendar.getInstance()
//        return ((now.timeInMillis - startTimeOfMedia.timeInMillis) / 1000).toInt()
//
//    }
//
//    override fun stop() {
//        try {
//            handler?.removeCallbacksAndMessages(null)
//            exoPlayer.stop()
//            isPlaying = false
//            currentPlayingMedia = null
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
//    }
//
//    override fun pause() {
//        exoPlayer.pause()
//    }
//
//    override fun resume() {
//        exoPlayer.play()
//    }
//
//    override fun release() {
//        exoPlayer.release()
//    }
//
//    override fun playTest() {
//    }
//
//    /**
//     * Check if a stream is still in time to play
//     * **/
//    private fun checkHls(mediaItem: MediaItem): Boolean {
//        val now = Calendar.getInstance()
//
//        val liveEndTime = toCalendar(mediaItem.fixTime!!).apply {
//            add(Calendar.SECOND, getDurationInSecond(mediaItem.duration!!))
//        }
//        val check = (now <= liveEndTime)
//
//        Log.d(TAG, "check hls: $check")
//
//        return check
//    }
//}