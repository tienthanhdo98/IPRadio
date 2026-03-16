package app.tek4tv.ipradio.core.stream

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Job
import androidx.core.net.toUri
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
    private val appContext: Context
) {
    companion object {
        private const val TAG = "StreamBackManager"
        private const val CMD_START13 = 0
        private const val CMD_START24 = 1
        private const val CMD_STOP = 2
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()

    private var currentStreamer: MicAacIcecastStreamer? = null
    private var streamJob: Job? = null
    private var stopTimeoutJob: Job? = null

    fun handleStreamBack(url: String, cmd: Int?, timeout: Int?) {
        when (cmd) {
            CMD_STOP -> {
                scope.launch { stopStreamBack() }
            }

            CMD_START13, CMD_START24 -> {
                scope.launch { startStreamBack(url, timeout) }
            }

            else -> {
                Log.w(TAG, "Unknown cmd=$cmd, ignore")
            }
        }
    }

    private suspend fun startStreamBack(url: String, timeout: Int?) {
        mutex.withLock {
            val config = parseIcecastUrl(url)
            if (config == null) {
                Log.e(TAG, "Invalid stream url: $url")
                return
            }

            stopStreamBackLocked()

            streamJob = scope.launch(Dispatchers.IO) {
                try {
                    val client = IcecastPutClient(config)
                    val streamer = MicAacIcecastStreamer(appContext, client)
                    currentStreamer = streamer

                    Log.d(TAG, "Connecting and streaming to $url")
                    streamer.start()
                    Log.d(TAG, "Connect OK")
                } catch (e: CancellationException) {
                    Log.d(TAG, "Stream job cancelled")
                    try {
                        currentStreamer?.stop()
                    } catch (_: Exception) {
                    }
                    currentStreamer = null
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Connect fail: ${e.message}", e)
                    try {
                        currentStreamer?.stop()
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
            currentStreamer?.stop()
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

    private fun parseIcecastUrl(rawUrl: String): IcecastConfig? {
        return try {
            val uri = rawUrl.toUri()

            val scheme = uri.scheme?.lowercase()
            val host = uri.host ?: return null
            val port = when {
                uri.port != -1 -> uri.port
                scheme == "icecasts" || scheme == "https" -> 443
                else -> 80
            }

            val userInfo = uri.encodedUserInfo ?: ""
            val parts = userInfo.split(":", limit = 2)
            val username = parts.getOrNull(0)?.let(Uri::decode).orEmpty()
            val password = parts.getOrNull(1)?.let(Uri::decode).orEmpty()

            val mount = uri.encodedPath
                ?.let(Uri::decode)
                ?.takeIf { it.isNotBlank() }
                ?: "/"

            val tls = scheme == "icecasts" || scheme == "https"

            if (username.isBlank() || password.isBlank()) return null

            IcecastConfig(
                host = host,
                port = port,
                mount = mount,
                username = username,
                password = password,
                tls = tls,
                contentType = "audio/aac"
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseIcecastUrl fail: ${e.message}", e)
            null
        }
    }
}