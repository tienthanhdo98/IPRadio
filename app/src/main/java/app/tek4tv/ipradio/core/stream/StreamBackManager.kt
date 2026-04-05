package app.tek4tv.ipradio.core.stream

import FFMEPGStreamer
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.Job
import androidx.core.net.toUri
import app.tek4tv.ipradio.model.IcecastConfig
import app.tek4tv.ipradio.model.McuStreamBackData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

class StreamBackManager(
    private val activity: AppCompatActivity
) {
    companion object {
        private const val TAG = "StreamBackManager"
        private const val CMD_START13 = 0
        private const val CMD_START24 = 1
        private const val CMD_STOP = 2
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()

    private var currentStreamer: FFMEPGStreamer? = null
    private var streamJob: Job? = null
    private var stopTimeoutJob: Job? = null

    fun handleStreamBack(data: McuStreamBackData, onStopBack: () -> Unit) {
        when (data.cmd) {
            CMD_STOP -> {
                scope.launch {
                    stopStreamBack()
                    onStopBack()
                }
            }

            CMD_START13, CMD_START24 -> {
                scope.launch { startStreamBack(data.streamUrl, data.timeout, onStopBack) }
            }

            else -> {
                Log.w(TAG, "Unknown cmd=${data.cmd}, ignore")
            }
        }
    }

    private suspend fun startStreamBack(url: String?, timeout: Int?, onStopBack: () -> Unit) {
        mutex.withLock {
            if (url.isNullOrBlank()) return

            stopStreamBackLocked()

            streamJob = scope.launch(Dispatchers.IO) {
                try {
                    val streamer = FFMEPGStreamer()
                    currentStreamer = streamer

                    Log.d(TAG, "Connecting and streaming to $url")
                    streamer.startMicToIcecast(activity, url)
                    Log.d(TAG, "Connect OK")
                } catch (e: CancellationException) {
                    Log.d(TAG, "Stream job cancelled")
                    try {
                        currentStreamer?.stopMicToIcecast()
                    } catch (_: Exception) {
                    }
                    currentStreamer = null
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Connect fail: ${e.message}", e)
                    try {
                        currentStreamer?.stopMicToIcecast()
                    } catch (_: Exception) {
                    }
                    currentStreamer = null
                }
            }

            val safeTimeout = timeout ?: 0
            if (safeTimeout > 0) {
                stopTimeoutJob = scope.launch {
                    delay(safeTimeout * 1000L)
                    Log.d(TAG, "Timeout reached: ${safeTimeout}s, stopping stream")
                    mutex.withLock {
                        stopStreamBackLocked()
                    }
                    onStopBack()
                }
            }
        }
    }

    suspend fun stopStreamBack() {
        mutex.withLock {
            stopStreamBackLocked()
        }
    }

    private fun stopStreamBackLocked() {
        stopTimeoutJob?.cancel()
        stopTimeoutJob = null

        streamJob?.cancel()
        streamJob = null

        try {
            currentStreamer?.stopMicToIcecast()
            Log.d(TAG, "Stream stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Stop stream fail: ${e.message}", e)
        } finally {
            currentStreamer = null
        }
    }

    fun release() {
        scope.launch {
            mutex.withLock {
                stopStreamBackLocked()
            }
            scope.cancel()
        }
    }

}