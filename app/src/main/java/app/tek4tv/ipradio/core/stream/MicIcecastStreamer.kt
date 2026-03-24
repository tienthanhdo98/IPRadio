package app.tek4tv.ipradio.core.stream

import android.util.Log

//class MicIcecastStreamer {
//
//    companion object {
//        private const val TAG = "MicIcecastStreamer"
//    }
//
//    private var session: FFmpegSession? = null
//
//    fun start(icecastUrl: String) {
//        stop()
//
//        val cmd = "-f opensles " +
//                "-i default " +
//                "-acodec libmp3lame " +
//                "-ab 128k " +
//                "-ar 44100 " +
//                "-ac 1 " +
//                "-content_type audio/mpeg " +
//                "-f mp3 " +
//                icecastUrl
//
//        Log.d(TAG, "Start: $cmd")
//
//        session = FFmpegKit.executeAsync(
//            cmd,
//            { s ->
//                when {
//                    ReturnCode.isSuccess(s.returnCode) ->
//                        Log.d(TAG, "Kết thúc OK")
//
//                    ReturnCode.isCancel(s.returnCode) ->
//                        Log.d(TAG, "Đã cancel")
//
//                    else ->
//                        Log.e(TAG, "Lỗi:\n${s.failStackTrace}")
//                }
//            },
//            { log -> Log.v(TAG, log.message) },
//            { stats -> Log.d(TAG, "time=${stats.time}ms size=${stats.size}KB") }
//        )
//    }
//
//    fun stop() {
//        session?.let {
//            FFmpegKit.cancel(it.sessionId)
//            session = null
//            Log.d(TAG, "Đã stop")
//        }
//    }
//
//    val isRunning: Boolean
//        get() = session?.isRunning == true
//}